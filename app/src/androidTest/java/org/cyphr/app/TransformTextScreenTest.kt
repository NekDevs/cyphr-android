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
class TransformTextScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun transformScreenShowsInputAndEmptyState() {
        rule.onNodeWithText("Transform").performClick()
        rule.onNodeWithText("Text to transform").assertIsDisplayed()
        rule.onNodeWithText("No contacts yet").assertIsDisplayed()
    }

    @Test
    fun transformButtonDisabledWhenNoInput() {
        rule.onNodeWithText("Transform").performClick()
        rule.onNodeWithText("Transform").assertIsNotEnabled()
    }

    @Test
    fun backFromTransform_returnsToHome() {
        rule.onNodeWithText("Transform").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cyphr").assertIsDisplayed()
    }
}
