package pl.slqpl.rodzina.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.slqpl.rodzina.cloud.MemberAppearance
import pl.slqpl.rodzina.location.TrackingMode
import pl.slqpl.rodzina.location.TrackingStatus

enum class LocatorTab(val label: String, val icon: LocatorSymbol) {
    MAP("Mapa", LocatorSymbol.MAP), FAMILY("Rodzina", LocatorSymbol.PEOPLE),
    HISTORY("Historia", LocatorSymbol.HISTORY), SETTINGS("Ustawienia", LocatorSymbol.SETTINGS),
}

enum class LocatorSymbol { MAP, PEOPLE, HISTORY, SETTINGS, BACK, NEXT, EDIT, TARGET, CHECK, INFO, SHARE, TIMER }

@Composable
fun LocatorIcon(symbol: LocatorSymbol, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                when (symbol) {
                    LocatorSymbol.MAP -> {
                        moveTo(12f, 22f); curveTo(9f, 18f, 4f, 13f, 4f, 9f)
                        arcTo(8f, 8f, 0f, true, true, 20f, 9f); curveTo(20f, 13f, 15f, 18f, 12f, 22f); close()
                        moveTo(15f, 9f); arcTo(3f, 3f, 0f, true, true, 9f, 9f); arcTo(3f, 3f, 0f, true, true, 15f, 9f)
                    }
                    LocatorSymbol.PEOPLE -> {
                        moveTo(15f, 6f); arcTo(3f, 3f, 0f, true, true, 9f, 6f); arcTo(3f, 3f, 0f, true, true, 15f, 6f)
                        moveTo(4f, 21f); lineTo(4f, 18f); curveTo(4f, 12f, 20f, 12f, 20f, 18f); lineTo(20f, 21f); close()
                        moveTo(19f, 4f); curveTo(23f, 4f, 23f, 10f, 19f, 10f)
                    }
                    LocatorSymbol.HISTORY -> {
                        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                        moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 15f)
                    }
                    LocatorSymbol.SETTINGS -> {
                        moveTo(9f, 3f); lineTo(15f, 3f); lineTo(16f, 6f); lineTo(19f, 7f)
                        lineTo(22f, 12f); lineTo(20f, 15f); lineTo(19f, 18f); lineTo(15f, 21f)
                        lineTo(12f, 20f); lineTo(9f, 21f); lineTo(5f, 18f); lineTo(4f, 15f)
                        lineTo(2f, 12f); lineTo(5f, 7f); lineTo(8f, 6f); close()
                        moveTo(16f, 12f); arcTo(4f, 4f, 0f, true, true, 8f, 12f); arcTo(4f, 4f, 0f, true, true, 16f, 12f)
                    }
                    LocatorSymbol.BACK -> { moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f) }
                    LocatorSymbol.NEXT -> { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
                    LocatorSymbol.EDIT -> {
                        moveTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(8f, 20f); lineTo(4f, 20f); close()
                        moveTo(13f, 7f); lineTo(17f, 11f)
                    }
                    LocatorSymbol.TARGET -> {
                        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 5f, 12f); arcTo(7f, 7f, 0f, true, true, 19f, 12f)
                        moveTo(15f, 12f); arcTo(3f, 3f, 0f, true, true, 9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f)
                        moveTo(12f, 1f); lineTo(12f, 5f); moveTo(12f, 19f); lineTo(12f, 23f)
                        moveTo(1f, 12f); lineTo(5f, 12f); moveTo(19f, 12f); lineTo(23f, 12f)
                    }
                    LocatorSymbol.CHECK -> { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(20f, 7f) }
                    LocatorSymbol.INFO -> {
                        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                        moveTo(12f, 11f); lineTo(12f, 17f); moveTo(12f, 7f); lineTo(12f, 7.2f)
                    }
                    LocatorSymbol.SHARE -> {
                        moveTo(7f, 11f); lineTo(17f, 6f); moveTo(7f, 13f); lineTo(17f, 18f)
                        moveTo(8f, 12f); arcTo(3f, 3f, 0f, true, true, 2f, 12f); arcTo(3f, 3f, 0f, true, true, 8f, 12f)
                        moveTo(22f, 5f); arcTo(3f, 3f, 0f, true, true, 16f, 5f); arcTo(3f, 3f, 0f, true, true, 22f, 5f)
                        moveTo(22f, 19f); arcTo(3f, 3f, 0f, true, true, 16f, 19f); arcTo(3f, 3f, 0f, true, true, 22f, 19f)
                    }
                    LocatorSymbol.TIMER -> {
                        moveTo(9f, 2f); lineTo(15f, 2f); moveTo(12f, 2f); lineTo(12f, 5f)
                        moveTo(20f, 14f); arcTo(8f, 8f, 0f, true, true, 4f, 14f); arcTo(8f, 8f, 0f, true, true, 20f, 14f)
                        moveTo(12f, 9f); lineTo(12f, 14f); moveTo(18f, 7f); lineTo(20f, 5f)
                    }
                }
            }
        }.build()
    }
    Icon(vector, description, modifier.size(24.dp), tint)
}

@Composable
fun LocatorScaffold(tab: LocatorTab, onTab: (LocatorTab) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                LocatorTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination, onClick = { onTab(destination) },
                        icon = { LocatorIcon(destination.icon) }, label = { Text(destination.label, maxLines = 2, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) },
                        modifier = Modifier.testTag("nav-${destination.name.lowercase()}"),
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
fun ScreenHeading(title: String, subtitle: String, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onSettings?.let { IconButton(onClick = it) { LocatorIcon(LocatorSymbol.SETTINGS, "Ustawienia") } }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun MemberAvatar(appearance: MemberAppearance, modifier: Modifier = Modifier) {
    val color = Color(appearance.colorArgb)
    Box(modifier.size(52.dp).clearAndSetSemantics { contentDescription = "${appearance.icon.label}, znacznik ${appearance.number}" }) {
        Surface(Modifier.size(48.dp).align(Alignment.Center), shape = CircleShape,
            color = color.copy(alpha = 0.07f), border = BorderStroke(2.dp, color)) {
            Box(contentAlignment = Alignment.Center) { Text(appearance.icon.glyph, fontSize = 27.sp) }
        }
        Surface(Modifier.align(Alignment.TopEnd), shape = CircleShape, color = color, contentColor = Color.White) {
            Text(appearance.number.toString(), Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SharingSummary(status: TrackingStatus, localOnly: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocatorIcon(LocatorSymbol.SHARE)
            Column(Modifier.weight(1f)) {
                Text(when {
                    localOnly && status.sharing -> "Pomiary lokalne włączone"
                    status.sharing -> "Udostępniasz położenie"
                    else -> "Udostępnianie wyłączone"
                }, fontWeight = FontWeight.SemiBold)
                Text(when {
                    localOnly -> "Pozycja zostaje na tym telefonie"
                    status.mode == TrackingMode.LIVE -> "Dokładny · jeszcze ${remainingTime(status.remainingSeconds)}"
                    status.sharing -> "Tryb zwykły"
                    status.running -> "Sterowanie rodzinne włączone"
                    else -> "Dotknij, aby włączyć"
                }, style = MaterialTheme.typography.bodySmall)
            }
            LocatorIcon(LocatorSymbol.NEXT)
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    SectionCard {
        LocatorIcon(LocatorSymbol.INFO, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

fun remainingTime(seconds: Long): String = "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"
