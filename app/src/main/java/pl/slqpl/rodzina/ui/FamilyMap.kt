package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.cloud.MemberAppearance
import pl.slqpl.rodzina.data.LocationSample
import java.text.DateFormat
import java.util.Date

private val locatorMapStyle = """[
 {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#f0f3f0"}]},
 {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#d9e9d8"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#b4d7ec"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#ffffff"}]},
 {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#64736b"}]}
]"""

@Composable
fun rememberLocatorMapProperties(): MapProperties = remember {
    MapProperties(mapStyleOptions = MapStyleOptions(locatorMapStyle))
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun FamilyMap(
    snapshot: FamilySnapshot, local: LocationSample?, currentUid: String?, now: Long, view: FamilyMapView,
    onShowAll: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    modifier: Modifier = Modifier,
    camera: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(52.0, 19.0), 5f)
    },
    onSelectMember: ((String) -> Unit)? = null,
) {
    val family = snapshot.positions
    val members = snapshot.members.associateBy { it.uid }
    val focusedMember = members[view.memberUid]
    val points = mapPoints(snapshot, local, currentUid)
    val targets = view.targets(points)
    var ready by remember { mutableStateOf(false) }
    var appliedRequest by remember { mutableIntStateOf(-1) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(LocalDensity.current) { 64.dp.roundToPx() }
    val pause by rememberUpdatedState(onPause)
    // Only coordinates and the explicit view selection move the camera.
    LaunchedEffect(targets, view.memberUid, view.following, view.requestId, ready, size) {
        if (!ready || !view.following || targets.isEmpty() || size.width == 0 || size.height == 0) return@LaunchedEffect
        val locations = targets.map { LatLng(it.latitude, it.longitude) }
        val update = if (locations.size == 1) CameraUpdateFactory.newLatLngZoom(locations.first(),
            if (view.memberUid == null) 15.5f else 16.5f)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { locations.forEach { include(it) } }.build(),
            size.width, size.height, padding.coerceAtMost(minOf(size.width, size.height) / 3))
        if (appliedRequest != view.requestId) {
            // Explicit selections must not compete with an unfinished follow animation.
            camera.move(update)
            appliedRequest = view.requestId
        } else camera.animate(update, durationMs = 600)
    }
    val mapDescription = when {
        targets.isEmpty() -> "Brak zapisanej pozycji"
        !view.following -> "Ręczne przeglądanie mapy"
        view.memberUid != null -> "Śledzenie wybranej osoby"
        else -> "Automatyczne dopasowanie do całej rodziny"
    }
    Box(modifier.fillMaxWidth().height(340.dp).testTag("family-map").semantics { stateDescription = mapDescription }) {
        GoogleMap(Modifier.fillMaxSize().testTag("map-canvas").onSizeChanged { size = it }.pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); pause() }
        }, cameraPositionState = camera, properties = rememberLocatorMapProperties(),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)) {
            MapEffect(Unit) { ready = true }
            if (family.isEmpty()) points.firstOrNull()?.let { point ->
                val position = LatLng(point.latitude, point.longitude)
                val appearance = members[currentUid]?.appearance ?: MemberAppearance()
                val bitmap = rememberMemberMarkerBitmap(appearance)
                Marker(state = remember(position) { MarkerState(position) }, title = members[currentUid]?.name ?: "Ten telefon",
                    icon = remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) })
                local?.let { Circle(center = position, radius = it.accuracyMeters.toDouble(),
                    fillColor = Color(appearance.colorArgb).copy(alpha = 0.12f), strokeColor = Color(appearance.colorArgb), strokeWidth = 2f) }
            }
            family.forEach { person -> key(person.uid) {
                val position = LatLng(person.latitude, person.longitude)
                val appearance = members[person.uid]?.appearance ?: MemberAppearance()
                val stale = now - person.measuredAt > 5 * 60_000 || person.measuredAt > now + 10_000
                val bitmap = rememberMemberMarkerBitmap(appearance, stale)
                val selected = view.memberUid == person.uid
                Marker(state = remember(position) { MarkerState(position) }, title = person.label,
                    icon = remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) },
                    zIndex = if (selected) 1f else 0f,
                    alpha = if (stale) 0.55f else if (view.memberUid == null || selected) 1f else 0.65f,
                    snippet = "Pomiar: ${DateFormat.getDateTimeInstance().format(Date(person.measuredAt))}",
                    onClick = { onSelectMember?.let { it(person.uid); true } ?: false })
                Circle(center = position, radius = person.accuracyMeters.toDouble(),
                    fillColor = Color(appearance.colorArgb).copy(alpha = 0.10f), strokeColor = Color(appearance.colorArgb), strokeWidth = 2f)
            } }
        }
        if (points.isNotEmpty()) FilledTonalButton(
            onClick = onShowAll, modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LocatorIcon(LocatorSymbol.PEOPLE, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text("Pokaż wszystkich", style = MaterialTheme.typography.labelMedium)
        }
        if (targets.isNotEmpty()) FilledTonalIconButton(
            onClick = if (view.following) onPause else onResume,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 28.dp).size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (view.following) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        ) {
            LocatorIcon(LocatorSymbol.TARGET,
                if (view.following) "Przeglądaj ręcznie" else if (view.memberUid == null) "Wznów dopasowanie" else "Wznów śledzenie")
        }
        if (!view.following || view.memberUid != null || targets.isEmpty()) Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 72.dp, bottom = 28.dp),
            shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (view.memberUid != null) Text("Na mapie: ${focusedMember?.name ?: "wybrana osoba"}",
                    style = MaterialTheme.typography.labelMedium)
                Text(mapDescription, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
