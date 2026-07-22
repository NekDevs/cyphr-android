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
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun profiles_backButton_returnsToHome() {
        rule.onNodeWithText("Profiles").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun contacts_backButton_returnsToHome() {
        rule.onNodeWithText("Contacts").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun myIdentity_backButton_returnsToHome() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun transform_backButton_returnsToHome() {
        rule.onNodeWithText("Transform").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun inspect_backButton_returnsToHome() {
        rule.onNodeWithText("Inspect").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun messageLog_backButton_returnsToHome() {
        rule.onNodeWithText("Message Log").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun settings_backButton_returnsToHome() {
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
