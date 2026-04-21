package com.jian.nemo.core.domain.repository

import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto

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
}
