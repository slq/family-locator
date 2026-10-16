package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.cloud.MemberAppearance
import pl.slqpl.rodzina.data.LocationSample
import java.text.DateFormat
import java.util.Date

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun FamilyMap(
    snapshot: FamilySnapshot, local: LocationSample?, currentUid: String?, now: Long, view: FamilyMapView,
    onShowAll: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, modifier: Modifier = Modifier,
    camera: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(52.0, 19.0), 5f)
    },
) {
    val family = snapshot.positions
    val members = snapshot.members.associateBy { it.uid }
    val focusedMember = members[view.memberUid]
    val points = mapPoints(snapshot, local, currentUid)
    val targets = view.targets(points)
    var ready by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(LocalDensity.current) { 64.dp.roundToPx() }
    val pause by rememberUpdatedState(onPause)
    // Targets contain coordinates only: names, heartbeats and other metadata never move the camera.
    LaunchedEffect(targets, view.memberUid, view.following, view.requestId, ready, size) {
        if (!ready || !view.following || targets.isEmpty() || size.width == 0 || size.height == 0) return@LaunchedEffect
        val locations = targets.map { LatLng(it.latitude, it.longitude) }
        val update = if (locations.size == 1) CameraUpdateFactory.newLatLngZoom(locations.first(),
            if (view.memberUid == null) 15.5f else 16.5f)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { locations.forEach { include(it) } }.build(),
            size.width, size.height, padding.coerceAtMost(minOf(size.width, size.height) / 3))
        camera.animate(update, durationMs = 600)
    }
    Card(modifier.fillMaxWidth().testTag("family-map")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (view.memberUid == null) "Mapa rodziny" else "Na mapie: ${focusedMember?.name ?: "wybrana osoba"}",
                style = MaterialTheme.typography.titleMedium)
            Text(when {
                !view.following -> "Ręczne przeglądanie mapy"
                view.memberUid != null -> "Śledzenie wybranej osoby"
                else -> "Automatyczne dopasowanie do całej rodziny"
            }, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onShowAll, enabled = points.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Pokaż wszystkich")
            }
            TextButton(onClick = if (view.following) onPause else onResume, enabled = targets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (view.following) "Przeglądaj ręcznie" else if (view.memberUid == null) "Wznów dopasowanie" else "Wznów śledzenie")
            }
            if (targets.isEmpty()) Text(if (view.memberUid == null) "Brak zapisanych pozycji do pokazania." else "Ta osoba nie ma dostępnej pozycji.")
            focusedMember?.position?.let { position ->
                val ageMinutes = ((now - position.measuredAt) / 60_000).coerceAtLeast(0)
                Text("Ostatni pomiar: ${if (ageMinutes == 0L) "przed chwilą" else "$ageMinutes min temu"} • dokładność ${position.accuracyMeters.toInt()} m",
                    style = MaterialTheme.typography.bodySmall)
            }
            GoogleMap(Modifier.fillMaxWidth().height(300.dp).testTag("map-canvas").onSizeChanged { size = it }.pointerInput(Unit) {
                // Observe without consuming input; pan, pinch, marker taps and zoom controls all pause following.
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); pause() }
            }, cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = true)) {
                // Ready as soon as the map is bound, including when map tiles cannot load offline.
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
                    val bitmap = rememberMemberMarkerBitmap(appearance)
                    val selected = view.memberUid == person.uid
                    Marker(state = remember(position) { MarkerState(position) }, title = person.label,
                        icon = remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) },
                        zIndex = if (selected) 1f else 0f, alpha = if (view.memberUid == null || selected) 1f else 0.65f,
                        snippet = "Pomiar: ${DateFormat.getDateTimeInstance().format(Date(person.measuredAt))}")
                    Circle(center = position, radius = person.accuracyMeters.toDouble(),
                        fillColor = Color(appearance.colorArgb).copy(alpha = 0.12f), strokeColor = Color(appearance.colorArgb), strokeWidth = 2f)
                } }
            }
        }
    }
}
