package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.cloud.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val historyDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val historyShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl"))

@Composable
fun HistoryPanel(
    members: List<FamilyMember>, currentUid: String, now: Long, state: HistorySnapshot,
    busy: Boolean, message: String, mapsAvailable: Boolean,
    onShow: (String, LocalDate) -> Unit, onDelete: (String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = HistoryPolicy.day(now)
    val oldest = HistoryPolicy.day(now - HistoryPolicy.RETENTION_MILLIS)
    var uid by rememberSaveable(currentUid) { mutableStateOf(currentUid) }
    var selectedDay by rememberSaveable(currentUid) { mutableLongStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(selectedDay).coerceIn(oldest, today)
    var selectingPerson by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingDeleteDate by remember { mutableStateOf(false) }
    var confirmDeleteDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.selection) {
        state.selection?.let { uid = it.uid; selectedDay = it.day.toEpochDay() }
    }
    val member = members.firstOrNull { it.uid == uid }
    val matches = member != null && state.selection == HistorySelection(uid, day)
    Column(modifier.fillMaxWidth().testTag("history-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { selectingPerson = true }, enabled = !busy && members.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("history-person"), shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    member?.let { MemberAvatar(it.appearance); Spacer(Modifier.width(12.dp)) }
                    Text((member?.name ?: "Wybierz osobę") + if (uid == currentUid && member != null) " (Ty)" else "",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    LocatorIcon(LocatorSymbol.NEXT)
                }
                DropdownMenu(expanded = selectingPerson, onDismissRequest = { selectingPerson = false }) {
                    members.forEach { person ->
                        DropdownMenuItem(text = { Text(person.name + if (person.uid == currentUid) " (Ty)" else "") },
                            leadingIcon = { Text(person.appearance.icon.glyph) },
                            onClick = { uid = person.uid; selectingPerson = false; onShow(person.uid, day) },
                            modifier = Modifier.testTag("history-person-${person.uid}"))
                    }
                }
            }
            TextButton(onClick = { member?.let { onShow(it.uid, day) } }, enabled = !busy && member != null,
                modifier = Modifier.testTag("history-load")) { Text("Odśwież") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selectedDay = day.minusDays(1).toEpochDay(); onShow(uid, day.minusDays(1)) },
                enabled = !busy && member != null && day > oldest) { LocatorIcon(LocatorSymbol.BACK, "Poprzedni dzień") }
            OutlinedButton(onClick = { pickingDay = true }, enabled = !busy, modifier = Modifier.weight(1f).testTag("history-date")) {
                Text(if (day == today) "Dzisiaj, ${day.format(historyShortDate)}" else day.format(historyDateFormat))
            }
            IconButton(onClick = { selectedDay = day.plusDays(1).toEpochDay(); onShow(uid, day.plusDays(1)) },
                enabled = !busy && member != null && day < today) { LocatorIcon(LocatorSymbol.NEXT, "Następny dzień") }
        }
        when {
            !matches -> EmptyState("Wybierz trasę", "Wybierz osobę i dzień, aby zobaczyć zapisane pomiary.")
            state.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Pobieranie historii…") }
            state.error.isNotBlank() -> Text(state.error, color = MaterialTheme.colorScheme.error)
            state.points.isEmpty() -> Text("Brak zapisanych pomiarów w tym dniu.", modifier = Modifier.testTag("history-empty"))
            !mapsAvailable -> EmptyState("Mapa jest niedostępna", "Ta wersja aplikacji nie ma włączonego widoku mapy.")
            else -> member?.let { selected -> key(uid, day) { HistoryMap(state.points, selected.appearance, selected.name) } }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message)
        Text("Daty i godziny w strefie czasowej tego telefonu.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (uid == currentUid && member != null) {
            TextButton(onClick = { pickingDeleteDate = true }, enabled = !busy,
                modifier = Modifier.testTag("history-delete"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Usuń moją historię do daty…")
            }
        } else Text("Historię może usuwać jej właściciel.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }
    if (pickingDay) HistoryDateDialog("Dzień historii", day, oldest, today,
        onDismiss = { pickingDay = false }, onConfirm = {
            selectedDay = it.toEpochDay(); pickingDay = false; if (member != null) onShow(uid, it)
        })
    if (pickingDeleteDate) HistoryDateDialog("Usuń historię do daty", day, LocalDate.of(1970, 1, 1), today,
        onDismiss = { pickingDeleteDate = false }, onConfirm = { confirmDeleteDay = it; pickingDeleteDate = false })
    confirmDeleteDay?.let { date -> AlertDialog(onDismissRequest = { confirmDeleteDay = null },
        title = { Text("Usunąć historię?") },
        text = { Text("Usuniesz historię osoby ${member?.name.orEmpty()} do ${date.format(historyDateFormat)} włącznie${if (date == today) " (dzisiaj: do teraz)" else ""}. Starsza ostatnia pozycja zniknie również z bieżącej mapy. Tej operacji nie można cofnąć. Nowe pomiary będą nadal zapisywane.") },
        confirmButton = { TextButton(onClick = { confirmDeleteDay = null; onDelete(uid, date) }, enabled = !busy && uid == currentUid,
            modifier = Modifier.testTag("history-confirm-delete")) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { confirmDeleteDay = null }) { Text("Anuluj") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDateDialog(title: String, initial: LocalDate, minimum: LocalDate, maximum: LocalDate,
    onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val selectable = remember(minimum, maximum) { object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in minimum..maximum
        override fun isSelectableYear(year: Int) = year in minimum.year..maximum.year
    } }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = minimum.year..maximum.year, selectableDates = selectable)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
            enabled = state.selectedDateMillis != null, modifier = Modifier.testTag("history-confirm-date")) { Text("Wybierz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }) {
        DatePicker(state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
