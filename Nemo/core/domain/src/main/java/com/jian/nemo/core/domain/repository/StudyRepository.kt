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
    suspend fun processReview(itemId: Long, itemType: String, rating: Int)

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

    /**
     * 暂停项目 (不再出现，state = -1)
     */
    suspend fun suspendItem(itemId: Long, itemType: String)

    /**
     * 取消暂停项目 (恢复到新学状态，state = 0)
     */
    suspend fun unsuspendItem(itemId: Long, itemType: String)

    /**
     * 今日暂缓 (今日不再出现)
     */
    suspend fun buryItem(itemId: Long, itemType: String, epochDay: Long)

    /**
     * 切换收藏状态
     */
    suspend fun toggleFavorite(itemId: Long, itemType: String, isFavorite: Boolean)

    /**
     * 重置所有进度
     */
    suspend fun resetAllProgress(itemType: String)

    /**
     * 清空所有收藏
     */
    suspend fun clearAllFavorites(itemType: String)
    /**
     * 播种今日新词 (调用 Supabase RPC fn_seed_daily_new_items)
     * 保证 Android 与 Web 端拉取的新词集合完全一致
     */
    suspend fun seedDailyNewItems(
        itemType: String,
        limit: Int,
        level: String,
        isRandom: Boolean,
        epochDay: Int
    )

    /**
     * 根据类型和等级获取到期的复习项目
     */
    fun getDueItemsByTypeAndLevelFlow(itemType: String, level: String): Flow<List<UserProgress>>

    /**
     * 执行强制全量同步 (拉取字典和所有用户进度)
     */
    suspend fun performFullSync()
}

