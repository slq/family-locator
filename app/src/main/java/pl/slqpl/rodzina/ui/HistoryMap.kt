package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val padding = with(LocalDensity.current) { 72.dp.roundToPx() }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LaunchedEffect(ready, size, selected, fitRequest, points) {
        if (!ready || size.width == 0 || size.height == 0) return@LaunchedEffect
        val locations = if (selected != null) listOf(LatLng(point.latitude, point.longitude)) else
            points.map { LatLng(it.latitude, it.longitude) }.distinct()
        val update = if (locations.size == 1) CameraUpdateFactory.newLatLngZoom(locations.first(), 16f)
        else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { locations.forEach { include(it) } }.build(),
            size.width, size.height, padding.coerceAtMost(minOf(size.width, size.height) / 3))
        // Scrubbing selects discrete samples: apply each camera update immediately.
        // This also avoids cancelling an unfinished animation when the next sample arrives.
        camera.move(update)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp))) {
            GoogleMap(Modifier.fillMaxSize().testTag("history-map").onSizeChanged { size = it },
                cameraPositionState = camera, properties = rememberLocatorMapProperties(),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)) {
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
            FilledTonalButton(onClick = { selected = null; fitRequest++ }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("Pokaż całą trasę") }
        }
        Text("${points.size} pomiarów · ${time.format(Date(points.first().measuredAt))}–${time.format(Date(points.last().measuredAt))}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Wybrany pomiar · ${time.format(Date(point.measuredAt))}", style = MaterialTheme.typography.titleMedium)
                Text("Punkt ${index + 1}/${points.size} · dokładność ±${point.accuracyMeters.toInt()} m · bateria ${point.batteryPercent.takeIf { it in 0..100 }?.let { "$it%" } ?: "nieznana"}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("history-point-details"))
                Text("Bateria przy pomiarze", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (points.size > 1) {
            Slider(value = index.toFloat(), onValueChange = { selected = it.roundToInt() },
                valueRange = 0f..points.lastIndex.toFloat(),
                modifier = Modifier.testTag("history-time-slider").semantics { contentDescription = "Czas pomiaru" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time.format(Date(points.first().measuredAt)), style = MaterialTheme.typography.labelMedium)
                Text(time.format(Date(points.last().measuredAt)), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocatorIcon(LocatorSymbol.INFO, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Trasa między pomiarami jest przybliżona. Przerwy ponad 15 min nie są łączone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
