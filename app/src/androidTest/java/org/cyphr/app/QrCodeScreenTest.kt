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
class QrCodeScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun qrCodeScreenShowsCopyAndShareButtons() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Share public key").performClick()
        rule.onNodeWithText("Show QR Code").performClick()
        rule.onNodeWithText("Copy public key").assertIsDisplayed()
        rule.onNodeWithText("Share via...").assertIsDisplayed()
    }

    @Test
    fun backFromQrCode_returnsToMyIdentity() {
        rule.onNodeWithText("My Identity").performClick()
        rule.onNodeWithText("Share public key").performClick()
        rule.onNodeWithText("Show QR Code").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Share public key").assertIsDisplayed()
    }
}
