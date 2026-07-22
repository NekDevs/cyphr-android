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
class MyIdentityScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fingerprintSectionsAreDisplayed() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Short fingerprint").assertIsDisplayed()
        rule.onNodeWithText("Full fingerprint").assertIsDisplayed()
    }

    @Test
    fun sharePublicKeyButtonIsDisplayed() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Share public key").assertIsDisplayed()
    }

    @Test
    fun keyRotationBannerNotShownOnFreshProfile() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Key recently rotated").assertDoesNotExist()
    }

    @Test
    fun backFromMyIdentity_returnsToHome() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
