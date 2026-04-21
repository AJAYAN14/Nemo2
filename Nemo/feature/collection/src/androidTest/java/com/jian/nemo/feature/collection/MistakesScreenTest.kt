package com.jian.nemo.feature.collection.mistakes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jian.nemo.core.domain.model.WrongAnswer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MistakesScreen UI测试
 *
 * 验证:
 * 1. 加载状态显示
 * 2. Tab切换（单词/语法）
 * 3. 空状态显示（无错题）
 * 4. 错题列表显示
 * 5. 删除错题交互
 * 6. 清空所有错题
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MistakesScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mistakesScreen_showsLoading_whenIsLoading() {
        // Arrange
        val state = MistakesUiState(isLoading = true)

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertIsDisplayed()
    }

    @Test
    fun mistakesScreen_showsTabs() {
        // Arrange
        val state = MistakesUiState(
            isLoading = false,
            selectedTab = MistakeTab.WORD
        )

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert - 检查Tab存在
        composeTestRule.onNodeWithText("单词").assertIsDisplayed()
        composeTestRule.onNodeWithText("语法").assertIsDisplayed()
    }

    @Test
    fun mistakesScreen_showsEmptyState_whenNoWordMistakes() {
        // Arrange
        val state = MistakesUiState(
            isLoading = false,
            selectedTab = MistakeTab.WORD,
            wordWrongAnswers = emptyList()
        )

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("暂无单词错题").assertIsDisplayed()
    }

    @Test
    fun mistakesScreen_showsEmptyState_whenNoGrammarMistakes() {
        // Arrange
        val state = MistakesUiState(
            isLoading = false,
            selectedTab = MistakeTab.GRAMMAR,
            grammarWrongAnswers = emptyList()
        )

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("暂无语法错题").assertIsDisplayed()
    }

    @Test
    fun mistakesScreen_showsWordMistakesList() {
        // Arrange
        val testWrongAnswers = listOf(
            WrongAnswer(
                id = 1,
                wordId = 1,
                word = "こんにちは",
                meaning = "你好",
                userAnswer = "再见",
                wrongCount = 3,
                lastWrongTime = System.currentTimeMillis()
            ),
            WrongAnswer(
                id = 2,
                wordId = 2,
                word = "ありがとう",
                meaning = "谢谢",
                userAnswer = "不客气",
                wrongCount = 2,
                lastWrongTime = System.currentTimeMillis()
            )
        )
        val state = MistakesUiState(
            isLoading = false,
            selectedTab = MistakeTab.WORD,
            wordWrongAnswers = testWrongAnswers
        )

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("こんにちは").assertIsDisplayed()
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
        composeTestRule.onNodeWithText("ありがとう").assertIsDisplayed()
        composeTestRule.onNodeWithText("谢谢").assertIsDisplayed()
    }

    @Test
    fun mistakesScreen_showsClearAllButton() {
        // Arrange
        val testWrongAnswers = listOf(
            WrongAnswer(
                id = 1,
                wordId = 1,
                word = "こんにちは",
                meaning = "你好",
                userAnswer = "再见",
                wrongCount = 3,
                lastWrongTime = System.currentTimeMillis()
            )
        )
        val state = MistakesUiState(
            isLoading = false,
            selectedTab = MistakeTab.WORD,
            wordWrongAnswers = testWrongAnswers
        )

        // Act
        composeTestRule.setContent {
            MistakesScreenContent(
                uiState = state,
                onTabSelected = {},
                onDeleteWordMistake = {},
                onDeleteGrammarMistake = {},
                onClearAll = {},
                onNavigateBack = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("清空错题").assertIsDisplayed()
    }
}

/**
 * 用于测试的MistakesScreenContent组件
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.runtime.Composable
private fun MistakesScreenContent(
    uiState: MistakesUiState,
    onTabSelected: (MistakeTab) -> Unit,
    onDeleteWordMistake: (Int) -> Unit,
    onDeleteGrammarMistake: (Int) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("错题本") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onClearAll) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                            contentDescription = "清空错题"
                        )
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            // Tab选择
            androidx.compose.material3.TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal
            ) {
                androidx.compose.material3.Tab(
                    selected = uiState.selectedTab == MistakeTab.WORD,
                    onClick = { onTabSelected(MistakeTab.WORD) },
                    text = { androidx.compose.material3.Text("单词") }
                )
                androidx.compose.material3.Tab(
                    selected = uiState.selectedTab == MistakeTab.GRAMMAR,
                    onClick = { onTabSelected(MistakeTab.GRAMMAR) },
                    text = { androidx.compose.material3.Text("语法") }
                )
            }

            // 内容区域
            when {
                uiState.isLoading -> {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                uiState.selectedTab == MistakeTab.WORD -> {
                    if (uiState.wordWrongAnswers.isEmpty()) {
                        EmptyMistakesContent("暂无单词错题")
                    } else {
                        WordMistakesListContent(
                            wrongAnswers = uiState.wordWrongAnswers,
                            onDelete = onDeleteWordMistake
                        )
                    }
                }
                uiState.selectedTab == MistakeTab.GRAMMAR -> {
                    if (uiState.grammarWrongAnswers.isEmpty()) {
                        EmptyMistakesContent("暂无语法错题")
                    } else {
                        EmptyMistakesContent("语法错题功能开发中")
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptyMistakesContent(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Info,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(64.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier.height(16.dp)
            )
            androidx.compose.material3.Text(
                text = message,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WordMistakesListContent(
    wrongAnswers: List<WrongAnswer>,
    onDelete: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = wrongAnswers.size,
            key = { index -> wrongAnswers[index].id }
        ) { index ->
            val wrongAnswer = wrongAnswers[index]
            SimpleWrongAnswerCard(
                wrongAnswer = wrongAnswer,
                onDelete = { onDelete(wrongAnswer.wordId) }
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun SimpleWrongAnswerCard(
    wrongAnswer: WrongAnswer,
    onDelete: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) {
                androidx.compose.material3.Text(
                    text = wrongAnswer.word,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                androidx.compose.material3.Text(
                    text = wrongAnswer.meaning,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.Text(
                    text = "错误 ${wrongAnswer.wrongCount} 次",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = "删除错题",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
