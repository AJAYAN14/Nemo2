package com.jian.nemo.core.domain.algorithm

import com.jian.nemo.core.domain.model.RatingAction
import com.jian.nemo.core.domain.model.UserProgress
import kotlin.math.*

/**
 * FSRS 6.0 算法实现 (1:1 镜像自 Web 端 fsrs.ts)
 * 遵循 rules.md: 3.D Algorithm Precision
 */
class Fsrs6Algorithm(
    private var w: DoubleArray = DEFAULT_PARAMETERS,
    private var targetRetention: Double = 0.9
) {

    companion object {
        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542
        )

        const val S_MIN = 0.01
        const val S_MAX = 36500.0
        const val D_MIN = 1.0
        const val D_MAX = 10.0
        const val MAX_INTERVAL = 36500

        private val FUZZ_RANGES = listOf(
            FuzzRange(2.5, 7.0, 0.15),
            FuzzRange(7.0, 20.0, 0.1),
            FuzzRange(20.0, Double.POSITIVE_INFINITY, 0.05)
        )

        private const val DAY_MINUTES = 24 * 60
    }

    private data class FuzzRange(val start: Double, val end: Double, val factor: Double)

    data class MemoryState(val stability: Double, val difficulty: Double)

    // --- Core Formulas ---

    private fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        val decay = w[20]
        val factor = 0.9.pow(1.0 / -decay) - 1.0
        return ((elapsedDays / stability) * factor + 1.0).pow(-decay)
    }

    private fun nextInterval(stability: Double, retention: Double = targetRetention): Double {
        val decay = w[20]
        val factor = 0.9.pow(1.0 / -decay) - 1.0
        return (stability / factor) * (retention.pow(1.0 / -decay) - 1.0)
    }

    private fun initStability(rating: Int): Double {
        return max(S_MIN, min(S_MAX, w[rating - 1]))
    }

    private fun initDifficulty(rating: Int): Double {
        val d = w[4] - exp(w[5] * (rating - 1)) + 1.0
        return max(D_MIN, min(D_MAX, d))
    }

    private fun nextDifficulty(difficulty: Double, rating: Int): Double {
        val deltaD = -w[6] * (rating - 3)
        val linearDamped = (deltaD * (10.0 - difficulty)) / 9.0
        val newD = difficulty + linearDamped

        // Mean reversion
        val d0Good = w[4] - exp(w[5] * 3.0) + 1.0
        val reverted = w[7] * (d0Good - newD) + newD

        return max(D_MIN, min(D_MAX, reverted))
    }

    private fun stabilityAfterSuccess(
        stability: Double,
        difficulty: Double,
        retrievability: Double,
        rating: Int
    ): Double {
        val hardPenalty = if (rating == 2) w[15] else 1.0
        val easyBonus = if (rating == 4) w[16] else 1.0

        val newS = stability * (
            exp(w[8]) *
                (11.0 - difficulty) *
                stability.pow(-w[9]) *
                (exp((1.0 - retrievability) * w[10]) - 1.0) *
                hardPenalty *
                easyBonus + 1.0
            )
        return max(S_MIN, min(S_MAX, newS))
    }

    private fun stabilityAfterFailure(
        stability: Double,
        difficulty: Double,
        retrievability: Double
    ): Double {
        val newS = w[11] *
            difficulty.pow(-w[12]) *
            ((stability + 1.0).pow(w[13]) - 1.0) *
            exp((1.0 - retrievability) * w[14])

        val minS = stability / exp(w[17] * w[18])
        return max(S_MIN, min(S_MAX, max(newS, minS)))
    }

    private fun stabilityShortTerm(stability: Double, rating: Int): Double {
        val sinc = exp(w[17] * (rating - 3 + w[18])) * stability.pow(-w[19])
        val clampedSinc = if (rating >= 2) max(sinc, 1.0) else sinc
        return max(S_MIN, min(S_MAX, stability * clampedSinc))
    }

    // --- High Level API ---

    fun step(
        currentState: MemoryState?,
        rating: Int,
        elapsedDays: Double
    ): MemoryState {
        if (currentState == null || currentState.stability == 0.0) {
            return MemoryState(
                stability = initStability(rating),
                difficulty = initDifficulty(rating)
            )
        }

        val s = max(S_MIN, min(S_MAX, currentState.stability))
        val d = max(D_MIN, min(D_MAX, currentState.difficulty))

        val newS: Double
        if (elapsedDays == 0.0) {
            newS = stabilityShortTerm(s, rating)
        } else {
            val r = forgettingCurve(elapsedDays, s)
            newS = if (rating == 1) {
                stabilityAfterFailure(s, d, r)
            } else {
                stabilityAfterSuccess(s, d, r, rating)
            }
        }

        val newD = nextDifficulty(d, rating)

        return MemoryState(
            stability = max(S_MIN, min(S_MAX, newS)),
            difficulty = max(D_MIN, min(D_MAX, newD))
        )
    }

    private fun fuzzDelta(interval: Double): Double {
        if (interval < 2.5) return 0.0

        var delta = 0.0
        for (range in FUZZ_RANGES) {
            val inRange = max(0.0, min(interval, range.end) - range.start)
            delta += range.factor * inRange
        }
        return delta + 1.0
    }

    private fun constrainedFuzzBounds(interval: Double, minimum: Int, maximum: Int): Pair<Int, Int> {
        val minBound = min(minimum.toDouble(), maximum.toDouble())
        val clampedInterval = max(minBound, min(maximum.toDouble(), interval))
        val delta = fuzzDelta(clampedInterval)

        var lower = java.lang.Math.round(clampedInterval - delta).toInt()
        var upper = java.lang.Math.round(clampedInterval + delta).toInt()

        lower = max(minBound.toInt(), min(maximum, lower))
        upper = max(minBound.toInt(), min(maximum, upper))

        if (upper == lower && upper > 2 && upper < maximum) {
            upper = lower + 1
        }

        return Pair(lower, upper)
    }

    /**
     * 实现 JavaScript Math.imul 的等效逻辑 (32位有符号整数乘法)
     */
    private fun imul(a: Int, b: Int): Int {
        return a * b
    }

    private fun fuzzFactorFromSeed(seed: Long): Double {
        // 使用 Long 模拟 JS 中的无符号右移和位运算逻辑
        var t = (seed and 0xFFFFFFFFL) + 0x6D2B79F5L
        
        // 第一次混合
        var tInt = t.toInt()
        tInt = imul(tInt xor (tInt ushr 15), tInt or 1)
        
        // 第二次混合
        tInt = tInt xor (tInt + imul(tInt xor (tInt ushr 7), tInt or 61))
        
        // 映射到 [0, 1)
        val result = (tInt xor (tInt ushr 14)).toLong() and 0xFFFFFFFFL
        return result.toDouble() / 4294967296.0
    }

    /**
     * 确定性 Fuzz 计算 (必须与 Web 端 nextIntervalDaysWithFuzz 结果一致)
     */
    fun nextIntervalDaysWithFuzz(stability: Double, seed: Long): Int {
        val rawInterval = nextInterval(stability)
        val (lower, upper) = constrainedFuzzBounds(rawInterval, 1, MAX_INTERVAL)
        val fuzzFactor = fuzzFactorFromSeed(seed)
        return floor(lower + fuzzFactor * (1 + upper - lower)).toInt()
    }

    /**
     * Build an Anki-style deterministic seed mirroring the Web's implementation.
     */
    fun buildFsrsDeterministicSeed(cardId: String, reps: Int): Long {
        // FNV-1a 32-bit hash of cardId string
        var hash = 2166136261L
        for (i in cardId.indices) {
            hash = hash xor cardId[i].code.toLong()
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        val safeReps = max(0, reps).toLong()
        return (hash + safeReps) and 0xFFFFFFFFL
    }

    /**
     * Determine whether a rating should advance a short-term step or graduate.
     * Ported from Web's srsService.evaluateRatingAction.
     */
    fun evaluateRatingAction(
        state: Int,
        lapses: Int,
        currentStep: Int,
        rating: Int, // 1: Again, 2: Hard, 3: Good, 4: Easy
        learningSteps: List<Int>,
        relearningSteps: List<Int>,
        leechThreshold: Int = 8,
        leechAction: String = "skip"
    ): RatingAction {
        val isRelearning = state == 3
        val isReview = state == 2

        // Leech check: only applies to graduated cards (Review or Relearning).
        if (rating == 1 && (isReview || isRelearning)) {
            val currentLapses = lapses + 1
            val halfThreshold = ceil(leechThreshold.toDouble() / 2.0).toInt()
            val isLeech = currentLapses >= leechThreshold && (currentLapses - leechThreshold) % halfThreshold == 0

            if (isLeech) {
                return RatingAction.Leech(
                    action = leechAction,
                    fallbackDelay = relearningSteps.firstOrNull() ?: 10
                )
            }
        }

        // Hybrid state machine
        if (isReview) {
            return if (rating == 1) {
                RatingAction.Requeue(nextStep = 0, delayMins = relearningSteps.firstOrNull() ?: 10)
            } else {
                RatingAction.Graduate
            }
        }

        val steps = if (isRelearning) relearningSteps else learningSteps
        val safeCurrentStep = max(0, currentStep)

        return when (rating) {
            1 -> { // Again
                RatingAction.Requeue(nextStep = 0, delayMins = steps.firstOrNull() ?: 1)
            }
            2 -> { // Hard
                val currentDelay = steps.getOrNull(safeCurrentStep) ?: steps.firstOrNull() ?: 1
                val delay = if (safeCurrentStep == 0) {
                    hardDelayMinsForFirstStep(currentDelay, steps.getOrNull(1))
                } else {
                    currentDelay
                }
                RatingAction.Requeue(nextStep = safeCurrentStep, delayMins = delay)
            }
            3 -> { // Good
                if (safeCurrentStep < steps.size - 1) {
                    RatingAction.Requeue(nextStep = safeCurrentStep + 1, delayMins = steps[safeCurrentStep + 1])
                } else {
                    RatingAction.Graduate
                }
            }
            else -> { // Easy (4) graduates immediately
                RatingAction.Graduate
            }
        }
    }

    private fun hardDelayMinsForFirstStep(againMins: Int, nextMins: Int?): Int {
        if (nextMins != null && nextMins > 0) {
            return maybeRoundInDaysMinutes((againMins + nextMins) / 2)
        }
        val increased = min(againMins * 1.5, (againMins + DAY_MINUTES).toDouble())
        return maybeRoundInDaysMinutes(increased.toInt())
    }

    private fun maybeRoundInDaysMinutes(delayMins: Int): Int {
        if (delayMins > DAY_MINUTES) {
            return max(DAY_MINUTES, (delayMins.toDouble() / DAY_MINUTES.toDouble()).roundToInt() * DAY_MINUTES)
        }
        return delayMins
    }
}
