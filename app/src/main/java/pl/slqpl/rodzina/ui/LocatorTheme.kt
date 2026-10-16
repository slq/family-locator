package pl.slqpl.rodzina.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LocatorColors = lightColorScheme(
    primary = Color(0xFF176B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF2EA), onPrimaryContainer = Color(0xFF104C40),
    secondary = Color(0xFF52665E), secondaryContainer = Color(0xFFE7F0EB),
    onSecondaryContainer = Color(0xFF243D32),
    background = Color(0xFFF6F8F7), onBackground = Color(0xFF172C26),
    surface = Color.White, onSurface = Color(0xFF172C26),
    surfaceVariant = Color(0xFFEAF0EC), onSurfaceVariant = Color(0xFF56645E),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF0F5F2),
    outline = Color(0xFF77867E), outlineVariant = Color(0xFFE1E8E4),
)

@Composable
fun LocatorTheme(content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val activity = view.context as? android.app.Activity
    androidx.compose.runtime.SideEffect {
        activity?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    val base = Typography()
    MaterialTheme(
        colorScheme = LocatorColors,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
