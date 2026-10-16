package pl.slqpl.rodzina.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.MainActivity
import pl.slqpl.rodzina.R
import pl.slqpl.rodzina.data.LocationSample
import java.util.concurrent.TimeUnit

class LocationSharingService : Service() {
    private val app get() = application as FamilyLocatorApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = TrackingPolicy()
    private val syncMutex = Mutex()
    private val presenceMutex = Mutex()
    private var remotePolicy = RemoteLivePolicy()
    private val pendingAcknowledgments = mutableMapOf<String, RemoteLiveRequest>()
    private var connected = false
    private var presenceReady = false
    private var serverTimeOffset = 0L
    private var cleanedUp = false
    private var notifiedRemote = false
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var appliedMode: TrackingMode? = null
    private var started = false
    private var ownerUid: String? = null
    private var label = ""
    private var lastMeasuredElapsedNanos = -1L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!started) return
            val point = result.lastLocation ?: return
            val age = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos)
            if (!SamplePolicy.acceptable(point.latitude, point.longitude, point.accuracy, age) ||
                point.elapsedRealtimeNanos <= lastMeasuredElapsedNanos) return
            lastMeasuredElapsedNanos = point.elapsedRealtimeNanos
            val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
            scope.launch {
                app.database.locations().insert(LocationSample(
                    ownerUid = ownerUid, latitude = point.latitude, longitude = point.longitude,
                    accuracyMeters = point.accuracy, measuredAt = point.time, batteryPercent = battery,
                ))
                app.tracking.update { it.copy(message = if (ownerUid == null)
                    "Pomiar zapisany tylko na tym telefonie" else "Pomiar zapisany; oczekiwanie na synchronizację") }
                syncPending()
            }
        }
        override fun onLocationAvailability(availability: LocationAvailability) {
            if (started && !availability.isLocationAvailable) app.tracking.update {
                it.copy(message = "Oczekiwanie na lokalizację — sprawdź GPS i wyjdź na zewnątrz")
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        if (!fine || !notifications) {
            app.tracking.update { it.copy(message = "Włącz dokładną lokalizację i powiadomienia, aby rozpocząć") }
            shutdown()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_LIVE) policy.startLive(SystemClock.elapsedRealtime())
        if (intent.action == ACTION_PERIODIC) {
            val cancelled = remotePolicy.cancel()
            pendingAcknowledgments.clear()
            policy.reset()
            ownerUid?.let { uid -> scope.launch {
                try { withTimeout(5_000) { app.cloud.acknowledge(uid, cancelled, "stopped") } }
                catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled }
                catch (_: Exception) { /* The device mode and heartbeat also reflect the local stop. */ }
            } }
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Udostępnianie lokalizacji", NotificationManager.IMPORTANCE_LOW))
            val serviceType = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), serviceType)
            if (!started) {
                ownerUid = app.cloud.user.value?.uid
                label = getSharedPreferences("settings", MODE_PRIVATE).getString("label", Build.MODEL).orEmpty()
                ownerUid?.let { uid ->
                    val prefix = "$uid:"
                    val consumed = getSharedPreferences("remote_live", MODE_PRIVATE).all
                        .filterKeys { it.startsWith(prefix) }.mapNotNull { (key, value) ->
                            (value as? String)?.let { key.removePrefix(prefix) to it }
                        }.toMap().toMutableMap()
                    remotePolicy = RemoteLivePolicy(consumed)
                }
                started = true
                ownerUid?.let { uid -> scope.launch { receiveRemoteRequests(uid) } }
                scope.launch {
                    while (isActive) {
                        applyMode()
                        delay(1_000)
                    }
                }
                scope.launch {
                    while (isActive) {
                        syncPending()
                        flushAcknowledgments()
                        publishPresence()
                        delay(30_000)
                    }
                }
            }
            applyMode()
        } catch (_: SecurityException) {
            app.tracking.update { it.copy(message = "System odmówił dostępu do lokalizacji") }
            shutdown()
        } catch (_: IllegalStateException) {
            app.tracking.update { it.copy(message = "Otwórz aplikację, aby uruchomić udostępnianie") }
            shutdown()
        }
        // Never silently restart after the user/system stops sharing. Start explicitly from the UI.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // Both runtime permissions and start context are checked above.
    private fun applyMode() {
        val elapsedNow = SystemClock.elapsedRealtime()
        policy.setRemoteDeadline(remotePolicy.deadline(elapsedNow))
        val mode = policy.mode(elapsedNow)
        app.tracking.update { it.copy(running = true, mode = mode,
            remainingSeconds = policy.remainingSeconds(SystemClock.elapsedRealtime())) }
        val remoteActive = remotePolicy.active(elapsedNow).isNotEmpty()
        if (appliedMode == mode) {
            if (notifiedRemote != remoteActive) {
                notifiedRemote = remoteActive
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            }
            return
        }
        notifiedRemote = remoteActive
        appliedMode = mode
        fused.removeLocationUpdates(callback)
        val priority = if (mode == TrackingMode.LIVE) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, mode.intervalMillis)
            .setMinUpdateIntervalMillis(mode.intervalMillis)
            .setMaxUpdateDelayMillis(0)
            .setMaxUpdateAgeMillis(0)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    app.tracking.update { it.copy(message = "Nie udało się uruchomić pomiarów. Sprawdź uprawnienia i usługi Google Play.") }
                    shutdown()
                }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            scope.launch { publishPresence() }
        } catch (_: SecurityException) {
            app.tracking.update { it.copy(message = "Cofnięto uprawnienie lokalizacji") }
            shutdown()
        }
    }

    private suspend fun receiveRemoteRequests(uid: String) {
        app.cloud.observeRemoteControl(uid).retryWhen { error, _ ->
            if (error is CancellationException) throw error
            connected = false; presenceReady = false
            remotePolicy.cancel(); pendingAcknowledgments.clear()
            if (started) {
                applyMode()
                app.tracking.update { it.copy(message = "Brak zdalnego sterowania. Sprawdź połączenie i reguły rodziny.") }
            }
            delay(5_000)
            started && app.cloud.user.value?.uid == uid
        }.collect { control ->
            if (!started || app.cloud.user.value?.uid != uid) return@collect
            serverTimeOffset = control.serverTimeOffset
            connected = control.connected
            if (!connected) { presenceReady = false; return@collect }
            val accepted = remotePolicy.reconcile(control.requests,
                System.currentTimeMillis() + serverTimeOffset, SystemClock.elapsedRealtime())
            getSharedPreferences("remote_live", MODE_PRIVATE).edit {
                remotePolicy.consumed.forEach { (requester, requestId) -> putString("$uid:$requester", requestId) }
            }
            accepted.forEach { pendingAcknowledgments[it.requesterUid] = it }
            applyMode()
            if (!started) return@collect
            if (accepted.isNotEmpty()) app.tracking.update {
                it.copy(message = "Włączono dokładne pomiary na prośbę rodziny")
            }
            flushAcknowledgments()
            publishPresence()
        }
    }

    private suspend fun flushAcknowledgments() {
        val uid = ownerUid ?: return
        if (!started || !connected) return
        val active = remotePolicy.active(SystemClock.elapsedRealtime()).associateBy { it.requesterUid }
        pendingAcknowledgments.entries.removeAll { (key, request) -> active[key]?.id != request.id }
        val pending = pendingAcknowledgments.values.toList()
        if (pending.isEmpty()) return
        try {
            withTimeout(5_000) { app.cloud.acknowledge(uid, pending, "active") }
            pending.forEach { if (pendingAcknowledgments[it.requesterUid]?.id == it.id) pendingAcknowledgments.remove(it.requesterUid) }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
        } catch (_: Exception) { /* Keep unacknowledged requests for the next online heartbeat. */ }
    }

    private suspend fun publishPresence() = presenceMutex.withLock {
        val uid = ownerUid ?: return@withLock
        if (!started || !connected || app.cloud.user.value?.uid != uid) return@withLock
        try {
            withTimeout(5_000) {
                if (!presenceReady) { app.cloud.registerDisconnect(uid); presenceReady = true }
                val elapsedNow = SystemClock.elapsedRealtime()
                val mode = policy.mode(elapsedNow)
                val until = if (mode == TrackingMode.LIVE)
                    System.currentTimeMillis() + serverTimeOffset + policy.remainingSeconds(elapsedNow) * 1000 else 0
                app.cloud.publishDevice(uid, mode, until)
            }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
        } catch (_: Exception) { /* Old heartbeats become unavailable after ninety seconds. */ }
    }

    private suspend fun syncPending() = syncMutex.withLock {
        val dao = app.database.locations()
        dao.prune(System.currentTimeMillis() - SamplePolicy.RETENTION_MILLIS, SamplePolicy.MAX_SAMPLES)
        val uid = ownerUid ?: return@withLock
        if (!started || app.cloud.user.value?.uid != uid) return@withLock
        val sample = dao.newestPending(uid) ?: return@withLock
        try {
            withTimeout(15_000) { app.cloud.upload(sample, label) }
            dao.acknowledge(uid, sample.id)
            app.tracking.update { it.copy(lastUploadAt = System.currentTimeMillis(), message = "Ostatnia pozycja zsynchronizowana") }
        } catch (_: TimeoutCancellationException) {
            app.cloud.cancelPendingWrites()
            app.tracking.update { it.copy(message = "Brak potwierdzenia z serwera — ponowimy wysyłkę") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            app.tracking.update { it.copy(message = "Nie wysłano pozycji — sprawdź internet, konto i reguły Firebase") }
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, LocationSharingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val destination = if (ownerUid == null && !started) app.cloud.user.value?.uid else ownerUid
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(if (destination == null) "Rodzina — lokalny test lokalizacji" else "Udostępniasz lokalizację rodzinie")
            .setContentText(if (remotePolicy.active(SystemClock.elapsedRealtime()).isNotEmpty())
                "Dokładne pomiary — prośba rodziny" else policy.mode(SystemClock.elapsedRealtime()).label)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Zatrzymaj", stop).build()
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        started = false
        val cancelled = remotePolicy.cancel()
        scope.cancel()
        fused.removeLocationUpdates(callback)
        app.cloud.cancelPendingWrites()
        ownerUid?.let { app.cloud.stopDevice(it, cancelled) }
        app.tracking.update { it.copy(running = false, remainingSeconds = 0) }
    }
    private fun shutdown() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "pl.slqpl.rodzina.START"
        const val ACTION_STOP = "pl.slqpl.rodzina.STOP"
        const val ACTION_LIVE = "pl.slqpl.rodzina.LIVE"
        const val ACTION_PERIODIC = "pl.slqpl.rodzina.PERIODIC"
        private const val CHANNEL = "location_sharing"
        private const val NOTIFICATION_ID = 1
    }
}
