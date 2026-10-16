package pl.slqpl.rodzina

import android.Manifest
import android.app.NotificationManager
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import pl.slqpl.rodzina.location.LocationSharingService
import pl.slqpl.rodzina.location.TrackingMode
import pl.slqpl.rodzina.ui.LocatorViewModel
import pl.slqpl.rodzina.ui.FamilyOverview
import pl.slqpl.rodzina.updates.UpdatesPanel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B))) {
                Surface(Modifier.fillMaxSize()) { LocatorScreen() }
            }
        }
    }
}

@Composable
private fun LocatorScreen(vm: LocatorViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val status by vm.status.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val family by vm.family.collectAsStateWithLifecycle()
    val cloudMessage by vm.cloudMessage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val memberBusy by vm.memberBusy.collectAsStateWithLifecycle()
    val memberMessage by vm.memberMessage.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
    val sharingServiceIntent = remember(context) { Intent(context, LocationSharingService::class.java) }
    var label by rememberSaveable { mutableStateOf(prefs.getString("label", Build.MODEL).orEmpty()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // Never save credentials into saved instance state.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var requestedAction by remember { mutableStateOf(LocationSharingService.ACTION_START) }
    var permissionRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val precise = remember(permissionRevision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val notifications = remember(permissionRevision) {
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    val gpsEnabled = remember(permissionRevision) {
        context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
    val unrestricted = remember(permissionRevision) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    fun launchSharing(action: String) {
        prefs.edit { putString("label", label.trim().ifBlank { Build.MODEL }.take(40)) }
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationSharingService::class.java).setAction(action))
        } catch (_: RuntimeException) { vm.report("Nie udało się uruchomić usługi. Sprawdź ustawienia aplikacji.") }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val shown = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        if (granted && shown) launchSharing(requestedAction)
        else vm.report("Potrzebna jest dokładna lokalizacja i zgoda na powiadomienia. Możesz je włączyć w ustawieniach aplikacji.")
    }
    fun requestSharing(action: String) {
        requestedAction = action
        if (precise && notifications) launchSharing(action) else {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Rodzina", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Lokalizacja bliskich • ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        UpdatesPanel(sharing = status.running)
        Panel {
            Text(if (status.running) "Pomiary włączone" else "Pomiary zatrzymane", style = MaterialTheme.typography.titleLarge)
            Text(if (user == null) "Tryb lokalny: pozycja zostaje na tym telefonie." else "Pozycja jest wysyłana do Twojej rodziny w Firebase.")
            if (status.running) {
                Text(status.mode.label)
                if (status.mode == TrackingMode.LIVE) Text("Powrót do trybu okresowego za ${status.remainingSeconds / 60}:${(status.remainingSeconds % 60).toString().padStart(2, '0')}")
            }
            Text(status.message)
            if (user == null) OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Nazwa telefonu / osoby") }, enabled = !status.running, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (status.running) {
                Button(onClick = { context.stopService(sharingServiceIntent) }, modifier = Modifier.fillMaxWidth()) { Text("Zatrzymaj pomiary i wysyłanie") }
                OutlinedButton(onClick = { requestSharing(if (status.mode == TrackingMode.LIVE) LocationSharingService.ACTION_PERIODIC else LocationSharingService.ACTION_LIVE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (status.mode == TrackingMode.LIVE) "Wróć do trybu okresowego" else "Dokładne pomiary przez 15 minut")
                }
            } else {
                Button(onClick = { requestSharing(LocationSharingService.ACTION_START) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (user == null) "Rozpocznij test lokalny" else "Udostępniaj rodzinie")
                }
                OutlinedButton(onClick = { requestSharing(LocationSharingService.ACTION_LIVE) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Dokładne pomiary przez 15 minut") }
            }
        }
        Panel {
            Text("Ostatni pomiar tego telefonu", style = MaterialTheme.typography.titleMedium)
            val point = latest
            if (point == null) Text("Brak pomiaru. Uruchom pomiary i poczekaj na ustalenie pozycji.") else {
                Text("${point.latitude}, ${point.longitude}")
                Text("Dokładność: około ${point.accuracyMeters.toInt()} m • ${ageText(point.measuredAt, now)}")
                Text("Bateria przy pomiarze: ${point.batteryPercent}%")
            }
            Text("Oczekujące punkty: $pending")
            status.lastUploadAt?.let { Text("Ostatnie potwierdzenie serwera: ${DateFormat.getTimeInstance().format(Date(it))}") }
        }
        FamilyOverview(family, latest, user?.uid, now + family.serverTimeOffset,
            memberBusy, memberMessage.ifBlank { cloudMessage }, BuildConfig.MAPS_CONFIGURED,
            vm::renameMember, vm::requestLive, vm::setOwnIcon)

        Panel {
            Text("Rodzina w chmurze", style = MaterialTheme.typography.titleMedium)
            if (!vm.configured) Text("Ten APK działa lokalnie. Konfigurację Firebase dodajemy podczas budowania aplikacji.")
            else if (user == null) {
                Text("Zaloguj się kontem utworzonym dla Ciebie w projekcie Firebase.")
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, enabled = !status.running && !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Hasło") }, visualTransformation = PasswordVisualTransformation(), enabled = !status.running && !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.signIn(email, password); password = "" }, enabled = !status.running && !busy && email.isNotBlank() && password.isNotBlank()) { Text(if (busy) "Logowanie…" else "Zaloguj") }
            } else {
                Text(user?.email.orEmpty())
                SelectionContainer { Text("UID: ${user?.uid}", style = MaterialTheme.typography.bodySmall) }
                Text("W prototypie konto przypisuje do rodziny administrator. Jeden telefon na konto.")
                OutlinedButton(onClick = vm::signOut, enabled = !status.running && !busy && memberBusy == null) { Text("Wyloguj i usuń lokalne pomiary") }
                OutlinedButton(onClick = vm::refreshFamily) { Text("Odśwież rodzinę") }
                Text("Zdalne pomiary włączysz przyciskiem przy osobie na liście rodziny.", style = MaterialTheme.typography.bodySmall)
            }
            if (cloudMessage.isNotBlank()) Text(cloudMessage, color = MaterialTheme.colorScheme.error)
        }
        Panel {
            Text("Diagnostyka", style = MaterialTheme.typography.titleMedium)
            Text("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Text("Dokładna lokalizacja: ${yesNo(precise)}\nPowiadomienia: ${yesNo(notifications)}\nGPS włączony: ${yesNo(gpsEnabled)}\nWyjątek optymalizacji baterii: ${yesNo(unrestricted)}")
            Text("Na Samsungu sprawdź również listę aplikacji usypianych. Wyjątek baterii nie gwarantuje ciągłej pracy.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text("Ustawienia aplikacji") }
            OutlinedButton(onClick = vm::clearLocalData, enabled = !status.running) { Text("Usuń pomiary z tego telefonu") }
            Text("Po restarcie lub zatrzymaniu aplikacji uruchom pomiary ponownie. Zatrzymanie nie usuwa ostatniej pozycji zapisanej w chmurze.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

private fun yesNo(value: Boolean) = if (value) "tak" else "nie"
private fun ageText(measuredAt: Long, now: Long): String {
    val seconds = (now - measuredAt) / 1000
    return when {
        seconds < 0 -> "sprawdź zegar telefonu"
        seconds < 60 -> "$seconds s temu"
        seconds < 3600 -> "${seconds / 60} min temu"
        else -> "${seconds / 3600} godz. temu — pozycja może być nieaktualna"
    }
}
