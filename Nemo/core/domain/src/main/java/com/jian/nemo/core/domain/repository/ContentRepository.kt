package com.jian.nemo.core.domain.repository

import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto

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
}
