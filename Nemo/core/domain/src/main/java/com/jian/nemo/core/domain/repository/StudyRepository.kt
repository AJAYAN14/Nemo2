package com.jian.nemo.core.domain.repository

import com.jian.nemo.core.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow

/**
 * 核心学习进度仓库接口
 */
interface StudyRepository {

    /**
     * 获取到期的复习项目 (响应式 Flow)
     */
    fun getDueItemsFlow(): Flow<List<UserProgress>>

    /**
     * 提交复习评分
     * @param itemId 单词或语法 ID
     * @param itemType 'word' | 'grammar'
     * @param rating 1:Again, 2:Hard, 3:Good, 4:Easy
     */
    suspend fun processReview(itemId: String, itemType: String, rating: Int)

    /**
     * 开始实时监听云端变更
     */
    fun startRealtimeSync()

    /**
     * 停止实时监听
     */
    fun stopRealtimeSync()

    /**
     * 强制触发一次同步 (处理 Outbox 积压)
     */
    suspend fun syncPendingTasks()
}
