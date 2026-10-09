package pl.slawek.rodzina.location

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
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.slawek.rodzina.FamilyLocatorApp
import pl.slawek.rodzina.MainActivity
import pl.slawek.rodzina.R
import pl.slawek.rodzina.data.LocationSample
import java.util.concurrent.TimeUnit

class LocationSharingService : Service() {
    private val app get() = application as FamilyLocatorApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = TrackingPolicy()
    private val syncMutex = Mutex()
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
        if (intent.action == ACTION_PERIODIC) policy.reset()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Udostępnianie lokalizacji", NotificationManager.IMPORTANCE_LOW))
            val serviceType = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), serviceType)
            if (!started) {
                ownerUid = app.cloud.user.value?.uid
                label = getSharedPreferences("settings", MODE_PRIVATE).getString("label", Build.MODEL).orEmpty()
                started = true
                scope.launch {
                    while (isActive) {
                        applyMode()
                        delay(1_000)
                    }
                }
                scope.launch {
                    while (isActive) {
                        syncPending()
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
        val mode = policy.mode(SystemClock.elapsedRealtime())
        app.tracking.update { it.copy(running = true, mode = mode,
            remainingSeconds = policy.remainingSeconds(SystemClock.elapsedRealtime())) }
        if (appliedMode == mode) return
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
        } catch (_: SecurityException) {
            app.tracking.update { it.copy(message = "Cofnięto uprawnienie lokalizacji") }
            shutdown()
        }
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
            .setContentText(policy.mode(SystemClock.elapsedRealtime()).label)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Zatrzymaj", stop).build()
    }

    private fun shutdown() {
        started = false
        scope.cancel()
        fused.removeLocationUpdates(callback)
        app.cloud.cancelPendingWrites()
        app.tracking.update { it.copy(running = false, remainingSeconds = 0) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        started = false
        scope.cancel()
        fused.removeLocationUpdates(callback)
        app.cloud.cancelPendingWrites()
        app.tracking.update { it.copy(running = false, remainingSeconds = 0) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "pl.slawek.rodzina.START"
        const val ACTION_STOP = "pl.slawek.rodzina.STOP"
        const val ACTION_LIVE = "pl.slawek.rodzina.LIVE"
        const val ACTION_PERIODIC = "pl.slawek.rodzina.PERIODIC"
        private const val CHANNEL = "location_sharing"
        private const val NOTIFICATION_ID = 1
    }
}
