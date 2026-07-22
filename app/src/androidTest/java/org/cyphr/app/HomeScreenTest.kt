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
class HomeScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun heroSectionShowsAppName() {
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }

    @Test
    fun tappingProfiles_navigatesToProfiles() {
        rule.onNodeWithText("Profiles").performClick()
        rule.onNodeWithText("Active profile").assertIsDisplayed()
    }

    @Test
    fun tappingMyIdentity_navigatesToMyIdentity() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Your public key fingerprint").assertIsDisplayed()
    }

    @Test
    fun tappingContacts_navigatesToContacts() {
        rule.onNodeWithText("Contacts").performClick()
        rule.onNodeWithText("Add a contact").assertIsDisplayed()
    }

    @Test
    fun tappingTransform_navigatesToTransform() {
        rule.onNodeWithText("Transform").performClick()
        rule.onNodeWithText("Transform text").assertIsDisplayed()
    }

    @Test
    fun tappingInspect_navigatesToInspect() {
        rule.onNodeWithText("Inspect").performClick()
        rule.onNodeWithText("Inspect payload").assertIsDisplayed()
    }

    @Test
    fun tappingMessageLog_navigatesToMessageLog() {
        rule.onNodeWithText("Message Log").performClick()
        rule.onNodeWithText("Message log").assertIsDisplayed()
    }

    @Test
    fun tappingSettings_navigatesToSettings() {
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Require biometric unlock").assertIsDisplayed()
    }
}
