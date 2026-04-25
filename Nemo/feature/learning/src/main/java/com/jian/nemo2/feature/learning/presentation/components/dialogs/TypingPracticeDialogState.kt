package com.jian.nemo2.feature.learning.presentation.components.dialogs

import androidx.compose.runtime.*
import com.jian.nemo2.core.domain.model.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FeedbackState {
    HIDDEN,   // 隐藏
    CORRECT,  // 正确
    INCORRECT // 错误
}

@Stable
class TypingPracticeDialogState(
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit
) {
    var kanaInput by mutableStateOf("")
        private set

    var kanjiInput by mutableStateOf("")
        private set

    var feedbackState by mutableStateOf(FeedbackState.HIDDEN)
        private set

    fun updateKanaInput(value: String) {
        kanaInput = value
        // 输入时隐藏错误提示，优化体验
        if (feedbackState == FeedbackState.INCORRECT) {
            feedbackState = FeedbackState.HIDDEN
        }
    }

    fun updateKanjiInput(value: String) {
        kanjiInput = value
        if (feedbackState == FeedbackState.INCORRECT) {
            feedbackState = FeedbackState.HIDDEN
        }
    }

    fun clear() {
        kanaInput = ""
        kanjiInput = ""
        feedbackState = FeedbackState.HIDDEN
    }

    fun validate(word: Word) {
        if (kanaInput.trim() == word.hiragana && kanjiInput.trim() == word.japanese) {
            feedbackState = FeedbackState.CORRECT
            // 正确后延迟关闭
            scope.launch {
                delay(1500)
                onDismiss()
            }
        } else {
            feedbackState = FeedbackState.INCORRECT
        }
    }
}

@Composable
fun rememberTypingPracticeDialogState(
    onDismiss: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
): TypingPracticeDialogState {
    return remember(scope, onDismiss) {
        TypingPracticeDialogState(scope, onDismiss)
    }
}
