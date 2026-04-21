package com.jian.nemo.feature.learning.domain

import javax.inject.Inject

/**
 * 队列选择结果
 */
sealed class QueueSelectionResult<out T> {
    /** 选中了下一个项目 */
    data class Next<out T>(val index: Int, val item: T) : QueueSelectionResult<T>()

    /** 需要等待 (所有项目都未到期且超过提前学习限制) */
    data class Wait<out T>(val waitingUntil: Long) : QueueSelectionResult<T>()

    /** 队列为空 */
    data object Empty : QueueSelectionResult<Nothing>()
}

/**
 * 学习队列管理器
 *
 * 负责从学习队列中选择下一个要展示的项目。
 * 核心逻辑：
 * 1. 优先级选择 (Priority Queue): 总是优先选择最早到期 (DueTime 最小) 的项目
 * 2. 提前学习 (Learn Ahead): 如果最早到期的项目还未到期，但在允许的提前学习窗口内，则允许提前展示
 * 3. 等待机制 (Waiting): 如果最早的项目也远未到期，则进入等待状态
 */
class LearningQueueManager @Inject constructor() {

    /**
     * 选择下一个项目
     *
     * @param items 候选列表
     * @param getDueTime 获取项目到期时间的函数
     * @param now 当前时间 (毫秒)
     * @param learnAheadLimitMs 提前学习限制 (毫秒)
     */
    fun <T> selectNextItem(
        items: List<T>,
        getDueTime: (T) -> Long,
        now: Long,
        learnAheadLimitMs: Long,
        preferredIndex: Int = 0
    ): QueueSelectionResult<T> {
        if (items.isEmpty()) {
            return QueueSelectionResult.Empty
        }

        // 1. 寻找最优项 (Min DueTime)
        var bestIndex = 0
        var minDueTime = Long.MAX_VALUE

        items.forEachIndexed { index, item ->
            val due = getDueTime(item)
            
            // 核心逻辑：
            // 1. 如果找到更小的 dueTime，无条件更新
            // 2. 如果 dueTime 相同（或都为 0L），则根据 preferredIndex 决定：
            //    - 如果当前 index >= preferredIndex 且目前的 bestIndex < preferredIndex，说明当前项是“更自然”的下一项
            if (due < minDueTime) {
                minDueTime = due
                bestIndex = index
            } else if (due == minDueTime) {
                // 当分值相等时，优先选取在游标位置及其之后的项，防止跳回开头
                if (index >= preferredIndex && bestIndex < preferredIndex) {
                    bestIndex = index
                }
            }
        }

        // 2. 检查是否需要等待
        if (minDueTime > now) {
            val waitTime = minDueTime - now
            // 如果等待时间超过了提前学习限制，则必须等待
            if (waitTime > learnAheadLimitMs) {
                return QueueSelectionResult.Wait(waitingUntil = minDueTime)
            }
            // 否则，允许提前学习 (Learn Ahead)
        }

        // 3. 返回选中项
        return QueueSelectionResult.Next(bestIndex, items[bestIndex])
    }
}
