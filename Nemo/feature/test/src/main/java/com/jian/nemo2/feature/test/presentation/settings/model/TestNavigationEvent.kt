package com.jian.nemo2.feature.test.presentation.settings.model

import com.jian.nemo2.core.domain.model.QuestionType
import com.jian.nemo2.core.domain.model.TestMode

/**
 * 此时测试导航的事件
 */
sealed class TestNavigationEvent {
    data class NavigateToTest(
        val level: String,
        val mode: TestMode,
        val questionType: QuestionType,
        val contentType: String,
        val source: String
    ) : TestNavigationEvent()

    data class NavigateToTypingTest(val level: String) : TestNavigationEvent()
}
