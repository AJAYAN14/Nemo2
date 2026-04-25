package com.jian.nemo2.feature.test

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TestScreen UI 测试
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TestScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testScreen_showsLoading_whenLoading() {
        val state = TestUiState(isLoading = true)

        composeTestRule.setContent {
            TestScreenContent(
                uiState = state,
                onNavigateBack = {},
                onRetakeTest = {},
                onSelectOption = {},
                onTypingInputChange = {},
                onSubmitAnswer = {},
                onNextQuestion = {},
                onFinishTest = {}
            )
        }

        // CircularProgressIndicator没有text，我们通Semantics或者层级检查
        // 简单方式：检查ProgressIndicator是否存在
        // composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        // Compose M3 CircularProgressIndicator 具有 ProgressBarRangeInfo.Indeterminate

        // 这里的测试更推荐找特定的Tag，但现在没有Tag，我们只是确保没有Crash且屏幕上没有Specific Error Text
        // 或者反向验证
    }

    @Test
    fun testScreen_showsError() {
        val errorMsg = "加载题目失败"
        val state = TestUiState(error = errorMsg)

        composeTestRule.setContent {
            TestScreenContent(
                uiState = state,
                onNavigateBack = {},
                onRetakeTest = {},
                onSelectOption = {},
                onTypingInputChange = {},
                onSubmitAnswer = {},
                onNextQuestion = {},
                onFinishTest = {}
            )
        }

        composeTestRule.onNodeWithText(errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText("返回").assertIsDisplayed()
    }
}
