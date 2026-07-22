package org.cyphr.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ProfileScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeProfileIsDisplayed() {
        rule.onNodeWithText("Profiles").performClick()
        rule.onNodeWithText("Active profile").assertIsDisplayed()
    }

    @Test
    fun createProfileWithName_createsNewProfile() {
        rule.onNodeWithText("Profiles").performClick()
        rule.onNodeWithText("Profile name").performTextInput("Test Profile")
        rule.onNodeWithText("Create").performClick()
        rule.onNodeWithText("Test Profile").assertIsDisplayed()
    }

    @Test
    fun backFromProfiles_returnsToHome() {
        rule.onNodeWithText("Profiles").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
