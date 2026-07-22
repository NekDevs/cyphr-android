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
class ContactsListScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun contactsShowsEmptyState() {
        rule.onNodeWithText("Contacts").performClick()
        rule.onNodeWithText("No contacts yet").assertIsDisplayed()
    }

    @Test
    fun contactsShowsAddContactSection() {
        rule.onNodeWithText("Contacts").performClick()
        rule.onNodeWithText("Add a contact").assertIsDisplayed()
    }

    @Test
    fun backFromContacts_returnsToHome() {
        rule.onNodeWithText("Contacts").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
