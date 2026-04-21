package com.jian.nemo.core.domain.algorithm

import com.jian.nemo.core.domain.model.Grammar
import com.jian.nemo.core.domain.model.SrsItem
import com.jian.nemo.core.domain.model.SrsUpdateResult
import com.jian.nemo.core.domain.model.Word
import com.jian.nemo.core.domain.repository.ReviewLogRepository
import com.jian.nemo.core.domain.service.SrsCalculator
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
 * 评分映射 (Nemo 0-5 → FSRS 1-4):
 * - quality 0-2 → Again (1)
 * - quality 3   → Hard (2)
 * - quality 4   → Good (3)
 * - quality 5   → Easy (4)
 */
@Singleton
class SrsCalculatorImpl @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository
) : SrsCalculator {

    @Volatile
    private var fsrs = Fsrs6Algorithm()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
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
        require(quality in 0..5) {
            "Quality must be between 0 and 5, got $quality"
        }

        // 1. 映射评分 (0-2 -> 1, 3 -> 2, 4 -> 3, 5 -> 4)
        val rating = mapQualityToRating(quality)

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

        // 5. 计算新间隔 (带 Fuzz)
        // 5. 更新次数和间隔 (Logic Authority: 对齐 Supabase RPC)
        val newRepetitionCount = item.repetitionCount + 1
        val newLapses = if (quality < 3) item.lapses + 1 else item.lapses
        
        val newInterval: Int
        if (quality < 3) {
            newInterval = 1 // 失败后强制复习
        } else {
            // [Fuzz Seed] 使用更新前的次数 (v_current.reps) 对齐服务端
            val seed = buildFuzzSeed(item, item.repetitionCount)
            newInterval = fsrs.nextIntervalDaysWithFuzz(newState.stability, seed)
        }

        // 6. 计算日期
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
            firstLearnedDate = firstLearnedDate
        )
    }

    private fun mapQualityToRating(quality: Int): Int {
        return when {
            quality < 3 -> 1 // Again
            quality == 3 -> 2 // Hard
            quality == 4 -> 3 // Good
            else -> 4 // Easy
        }
    }

    private fun buildFuzzSeed(item: SrsItem, repetitions: Int): Long {
        // [Logic Parity] 对齐 Web/Server 的 buildFsrsDeterministicSeed
        val cardId = item.id ?: ""
        val cardSeed = hashStringToUint32(cardId)
        return (cardSeed + repetitions.toLong()) and 0xFFFFFFFFL
    }

    private fun hashStringToUint32(input: String): Long {
        // FNV-1a 32-bit 对齐 Web 版 hashStringToUint32
        var hash = 2166136261L
        for (char in input) {
            hash = hash xor char.code.toLong()
            // 在 Kotlin 中使用 Int 乘法模拟 imul，然后转回 Long 并截断
            hash = (hash.toInt() * 16777619).toLong() and 0xFFFFFFFFL
        }
        return hash
    }
}
