package com.jian.nemo.feature.learning.domain

import org.junit.Assert.*
import org.junit.Test

class LearningQueueManagerTest {

    private val manager = LearningQueueManager()

    @Test
    fun `when dueTimes are all zero, selectNextItem should honor preferredIndex`() {
        // Given: 10个新词，dueTime 全是 0
        val items = (0 until 10).toList()
        val now = 1000L
        val learnAheadLimitMs = 20 * 60 * 1000L
        
        // When: preferredIndex 为 5
        val result = manager.selectNextItem(
            items = items,
            getDueTime = { 0L },
            now = now,
            learnAheadLimitMs = learnAheadLimitMs,
            preferredIndex = 5
        )

        // Then: 应该选中索引 5
        assertTrue(result is QueueSelectionResult.Next)
        assertEquals(5, (result as QueueSelectionResult.Next).index)
    }

    @Test
    fun `when items have different dueTimes, selectNextItem should choose the minimum even if it is before preferredIndex`() {
        // Given: 索引 2 的到期时间最小（已到期）
        val items = listOf(0, 1, 2, 3, 4)
        val dueTimes = mapOf(
            0 to 500L,
            1 to 500L,
            2 to 100L, // 最优项
            3 to 500L,
            4 to 500L
        )

        // When: 即使 preferredIndex 为 3
        val result = manager.selectNextItem(
            items = items,
            getDueTime = { dueTimes[it] ?: 1000L },
            now = 1000L,
            learnAheadLimitMs = 0,
            preferredIndex = 3
        )

        // Then: 仍然应该返回全局最优项索引 2
        assertTrue(result is QueueSelectionResult.Next)
        assertEquals(2, (result as QueueSelectionResult.Next).index)
    }

    @Test
    fun `when dueTimes are equal and preferredIndex is out of range, should fallback to index 0`() {
        val items = listOf(0, 1, 2)
        
        val result = manager.selectNextItem(
            items = items,
            getDueTime = { 0L },
            now = 1000L,
            learnAheadLimitMs = 0,
            preferredIndex = 10 // 超出范围
        )

        assertEquals(0, (result as QueueSelectionResult.Next).index)
    }
}
