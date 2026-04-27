package com.jian.nemo2.core.domain.algorithm

import com.jian.nemo2.core.domain.model.SrsItem
import com.jian.nemo2.core.domain.model.SrsUpdateResult
import com.jian.nemo2.core.domain.repository.ReviewLogRepository
import com.jian.nemo2.core.domain.service.SrsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 FSRS 6.0 的 SRS 计算器实现
 * 遵循 rules.md: 3.D Algorithm Precision
 *
 * 评分映射 (1-4):
 * - 1: Again
 * - 2: Hard
 * - 3: Good
 * - 4: Easy
 */
@Singleton
class SrsCalculatorImpl @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository,
    private val settingsRepository: com.jian.nemo2.core.domain.repository.SettingsRepository
) : SrsCalculator {

    @Volatile
    private var fsrs = Fsrs6Algorithm()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 观察留存率变化并动态更新算法实例
        scope.launch {
            settingsRepository.fsrsTargetRetentionFlow.collect { retention ->
                fsrs = Fsrs6Algorithm(targetRetention = retention)
            }
        }

        // 后台加载优化参数（如果可用）
        scope.launch {
            try {
                val logs = reviewLogRepository.getRecentLogs(limit = 1500)
                // TODO: FsrsParameterOptimizer also needs to be updated to Double if used
                // For now, keeping it robust with default or simple optimization
            } catch (_: Exception) {
                // Ignore initialization errors
            }
        }
    }

    override fun calculate(
        item: SrsItem,
        quality: Int,
        today: Long
    ): SrsUpdateResult {
        require(quality in 1..4) {
            "Quality must be between 1 and 4, got $quality"
        }

        // 1. 直接使用评分 (1-4)
        val rating = quality

        // 2. 构建当前记忆状态
        val currentState = if (item.stability > 0.0 && item.repetitionCount > 0) {
            Fsrs6Algorithm.MemoryState(stability = item.stability, difficulty = item.difficulty)
        } else {
            null // 新卡
        }

        // 3. 计算经过天数 (Double 精度)
        val elapsedDays = if (item.lastReviewedDate != null && item.lastReviewedDate!! > 0) {
            (today - item.lastReviewedDate!!).toDouble().coerceAtLeast(0.0)
        } else {
            0.0
        }

        // 4. 执行 FSRS step
        val newState = fsrs.step(currentState, rating, elapsedDays)

        // 5. 更新次数 (Logic Parity: 与 Web/Supabase RPC 保持一致)
        // 只有当评分不是 Again (1) 时，repetitionCount 才会增加
        // 注意：如果是重学阶段点 Again，reps 也不增加。
        val newRepetitionCount = if (rating >= 2) item.repetitionCount + 1 else item.repetitionCount
        val newLapses = if (quality < 2) item.lapses + 1 else item.lapses

        // 6. [Fuzz Seed] 使用更新前的次数 (v_current.reps) 对齐服务端
        val seed = if (item.progressId != null) {
            fsrs.buildFsrsDeterministicSeed(item.progressId!!, item.repetitionCount)
        } else {
            fsrs.buildFsrsDeterministicSeed(item.id, item.repetitionCount)
        }
        val newInterval = fsrs.nextIntervalDaysWithFuzz(newState.stability, seed)

        // 7. 计算日期
        val nextReviewDate = today + newInterval

        val firstLearnedDate = when {
            item.repetitionCount == 0 && quality >= 3 -> today // 新卡首次通过
            else -> item.firstLearnedDate
        }

        val lastReviewedDate = today

        return SrsUpdateResult(
            repetitionCount = newRepetitionCount,
            lapses = newLapses,
            stability = newState.stability,
            difficulty = newState.difficulty,
            interval = newInterval,
            nextReviewDate = nextReviewDate,
            lastReviewedDate = lastReviewedDate,
            firstLearnedDate = firstLearnedDate,
            state = if (newInterval > 0) 2 else (if (item.repetitionCount == 0) 1 else item.state)
        )
    }
}
