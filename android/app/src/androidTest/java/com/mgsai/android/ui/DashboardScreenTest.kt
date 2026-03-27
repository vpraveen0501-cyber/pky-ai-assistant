package com.pkyai.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardDisplaysLoadingStateInitially() {
        composeTestRule.setContent {
            // Using a stub or observing initial Loading state.
            // Since we're using a real viewmodel, it defaults to Loading.
            DashboardScreen()
        }

        // Wait for UI and verify core text
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent Interactions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Career Upskilling Path").assertIsDisplayed()
    }
}
