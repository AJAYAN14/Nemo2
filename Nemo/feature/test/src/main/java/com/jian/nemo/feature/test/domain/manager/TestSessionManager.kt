package com.jian.nemo.feature.test.domain.manager

import com.jian.nemo.core.domain.model.TestQuestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 测试会话管理器
 *
 * 用于在TestSettingsViewModel（生成题目）和TestViewModel（使用题目）之间传递数据。
 * 这避免了在Intent中传递大量Parcelable数据，也解耦了题目生成和导航逻辑。
 */
@Singleton
class TestSessionManager @Inject constructor() {

    private var preGeneratedQuestions: List<TestQuestion>? = null

    /**
     * 设置预生成的题目
     */
    fun setQuestions(questions: List<TestQuestion>) {
        preGeneratedQuestions = questions
    }

    /**
     * 获取预生成的题目，并（可选）清除缓存
     * @param clearAfterGet 是否在获取后清除缓存，默认为true
     */
    fun getQuestions(clearAfterGet: Boolean = true): List<TestQuestion>? {
        val questions = preGeneratedQuestions
        if (clearAfterGet) {
            preGeneratedQuestions = null
        }
        return questions
    }

    /**
     * 清除缓存
     */
    fun clear() {
        preGeneratedQuestions = null
    }

    /**
     * 检查是否有预生成的题目
     */
    fun hasQuestions(): Boolean {
        return preGeneratedQuestions != null && preGeneratedQuestions!!.isNotEmpty()
    }
}
