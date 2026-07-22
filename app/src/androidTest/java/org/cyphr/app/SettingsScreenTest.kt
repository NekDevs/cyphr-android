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
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsShowsBiometricToggle() {
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Require biometric unlock").assertIsDisplayed()
    }

    @Test
    fun backFromSettings_returnsToHome() {
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
