package com.jian.nemo.feature.learning.domain

import com.jian.nemo.core.domain.model.SrsItem
import com.jian.nemo.core.domain.service.SrsCalculator
import javax.inject.Inject

/**
 * SRS 间隔预览计算器
 * 负责计算并在 UI 上显示 0-5 分对应的下次复习间隔
 *
 * 适用于 Word 和 Grammar
 */
class SrsIntervalPreview @Inject constructor(
    private val srsCalculator: SrsCalculator
) {
    /**
     * 计算间隔预览文本
     *
     * @param item SRS 项目 (Word 或 Grammar)
     * @param itemId 项目 ID (用于在 steps 中查找)
     * @param steps 当前学习阶段映射表 (WordSteps 或 GrammarSteps).
     * @param learningStepsConfig 新词学习步骤配置
     * @param relearningStepsConfig 重学步骤配置
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

        val currentStep = steps?.get(itemId)
        val isNew = item.repetitionCount == 0
        // Currently Learning: New Card OR Relearning Card
        val isLearning = isNew || currentStep != null

        for (q in 0..5) {
             // 1. Fail (Again): Show first relearning step (or learning step if new)
             if (q < 3) {
                 val firstStepMin = if (isNew) {
                     learningStepsConfig.firstOrNull() ?: 1
                 } else {
                     relearningStepsConfig.firstOrNull() ?: 1
                 }

                 // Show "< 1m" or "10m" etc.
                 val text = if (firstStepMin < 1) "< 1m" else "${firstStepMin}m"
                 intervals[q] = text
                 continue
             }

             // 2. Pass (Hard/Good/Easy) while in Learning Mode
             if (isLearning) {
                 // Decide which config to use
                 val config = if (isNew) learningStepsConfig else relearningStepsConfig
                 val stepIndex = currentStep ?: 0

                 // Good (4) Logic:
                 if (q == 4) {
                    if (stepIndex < config.size - 1) {
                        // Move to next step
                        val nextStepMin = config.getOrElse(stepIndex + 1) { 10 }
                        intervals[q] = "${nextStepMin}m"
                        continue
                    } else {
                        // Graduate
                        // 特殊处理: 重学毕业 (Relearning Graduation)
                        // 此时不应调用 SrsCalculator (因为它会再次乘 EF)，而是应该保持当前的惩罚后间隔
                        if (!isNew) {
                            // Relearning Graduate: Use current (penalized) interval
                            // 显示 "45d" 而不是 "112d"
                            intervals[q] = "${item.interval}d"
                            continue
                        }
                        // 新卡毕业 -> Fall through to calculator
                    }
                } else if (q == 3) {
                     // Hard (3) Logic: Anki algorithm (average of first and second step if on first step)
                     val delay = calculateHardDelayMin(stepIndex, config)
                     val isWhole = (delay % 1.0) == 0.0
                     val formatted = if (isWhole) delay.toInt().toString() else delay.toString()
                     intervals[q] = "${formatted}m"
                     continue
                 } else if (q == 5) {
                     // Easy (5): Instant Graduate
                     // Fall through to calculator
                 }
             }

             // 3. SRS Calculator / Graduation
             val result = srsCalculator.calculate(item, q, today)

             val text = if (result.interval <= 0) {
                 "< 1m" // Should not happen for graduated cards usually
             } else {
                 "${result.interval}d"
             }
             intervals[q] = text
        }
        return intervals
    }

    private fun calculateHardDelayMin(stepIndex: Int, config: List<Int>): Double {
        val currentStepMin = config.getOrElse(stepIndex) { 1 }
        if (stepIndex == 0) {
            val nextStepMin = config.getOrNull(1)
            return if (nextStepMin != null && nextStepMin > 0) {
                maybeRoundInDaysMinutes((currentStepMin + nextStepMin) / 2.0)
            } else {
                val increased = minOf(currentStepMin * 1.5, currentStepMin + 1440.0)
                maybeRoundInDaysMinutes(increased)
            }
        }
        return currentStepMin.toDouble()
    }

    private fun maybeRoundInDaysMinutes(delayMins: Double): Double {
        val dayMinutes = 1440.0
        if (delayMins > dayMinutes) {
            return maxOf(dayMinutes, Math.round(delayMins / dayMinutes) * dayMinutes)
        }
        return delayMins
    }
}
