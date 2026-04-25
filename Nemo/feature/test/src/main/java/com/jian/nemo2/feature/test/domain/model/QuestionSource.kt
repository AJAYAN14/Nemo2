package com.jian.nemo2.feature.test.domain.model

/**
 * 题目来源枚举
 *
 * 定义了测试中可用的题目来源范围
 */
enum class QuestionSource(val key: String, val displayName: String) {
    TODAY("today", "今日学习的内容"),
    WRONG("wrong", "我的错题"),
    FAVORITE("favorite", "我的收藏"),
    LEARNED("learned", "所有已学习过的内容"),
    TODAY_REVIEWED("today_reviewed", "今日复习的内容"),
    ALL("all", "所有内容");

    companion object {
        fun fromKey(key: String): QuestionSource =
            entries.find { it.key == key } ?: ALL
    }
}
