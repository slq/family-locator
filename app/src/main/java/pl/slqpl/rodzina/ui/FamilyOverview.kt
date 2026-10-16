package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.cloud.FamilyIcon
import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.data.LocationSample

private val mapViewSaver = listSaver<FamilyMapView, Any>(
    save = { listOf(it.memberUid.orEmpty(), it.following, it.requestId) },
    restore = { FamilyMapView((it[0] as String).ifEmpty { null }, it[1] as Boolean, it[2] as Int) },
)

@Composable
fun FamilyOverview(
    snapshot: FamilySnapshot, local: LocationSample?, currentUid: String?, now: Long,
    busyUid: String?, message: String, mapsAvailable: Boolean,
    onRename: (String, String) -> Unit, onRequestLive: (String) -> Unit, onChooseIcon: (FamilyIcon?) -> Unit,
) {
    var view by rememberSaveable(currentUid, stateSaver = mapViewSaver) { mutableStateOf(FamilyMapView()) }
    val mapRequester = remember { BringIntoViewRequester() }
    var scrollRequest by remember { mutableIntStateOf(0) }
    val memberUids = snapshot.members.map { it.uid }.toSet()
    LaunchedEffect(memberUids) { view = view.reconcileMembers(memberUids) }
    LaunchedEffect(scrollRequest) {
        if (scrollRequest > 0 && mapsAvailable) mapRequester.bringIntoView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (currentUid != null) FamilyMembersPanel(
            snapshot.members, currentUid, now, busyUid, message, onRename, onRequestLive, onChooseIcon,
            selectedUid = view.memberUid, mapsAvailable = mapsAvailable,
            onShowMember = { uid -> view = view.focus(uid); scrollRequest++ },
        )
        if (mapsAvailable) key(currentUid) {
            FamilyMap(snapshot, local, currentUid, now, view,
                onShowAll = { view = view.showAll() }, onPause = { view = view.pause() },
                onResume = { view = view.resume() }, modifier = Modifier.bringIntoViewRequester(mapRequester))
        } else Card(Modifier.fillMaxWidth()) {
            Text("Mapa pojawi się po skonfigurowaniu klucza Google Maps. Pomiary lokalne działają bez niego.", Modifier.padding(16.dp))
        }
    }
}
