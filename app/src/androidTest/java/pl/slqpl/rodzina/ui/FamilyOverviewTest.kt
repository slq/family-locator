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
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import pl.slqpl.rodzina.cloud.*
import kotlin.math.abs

/** Synthetic members only: these UI checks neither read nor write the user's Firebase data. */
class FamilyOverviewTest {
    @get:Rule val compose = createComposeRule()

    private fun member(uid: String, name: String, latitude: Double?) = FamilyMember(uid, name,
        latitude?.let { FamilyPosition(uid, name, it, 21.0, 10f, 1_000, 1_000, 70) }, null, null, null)

    private fun show(snapshot: FamilySnapshot) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    FamilyOverview(snapshot, null, "alice", 1_000, null, "", true,
                        onRename = { _, _ -> }, onRequestLive = {}, onChooseIcon = {})
                }
            }
        }
    }

    @Test fun selectingMemberScrollsToMapAndCanReturnToAllMembers() {
        show(FamilySnapshot(listOf(member("bob", "Bob", 50.0), member("alice", "Alice", 52.0))))
        compose.onNodeWithTag("show-member-bob").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Na mapie: Bob").assertIsDisplayed()
        compose.onNodeWithText("Śledzenie wybranej osoby").assertIsDisplayed()
        compose.onNodeWithText("Przeglądaj ręcznie").performClick()
        compose.onNodeWithText("Ręczne przeglądanie mapy").assertIsDisplayed()
        compose.onNodeWithText("Wznów śledzenie").performClick()
        compose.onNodeWithText("Śledzenie wybranej osoby").assertIsDisplayed()
        compose.onNodeWithText("Pokaż wszystkich").performClick()
        compose.onNodeWithText("Mapa rodziny").assertIsDisplayed()
        compose.onNodeWithText("Automatyczne dopasowanie do całej rodziny").assertIsDisplayed()
    }

    @Test fun memberWithoutPositionCannotBeFocused() {
        show(FamilySnapshot(listOf(member("bob", "Bob", null))))
        compose.onNodeWithTag("show-member-bob").assertIsNotEnabled()
        compose.onNodeWithText("Brak zapisanej pozycji").assertExists()
    }

    @Test fun cameraFollowsNewMeasurementsAndPausesWhenTouched() {
        val snapshot = mutableStateOf(FamilySnapshot(listOf(member("bob", "Bob", 50.0), member("alice", "Alice", 52.0))))
        val view = mutableStateOf(FamilyMapView())
        lateinit var camera: CameraPositionState
        compose.setContent {
            MaterialTheme {
                camera = rememberCameraPositionState()
                FamilyMap(snapshot.value, null, "alice", 1_000, view.value,
                    onShowAll = { view.value = view.value.showAll() }, onPause = { view.value = view.value.pause() },
                    onResume = { view.value = view.value.resume() }, camera = camera)
            }
        }
        // Two points 220 km apart require a broad overview, instead of centering just one person.
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
        compose.onNodeWithText("Wznów śledzenie").performClick()
        compose.waitUntil(10_000) { !camera.isMoving && abs(camera.position.target.latitude - 50.02) < 0.00001 }
        compose.onNodeWithText("Pokaż wszystkich").performClick()
        compose.waitUntil(10_000) { !camera.isMoving && camera.position.zoom < 10 }
    }
}
