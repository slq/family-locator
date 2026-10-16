package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import pl.slqpl.rodzina.cloud.FamilyIcon
import pl.slqpl.rodzina.cloud.FamilyMember
import pl.slqpl.rodzina.cloud.FamilyIcons
import pl.slqpl.rodzina.cloud.TrackingRequestState
import pl.slqpl.rodzina.location.TrackingMode

@Composable
fun FamilyMembersPanel(
    members: List<FamilyMember>, currentUid: String, now: Long, busyUid: String?, message: String,
    onRename: (String, String) -> Unit, onTracking: (String, TrackingMode) -> Unit,
    onChooseIcon: (FamilyIcon?) -> Unit,
    selectedUid: String? = null, mapsAvailable: Boolean = true, onShowMember: (String) -> Unit = {},
    ownMode: TrackingMode? = null,
    onShowHistory: (String) -> Unit = {},
) {
    var editingUid by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var choosingIcon by remember(currentUid) { mutableStateOf(false) }
    var chosenIcon by remember(currentUid) { mutableStateOf<FamilyIcon?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Moja rodzina", style = MaterialTheme.typography.titleLarge)
            Text("Każdy członek może zmieniać nazwy oraz włączać i wyłączać udostępnianie u siebie i pozostałych osób.",
                style = MaterialTheme.typography.bodySmall)
            if (members.isEmpty()) Text("Oczekiwanie na listę członków. W razie błędu odśwież rodzinę w sekcji konta.")
            members.forEach { member -> key(member.uid) {
                HorizontalDivider()
                Surface(onClick = { onShowMember(member.uid) }, enabled = mapsAvailable && member.position != null,
                    shape = RoundedCornerShape(12.dp), color = if (selectedUid == member.uid)
                        MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().testTag("show-member-${member.uid}")) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MemberMarkerImage(member.appearance)
                        Column(Modifier.weight(1f)) {
                            Text(member.name + if (member.uid == currentUid) " (Ty)" else "", fontWeight = FontWeight.SemiBold)
                            Text("Znacznik ${member.appearance.number} • ${member.appearance.icon.label}", style = MaterialTheme.typography.bodySmall)
                            if (mapsAvailable && member.position != null) Text("Pokaż na mapie", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (member.uid == currentUid) OutlinedButton(onClick = {
                    chosenIcon = member.selectedIcon; choosingIcon = true
                }, enabled = busyUid == null) { Text("Wybierz moją ikonkę") }
                val available = member.device?.available(now) == true
                Text(when {
                    member.device?.controllable(now) == true && !member.device.sharing -> "Udostępnianie wyłączone • można włączyć zdalnie"
                    !available -> "Brak aktualnego potwierdzenia telefonu"
                    member.device?.mode == "live" && member.device.liveUntil > now ->
                        "Dokładne pomiary • pozostało ${remaining(member.device.liveUntil, now)}"
                    else -> "Udostępnia • tryb okresowy"
                })
                member.position?.let { point ->
                    val age = ((now - point.measuredAt) / 60_000).coerceAtLeast(0)
                    Text("Ostatni pomiar: ${if (age == 0L) "przed chwilą" else "$age min temu"} • dokładność ${point.accuracyMeters.toInt()} m • bateria ${point.batteryPercent}%",
                        style = MaterialTheme.typography.bodySmall)
                } ?: Text("Brak zapisanej pozycji", style = MaterialTheme.typography.bodySmall)
                when (member.requestState(now)) {
                    TrackingRequestState.PENDING -> Text("Wysłano: ${requestedMode(member.request!!.mode)} • oczekiwanie na telefon (${remaining(member.request.expiresAt, now)})")
                    TrackingRequestState.CONFIRMED -> Text("Telefon potwierdził: ${requestedMode(member.request!!.mode)}")
                    TrackingRequestState.CHANGED -> Text("Po potwierdzeniu telefon zmienił tryb")
                    TrackingRequestState.FINISHED -> Text("Zakończono 15 minut dokładnych pomiarów")
                    TrackingRequestState.EXPIRED -> Text("Polecenie wygasło bez potwierdzenia. Wyślij je ponownie.")
                    TrackingRequestState.UNAVAILABLE -> Text("Polecenie było potwierdzone • telefon obecnie niedostępny")
                    TrackingRequestState.NONE -> Unit
                }
                OutlinedButton(onClick = { editingUid = member.uid; name = member.name }, enabled = busyUid == null) {
                    Text("Zmień nazwę")
                }
                OutlinedButton(onClick = { onShowHistory(member.uid) }, modifier = Modifier.testTag("member-history-${member.uid}")) {
                    Text("Historia na mapie")
                }
                TrackingControls(member.uid, if (member.uid == currentUid && ownMode != null) ownMode
                    else member.device?.takeIf { it.fresh(now) }?.mode?.let(TrackingMode::fromWire),
                    enabled = busyUid == null, onSelect = { onTracking(member.uid, it) })
                if (busyUid == member.uid) Text("Wysyłanie polecenia…")
                if (member.device?.controllable(now) != true) Text("Zdalne sterowanie wymaga wersji 0.6.0 i włączonego sterowania rodzinnego na tym telefonie. Polecenie czeka maksymalnie 15 min; po restarcie lub pełnym zatrzymaniu aplikacji trzeba uruchomić sterowanie ponownie.",
                    style = MaterialTheme.typography.bodySmall)
            } }
            if (message.isNotBlank()) Text(message)
        }
    }
    val editing = members.firstOrNull { it.uid == editingUid }
    if (editing != null) AlertDialog(
        onDismissRequest = { editingUid = null },
        title = { Text("Nazwa członka rodziny") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nową nazwę zobaczy cała rodzina.")
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("Nazwa") }, supportingText = { Text("${name.trim().length}/40") })
        } },
        confirmButton = { TextButton(onClick = { onRename(editing.uid, name.trim()); editingUid = null },
            enabled = name.trim().isNotEmpty() && busyUid == null) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { editingUid = null }) { Text("Anuluj") } },
    )
    val current = members.firstOrNull { it.uid == currentUid }
    val automaticIcon = remember(members, currentUid) {
        val others = members.mapNotNull { member ->
            member.selectedIcon?.takeIf { member.uid != currentUid }?.let { member.uid to it.id }
        }.toMap()
        FamilyIcons.resolve(members.map { it.uid }, others)[currentUid]?.icon
    }
    if (choosingIcon && current != null) AlertDialog(
        onDismissRequest = { choosingIcon = false },
        title = { Text("Moja ikonka na mapie") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MemberMarkerImage(current.appearance.copy(icon = chosenIcon ?: automaticIcon ?: current.appearance.icon))
            }
            Text("Wybraną ikonkę zobaczy cała rodzina. Kolor i numer pomagają rozróżnić osoby z tym samym symbolem.")
            FilterChip(selected = chosenIcon == null, onClick = { chosenIcon = null }, label = { Text("Dobierz automatycznie") })
            FamilyIcon.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { icon ->
                        OutlinedButton(onClick = { chosenIcon = icon }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor =
                                if (chosenIcon == icon) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon.glyph, fontSize = 27.sp)
                                Text(icon.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                                if (chosenIcon == icon) Text("Wybrana", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onChooseIcon(chosenIcon); choosingIcon = false },
            enabled = busyUid == null) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { choosingIcon = false }) { Text("Anuluj") } },
    )
}

private fun requestedMode(mode: TrackingMode) = when (mode) {
    TrackingMode.PERIODIC -> "udostępnianie położenia"
    TrackingMode.LIVE -> "dokładne pomiary przez 15 min"
    TrackingMode.STOPPED -> "wyłączenie udostępniania"
}

private fun remaining(until: Long, now: Long): String {
    val seconds = ((until - now).coerceAtLeast(0) + 999) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
