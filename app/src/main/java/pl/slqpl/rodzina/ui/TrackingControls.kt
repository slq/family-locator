package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.location.TrackingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingControls(tag: String, mode: TrackingMode?, enabled: Boolean, onSelect: (TrackingMode) -> Unit) {
    val choices = listOf(
        TrackingMode.PERIODIC to "Zwykły",
        TrackingMode.LIVE to "Dokładny",
        TrackingMode.STOPPED to "Wyłącz",
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (choice, label) ->
            SegmentedButton(
                selected = choice == mode, enabled = enabled, onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size), icon = {},
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tracking-$tag-${choice.wireName}"),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
