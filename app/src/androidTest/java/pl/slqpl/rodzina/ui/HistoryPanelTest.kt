package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pl.slqpl.rodzina.cloud.*
import java.time.LocalDate
import kotlin.math.abs

class HistoryPanelTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val today = HistoryPolicy.day(now)
    private val members = listOf("alice", "bob").map { FamilyMember(it, it, null, null, null, null) }

    @Test fun selectingAnyFamilyMemberAndDayLoadsTheirHistoryButDeletionBelongsToTheOwner() {
        val loaded = mutableListOf<HistorySelection>()
        val state = mutableStateOf(HistorySnapshot())
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HistoryPanel(members, "alice", now, state.value, false, "", false,
                    onShow = { uid, day -> loaded += HistorySelection(uid, day); state.value = HistorySnapshot(HistorySelection(uid, day)) },
                    onDelete = { _, _ -> error("No deletion expected") })
            }
        } }
        compose.onNodeWithTag("history-person").performScrollTo().performClick()
        compose.onNodeWithTag("history-person-bob").performClick()
        compose.onNodeWithTag("history-load").performScrollTo().performClick()
        compose.onNodeWithTag("history-empty").assertExists()
        compose.onNodeWithTag("history-delete").assertDoesNotExist()
        compose.onNodeWithText("Poprzedni dzień").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(HistorySelection("bob", today), HistorySelection("bob", today.minusDays(1))), loaded) }
    }

    @Test fun deletingThroughADateRequiresAnExplicitConfirmation() {
        val deletions = mutableListOf<Pair<String, LocalDate>>()
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HistoryPanel(members, "alice", now, HistorySnapshot(), false, "", false,
                    onShow = { _, _ -> }, onDelete = { uid, date -> deletions += uid to date })
            }
        } }
        compose.onNodeWithTag("history-delete").performScrollTo().performClick()
        compose.onNodeWithTag("history-confirm-date").performClick()
        compose.runOnIdle { assertTrue(deletions.isEmpty()) }
        compose.onNodeWithText("Anuluj").performClick()
        compose.runOnIdle { assertTrue(deletions.isEmpty()) }
        compose.onNodeWithTag("history-delete").performClick()
        compose.onNodeWithTag("history-confirm-date").performClick()
        compose.onNodeWithTag("history-confirm-delete").performClick()
        compose.runOnIdle { assertEquals(listOf("alice" to today), deletions) }
    }

    @Test fun routeFitsAllPointsAndTheTimeSliderFocusesAnIndividualMeasurement() {
        lateinit var camera: CameraPositionState
        val points = listOf(HistoryPoint("a", 50.0, 21.0, 10f, now - 10000, 70),
            HistoryPoint("b", 52.0, 21.0, 20f, now, 69))
        compose.setContent { MaterialTheme {
            camera = rememberCameraPositionState()
            HistoryMap(points, members.first().appearance, "alice", camera)
        } }
        compose.waitUntil(10_000) { !camera.isMoving && camera.position.target.latitude in 50.5..51.5 && camera.position.zoom < 10 }
        compose.onNodeWithTag("history-time-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.waitUntil(10_000) { !camera.isMoving && abs(camera.position.target.latitude - 50.0) < 0.00001 && camera.position.zoom > 15 }
        compose.onNodeWithTag("history-point-details").assertTextContains("Punkt 1/2", substring = true)
        compose.onNodeWithText("Pokaż całą trasę").performClick()
        compose.waitUntil(10_000) { !camera.isMoving && camera.position.zoom < 10 }
    }
}
