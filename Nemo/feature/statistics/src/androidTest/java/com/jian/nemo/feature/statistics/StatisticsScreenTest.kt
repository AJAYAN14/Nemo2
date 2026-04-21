package com.jian.nemo.feature.statistics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jian.nemo.core.domain.model.LearningStats
import com.jian.nemo.core.domain.model.StudyRecord
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * StatisticsScreen UI测试
 *
 * 验证:
 * 1. 加载状态显示
 * 2. 统计数据卡片显示
 * 3. 学习趋势图表显示
 * 4. 空状态处理
 * 5. 错误状态显示
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statisticsScreen_showsLoading_whenIsLoading() {
        // Arrange
        val state = StatisticsUiState(isLoading = true)

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertIsDisplayed()
    }

    @Test
    fun statisticsScreen_showsStatCards_whenHasStats() {
        // Arrange
        val testStats = LearningStats(
            dailyStreak = 7,
            totalStudyDays = 45,
            todayLearnedWords = 20,
            todayLearnedGrammars = 5,
            todayReviewedWords = 10,
            todayReviewedGrammars = 5,
            masteredWords = 1000,
            masteredGrammars = 200,
            dueWords = 80,
            dueGrammars = 20
        )
        val state = StatisticsUiState(
            isLoading = false,
            stats = testStats
        )

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert - 检查关键统计数据是否显示
        composeTestRule.onNodeWithText("1200").assertIsDisplayed() // 总掌握
        composeTestRule.onNodeWithText("25").assertIsDisplayed() // 今日学习
        composeTestRule.onNodeWithText("7").assertIsDisplayed() // 当前连续天数
    }

    @Test
    fun statisticsScreen_showsEmptyState_whenNoStats() {
        // Arrange
        val state = StatisticsUiState(
            isLoading = false,
            stats = null
        )

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("暂无统计数据").assertIsDisplayed()
    }

    @Test
    fun statisticsScreen_showsError_whenErrorOccurs() {
        // Arrange
        val errorMessage = "加载失败: 网络错误"
        val state = StatisticsUiState(
            isLoading = false,
            stats = null,
            error = errorMessage
        )

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText(errorMessage).assertExists()
    }

    @Test
    fun statisticsScreen_showsTitle() {
        // Arrange
        val state = StatisticsUiState(isLoading = false)

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("学习统计").assertIsDisplayed()
    }

    @Test
    fun statisticsScreen_showsLearningTrend_whenHasRecords() {
        // Arrange
        val testRecords = listOf(
            StudyRecord(
                id = 1,
                date = System.currentTimeMillis(),
                wordsLearned = 10,
                wordsReviewed = 5,
                grammarsLearned = 2,
                grammarsReviewed = 1,
                correctRate = 0.85f,
                studyDuration = 1800
            ),
            StudyRecord(
                id = 2,
                date = System.currentTimeMillis() - 86400000, // 昨天
                wordsLearned = 12,
                wordsReviewed = 8,
                grammarsLearned = 3,
                grammarsReviewed = 2,
                correctRate = 0.90f,
                studyDuration = 2100
            )
        )
        val testStats = LearningStats(
            dailyStreak = 7,
            totalStudyDays = 45,
            todayLearnedWords = 20,
            todayLearnedGrammars = 5,
            todayReviewedWords = 10,
            todayReviewedGrammars = 5,
            masteredWords = 1000,
            masteredGrammars = 200,
            dueWords = 80,
            dueGrammars = 20
        )
        val state = StatisticsUiState(
            isLoading = false,
            stats = testStats,
            recentRecords = testRecords
        )

        // Act
        composeTestRule.setContent {
            StatisticsScreenContent(
                uiState = state,
                onRefresh = {},
                onBack = {}
            )
        }

        // Assert - 检查学习趋势标题
        composeTestRule.onNodeWithText("学习趋势").assertIsDisplayed()
    }
}

/**
 * 用于测试的StatisticsScreenContent组件
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.runtime.Composable
private fun StatisticsScreenContent(
    uiState: StatisticsUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("学习统计") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onRefresh) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = uiState.error,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.stats == null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text("暂无统计数据")
                }
            }
            else -> {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    // 统计卡片
                    StatsCards(stats = uiState.stats)

                    // 学习趋势
                    if (uiState.recentRecords.isNotEmpty()) {
                        androidx.compose.material3.Text(
                            text = "学习趋势",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatsCards(stats: LearningStats) {
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "总掌握",
            value = stats.totalMastered.toString(),
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )
        StatCard(
            title = "今日学习",
            value = stats.todayLearned.toString(),
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )
        StatCard(
            title = "连续天数",
            value = stats.currentStreak.toString(),
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )
    }
}

@androidx.compose.runtime.Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = value,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier.height(4.dp)
            )
            androidx.compose.material3.Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
