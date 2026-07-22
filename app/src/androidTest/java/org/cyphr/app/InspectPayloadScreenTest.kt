package org.cyphr.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class InspectPayloadScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun inspectScreenShowsInputAndButtons() {
        rule.onNodeWithText("Inspect").performClick()
        rule.onNodeWithText("Payload to inspect").assertIsDisplayed()
        rule.onNodeWithText("Paste from clipboard").assertIsDisplayed()
    }

    @Test
    fun inspectButtonDisabledWhenNoInput() {
        rule.onNodeWithText("Inspect").performClick()
        rule.onNodeWithText("Inspect").assertIsNotEnabled()
    }

    @Test
    fun backFromInspect_returnsToHome() {
        rule.onNodeWithText("Inspect").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
