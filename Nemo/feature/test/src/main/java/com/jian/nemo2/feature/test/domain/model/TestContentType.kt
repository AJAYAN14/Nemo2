package com.jian.nemo2.feature.test.domain.model

/**
 * 测试内容类型枚举
 *
 * 定义了测试中可以使用的内容类型
 */
enum class TestContentType(val key: String, val displayName: String) {
    WORDS("words", "仅测试单词"),
    GRAMMAR("grammar", "仅测试语法"),
    MIXED("mixed", "单词和语法混合");

    companion object {
        fun fromKey(key: String): TestContentType =
            entries.find { it.key == key } ?: MIXED
    }
}
