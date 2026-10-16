package pl.slqpl.rodzina.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import pl.slqpl.rodzina.MainActivity

class LocatorNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationOpensEachScreenAndRestoresTheSelectedTabAfterRecreation() {
        compose.onNodeWithTag("nav-map").assertIsSelected()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Twój telefon i konto").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithText("Udostępnianie tego telefonu").assertIsDisplayed()
        compose.onNodeWithTag("nav-history").performClick()
        compose.onNodeWithText("Ostatnie 30 dni").assertIsDisplayed()
        compose.onNodeWithTag("nav-family").performClick()
        compose.onNodeWithText("Położenie i udostępnianie").assertIsDisplayed()
        compose.onNodeWithTag("nav-map").performClick()
        compose.onNodeWithText("Pozycje bliskich").assertIsDisplayed()
    }
}
