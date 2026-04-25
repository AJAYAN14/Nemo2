package com.jian.nemo2.feature.learning.domain

import com.jian.nemo2.core.domain.model.Grammar
import com.jian.nemo2.core.domain.model.Word
import com.jian.nemo2.feature.learning.presentation.LearningItem
import javax.inject.Inject

/**
 * 撤销快照 - 保存评分前的完整状态
 */
data class UndoSnapshot(
    // 卡片信息
    val item: LearningItem,
    val originalWord: Word? = null,
    val originalGrammar: Grammar? = null,

    // 队列状态
    val wordList: List<Word>,
    val grammarList: List<Grammar>,
    val currentIndex: Int,
    val currentGrammarIndex: Int,

    // 学习状态
    val learningSteps: Map<Long, Int>,
    val lapseCounts: Map<Long, Int>,
    val requeuedItems: Set<Long>,
    val learningDueTimes: Map<Long, Long>,

    // 统计
    val completedToday: Int,
    val completedThisSession: Int,
    val sessionProcessedCount: Int,

    // 元数据
    val requestId: String? = null,
    val lastReview: String? = null,
    val studyField: String? = null,

    // 是否为新词（用于恢复统计）
    val wasNew: Boolean,
    val wasLeech: Boolean = false
)

/**
 * 撤销功能辅助类
 *
 * 负责管理撤销快照 (Snapshot) 的创建和存储。
 * 目前只支持单步撤销 (保留最后一次状态)。
 */
class LearningUndoHelper @Inject constructor() {

    private var snapshot: UndoSnapshot? = null

    /**
     * 保存快照
     */
    fun saveSnapshot(undoSnapshot: UndoSnapshot) {
        this.snapshot = undoSnapshot
    }

    /**
     * 获取并消耗快照 (获取后清空，防止多次撤销)
     */
    fun popSnapshot(): UndoSnapshot? {
        val s = snapshot
        snapshot = null
        return s
    }

    /**
     * 清空快照
     */
    fun clear() {
        snapshot = null
    }

    /**
     * 是否有可撤销的内容
     */
    fun canUndo(): Boolean {
        return snapshot != null
    }
}
