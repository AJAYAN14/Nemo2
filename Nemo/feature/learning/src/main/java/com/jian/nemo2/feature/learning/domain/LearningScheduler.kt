package com.jian.nemo2.feature.learning.domain

import com.jian.nemo2.feature.learning.presentation.LearningItem
import javax.inject.Inject

/**
 * 调度结果
 */
sealed class ScheduleResult {
    /**
     * 重新入队 (Review/Learning)
     * 对应: Again, Hard, Good (Intermediate Step)
     */
    data class Requeue(
        val updatedItem: LearningItem,
        val nextStepIndex: Int,
        val dueTime: Long,
        val isLapse: Boolean // 是否为失败导致
    ) : ScheduleResult()

    /**
     * 毕业 (Graduate)
     * 对应: Good (Last Step), Easy
     */
    data class Graduate(
        val item: LearningItem,
        val quality: Int
    ) : ScheduleResult()

    /**
     * 钉子户 (Leech)
     * 对应: 失败次数过多
     */
    data class Leech(
        val item: LearningItem,
        val totalLapses: Int
    ) : ScheduleResult()
}

/**
 * 学习调度器
 *
 * 负责处理卡片评分后的流转逻辑 (Anki 算法的核心状态机)。
 * 不涉及数据库操作，只进行纯逻辑计算。
 */
class LearningScheduler @Inject constructor() {

    companion object {
        private const val LEECH_THRESHOLD = 5
    }

    /**
     * 处理失败 (评分 < 2)
     */
    fun scheduleFailure(
        item: LearningItem,
        currentLapseCount: Int,
        stepConfig: List<Int>,
        leechThreshold: Int = LEECH_THRESHOLD
    ): ScheduleResult {
        val newLapseCount = currentLapseCount + 1

        // 1. 钉子户检测
        if (newLapseCount >= leechThreshold.coerceAtLeast(1)) {
            return ScheduleResult.Leech(item, newLapseCount)
        }

        // 2. Anki Logic: Again -> Reset to Step 0
        val nextStep = 0
        // Use the first step from config (e.g. 1 min for relearning steps)
        val firstStepMin = stepConfig.firstOrNull() ?: 1
        val dueTime = System.currentTimeMillis() + firstStepMin * 60 * 1000L

        val updatedItem = when (item) {
            is LearningItem.WordItem -> item.copy(step = nextStep, dueTime = dueTime)
            is LearningItem.GrammarItem -> item.copy(step = nextStep, dueTime = dueTime)
        }

        return ScheduleResult.Requeue(
            updatedItem = updatedItem,
            nextStepIndex = nextStep,
            dueTime = dueTime,
            isLapse = true
        )
    }

    /**
     * 处理通过 (评分 >= 2)
     */
    fun schedulePass(
        item: LearningItem,
        quality: Int,
        currentStep: Int,
        stepConfig: List<Int>
    ): ScheduleResult {
        // Hard (2): 保持当前 Step，如果是第一步则取前两步的均值，否则使用当前 Step 的时间
        if (quality == 2) {
            val delayMins = calculateHardDelayMin(currentStep, stepConfig)
            val dueTime = System.currentTimeMillis() + (delayMins * 60 * 1000.0).toLong()

            val updatedItem = when (item) {
                is LearningItem.WordItem -> item.copy(step = currentStep, dueTime = dueTime)
                is LearningItem.GrammarItem -> item.copy(step = currentStep, dueTime = dueTime)
            }

            return ScheduleResult.Requeue(
                updatedItem = updatedItem,
                nextStepIndex = currentStep,
                dueTime = dueTime,
                isLapse = false
            )
        }

        // 如果是 Good (3)，尝试进入下一个 Step
        if (quality == 3) {
            val nextStep = currentStep + 1
            if (nextStep < stepConfig.size) {
                val nextStepMin = stepConfig.getOrElse(nextStep) { 10 }
                val dueTime = System.currentTimeMillis() + nextStepMin * 60 * 1000L

                val updatedItem = when (item) {
                    is LearningItem.WordItem -> item.copy(step = nextStep, dueTime = dueTime)
                    is LearningItem.GrammarItem -> item.copy(step = nextStep, dueTime = dueTime)
                }

                return ScheduleResult.Requeue(
                    updatedItem = updatedItem,
                    nextStepIndex = nextStep,
                    dueTime = dueTime,
                    isLapse = false
                )
            }
        }

        // 毕业 (Graduate):
        // 1. 评分是 Easy (4)
        // 2. 评分是 Good (3) 且已经是最后一个台阶
        return ScheduleResult.Graduate(item, quality)
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
