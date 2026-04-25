package com.jian.nemo2.feature.learning.domain

import javax.inject.Inject

/**
 * 学习会话策略
 * 负责计算配额、混合列表等纯逻辑算法
 *
 * 遵循原则:
 * 1. 纯逻辑，不包含 Android 依赖
 * 2. 泛型支持，统用于 Word 和 Grammar
 */
class LearningSessionPolicy @Inject constructor() {

    /**
     * 计算调整后的新词配额
     *
     * 当前策略：不对新词做减载，始终返回目标配额。
     * 即“有多少学多少”，复习数量不会压缩新词配额。
     *
     * @param targetQuota 原始目标配额
     * @param dueCount 到期复习数量（当前策略未使用）
     */
    fun calculateAdjustedNewQuota(targetQuota: Int, dueCount: Int): Int {
        return targetQuota
    }

    /**
     * 智能穿插排序 (Sandwich Mix)
     * 结构：[高危复习] -> [新词均匀分散在普通复习中]
     *
     * 算法：动态比例分散
     * - 根据复习/新词的实际数量比例计算插入间隔
     * - 新词均匀分布在整个 session 中，避免堆积
     * - 例如：10复习+4新词 → 每隔约2-3个复习插入1个新词
     *
     * @param dueItems 复习项列表 (假设已按优先级/DueDate排序)
     * @param newItems 新项列表
     */
    fun <T> mixSessionItems(dueItems: List<T>, newItems: List<T>): List<T> {
        if (dueItems.isEmpty()) return newItems
        if (newItems.isEmpty()) return dueItems

        // 1. 提取高危复习项 (Top 20%, 最少3个, 最多全部)
        //    这些项会被放在最前面优先复习
        val urgentCount = (dueItems.size * 0.2).toInt().coerceAtLeast(3).coerceAtMost(dueItems.size)
        val urgentReviews = dueItems.take(urgentCount)
        val normalReviews = dueItems.drop(urgentCount)

        // 2. 如果没有普通复习项，直接返回 紧急复习 + 新词
        if (normalReviews.isEmpty()) {
            return urgentReviews + newItems
        }

        // 3. 动态比例混合：将新词均匀分散到普通复习中
        val mixed = mutableListOf<T>()
        val reviewCount = normalReviews.size
        val newCount = newItems.size

        // 计算插入位置：将新词均匀分布
        // 例如：10个复习位置，4个新词 → 在位置 2, 5, 7, 10 后插入新词
        val insertPositions = if (newCount > 0) {
            val step = (reviewCount + 1).toDouble() / newCount
            (0 until newCount).map { i ->
                ((i + 1) * step).toInt().coerceAtMost(reviewCount)
            }.toSet()
        } else {
            emptySet()
        }

        var newItemIndex = 0
        normalReviews.forEachIndexed { index, review ->
            mixed.add(review)
            // 在特定位置后插入新词
            if ((index + 1) in insertPositions && newItemIndex < newItems.size) {
                mixed.add(newItems[newItemIndex])
                newItemIndex++
            }
        }

        // 4. 添加剩余的新词（如果有的话）
        while (newItemIndex < newItems.size) {
            mixed.add(newItems[newItemIndex])
            newItemIndex++
        }

        // 5. 组合最终列表
        return urgentReviews + mixed
    }
}
