package org.cyphr.app

import androidx.compose.ui.test.assertIsDisplayed
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
class MessageLogScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun messageLogShowsEmptyState() {
        rule.onNodeWithText("Message Log").performClick()
        rule.onNodeWithText("No saved messages").assertIsDisplayed()
    }

    @Test
    fun backFromMessageLog_returnsToHome() {
        rule.onNodeWithText("Message Log").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
