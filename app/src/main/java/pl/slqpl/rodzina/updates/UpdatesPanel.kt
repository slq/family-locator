package pl.slqpl.rodzina.updates

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.slqpl.rodzina.BuildConfig
import java.io.File

@Composable
fun UpdatesPanel(sharing: Boolean) {
    val context = LocalContext.current
    val updates = remember(context) { AppUpdates(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("") }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        message = if (context.packageManager.canRequestPackageInstalls()) "Zgoda przyznana. Wybierz „Zainstaluj aktualizację”."
            else "Aby zaktualizować aplikację, zezwól jej na instalowanie aktualizacji."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aktualizacje", style = MaterialTheme.typography.titleMedium)
            Text("Wersja ${BuildConfig.VERSION_NAME} • aktualizacje z GitHub Releases")
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; message = "Sprawdzanie…"; release = null; downloaded = null
                    try {
                        release = updates.latest()
                        message = release?.let { "Dostępna wersja ${it.versionName}." } ?: "Brak nowszej opublikowanej wersji."
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { message = "Nie udało się sprawdzić aktualizacji. Sprawdź internet i spróbuj ponownie." }
                    finally { busy = false }
                }
            }) { Text("Sprawdź aktualizacje") }
            val available = release
            if (available != null && downloaded == null) {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; progress = 0; message = "Pobieranie aktualizacji…"
                        try {
                            downloaded = updates.download(available) { percent -> scope.launch { progress = percent } }
                            message = "Aktualizacja pobrana i sprawdzona."
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (error: Exception) { message = error.message ?: "Nie udało się pobrać aktualizacji." }
                        finally { busy = false; progress = null }
                    }
                }) { Text("Pobierz ${available.versionName}") }
            }
            progress?.let { Text("Pobrano $it%") }
            val apk = downloaded
            if (apk != null) {
                Button(enabled = !sharing && !busy, onClick = {
                    try {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                        } else context.startActivity(updates.installer(apk))
                    } catch (_: RuntimeException) { message = "Nie udało się otworzyć instalatora. Spróbuj ponownie." }
                }) { Text("Zainstaluj aktualizację") }
                if (sharing) Text("Zatrzymaj pomiary przed instalacją. Po aktualizacji uruchom je ponownie.")
                else Text("Android poprosi o potwierdzenie instalacji. Konto i dane aplikacji zostaną zachowane.")
            }
            if (message.isNotBlank()) Text(message)
        }
    }
}
