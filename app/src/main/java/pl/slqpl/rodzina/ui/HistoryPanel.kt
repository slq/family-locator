package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.cloud.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val historyDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
    val day = LocalDate.ofEpochDay(selectedDay)
    var selectingPerson by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingDeleteDate by remember { mutableStateOf(false) }
    var confirmDeleteDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.selection) {
        state.selection?.let { uid = it.uid; selectedDay = it.day.toEpochDay() }
    }
    val member = members.firstOrNull { it.uid == uid }
    val matches = member != null && state.selection == HistorySelection(uid, day)
    Card(modifier.fillMaxWidth().testTag("history-panel")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Historia położenia", style = MaterialTheme.typography.titleLarge)
            Text("Ostatnie 30 dni • daty i godziny w strefie czasowej tego telefonu", style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { selectingPerson = true }, enabled = !busy && members.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("history-person")) { Text(member?.name ?: "Wybierz osobę") }
                DropdownMenu(expanded = selectingPerson, onDismissRequest = { selectingPerson = false }) {
                    members.forEach { person -> DropdownMenuItem(text = { Text(person.name + if (person.uid == currentUid) " (Ty)" else "") },
                        onClick = { uid = person.uid; selectingPerson = false }, modifier = Modifier.testTag("history-person-${person.uid}")) }
                }
            }
            OutlinedButton(onClick = { pickingDay = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("history-date")) {
                Text("Dzień: ${day.format(historyDateFormat)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selectedDay--; onShow(uid, day.minusDays(1)) }, enabled = !busy && member != null && day > oldest,
                    modifier = Modifier.weight(1f)) { Text("Poprzedni dzień") }
                TextButton(onClick = { selectedDay++; onShow(uid, day.plusDays(1)) }, enabled = !busy && member != null && day < today,
                    modifier = Modifier.weight(1f)) { Text("Następny dzień") }
            }
            Button(onClick = { member?.let { onShow(it.uid, day) } }, enabled = !busy && member != null && day in oldest..today,
                modifier = Modifier.fillMaxWidth().testTag("history-load")) { Text(if (matches) "Odśwież historię" else "Pokaż historię na mapie") }
            when {
                !matches -> Text("Wybierz osobę i dzień, a następnie pokaż historię. Historia jest zbierana od wersji 0.7.0.")
                state.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Pobieranie historii…") }
                state.error.isNotBlank() -> Text(state.error, color = MaterialTheme.colorScheme.error)
                state.points.isEmpty() -> Text("Brak zapisanych pomiarów w tym dniu.", modifier = Modifier.testTag("history-empty"))
                !mapsAvailable -> Text("Pokazanie trasy wymaga klucza Google Maps.")
                else -> member?.let { selected -> key(uid, day) {
                    Text("Trasa: ${selected.name} • ${day.format(historyDateFormat)}", style = MaterialTheme.typography.titleMedium)
                    HistoryMap(state.points, selected.appearance, selected.name)
                } }
            }
            HorizontalDivider()
            if (uid == currentUid && member != null) {
                OutlinedButton(onClick = { pickingDeleteDate = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("history-delete")) { Text("Usuń moją historię do daty…") }
                Text("Usunięcie obejmuje wskazany dzień i wcześniejsze pomiary. Dla dzisiaj — pomiary do chwili usunięcia.", style = MaterialTheme.typography.bodySmall)
            } else Text("Historię może usuwać jej właściciel.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message)
        }
    }
    if (pickingDay) HistoryDateDialog("Dzień historii", day.coerceIn(oldest, today), oldest, today,
        onDismiss = { pickingDay = false }, onConfirm = { selectedDay = it.toEpochDay(); pickingDay = false })
    if (pickingDeleteDate) HistoryDateDialog("Usuń historię do daty", day.coerceAtMost(today), LocalDate.of(1970, 1, 1), today,
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
