package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import pl.slqpl.rodzina.cloud.HistoryPoint
import pl.slqpl.rodzina.cloud.HistoryPolicy
import pl.slqpl.rodzina.cloud.MemberAppearance
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun HistoryMap(points: List<HistoryPoint>, appearance: MemberAppearance, name: String,
    camera: CameraPositionState = rememberCameraPositionState()) {
    if (points.isEmpty()) return
    var selected by remember(points.first().id) { mutableStateOf<Int?>(null) }
    val index = (selected ?: points.lastIndex).coerceIn(points.indices)
    val point = points[index]
    val segments = remember(points) { HistoryPolicy.segments(points) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(LocalDensity.current) { 40.dp.roundToPx() }
    LaunchedEffect(ready, size, selected, fitRequest, points) {
        if (!ready || size.width == 0 || size.height == 0) return@LaunchedEffect
        val locations = if (selected != null) listOf(LatLng(point.latitude, point.longitude)) else
            points.map { LatLng(it.latitude, it.longitude) }.distinct()
        val update = if (locations.size == 1) CameraUpdateFactory.newLatLngZoom(locations.first(), 16f)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { locations.forEach { include(it) } }.build(),
            size.width, size.height, padding.coerceAtMost(minOf(size.width, size.height) / 3))
        camera.animate(update, 500)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${points.size} pomiarów • ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(points.first().measuredAt))}–${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(points.last().measuredAt))}")
        Text("Punkt ${index + 1}/${points.size}: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(point.measuredAt))} • dokładność ${point.accuracyMeters.toInt()} m • bateria ${point.batteryPercent}%",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("history-point-details"))
        if (points.size > 1) Slider(value = index.toFloat(), onValueChange = { selected = it.roundToInt() },
            valueRange = 0f..points.lastIndex.toFloat(), modifier = Modifier.testTag("history-time-slider"))
        OutlinedButton(onClick = { selected = null; fitRequest++ }, modifier = Modifier.fillMaxWidth()) { Text("Pokaż całą trasę") }
        GoogleMap(Modifier.fillMaxWidth().height(340.dp).testTag("history-map").onSizeChanged { size = it }, cameraPositionState = camera) {
            MapEffect(Unit) { ready = true }
            segments.filter { it.size >= 2 }.forEach { segment ->
                Polyline(points = segment.map { LatLng(it.latitude, it.longitude) }, color = Color(appearance.colorArgb), width = 9f)
            }
            val first = points.first()
            Marker(state = remember(first.id) { MarkerState(LatLng(first.latitude, first.longitude)) }, title = "Początek — $name",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            if (points.size > 1) {
                val last = points.last()
                Marker(state = remember(last.id) { MarkerState(LatLng(last.latitude, last.longitude)) }, title = "Koniec — $name",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }
            val bitmap = rememberMemberMarkerBitmap(appearance)
            Marker(state = remember(point.id) { MarkerState(LatLng(point.latitude, point.longitude)) }, title = name,
                snippet = DateFormat.getDateTimeInstance().format(Date(point.measuredAt)), zIndex = 2f,
                icon = remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) })
            Circle(center = LatLng(point.latitude, point.longitude), radius = point.accuracyMeters.toDouble(),
                fillColor = Color(appearance.colorArgb).copy(alpha = 0.12f), strokeColor = Color(appearance.colorArgb), strokeWidth = 2f)
        }
        Text("Linia łączy zapisane pomiary. Przerwy dłuższe niż 15 min nie są łączone; przebieg między punktami jest przybliżony.", style = MaterialTheme.typography.bodySmall)
    }
}
