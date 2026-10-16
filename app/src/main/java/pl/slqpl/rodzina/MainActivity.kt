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
import pl.slqpl.rodzina.ui.*
import pl.slqpl.rodzina.cloud.HistoryPolicy
import pl.slqpl.rodzina.updates.UpdatesPanel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocatorTheme {
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
    val history by vm.history.collectAsStateWithLifecycle()
    val historyBusy by vm.historyBusy.collectAsStateWithLifecycle()
    val historyMessage by vm.historyMessage.collectAsStateWithLifecycle()
    LaunchedEffect(user?.uid) { vm.maintainHistory() }
    val prefs = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
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
    fun changeOwnTracking(mode: TrackingMode) {
        if (mode == TrackingMode.STOPPED) {
            if (!status.running || user == null) {
                LocationSharingService.stop(context)
                vm.report("Udostępnianie i sterowanie rodzinne są wyłączone.")
            } else {
                // Stopping must work even if a permission or notifications were just disabled.
                launchSharing(LocationSharingService.ACTION_PAUSE)
            }
            return
        }
        requestSharing(when (mode) {
            TrackingMode.PERIODIC -> LocationSharingService.ACTION_PERIODIC
            TrackingMode.LIVE -> LocationSharingService.ACTION_LIVE
            TrackingMode.STOPPED -> LocationSharingService.ACTION_PAUSE
        })
    }

    var tabName by rememberSaveable { mutableStateOf(LocatorTab.MAP.name) }
    val tab = LocatorTab.valueOf(tabName)
    var focusUid by rememberSaveable(user?.uid) { mutableStateOf<String?>(null) }
    var focusRequest by rememberSaveable { mutableIntStateOf(0) }
    val screenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val serverNow = now + family.serverTimeOffset
    LaunchedEffect(tab, user?.uid) {
        if (tab == LocatorTab.HISTORY && user != null && history.selection == null) {
            vm.showHistory(user!!.uid, HistoryPolicy.day(serverNow))
        }
    }
    androidx.activity.compose.BackHandler(enabled = tab != LocatorTab.MAP) { tabName = LocatorTab.MAP.name }
    LocatorScaffold(tab, onTab = { tabName = it.name }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            ScreenHeading(
                when (tab) {
                    LocatorTab.MAP -> "Rodzina"
                    LocatorTab.FAMILY -> "Moja rodzina"
                    LocatorTab.HISTORY -> "Historia"
                    LocatorTab.SETTINGS -> "Ustawienia"
                },
                when (tab) {
                    LocatorTab.MAP -> "Pozycje bliskich"
                    LocatorTab.FAMILY -> "Położenie i udostępnianie"
                    LocatorTab.HISTORY -> "Ostatnie 30 dni"
                    LocatorTab.SETTINGS -> "Twój telefon i konto"
                },
                onSettings = if (tab == LocatorTab.MAP) ({ tabName = LocatorTab.SETTINGS.name }) else null,
            )
            if (tab == LocatorTab.MAP) SharingSummary(status, user == null) { tabName = LocatorTab.SETTINGS.name }
            Box(Modifier.weight(1f)) {
                screenState.SaveableStateProvider("${user?.uid.orEmpty()}-${tab.name}") {
                    when (tab) {
                        LocatorTab.MAP, LocatorTab.FAMILY -> FamilyOverview(
                            family, latest, user?.uid, serverNow, memberBusy,
                            memberMessage.ifBlank { cloudMessage }, BuildConfig.MAPS_CONFIGURED,
                            vm::renameMember,
                            onTracking = { uid, mode ->
                                if (uid == user?.uid) changeOwnTracking(mode) else vm.requestTracking(uid, mode)
                            },
                            onChooseIcon = vm::setOwnIcon, ownMode = status.mode,
                            onShowHistory = { uid ->
                                vm.showHistory(uid, HistoryPolicy.day(serverNow)); tabName = LocatorTab.HISTORY.name
                            },
                            modifier = Modifier.fillMaxSize(), showMap = tab == LocatorTab.MAP,
                            onOpenFamily = { tabName = LocatorTab.FAMILY.name },
                            onOpenSettings = { tabName = LocatorTab.SETTINGS.name },
                            onOpenMap = { uid -> focusUid = uid; focusRequest++; tabName = LocatorTab.MAP.name },
                            focusUid = focusUid, focusRequest = focusRequest, ownRemainingSeconds = status.remainingSeconds, actionsEnabled = !busy && !historyBusy, ownMessage = status.message,
                        )
                        LocatorTab.HISTORY -> {
                            val uid = user?.uid
                            if (uid == null) {
                                Column(Modifier.padding(20.dp)) {
                                    EmptyState("Historia Twojej rodziny", "Zaloguj się, aby przeglądać zapisane trasy bliskich.",
                                        "Przejdź do konta", { tabName = LocatorTab.SETTINGS.name })
                                }
                            } else HistoryPanel(
                                family.members, uid, serverNow, history, historyBusy, historyMessage,
                                BuildConfig.MAPS_CONFIGURED, vm::showHistory, vm::deleteHistory,
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        LocatorTab.SETTINGS -> Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SectionCard {
                                Text("Udostępnianie tego telefonu", style = MaterialTheme.typography.titleLarge)
                                Text(if (user == null) "Tryb lokalny: pozycja zostaje na tym telefonie."
                                    else if (status.sharing) "Pozycja jest wysyłana do Twojej rodziny."
                                    else "Nie pobieramy ani nie wysyłamy nowych pozycji.")
                                if (status.mode == TrackingMode.LIVE) {
                                    Text("Tryb dokładny · jeszcze ${remainingTime(status.remainingSeconds)}", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(status.message, style = MaterialTheme.typography.bodyMedium)
                                if (user == null) OutlinedTextField(
                                    value = label, onValueChange = { label = it.take(40) },
                                    label = { Text("Nazwa telefonu / osoby") }, enabled = !status.running,
                                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                                )
                                TrackingControls("self", status.mode, enabled = !busy && memberBusy == null && !historyBusy, onSelect = ::changeOwnTracking)
                                Text("Zwykły: pomiar co około 3 min. Dokładny: co około 10 s przez 15 min.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                if (status.running) {
                                    Text("Sterowanie rodzinne włączone", fontWeight = FontWeight.SemiBold)
                                    Text("Rodzina może zmienić tryb i wznowić udostępnianie przy wygaszonym ekranie. Usługa ma widoczne powiadomienie.",
                                        style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { LocationSharingService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Wyłącz udostępnianie i sterowanie rodzinne")
                                    }
                                } else if (user != null) {
                                    OutlinedButton(
                                        onClick = { requestSharing(LocationSharingService.ACTION_PAUSE) },
                                        enabled = !busy && memberBusy == null && !historyBusy, modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Włącz samo sterowanie rodzinne") }
                                }
                                Text("Wyłączenie samego udostępniania pozostawia odbiór poleceń. Po restarcie lub pełnym zatrzymaniu uruchom usługę ponownie.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SectionCard {
                                Text("Konto i rodzina", style = MaterialTheme.typography.titleLarge)
                                if (!vm.configured) Text("Ten APK działa lokalnie. Konfigurację Firebase dodajemy podczas budowania aplikacji.")
                                else if (user == null) {
                                    Text("Zaloguj się kontem utworzonym dla Ciebie w projekcie Firebase.")
                                    OutlinedTextField(email, { email = it }, label = { Text("E-mail") },
                                        enabled = !status.running && !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                                    OutlinedTextField(password, { password = it }, label = { Text("Hasło") },
                                        visualTransformation = PasswordVisualTransformation(), enabled = !status.running && !busy,
                                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                                    Button(onClick = { vm.signIn(email, password); password = "" },
                                        enabled = !status.running && !busy && email.isNotBlank() && password.isNotBlank()) {
                                        Text(if (busy) "Logowanie…" else "Zaloguj")
                                    }
                                    if (status.running) Text("Przed logowaniem wyłącz usługę tego telefonu.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(user?.email.orEmpty())
                                    OutlinedButton(onClick = vm::refreshFamily) { Text("Odśwież rodzinę") }
                                    OutlinedButton(onClick = vm::signOut, enabled = !status.running && !busy && memberBusy == null && !historyBusy) {
                                        Text("Wyloguj i usuń lokalne pomiary")
                                    }
                                    if (status.running) Text("Przed wylogowaniem wyłącz również sterowanie rodzinne.", style = MaterialTheme.typography.bodySmall)
                                    SelectionContainer { Text("UID: ${user?.uid}", style = MaterialTheme.typography.bodySmall) }
                                }
                                if (cloudMessage.isNotBlank()) Text(cloudMessage, color = MaterialTheme.colorScheme.error)
                            }
                            UpdatesPanel(sharing = status.running)
                            SectionCard {
                                Text("Ostatni pomiar", style = MaterialTheme.typography.titleLarge)
                                val point = latest
                                if (point == null) Text("Brak pomiaru. Uruchom pomiary i poczekaj na ustalenie pozycji.")
                                else {
                                    SelectionContainer { Text("${point.latitude}, ${point.longitude}") }
                                    Text("Dokładność ±${point.accuracyMeters.toInt()} m · ${measurementAge(point.measuredAt, now)}")
                                    Text("Bateria przy pomiarze: ${point.batteryPercent}%")
                                }
                                Text("Oczekujące punkty: $pending", style = MaterialTheme.typography.bodySmall)
                                status.lastUploadAt?.let { Text("Potwierdzenie serwera: ${DateFormat.getTimeInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                            }
                            SectionCard {
                                Text("Diagnostyka", style = MaterialTheme.typography.titleLarge)
                                Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                                Text("Dokładna lokalizacja: ${yesNo(precise)}\nPowiadomienia: ${yesNo(notifications)}\nGPS: ${yesNo(gpsEnabled)}\nWyjątek baterii: ${yesNo(unrestricted)}")
                                Text("Na Samsungu sprawdź także listę aplikacji usypianych. Wyjątek baterii nie gwarantuje ciągłej pracy.",
                                    style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) {
                                    Text("Ustawienia aplikacji")
                                }
                                OutlinedButton(onClick = vm::clearLocalData, enabled = !status.running) { Text("Usuń lokalne pomiary") }
                                Text("Zatrzymanie nie usuwa ostatniej pozycji zapisanej w chmurze.", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun yesNo(value: Boolean) = if (value) "tak" else "nie"
