package com.jian.nemo2.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jian.nemo2.feature.settings.components.AdvancedLearningSettingsBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdvancedLearningSettingsBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun should_display_leech_controls_with_initial_values() {
        composeTestRule.setContent {
            MaterialTheme {
                AdvancedLearningSettingsBottomSheet(
                    learningSteps = "1 10",
                    relearningSteps = "1 10",
                    learnAheadLimit = 20,
                    leechThreshold = 5,
                    leechAction = "skip",
                    onDismiss = {},
                    onSave = { _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Leech 阈值（累计失败）").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leech_threshold_value").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂停卡片（skip）").assertIsDisplayed()
        composeTestRule.onNodeWithText("仅埋到明天（bury_today）").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 次").assertIsDisplayed()
    }

    @Test
    fun should_save_updated_leech_threshold_and_action() {
        val savedThreshold = mutableStateOf(-1)
        val savedAction = mutableStateOf("")

        composeTestRule.setContent {
            MaterialTheme {
                val show = remember { mutableStateOf(true) }
                if (show.value) {
                    AdvancedLearningSettingsBottomSheet(
                        learningSteps = "1 10",
                        relearningSteps = "1 10",
                        learnAheadLimit = 20,
                        leechThreshold = 5,
                        leechAction = "skip",
                        onDismiss = { show.value = false },
                        onSave = { _, _, _, threshold, action ->
                            savedThreshold.value = threshold
                            savedAction.value = action
                            show.value = false
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("leech_threshold_plus").performClick()
        composeTestRule.onNodeWithTag("leech_action_bury_row").performClick()
        composeTestRule.onNodeWithTag("advanced_learning_save_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(6, savedThreshold.value)
            assertEquals("bury_today", savedAction.value)
        }
    }
}
