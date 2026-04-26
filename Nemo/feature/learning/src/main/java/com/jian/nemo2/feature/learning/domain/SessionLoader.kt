package com.jian.nemo2.feature.learning.domain

import javax.inject.Inject

/**
 * 会话加载结果
 * 封装 Session 加载的三种可能结果
 */
sealed class SessionLoadResult<T> {
    /**
     * 恢复了之前未完成的会话
     */
    data class Restored<T>(
        val items: List<T>,
        val index: Int,
        val steps: Map<Long, Int>,
        val dailyGoal: Int,
        val completedToday: Int,
        val waitingUntil: Long = 0L // 新增
    ) : SessionLoadResult<T>()

    /**
     * 创建了新会话
     */
    data class NewSession<T>(
        val items: List<T>,
        val dueCount: Int,
        val newCount: Int,
        val dailyGoal: Int,
        val completedToday: Int
    ) : SessionLoadResult<T>()

    /**
     * 会话已完成（无更多项目可学习）
     */
    data class Completed<T>(
        val dailyGoal: Int,
        val completedToday: Int
    ) : SessionLoadResult<T>()
}

/**
 * 已保存的会话数据
 */
data class SavedSession(
    val ids: List<Long>,
    val index: Int,
    val level: String,
    val steps: Map<Long, Int>,
    val waitingUntil: Long = 0L // 新增：保存等待状态
)

/**
 * 会话加载器
 *
 * 负责统一处理 Word 和 Grammar 会话的加载逻辑：
 * 1. 尝试恢复已保存的会话
 * 2. 获取到期复习项
 * 3. 计算新项配额
 * 4. 智能混合排序
 *
 * 遵循原则:
 * 1. 纯逻辑，不包含 Android 依赖
 * 2. 泛型支持，统一用于 Word 和 Grammar
 */
class SessionLoader @Inject constructor(
    private val learningSessionPolicy: LearningSessionPolicy
) {
    /**
     * 加载学习会话
     *
     * @param T 项目类型 (Word 或 Grammar)
     * @param level 当前学习等级
     * @param dailyGoal 每日目标
     * @param completedToday 今日已完成数量
     * @param savedSession 已保存的会话（如果有）
     * @param getItemsByIds 根据 ID 列表获取项目
     * @param getDueItems 获取到期复习项
     * @param getNewItems 获取新项目
     * @param getItemId 获取项目 ID
     * @param filterByLevel 按等级过滤
     */
    /**
     * 加载学习会话
     *
     * @param T 项目类型 (Word 或 Grammar)
     * @param level 当前学习等级
     * @param dailyGoal 每日目标
     * @param completedToday 今日已完成数量
     * @param savedSession 已保存的会话（如果有）
     * @param getItemsByIds 根据 ID 列表获取项目
     * @param getDueItems 获取所有待学项目 (包含新词和到期复习项)
     * @param getItemId 获取项目 ID
     * @param isLearned 判断项目是否已学习 (reps > 0)
     * @param seedNewItems 播种今日新词回调
     */
    suspend fun <T> loadSession(
        level: String,
        dailyGoal: Int,
        completedToday: Int,
        savedSession: SavedSession?,
        getItemsByIds: suspend (List<Long>) -> List<T>,
        getDueItems: suspend () -> List<T>,
        getItemId: (T) -> Long,
        isLearned: (T) -> Boolean,
        seedNewItems: suspend () -> Unit
    ): SessionLoadResult<T> {

        // 1. Fetch current due items pool (this contains state 0, 1, 2, 3)
        val currentDueItems = getDueItems()
        val currentDueIds = currentDueItems.map { getItemId(it) }.toSet()

        // 2. 尝试恢复会话 (保持优先级最高)
        if (savedSession != null && savedSession.level == level) {
            val (ids, index, _, steps) = savedSession
            val allItems = getItemsByIds(ids)
            val itemMap = allItems.associateBy { getItemId(it) }
            
            var adjustedIndex = index
            val restoredItems = mutableListOf<T>()
            
            ids.forEachIndexed { i, id ->
                val item = itemMap[id]
                // 仅保留仍然在待学池中的项 (未在其他设备提前完成)
                if (item != null && currentDueIds.contains(id)) {
                    restoredItems.add(item)
                } else {
                    // 如果前面的项被剔除了，索引需要前移
                    if (i < index) {
                        adjustedIndex--
                    }
                }
            }

            if (restoredItems.isNotEmpty()) {
                val finalIndex = adjustedIndex.coerceIn(0, restoredItems.size - 1)
                println("✅ 恢复并调整学习会话: Index $finalIndex / ${restoredItems.size} (原索引 $index)")
                return SessionLoadResult.Restored(
                    items = restoredItems,
                    index = finalIndex,
                    steps = steps,
                    dailyGoal = dailyGoal,
                    completedToday = completedToday,
                    waitingUntil = savedSession.waitingUntil
                )
            }
        }

        // 3. 播种今日新词 (如果恢复失败，则进行播种)
        seedNewItems()

        // 4. 获取所有待学项目 (刷新后的全量列表)
        val allItems = getDueItems()

        // 5. 分离新词和到期项
        val newPool = allItems.filter { !isLearned(it) }
        val duePool = allItems.filter { isLearned(it) }

        val dueCount = duePool.size

        // 5. 计算新项配额
        val rawRemainingQuota = (dailyGoal - completedToday).coerceAtLeast(0)
        val adjustedQuota = learningSessionPolicy.calculateAdjustedNewQuota(rawRemainingQuota, dueCount)

        // [Debug Log]
        println("📊 会话规划: 目标=$dailyGoal, 已学=$completedToday, 复习堆积=$dueCount")
        println("   -> 新词配额=$adjustedQuota (池中可用: ${newPool.size})")

        // 6. 如果配额为 0 且无复习项，则会话完成
        if (adjustedQuota <= 0 && duePool.isEmpty()) {
            return SessionLoadResult.Completed(
                dailyGoal = dailyGoal,
                completedToday = completedToday
            )
        }

        // 7. 组装会话列表: 智能混合 (Smart Interleaving)
        // 仅取配额内的新词
        val sessionNewItems = newPool.take(adjustedQuota)
        val sessionItems = learningSessionPolicy.mixSessionItems(duePool, sessionNewItems)

        if (sessionItems.isEmpty()) {
            return SessionLoadResult.Completed(
                dailyGoal = dailyGoal,
                completedToday = completedToday
            )
        }

        println("✅ 学习会话启动成功: ${sessionItems.size} 个项目 (复习: ${duePool.size}, 新词: ${sessionNewItems.size})")

        return SessionLoadResult.NewSession(
            items = sessionItems,
            dueCount = duePool.size,
            newCount = sessionNewItems.size,
            dailyGoal = dailyGoal,
            completedToday = completedToday
        )
    }
}
