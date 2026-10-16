package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.slqpl.rodzina.location.TrackingMode

@Composable
fun TrackingControls(tag: String, mode: TrackingMode?, enabled: Boolean, onSelect: (TrackingMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            TrackingMode.PERIODIC to "Udostępnianie położenia",
            TrackingMode.LIVE to "Dokładne pomiary przez 15 min",
            TrackingMode.STOPPED to "Wyłączenie udostępniania",
        ).forEach { (choice, label) ->
            OutlinedButton(onClick = { onSelect(choice) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("tracking-$tag-${choice.wireName}"),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (choice == mode)
                    MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                Text(label)
            }
        }
    }
}
