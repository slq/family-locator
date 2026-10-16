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
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.MainActivity
import pl.slqpl.rodzina.R
import pl.slqpl.rodzina.data.LocationSample
import pl.slqpl.rodzina.cloud.HistoryPolicy
import java.util.concurrent.TimeUnit

class LocationSharingService : Service() {
    private val app get() = application as FamilyLocatorApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = TrackingPolicy()
    private val syncMutex = Mutex()
    private val modeMutex = Mutex()
    private var remotePolicy = RemoteTrackingPolicy(SystemClock.elapsedRealtime())
    private var activeRemote: TrackingRequest? = null
    private var pendingAck: TrackingRequest? = null
    private var connected = false
    private var presenceReady = false
    private var modeReady = false
    private var serverTimeOffset = 0L
    private var cleanedUp = false
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var appliedMode: TrackingMode? = null
    private var started = false
    private var ownerUid: String? = null
    private var label = ""
    private var lastMeasuredElapsedNanos = -1L
    private var measurementsJob: Job? = null
    private var modeJob: Job? = null
    private var uploadJob: Job? = null
    private var presenceJob: Job? = null
    private var pauseCleanup: Job? = null

    private fun sharing() = started && policy.mode(SystemClock.elapsedRealtime()) != TrackingMode.STOPPED

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!sharing() || !modeReady) return
            val job = measurementsJob ?: return
            val point = result.lastLocation ?: return
            val age = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos)
            if (!SamplePolicy.acceptable(point.latitude, point.longitude, point.accuracy, age) ||
                point.elapsedRealtimeNanos <= lastMeasuredElapsedNanos) return
            lastMeasuredElapsedNanos = point.elapsedRealtimeNanos
            val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
            scope.launch(job) {
                pauseCleanup?.join()
                ensureActive()
                if (!sharing()) return@launch
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
            if (sharing() && !availability.isLocationAvailable) app.tracking.update {
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
            shutdown("Włącz dokładną lokalizację i powiadomienia, aby rozpocząć")
            return START_NOT_STICKY
        }
        val mode = when (intent.action) {
            ACTION_LIVE -> TrackingMode.LIVE
            ACTION_PAUSE -> TrackingMode.STOPPED
            else -> TrackingMode.PERIODIC
        }
        val elapsedNow = SystemClock.elapsedRealtime()
        policy.setMode(mode, elapsedNow)
        activeRemote = null; pendingAck = null
        remotePolicy.localChange(elapsedNow)
        try {
            if (!started) {
                ownerUid = app.cloud.user.value?.uid
                label = getSharedPreferences("settings", MODE_PRIVATE).getString("label", Build.MODEL).orEmpty()
                remotePolicy = RemoteTrackingPolicy(elapsedNow,
                    ownerUid?.let { getSharedPreferences("remote_tracking", MODE_PRIVATE).getString(it, null) })
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Lokalizacja i sterowanie rodzinne", NotificationManager.IMPORTANCE_LOW))
                val serviceType = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), serviceType)
                started = true
                // Preserve an offline queue across a process crash; an explicit stop marks it for discarding.
                ownerUid?.let { uid ->
                    if (getSharedPreferences("queue_policy", MODE_PRIVATE).getBoolean(uid, false))
                        pauseCleanup = scope.launch {
                            app.database.locations().discardPending(uid)
                            getSharedPreferences("queue_policy", MODE_PRIVATE).edit { putBoolean(uid, false) }
                        }
                }
                ownerUid?.let { uid -> scope.launch { receiveRemoteRequests(uid) } }
                scope.launch {
                    while (isActive) { applyMode(); delay(1_000) }
                }
                scope.launch {
                    while (isActive) {
                        if (sharing() && uploadJob?.isActive != true) measurementsJob?.let { job ->
                            uploadJob = scope.launch(job) { syncPending() }
                        }
                        requestPresence()
                        delay(30_000)
                    }
                }
                ownerUid?.let { uid -> scope.launch {
                    while (isActive) {
                        try { withTimeout(30_000) { app.cloud.history.maintain(uid) } }
                        catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled }
                        catch (_: Exception) { /* Maintenance resumes at the next run or in the history screen. */ }
                        delay(60 * 60 * 1000L)
                    }
                } }
            }
            app.tracking.update { it.copy(message = if (mode == TrackingMode.STOPPED)
                "Nie pobieramy ani nie wysyłamy pozycji. Rodzina może wznowić udostępnianie."
                else "Uruchamianie udostępniania") }
            applyMode()
            requestPresence()
        } catch (_: SecurityException) {
            shutdown("System odmówił dostępu do lokalizacji")
        } catch (_: IllegalStateException) {
            shutdown("Otwórz aplikację, aby uruchomić sterowanie rodzinne")
        }
        // A force stop, process death or restart requires an explicit start from the app.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun applyMode() {
        if (!started) return
        val elapsedNow = SystemClock.elapsedRealtime()
        val mode = policy.mode(elapsedNow)
        app.tracking.update { it.copy(running = true, mode = mode, remainingSeconds = policy.remainingSeconds(elapsedNow)) }
        if (appliedMode == mode) return
        appliedMode = mode
        modeReady = false
        modeJob?.cancel()
        presenceJob?.cancel()
        if (mode == TrackingMode.STOPPED) {
            val previousMeasurements = measurementsJob
            previousMeasurements?.cancel(); measurementsJob = null
            app.cloud.cancelPendingWrites()
            ownerUid?.let { getSharedPreferences("queue_policy", MODE_PRIVATE).edit { putBoolean(it, true) } }
            val previousCleanup = pauseCleanup
            pauseCleanup = scope.launch {
                previousCleanup?.join()
                previousMeasurements?.join()
                ownerUid?.let { uid ->
                    app.database.locations().discardPending(uid)
                    getSharedPreferences("queue_policy", MODE_PRIVATE).edit { putBoolean(uid, false) }
                }
            }
        } else if (measurementsJob == null) {
            measurementsJob = SupervisorJob(scope.coroutineContext[Job])
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        modeJob = scope.launch {
            try {
                modeMutex.withLock {
                    fused.removeLocationUpdates(callback).await()
                    pauseCleanup?.join()
                    ensureActive()
                    if (mode != TrackingMode.STOPPED) {
                        val priority = if (mode == TrackingMode.LIVE) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                        val request = LocationRequest.Builder(priority, mode.intervalMillis)
                            .setMinUpdateIntervalMillis(mode.intervalMillis).setMaxUpdateDelayMillis(0).setMaxUpdateAgeMillis(0).build()
                        fused.requestLocationUpdates(request, callback, Looper.getMainLooper()).await()
                    }
                    ensureActive()
                    modeReady = true
                    requestPresence()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                shutdown("Nie udało się zmienić pomiarów. Sprawdź uprawnienia i usługi Google Play.")
            }
        }
    }

    private suspend fun receiveRemoteRequests(uid: String) {
        app.cloud.observeRemoteControl(uid).retryWhen { error, _ ->
            if (error is CancellationException) throw error
            connected = false; presenceReady = false
            presenceJob?.cancel()
            if (activeRemote != null) {
                activeRemote = null; pendingAck = null
                policy.setMode(TrackingMode.STOPPED, SystemClock.elapsedRealtime())
                applyMode()
            }
            if (started) app.tracking.update { it.copy(message = "Brak zdalnego sterowania. Sprawdź połączenie i reguły rodziny.") }
            delay(5_000)
            started && app.cloud.user.value?.uid == uid
        }.collect { control ->
            if (!started || app.cloud.user.value?.uid != uid) return@collect
            serverTimeOffset = control.serverTimeOffset
            connected = control.connected
            if (!connected) { presenceReady = false; return@collect }
            if (control.request == null && activeRemote != null) {
                // A command author removed from this family cannot keep a remote session running.
                activeRemote = null; pendingAck = null
                policy.setMode(TrackingMode.STOPPED, SystemClock.elapsedRealtime())
                applyMode()
            }
            val serverNow = System.currentTimeMillis() + serverTimeOffset
            val elapsedNow = SystemClock.elapsedRealtime()
            val accepted = remotePolicy.accept(control.request, serverNow, elapsedNow)
            if (accepted != null) {
                presenceJob?.cancel()
                getSharedPreferences("remote_tracking", MODE_PRIVATE).edit { putString(uid, accepted.id) }
                activeRemote = accepted; pendingAck = accepted
                policy.setMode(accepted.mode, elapsedNow, accepted.expiresAt - serverNow)
                app.tracking.update { it.copy(message = when (accepted.mode) {
                    TrackingMode.STOPPED -> "Rodzina wyłączyła udostępnianie. Nie pobieramy ani nie wysyłamy pozycji."
                    TrackingMode.PERIODIC -> "Rodzina włączyła udostępnianie położenia"
                    TrackingMode.LIVE -> "Rodzina włączyła dokładne pomiary na pozostały czas żądania"
                }) }
                applyMode()
            }
            if (pendingAck?.id != control.request?.id) pendingAck = null
            requestPresence()
        }
    }

    private fun requestPresence() {
        if (!started || !connected || !modeReady || presenceJob?.isActive == true) return
        presenceJob = scope.launch {
            val uid = ownerUid ?: return@launch
            if (app.cloud.user.value?.uid != uid) return@launch
            try {
                withTimeout(5_000) {
                    if (!presenceReady) { app.cloud.registerDisconnect(uid); presenceReady = true }
                    val elapsedNow = SystemClock.elapsedRealtime()
                    val mode = policy.mode(elapsedNow)
                    if (mode != appliedMode) return@withTimeout
                    val until = if (mode == TrackingMode.LIVE)
                        System.currentTimeMillis() + serverTimeOffset + policy.remainingSeconds(elapsedNow) * 1000 else 0
                    val ack = pendingAck?.takeIf { it.mode == mode && activeRemote?.id == it.id }
                    if (ack != null) {
                        app.cloud.acknowledgeTracking(uid, ack, until)
                        if (pendingAck?.id == ack.id) pendingAck = null
                    } else app.cloud.publishDevice(uid, mode, until)
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                app.tracking.update { it.copy(message = "Nie potwierdzono sterowania rodzinnego. Sprawdź połączenie i aktualne reguły Firebase.") }
            }
        }
    }

    private suspend fun syncPending() = syncMutex.withLock {
        pauseCleanup?.join()
        if (!sharing() || !modeReady) return@withLock
        val dao = app.database.locations()
        dao.prune(System.currentTimeMillis() - SamplePolicy.RETENTION_MILLIS, SamplePolicy.MAX_SAMPLES)
        val uid = ownerUid ?: return@withLock
        if (app.cloud.user.value?.uid != uid) return@withLock
        try {
            do {
                val batch = dao.pending(uid, HistoryPolicy.BATCH_SIZE)
                if (batch.isEmpty()) return@withLock
                withTimeout(20_000) {
                    val history = app.cloud.history.uploadContext(uid)
                    dao.deleteThrough(uid, history.deletedThrough)
                    dao.newestPending(uid)?.takeIf { HistoryPolicy.current(it, history.serverNow, history.deletedThrough) }
                        ?.let { app.cloud.upload(it, label) }
                    app.cloud.history.store(history, batch)
                    dao.acknowledge(uid, batch.last().id)
                }
                app.tracking.update { it.copy(lastUploadAt = System.currentTimeMillis(), message = "Pozycja i historia zsynchronizowane") }
                if (batch.size < HistoryPolicy.BATCH_SIZE) break
                delay(50)
            } while (sharing())
        } catch (_: TimeoutCancellationException) {
            app.cloud.cancelPendingWrites()
            app.tracking.update { it.copy(message = "Brak potwierdzenia z serwera — ponowimy wysyłkę") }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            app.tracking.update { it.copy(message = "Nie wysłano pozycji — sprawdź internet, konto i reguły Firebase") }
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, LocationSharingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(this, 2, Intent(this, LocationSharingService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val mode = policy.mode(SystemClock.elapsedRealtime())
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(if (mode == TrackingMode.STOPPED) "Rodzina — udostępnianie wyłączone"
                else if (ownerUid == null) "Rodzina — lokalny test lokalizacji" else "Udostępniasz lokalizację rodzinie")
            .setContentText(if (mode == TrackingMode.STOPPED) "Bez pomiarów. Rodzina może wznowić udostępnianie." else mode.label)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .apply { if (mode != TrackingMode.STOPPED) addAction(0, "Wyłącz udostępnianie", pause) }
            .addAction(0, "Wyłącz też sterowanie", stop).build()
    }

    private fun cleanup(message: String = "Udostępnianie i sterowanie rodzinne wyłączone. Włącz je ponownie z aplikacji.") {
        if (cleanedUp) return
        cleanedUp = true; started = false
        ownerUid?.let { getSharedPreferences("queue_policy", MODE_PRIVATE).edit { putBoolean(it, true) } }
        scope.cancel()
        fused.removeLocationUpdates(callback)
        app.cloud.cancelPendingWrites()
        ownerUid?.let { app.cloud.stopDevice(it) }
        app.tracking.update { it.copy(running = false, mode = TrackingMode.STOPPED, remainingSeconds = 0,
            message = message) }
    }
    private fun shutdown(message: String = "Udostępnianie i sterowanie rodzinne wyłączone. Włącz je ponownie z aplikacji.") {
        cleanup(message); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    companion object {
        const val ACTION_START = "pl.slqpl.rodzina.START"
        const val ACTION_STOP = "pl.slqpl.rodzina.STOP"
        const val ACTION_PAUSE = "pl.slqpl.rodzina.PAUSE"
        const val ACTION_LIVE = "pl.slqpl.rodzina.LIVE"
        const val ACTION_PERIODIC = "pl.slqpl.rodzina.PERIODIC"
        private const val CHANNEL = "location_sharing"
        private const val NOTIFICATION_ID = 1
    }
}
