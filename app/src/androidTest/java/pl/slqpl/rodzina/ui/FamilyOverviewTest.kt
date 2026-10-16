package pl.slqpl.rodzina.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import pl.slqpl.rodzina.cloud.*
import pl.slqpl.rodzina.location.TrackingMode
import pl.slqpl.rodzina.location.TrackingStatus
import kotlin.math.abs

/** Synthetic members only: these UI checks never read or write real family data. */
class FamilyOverviewTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()

    private fun member(uid: String, name: String, latitude: Double?) = FamilyMember(uid, name,
        latitude?.let { FamilyPosition(uid, name, it, 21.0, 10f, now - 60_000, now, 70) },
        FamilyDevice(true, "periodic", now, 0, true), null, null)

    private fun show(snapshot: FamilySnapshot) {
        compose.setContent {
            LocatorTheme {
                FamilyOverview(snapshot, null, "alice", now, null, "", true,
                    onRename = { _, _ -> }, onTracking = { _, _ -> }, onChooseIcon = {},
                    modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Test fun selectingMemberOpensDetailsAndCanReturnToAllMembers() {
        show(FamilySnapshot(listOf(member("bob", "Bob", 50.0), member("alice", "Alice", 52.0))))
        compose.onNodeWithTag("show-member-bob").performClick()
        compose.onNodeWithTag("member-details").assertIsDisplayed()
        compose.onNodeWithTag("tracking-bob-live").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zamknij szczegóły").performClick()
        compose.onNodeWithText("Na mapie: Bob").assertIsDisplayed()
        compose.onNodeWithText("Śledzenie wybranej osoby").assertIsDisplayed()
        compose.onNodeWithContentDescription("Przeglądaj ręcznie").performClick()
        compose.onNodeWithText("Ręczne przeglądanie mapy").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wznów śledzenie").performClick()
        compose.onNodeWithText("Śledzenie wybranej osoby").assertIsDisplayed()
        compose.onNodeWithText("Pokaż wszystkich").performClick()
        compose.onNodeWithTag("family-map").assertIsDisplayed()
        compose.onNodeWithTag("family-map").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Automatyczne dopasowanie do całej rodziny"))
    }

    @Test fun memberWithoutPositionStillHasDetailsAndSharingControls() {
        show(FamilySnapshot(listOf(member("bob", "Bob", null))))
        compose.onNodeWithTag("show-member-bob").assertIsEnabled().performClick()
        compose.onNodeWithText("Brak zapisanej pozycji. Możesz zmienić tryb udostępniania tego telefonu.").assertIsDisplayed()
        compose.onNodeWithTag("tracking-bob-periodic").assertIsEnabled()
        compose.onNodeWithTag("member-history-bob").assertExists()
    }

    @Test fun cameraFollowsNewMeasurementsAndPausesWhenTouched() {
        val snapshot = mutableStateOf(FamilySnapshot(listOf(member("bob", "Bob", 50.0), member("alice", "Alice", 52.0))))
        val view = mutableStateOf(FamilyMapView())
        lateinit var camera: CameraPositionState
        compose.setContent {
            LocatorTheme {
                camera = rememberCameraPositionState()
                FamilyMap(snapshot.value, null, "alice", now, view.value,
                    onShowAll = { view.value = view.value.showAll() }, onPause = { view.value = view.value.pause() },
                    onResume = { view.value = view.value.resume() }, camera = camera)
            }
        }
        compose.waitUntil(10_000) { !camera.isMoving && camera.position.target.latitude in 50.5..51.5 && camera.position.zoom < 10 }
        compose.runOnIdle { view.value = view.value.focus("bob") }
        compose.waitUntil(10_000) { !camera.isMoving && abs(camera.position.target.latitude - 50.0) < 0.00001 && camera.position.zoom > 16 }
        compose.runOnIdle { snapshot.value = FamilySnapshot(listOf(member("bob", "Bob", 50.01), member("alice", "Alice", 52.0))) }
        compose.waitUntil(10_000) { !camera.isMoving && abs(camera.position.target.latitude - 50.01) < 0.00001 }
        compose.onNodeWithTag("map-canvas").performTouchInput { click(center) }
        compose.runOnIdle {
            assertFalse(view.value.following)
            snapshot.value = FamilySnapshot(listOf(member("bob", "Bob", 50.02), member("alice", "Alice", 52.0)))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(50.01, camera.position.target.latitude, 0.00001) }
        compose.onNodeWithContentDescription("Wznów śledzenie").performClick()
        compose.waitUntil(10_000) { !camera.isMoving && abs(camera.position.target.latitude - 50.02) < 0.00001 }
        compose.onNodeWithText("Pokaż wszystkich").performClick()
        compose.waitUntil(10_000) { !camera.isMoving && camera.position.zoom < 10 }
    }

    @Test fun allThreeControlsAddressTheSelectedPersonIncludingSomeoneWithoutAPosition() {
        val actions = mutableListOf<Pair<String, TrackingMode>>()
        compose.setContent { LocatorTheme {
            FamilyOverview(FamilySnapshot(listOf(member("alice", "Alice", 52.0), member("bob", "Bob", null))),
                null, "alice", now, null, "", false, onRename = { _, _ -> },
                onTracking = { uid, mode -> actions += uid to mode }, onChooseIcon = {},
                showMap = false, modifier = Modifier.fillMaxSize())
        } }
        for (uid in listOf("alice", "bob")) {
            compose.onNodeWithTag("show-member-$uid").performClick()
            for (mode in TrackingMode.entries) {
                compose.onNodeWithTag("tracking-$uid-${mode.wireName}").performScrollTo().performClick()
            }
            compose.onNodeWithContentDescription("Zamknij szczegóły").performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf("alice", "bob").flatMap { uid -> TrackingMode.entries.map { uid to it } }, actions)
        }
    }

    @Test fun mapRosterAndNavigationFitTogetherAndDetailsRemainReachable() {
        val people = listOf(member("anna", "Anna", 52.225), member("alice", "Marek", 52.22),
            member("ola", "Ola", 52.23).let { it.copy(position = it.position?.copy(measuredAt = now - 42 * 60_000)) })
            .mapIndexed { index, person -> person.copy(appearance = MemberAppearance(FamilyIcon.entries[index], index + 1)) }
        compose.setContent { LocatorTheme {
            LocatorScaffold(LocatorTab.MAP, {}) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    ScreenHeading("Rodzina", "Pozycje bliskich", onSettings = {})
                    SharingSummary(TrackingStatus(true, TrackingMode.PERIODIC), false, {})
                    FamilyOverview(FamilySnapshot(people), null, "alice", now, null, "", true,
                        onRename = { _, _ -> }, onTracking = { _, _ -> }, onChooseIcon = {},
                        modifier = Modifier.weight(1f))
                }
            }
        } }
        compose.onNodeWithTag("family-map").assertIsDisplayed()
        compose.onNodeWithTag("nav-map").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        compose.onNodeWithTag("show-member-anna").assertIsDisplayed()
        compose.onNodeWithTag("show-member-ola").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stara pozycja").assertIsDisplayed()
        compose.onNodeWithTag("show-member-anna").performScrollTo()
        compose.waitForIdle()
        captureUiPreview("mapa")
        compose.onNodeWithTag("show-member-anna").performClick()
        compose.onNodeWithTag("tracking-anna-live").assertIsDisplayed()
        captureUiPreview("osoba")
    }

    @Test fun ownPhonePermissionFailureIsVisibleInItsDetails() {
        compose.setContent { LocatorTheme {
            FamilyOverview(FamilySnapshot(listOf(member("alice", "Alice", null))),
                null, "alice", now, null, "", false,
                onRename = { _, _ -> }, onTracking = { _, _ -> }, onChooseIcon = {},
                ownMessage = "Potrzebna jest dokładna lokalizacja i zgoda na powiadomienia.",
                showMap = false, modifier = Modifier.fillMaxSize())
        } }
        compose.onNodeWithTag("show-member-alice").performClick()
        compose.onNodeWithText("Potrzebna jest dokładna lokalizacja i zgoda na powiadomienia.")
            .performScrollTo().assertIsDisplayed()
    }
}
