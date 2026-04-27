package com.jian.nemo2.feature.learning.domain

import com.jian.nemo2.core.domain.algorithm.Fsrs6Algorithm
import com.jian.nemo2.core.domain.model.RatingAction
import com.jian.nemo2.core.domain.model.SrsItem
import com.jian.nemo2.core.domain.service.SrsCalculator
import javax.inject.Inject

/**
 * SRS 间隔预览计算器
 * 负责计算并在 UI 上显示 1-4 分 (Again, Hard, Good, Easy) 对应的下次复习间隔
 *
 * 遵循 rules.md: 3.D Algorithm Precision (对齐 Web 端 logic)
 */
class SrsIntervalPreview @Inject constructor(
    private val srsCalculator: SrsCalculator
) {
    private val fsrs = Fsrs6Algorithm()

    /**
     * 计算间隔预览文本
     *
     * @param item SRS 项目 (Word 或 Grammar)
     * @param itemId 项目 ID (用于在 steps 中查找)
     * @param steps 当前学习阶段映射表 (ItemId -> StepIndex)
     * @param learningStepsConfig 新词学习步骤配置 (分钟)
     * @param relearningStepsConfig 重学步骤配置 (分钟)
     * @param today 当前学习日
     */
    fun calculate(
        item: SrsItem?,
        itemId: Long,
        steps: Map<Long, Int>?,
        learningStepsConfig: List<Int>,
        relearningStepsConfig: List<Int>,
        today: Long
    ): Map<Int, String> {
        if (item == null) return emptyMap()

        val intervals = mutableMapOf<Int, String>()
        val currentStep = steps?.get(itemId) ?: 0
        
        // 确定状态 (0: New, 1: Learning, 2: Review, 3: Relearning)
        val isNew = item.repetitionCount == 0
        val state = when {
            isNew -> 0
            item.repetitionCount > 0 && steps?.containsKey(itemId) == true -> 3 // Relearning
            item.repetitionCount > 0 -> 2 // Review
            else -> 1 // Learning
        }

        for (rating in 1..4) {
            val action = fsrs.evaluateRatingAction(
                state = state,
                lapses = item.lapses,
                currentStep = currentStep,
                rating = rating,
                learningSteps = learningStepsConfig,
                relearningSteps = relearningStepsConfig
            )

            intervals[rating] = when (action) {
                is RatingAction.Requeue -> {
                    // 显示分钟
                    if (action.delayMins < 1) "< 1m" else "${action.delayMins}m"
                }
                is RatingAction.Graduate, is RatingAction.Leech -> {
                    // 使用 SrsCalculator 计算毕业后的天数
                    val result = srsCalculator.calculate(item, rating, today)
                    val interval = result.interval
                    if (interval <= 0) "1d" else "${interval}d"
                }
            }
        }
        return intervals
    }
}
