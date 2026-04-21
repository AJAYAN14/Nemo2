package com.jian.nemo.core.domain.algorithm

import com.jian.nemo.core.domain.model.ReviewLog
import kotlin.math.max

/**
 * 轻量个性化参数微调器 (Double 精度版)。
 * 遵循 rules.md: 3.D Algorithm Precision
 */
object FsrsParameterOptimizer {

    private const val MIN_LOGS_FOR_TUNING = 400

    data class OptimizationResult(
        val parameters: DoubleArray,
        val sampleSize: Int,
        val againRate: Double,
        val hardRate: Double
    )

    fun optimize(
        logs: List<ReviewLog>,
        base: DoubleArray = Fsrs6Algorithm.DEFAULT_PARAMETERS
    ): OptimizationResult? {
        if (logs.size < MIN_LOGS_FOR_TUNING) return null

        val tuned = base.clone()
        val total = logs.size.toDouble()
        val againRate = logs.count { it.rating <= 2 }.toDouble() / total
        val hardRate = logs.count { it.rating == 3 }.toDouble() / total

        // 1) 忘记率偏高：收紧间隔增长；忘记率偏低：适度放宽。
        val againDrift = againRate - 0.25
        tuned[11] = tuned[11] * clamp(1.0 + againDrift * 0.50, 0.92, 1.08) // failure base
        tuned[8] = tuned[8] * clamp(1.0 - againDrift * 0.35, 0.92, 1.08)   // success growth exp
        tuned[16] = tuned[16] * clamp(1.0 - againDrift * 0.25, 0.94, 1.06)  // easy bonus

        // 2) Hard 比例偏高：略微增加 hard penalty（更保守）；反之放宽。
        val hardDrift = hardRate - 0.20
        tuned[15] = tuned[15] * clamp(1.0 - hardDrift * 0.40, 0.90, 1.10)

        // 3) 稳定性保护，避免参数异常导致极端结果。
        tuned[11] = max(0.5, tuned[11])
        tuned[16] = max(1.1, tuned[16])

        return OptimizationResult(
            parameters = tuned,
            sampleSize = logs.size,
            againRate = againRate,
            hardRate = hardRate
        )
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return value.coerceIn(min, max)
    }
}
