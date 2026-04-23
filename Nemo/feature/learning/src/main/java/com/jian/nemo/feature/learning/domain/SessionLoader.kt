package com.jian.nemo.feature.learning.domain

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

        // 1. 尝试恢复会话 (保持优先级最高，避免重复播种导致队列变动)
        if (savedSession != null && savedSession.level == level) {
            val (ids, index, _, steps) = savedSession
            val allItems = getItemsByIds(ids)

            val itemMap = allItems.associateBy { getItemId(it) }
            // [Duolingo-style] 恢复时进行有效性过滤：剔除已在其他设备复习过的项目
            val restoredItems = ids.mapNotNull { id ->
                val item = itemMap[id]
                if (item != null) {
                    // 如果项目已经学习过 (isLearned) 且不再处于待复习池 (allItems 不包含它)
                    // 则认为它已经不再属于当前学习计划，应该剔除
                    if (isLearned(item) && !allItems.contains(item)) {
                        println("⚠️ 自动过滤已复习项: $id")
                        null
                    } else {
                        item
                    }
                } else null
            }

            if (restoredItems.isNotEmpty() && index < restoredItems.size) {
                println("✅ 恢复上次学习会话: Index $index / ${restoredItems.size}")
                return SessionLoadResult.Restored(
                    items = restoredItems,
                    index = index,
                    steps = steps,
                    dailyGoal = dailyGoal,
                    completedToday = completedToday,
                    waitingUntil = savedSession.waitingUntil
                )
            }
        }

        // 2. 播种今日新词 (如果恢复失败，则进行播种)
        // 注意：seedNewItems 内部应包含 RPC 调用和后续的同步逻辑
        seedNewItems()

        // 3. 获取所有待学项目 (从 user_progress 表获取 state IN (0,1,2,3))
        // 这里的 getDueItems 应当返回包含新播种的新词 (state=0) 和到期复习项 (state=2) 的集合
        val allItems = getDueItems()
        
        // 4. 分离新词和到期项
        // 新词：reps = 0 (state = 0)
        // 到期项：reps > 0 (state = 1, 2, 3)
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
