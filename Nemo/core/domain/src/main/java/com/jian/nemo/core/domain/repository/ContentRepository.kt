package com.jian.nemo.core.domain.repository

import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto
import com.jian.nemo.core.domain.model.dto.GrammarTestQuestionDto

/**
 * 词库内容同步仓库
 *
 * 负责从 Supabase dictionary_words 和 dictionary_grammars 表拉取最新数据。
 */
interface ContentRepository {

    /**
     * 获取云端当前词库版本号 (可通过 sync_meta 或配置表获取)
     */
    suspend fun getRemoteContentVersion(): Int?

    /**
     * 拉取指定等级的云端单词列表
     * @param level N1～N5
     */
    suspend fun fetchRemoteWords(level: String): List<WordDto>

    /**
     * 拉取指定等级的云端语法列表
     * @param level N1～N5
     */
    suspend fun fetchRemoteGrammars(level: String): List<GrammarDto>

    /**
     * 拉取指定等级的云端语法测试题
     * @param level N1～N5
     */
    suspend fun fetchRemoteGrammarQuestions(level: String): List<GrammarTestQuestionDto>

    // ========== 全量拉取 (性能优化：减少网络往返) ==========

    /**
     * 一次性拉取所有等级的单词
     */
    suspend fun fetchAllRemoteWords(): List<WordDto>

    /**
     * 一次性拉取所有等级的语法
     */
    suspend fun fetchAllRemoteGrammars(): List<GrammarDto>

    /**
     * 一次性拉取所有语法测试题
     */
    suspend fun fetchAllRemoteGrammarQuestions(): List<GrammarTestQuestionDto>
}
