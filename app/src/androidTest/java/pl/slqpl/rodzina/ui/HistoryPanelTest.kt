package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
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

    @Test fun selectingAnyFamilyMemberAndDayLoadsTheirHistoryAndAllowsDeletion() {
        val loaded = mutableListOf<HistorySelection>()
        val state = mutableStateOf(HistorySnapshot())
        compose.setContent { LocatorTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HistoryPanel(members, "alice", now, state.value, false, "", false,
                    onShow = { uid, day -> loaded += HistorySelection(uid, day); state.value = HistorySnapshot(HistorySelection(uid, day)) },
                    onDelete = { _, _ -> error("No deletion expected") })
            }
        } }
        compose.onNodeWithTag("history-person").performScrollTo().performClick()
        compose.onNodeWithTag("history-person-bob").performClick()

        compose.onNodeWithTag("history-empty").assertExists()
        compose.onNodeWithTag("history-delete").assertExists()
        compose.onNodeWithContentDescription("Poprzedni dzień").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(HistorySelection("bob", today), HistorySelection("bob", today.minusDays(1))), loaded) }
    }

    @Test fun deletingThroughADateRequiresAnExplicitConfirmation() {
        val deletions = mutableListOf<Pair<String, LocalDate>>()
        compose.setContent { LocatorTheme {
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

    @Test fun deletingAnotherMemberKeepsTheConfirmedPersonWhenTheLoadedSelectionChanges() {
        val deletions = mutableListOf<Pair<String, LocalDate>>()
        val state = mutableStateOf(HistorySnapshot(HistorySelection("bob", today)))
        compose.setContent { LocatorTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HistoryPanel(members, "alice", now, state.value, false, "", false,
                    onShow = { _, _ -> }, onDelete = { uid, date -> deletions += uid to date })
            }
        } }
        compose.onNodeWithTag("history-delete").performScrollTo().performClick()
        compose.onNodeWithTag("history-confirm-date").performClick()
        compose.onNodeWithText("Usuniesz historię osoby bob", substring = true).assertExists()
        compose.runOnIdle { state.value = HistorySnapshot(HistorySelection("alice", today)) }
        compose.onNodeWithText("Usuniesz historię osoby bob", substring = true).assertExists()
        compose.onNodeWithTag("history-confirm-delete").performClick()
        compose.runOnIdle { assertEquals(listOf("bob" to today), deletions) }
    }

    @Test fun removingTheTargetFromTheFamilyDisablesThePendingConfirmation() {
        val roster = mutableStateOf(members)
        compose.setContent { LocatorTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HistoryPanel(roster.value, "alice", now, HistorySnapshot(HistorySelection("bob", today)), false, "", false,
                    onShow = { _, _ -> }, onDelete = { _, _ -> error("A removed member cannot be deleted") })
            }
        } }
        compose.onNodeWithTag("history-delete").performScrollTo().performClick()
        compose.onNodeWithTag("history-confirm-date").performClick()
        compose.runOnIdle { roster.value = members.filter { it.uid != "bob" } }
        compose.onNodeWithTag("history-confirm-delete").assertIsNotEnabled()
        compose.onNodeWithText("Anuluj").performClick()
    }

    @Test fun routeFitsAllPointsAndTheTimeSliderFocusesAnIndividualMeasurement() {
        lateinit var camera: CameraPositionState
        val points = listOf(HistoryPoint("a", 50.0, 21.0, 10f, now - 10000, 70),
            HistoryPoint("b", 52.0, 21.0, 20f, now, 69))
        compose.setContent { LocatorTheme {
            camera = rememberCameraPositionState()
            HistoryMap(points, members.first().appearance, "alice", camera)
        } }
        fun awaitCamera(label: String, condition: () -> Boolean) {
            try { compose.waitUntil(10_000, condition) }
            catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("$label: camera=${camera.position}, moving=${camera.isMoving}", failure)
            }
        }
        awaitCamera("Initial route") { !camera.isMoving && camera.position.target.latitude in 50.5..51.5 && camera.position.zoom < 10 }
        compose.onNodeWithTag("history-time-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("history-point-details").assertTextContains("Punkt 1/2", substring = true)
        awaitCamera("Selected sample") { !camera.isMoving && abs(camera.position.target.latitude - 50.0) < 0.00001 && camera.position.zoom > 15 }
        compose.onNodeWithTag("history-point-details").assertTextContains("Punkt 1/2", substring = true)
        compose.onNodeWithText("Pokaż całą trasę").performClick()
        awaitCamera("Whole route") { !camera.isMoving && camera.position.zoom < 10 }
    }

    @Test fun selectedSampleInTheNewHistoryLayoutShowsItsRecordedTimeAndBattery() {
        val start = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 8 * 3_600_000
        val points = (0 until 180).map { i ->
            HistoryPoint("p$i", 52.22 + i * 0.00002, 21.0 + kotlin.math.sin(i / 20.0) * 0.001,
                12f, start + i * 60_000L + if (i >= 90) 30 * 60_000 else 0, 82)
        }
        compose.setContent { LocatorTheme {
            LocatorScaffold(LocatorTab.HISTORY, {}) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    ScreenHeading("Historia", "Ostatnie 30 dni")
                    HistoryPanel(members, "alice", now, HistorySnapshot(HistorySelection("alice", today), points),
                        false, "", true, onShow = { _, _ -> }, onDelete = { _, _ -> },
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp))
                }
            }
        } }
        compose.onNodeWithTag("history-map").assertIsDisplayed()
        compose.onNodeWithTag("nav-history").assertIsSelected()
        compose.waitForIdle()
        captureUiPreview("historia")
        compose.onNodeWithTag("history-time-slider").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(118f) }
        compose.onNodeWithTag("history-point-details").assertTextContains("Punkt 119/180", substring = true)
        compose.onNodeWithTag("history-point-details").assertTextContains("bateria 82%", substring = true)
        compose.waitForIdle()
        captureUiPreview("historia-suwak")
    }
}
