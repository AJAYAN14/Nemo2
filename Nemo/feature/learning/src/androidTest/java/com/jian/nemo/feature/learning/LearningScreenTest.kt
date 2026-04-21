package com.jian.nemo.feature.learning

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jian.nemo.feature.learning.presentation.LearningScreenContent
import com.jian.nemo.feature.learning.presentation.LearningStatus
import com.jian.nemo.feature.learning.presentation.LearningUiState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LearningScreen UI 测试
 *
 * 验证:
 * 1. 加载状态显示
 * 2. 完成状态显示
 * 3. 错误状态显示
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LearningScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun learningScreen_showsLoading_whenStatusIsLoading() {
        // Arrange
        val state = LearningUiState(
            status = LearningStatus.Loading
        )

        // Act
        composeTestRule.setContent {
            LearningScreenContent(
                uiState = state,
                level = "N1",
                onNavigateBack = {},
                onNavigateToReview = {},
                onEvent = {},
                showLevelSheet = false,
                onLevelSheetDismiss = {},
                onShowLevelSheet = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("加载中...").assertIsDisplayed()
    }

    @Test
    fun learningScreen_showsError_whenStatusIsError() {
        // Arrange
        val errorMessage = "网络连接失败"
        val state = LearningUiState(
            status = LearningStatus.Error,
            error = errorMessage
        )

        // Act
        composeTestRule.setContent {
            LearningScreenContent(
                uiState = state,
                level = "N1",
                onNavigateBack = {},
                onNavigateToReview = {},
                onEvent = {},
                showLevelSheet = false,
                onLevelSheetDismiss = {},
                onShowLevelSheet = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun learningScreen_showsCompleted_whenStatusIsCompleted() {
        // Arrange
        val state = LearningUiState(
            status = LearningStatus.SessionCompleted,
            masteredCount = 5,
            skippedCount = 2
        )

        // Act
        composeTestRule.setContent {
            LearningScreenContent(
                uiState = state,
                level = "N1",
                onNavigateBack = {},
                onNavigateToReview = {},
                onEvent = {},
                showLevelSheet = false,
                onLevelSheetDismiss = {},
                onShowLevelSheet = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("学习完成！").assertIsDisplayed()
        composeTestRule.onNodeWithText("掌握: 5 个").assertIsDisplayed()
        composeTestRule.onNodeWithText("跳过: 2 个").assertIsDisplayed()
    }
}
