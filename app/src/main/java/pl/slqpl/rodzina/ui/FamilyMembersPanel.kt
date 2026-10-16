package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import pl.slqpl.rodzina.cloud.*
import pl.slqpl.rodzina.location.TrackingMode

@Composable
fun FamilyMembersPanel(
    members: List<FamilyMember>, currentUid: String, now: Long,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier, selectedUid: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        members.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val status = memberStatus(member, now)
            val statusColor = when { status.attention -> Color(0xFF986000); status == MemberStatus.SHARING -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
            Surface(onClick = { onSelect(member.uid) },
                color = if (selectedUid == member.uid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("show-member-${member.uid}")) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(member.appearance)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(member.name + if (member.uid == currentUid) " (Ty)" else "", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (status.attention) LocatorIcon(LocatorSymbol.HISTORY, modifier = Modifier.size(14.dp), tint = statusColor)
                            Text(status.label, style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                        Text(member.position?.let { "Pomiar ${measurementAge(it.measuredAt, now)} · ±${it.accuracyMeters.toInt()} m" }
                            ?: "Brak zapisanej pozycji", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    member.position?.batteryPercent?.takeIf { it in 0..100 }?.let {
                        Text("$it%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LocatorIcon(LocatorSymbol.NEXT, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (members.isNotEmpty()) Text("Bateria z ostatniego pomiaru",
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun FamilyMemberDetails(
    member: FamilyMember, members: List<FamilyMember>, currentUid: String, now: Long,
    enabled: Boolean, message: String, onRename: (String, String) -> Unit,
    onTracking: (String, TrackingMode) -> Unit, onChooseIcon: (FamilyIcon?) -> Unit,
    onShowHistory: (String) -> Unit, onShowMap: (() -> Unit)? = null,
    ownMode: TrackingMode? = null, ownRemainingSeconds: Long = 0, onDismiss: () -> Unit = {},
) {
    var renaming by remember(member.uid) { mutableStateOf(false) }
    var name by remember(member.uid) { mutableStateOf(member.name) }
    var choosingIcon by remember(member.uid) { mutableStateOf(false) }
    val self = member.uid == currentUid
    val mode = if (self && ownMode != null) ownMode else member.device?.takeIf { it.fresh(now) }?.mode?.let(TrackingMode::fromWire)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onDismiss) { LocatorIcon(LocatorSymbol.BACK, "Zamknij szczegóły") }
            MemberAvatar(member.appearance)
            Column(Modifier.weight(1f)) {
                Text(member.name + if (self) " (Ty)" else "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(memberStatus(member, now).label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { name = member.name; renaming = true }, enabled = enabled) {
                LocatorIcon(LocatorSymbol.EDIT, "Zmień nazwę")
            }
        }
        val point = member.position
        if (point == null) Text("Brak zapisanej pozycji. Możesz zmienić tryb udostępniania tego telefonu.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MeasurementStat("Pomiar", measurementAge(point.measuredAt, now), Modifier.weight(1f))
                MeasurementStat("Dokładność", "±${point.accuracyMeters.toInt()} m", Modifier.weight(1f))
                MeasurementStat("Bateria", point.batteryPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }
            Text("Bateria z ostatniego pomiaru", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Tryb udostępniania", style = MaterialTheme.typography.titleMedium)
        TrackingControls(member.uid, mode, enabled) { onTracking(member.uid, it) }
        Text("Zwykły: co około 3 min. Dokładny: co około 10 s przez 15 min.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val liveSeconds = if (self) ownRemainingSeconds else ((member.device?.liveUntil ?: now) - now) / 1_000
        if (mode == TrackingMode.LIVE && liveSeconds > 0) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LocatorIcon(LocatorSymbol.TIMER)
                    Column {
                        Text("Jeszcze ${remainingTime(liveSeconds)}", fontWeight = FontWeight.SemiBold)
                        Text("Potem powrót do trybu zwykłego", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val request = member.requestState(now)
        val requestText = when (request) {
            TrackingRequestState.PENDING -> "Oczekiwanie na telefon · ${remainingTime(((member.request!!.expiresAt - now) / 1_000).coerceAtLeast(0))}"
            TrackingRequestState.CONFIRMED -> "Telefon potwierdził tryb"
            TrackingRequestState.CHANGED -> "Telefon zmienił tryb po potwierdzeniu"
            TrackingRequestState.FINISHED -> "Zakończono 15 minut dokładnych pomiarów"
            TrackingRequestState.EXPIRED -> "Polecenie wygasło bez potwierdzenia. Wyślij je ponownie."
            TrackingRequestState.UNAVAILABLE -> "Polecenie było potwierdzone; telefon jest obecnie niedostępny."
            TrackingRequestState.NONE -> if (member.device?.controllable(now) == true) "Sterowanie rodzinne włączone"
                else "Brak aktualnego potwierdzenia telefonu. Polecenie może oczekiwać do 15 min."
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LocatorIcon(if (request == TrackingRequestState.CONFIRMED) LocatorSymbol.CHECK else LocatorSymbol.INFO,
                tint = MaterialTheme.colorScheme.primary)
            Text(requestText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("member-request-state"))
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        if (onShowMap != null && point != null) OutlinedButton(onClick = onShowMap, modifier = Modifier.fillMaxWidth()) {
            LocatorIcon(LocatorSymbol.MAP); Spacer(Modifier.width(8.dp)); Text("Pokaż na mapie")
        }
        OutlinedButton(onClick = { onShowHistory(member.uid) }, modifier = Modifier.fillMaxWidth().testTag("member-history-${member.uid}")) {
            LocatorIcon(LocatorSymbol.HISTORY); Spacer(Modifier.width(8.dp)); Text("Historia na mapie")
        }
        if (self) TextButton(onClick = { choosingIcon = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Wybierz moją ikonkę") }
    }
    if (renaming) AlertDialog(onDismissRequest = { renaming = false },
        title = { Text("Nazwa członka rodziny") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nową nazwę zobaczy cała rodzina.")
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nazwa") }, singleLine = true,
                supportingText = { Text("${name.trim().length}/40") })
        } },
        confirmButton = { TextButton(onClick = { onRename(member.uid, name.trim()); renaming = false },
            enabled = enabled && name.trim().isNotEmpty()) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("Anuluj") } })
    if (choosingIcon) MemberIconDialog(member, members, enabled, onChooseIcon, onDismiss = { choosingIcon = false })
}

@Composable
private fun MeasurementStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemberIconDialog(member: FamilyMember, members: List<FamilyMember>, enabled: Boolean,
    onChoose: (FamilyIcon?) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(member.selectedIcon) }
    val automatic = remember(members, member.uid) {
        FamilyIcons.resolve(members.map { it.uid },
            members.filter { it.uid != member.uid }.mapNotNull { other -> other.selectedIcon?.let { other.uid to it.id } }.toMap())[member.uid]?.icon
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Moja ikonka na mapie") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MemberAvatar(member.appearance.copy(icon = chosen ?: automatic ?: member.appearance.icon))
            }
            Text("Kolor i numer pomagają rozróżnić osoby z tym samym symbolem.")
            FilterChip(selected = chosen == null, onClick = { chosen = null }, label = { Text("Dobierz automatycznie") })
            FamilyIcon.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { icon ->
                        OutlinedButton(onClick = { chosen = icon }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor =
                                if (chosen == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon.glyph, fontSize = 27.sp)
                                Text(icon.label, style = MaterialTheme.typography.labelSmall)
                                if (chosen == icon) Text("Wybrana", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onChoose(chosen); onDismiss() }, enabled = enabled) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } })
}
