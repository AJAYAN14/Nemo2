package com.jian.nemo2.core.domain.repository

import com.jian.nemo2.core.domain.model.dto.WordDto
import com.jian.nemo2.core.domain.model.dto.GrammarDto
import com.jian.nemo2.core.domain.model.dto.GrammarTestQuestionDto

/**
 * 将云端词库数据合并到本地 DB（按等级）
 *
 * 策略：
 * 1. 单词：按 (level, japanese) 匹配则 UPDATE（保持 ID 兼容），否则 INSERT。
 * 2. 语法：按 id REPLACE 主表并重写 usages/examples。
 */
interface ContentUpdateApplier {

    /**
     * 将指定等级的单词数据合并到本地
     * @return 本等级更新/插入的条数，失败返回 null
     */
    suspend fun applyWords(level: String, words: List<WordDto>): Int?

    /**
     * 将指定等级的语法数据合并到本地
     * @return 本等级更新/插入的条数，失败返回 null
     */
    suspend fun applyGrammars(level: String, grammars: List<GrammarDto>): Int?

    /**
     * 将指定等级的语法测试题合并到本地
     * @return 本等级更新/插入的条数，失败返回 null
     */
    suspend fun applyGrammarQuestions(level: String, questions: List<GrammarTestQuestionDto>): Int?

    // ========== 全量批量写入 (性能优化) ==========

    /**
     * 一次性将所有单词数据合并到本地 (批量事务写入)
     * 自动按 level 分组处理下架逻辑
     * @return 更新/插入的总条数，失败返回 null
     */
    suspend fun applyAllWords(words: List<WordDto>): Int?

    /**
     * 一次性将所有语法数据合并到本地 (批量事务写入)
     * 优化：先收集所有子表数据，再一次性批量插入
     * @return 更新/插入的总条数，失败返回 null
     */
    suspend fun applyAllGrammars(grammars: List<GrammarDto>): Int?

    /**
     * 一次性将所有语法测试题合并到本地 (批量事务写入)
     * @return 更新/插入的总条数，失败返回 null
     */
    suspend fun applyAllGrammarQuestions(questions: List<GrammarTestQuestionDto>): Int?
}

