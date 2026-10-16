package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.cloud.*
import pl.slqpl.rodzina.data.LocationSample
import pl.slqpl.rodzina.location.TrackingMode

private val mapViewSaver = listSaver<FamilyMapView, Any>(
    save = { listOf(it.memberUid.orEmpty(), it.following, it.requestId) },
    restore = { FamilyMapView((it[0] as String).ifEmpty { null }, it[1] as Boolean, it[2] as Int) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyOverview(
    snapshot: FamilySnapshot, local: LocationSample?, currentUid: String?, now: Long,
    busyUid: String?, message: String, mapsAvailable: Boolean,
    onRename: (String, String) -> Unit, onTracking: (String, TrackingMode) -> Unit, onChooseIcon: (FamilyIcon?) -> Unit,
    modifier: Modifier = Modifier, ownMode: TrackingMode? = null, onShowHistory: (String) -> Unit = {},
    showMap: Boolean = true,
    onOpenFamily: () -> Unit = {}, onOpenSettings: () -> Unit = {}, onOpenMap: (String) -> Unit = {},
    focusUid: String? = null, focusRequest: Int = 0, ownRemainingSeconds: Long = 0,
    actionsEnabled: Boolean = true, ownMessage: String = "",
) {
    var view by rememberSaveable(currentUid, stateSaver = mapViewSaver) { mutableStateOf(FamilyMapView()) }
    var detailsUid by rememberSaveable(currentUid) { mutableStateOf<String?>(null) }
    val memberUids = snapshot.members.map { it.uid }.toSet()
    LaunchedEffect(memberUids) {
        view = view.reconcileMembers(memberUids)
        if (memberUids.isNotEmpty() && detailsUid !in memberUids) detailsUid = null
    }
    LaunchedEffect(focusUid, focusRequest) {
        focusUid?.let { view = view.focus(it) }
    }
    val select: (String) -> Unit = { uid ->
        if (mapsAvailable && snapshot.members.firstOrNull { it.uid == uid }?.position != null) view = view.focus(uid)
        detailsUid = uid
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rosterHeight = (maxHeight * 0.56f).coerceIn(100.dp, 320.dp)
        Column(Modifier.fillMaxSize()) {
            if (showMap) {
                if (mapsAvailable) key(currentUid) {
                    FamilyMap(snapshot, local, currentUid, now, view,
                        onShowAll = { view = view.showAll() }, onPause = { view = view.pause() },
                        onResume = { view = view.resume() }, modifier = Modifier.fillMaxWidth().weight(1f),
                        onSelectMember = select)
                } else Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    EmptyState("Mapa jest niedostępna", "Ta wersja aplikacji nie ma włączonej mapy. Udostępnianie i lista rodziny są nadal dostępne.",
                        "Ustawienia", onOpenSettings)
                }
            }
            if (currentUid == null) {
                Column(Modifier.padding(20.dp)) {
                    EmptyState("Twoja rodzina na jednej mapie", "Zaloguj się, aby zobaczyć pozycje bliskich. Pomiary lokalne znajdziesz w ustawieniach.",
                        "Przejdź do konta", onOpenSettings)
                }
            } else if (snapshot.members.isEmpty()) {
                Column(Modifier.padding(20.dp)) {
                    EmptyState("Czekamy na Twoją rodzinę", message.ifBlank { "Lista osób pojawi się po połączeniu. Sprawdź konto i przypisanie do rodziny." },
                        "Konto i połączenie", onOpenSettings)
                }
            } else Surface(Modifier.fillMaxWidth().then(if (showMap) Modifier.heightIn(max = rosterHeight) else Modifier.weight(1f)),
                shape = if (showMap) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (showMap) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Moja rodzina", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenFamily) { Text("Wszyscy (${snapshot.members.size})") }
                    }
                    FamilyMembersPanel(snapshot.members, currentUid, now, select, selectedUid = view.memberUid)
                    if (message.isNotBlank() && detailsUid == null) Text(message, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    val selected = snapshot.members.firstOrNull { it.uid == detailsUid }
    if (selected != null && currentUid != null) ModalBottomSheet(
        onDismissRequest = { detailsUid = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("member-details"),
    ) {
        FamilyMemberDetails(selected, snapshot.members, currentUid, now, actionsEnabled && busyUid == null,
            if (busyUid == selected.uid) "Wysyłanie polecenia…" else
                listOf(message, ownMessage.takeIf { selected.uid == currentUid }.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString("\n"),
            onRename, onTracking, onChooseIcon,
            onShowHistory = { uid -> detailsUid = null; onShowHistory(uid) },
            onShowMap = if (!showMap && mapsAvailable) ({ detailsUid = null; onOpenMap(selected.uid) }) else null,
            ownMode = ownMode, ownRemainingSeconds = ownRemainingSeconds, onDismiss = { detailsUid = null })
    }
}
