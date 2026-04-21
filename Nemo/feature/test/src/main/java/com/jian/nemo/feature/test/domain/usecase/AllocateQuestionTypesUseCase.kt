package com.jian.nemo.feature.test.domain.usecase

import kotlin.random.Random
import javax.inject.Inject

/**
 * 题型题数分配 UseCase（方案 B + E：交错练习优先 + 约束满足）
 *
 * 核心逻辑：
 * 1. 默认比例（交错练习）：单词/混合在合适比例范围内随机或固定 4:3:2:1，语法 100% 选择
 * 2. 池感知：根据各题型池大小调整默认比例（池为 0 的题型移除并归一化）
 * 3. 约束满足：在「总题数、池大小、内容类型支持」约束下，用最大余额法分配整数题数
 * 4. 总题数变化时：保持当前比例，用最大余额法重算
 * 5. 内容类型切换：语法归并到选择；单词/混合恢复上次配置或智能默认
 *
 * 比例随机策略（见 question-type-allocation-design-options.md 第七章）：
 * - 仅当「无当前配置」且单词/混合时，可在 [min,max] 区间内随机；可关闭或传入种子以复现。
 */
class AllocateQuestionTypesUseCase @Inject constructor(
    private val queryPoolSizesUseCase: QueryQuestionTypePoolSizesUseCase
) {
    companion object {
        // 固定默认比例（交错练习）：单词/混合模式，当 useRandomRatio=false 时使用
        private val DEFAULT_RATIO_WORDS_MIXED = mapOf(
            "multiple_choice" to 0.4,
            "typing" to 0.3,
            "card_matching" to 0.2,
            "sorting" to 0.1
        )

        // 比例随机区间 [min, max]（文档 §7.2）
        private val RATIO_BOUNDS = mapOf(
            "multiple_choice" to (0.25 to 0.50),
            "typing" to (0.20 to 0.40),
            "card_matching" to (0.10 to 0.30),
            "sorting" to (0.05 to 0.20)
        )

        // 语法模式：仅选择题
        private val DEFAULT_RATIO_GRAMMAR = mapOf(
            "multiple_choice" to 1.0,
            "typing" to 0.0,
            "card_matching" to 0.0,
            "sorting" to 0.0
        )
    }

    /**
     * 分配题型题数（主入口）
     *
     * @param totalCount 总题数
     * @param contentType 内容类型: "words", "grammar", "mixed"
     * @param source 题目来源
     * @param selectedWordLevels 选中的单词等级
     * @param selectedGrammarLevels 选中的语法等级
     * @param currentCounts 当前各题型题数（用于总题数变化时保持比例，或用户自定义）
     * @param targetRatio 目标比例（可选，默认使用智能默认比例）
     * @param useRandomRatio 单词/混合且无当前配置时，是否在比例区间内随机；false 则用固定 4:3:2:1
     * @param randomSeed 随机种子，非空时可复现同一次分配（单测/调试用）
     */
    suspend operator fun invoke(
        totalCount: Int,
        contentType: String,
        source: String,
        selectedWordLevels: List<String>,
        selectedGrammarLevels: List<String>,
        currentCounts: Map<String, Int>? = null,
        targetRatio: Map<String, Double>? = null,
        useRandomRatio: Boolean = true,
        randomSeed: Long? = null
    ): AllocationResult {
        // 1. 查询各题型池大小
        val poolSizes = queryPoolSizesUseCase(
            contentType = contentType,
            source = source,
            selectedWordLevels = selectedWordLevels,
            selectedGrammarLevels = selectedGrammarLevels
        )

        // 2. 确定目标比例
        val effectiveTargetRatio = targetRatio ?: when {
            currentCounts != null && currentCounts.values.sum() > 0 -> {
                calculateRatioFromCounts(currentCounts)
            }
            else -> {
                calculateSmartDefaultRatio(
                    contentType = contentType,
                    poolSizes = poolSizes,
                    useRandomRatio = useRandomRatio,
                    randomSeed = randomSeed
                )
            }
        }

        // 3. 约束满足：在池大小约束下，用最大余额法分配
        val allocated = allocateWithConstraints(
            totalCount = totalCount,
            targetRatio = effectiveTargetRatio,
            poolSizes = poolSizes,
            contentType = contentType
        )

        // 4. 计算实际可执行的总题数（可能因池不足而小于目标）
        val actualTotal = allocated.values.sum()
        val maxPossibleTotal = poolSizes.values.sum()

        return AllocationResult(
            allocatedCounts = allocated,
            actualTotal = actualTotal,
            maxPossibleTotal = maxPossibleTotal,
            targetTotal = totalCount,
            poolSizes = poolSizes
        )
    }

    /**
     * 从当前题数计算比例
     */
    private fun calculateRatioFromCounts(counts: Map<String, Int>): Map<String, Double> {
        val total = counts.values.sum().toDouble()
        if (total == 0.0) return DEFAULT_RATIO_WORDS_MIXED

        return counts.mapValues { (_, count) -> count / total }
    }

    /**
     * 计算智能默认比例（池感知）
     * 单词/混合且 useRandomRatio=true 时，在 RATIO_BOUNDS 内随机后归一化；否则固定 4:3:2:1。
     * 池为 0 的题型一律置 0，其余归一化。
     */
    private fun calculateSmartDefaultRatio(
        contentType: String,
        poolSizes: Map<String, Int>,
        useRandomRatio: Boolean = true,
        randomSeed: Long? = null
    ): Map<String, Double> {
        if (contentType == "grammar") {
            return DEFAULT_RATIO_GRAMMAR
        }

        // 仅对池>0 的题型生成比例
        val availableTypes = poolSizes.filter { it.value > 0 }.keys
        if (availableTypes.isEmpty()) {
            return DEFAULT_RATIO_WORDS_MIXED.mapValues { 0.0 }
        }

        val rawRatio = if (useRandomRatio) {
            sampleRandomRatioInBounds(availableTypes, randomSeed)
        } else {
            DEFAULT_RATIO_WORDS_MIXED.filter { it.key in availableTypes }
        }

        val sum = rawRatio.values.sum()
        return if (sum > 0) {
            rawRatio.mapValues { it.value / sum }
        } else {
            DEFAULT_RATIO_WORDS_MIXED.mapValues { if (it.key in availableTypes) 1.0 / availableTypes.size else 0.0 }
        }
    }

    /**
     * 在 RATIO_BOUNDS 内对给定题型均匀采样，再归一化到加和为 1
     */
    private fun sampleRandomRatioInBounds(
        types: Set<String>,
        seed: Long?
    ): Map<String, Double> {
        val rng = seed?.let { Random(it) } ?: Random.Default
        val raw = types.associateWith { type ->
            val (lo, hi) = RATIO_BOUNDS[type] ?: (0.1 to 0.4)
            lo + (hi - lo) * rng.nextDouble()
        }
        val sum = raw.values.sum()
        return if (sum > 0) raw.mapValues { it.value / sum } else raw
    }

    /**
     * 约束满足分配（最大余额法 + 池约束）
     *
     * 算法步骤：
     * 1. 按目标比例计算各题型的「理想题数」（浮点数）
     * 2. 先分配整数部分
     * 3. 用最大余额法分配剩余题数（按小数部分排序）
     * 4. 检查池约束：超池的压到池大小，不足的按比例补到其它题型（迭代 1-2 轮）
     * 5. 确保总和 = totalCount（或尽可能接近，受池限制）
     */
    private fun allocateWithConstraints(
        totalCount: Int,
        targetRatio: Map<String, Double>,
        poolSizes: Map<String, Int>,
        contentType: String
    ): Map<String, Int> {
        if (totalCount <= 0) {
            return poolSizes.keys.associateWith { 0 }
        }

        // 1. 计算理想题数（浮点数）
        val idealCounts = targetRatio.mapValues { (_, ratio) ->
            ratio * totalCount
        }

        // 2. 先分配整数部分
        val allocated = idealCounts.mapValues { (_, ideal) ->
            ideal.toInt()
        }.toMutableMap()
        var currentTotal = allocated.values.sum()
        var remainder = totalCount - currentTotal

        // 3. 最大余额法：按小数部分排序，分配剩余题数
        if (remainder > 0) {
            val sortedByDecimal = idealCounts.toList().sortedByDescending { (_, ideal) ->
                ideal - ideal.toInt() // 小数部分
            }

            for ((type, _) in sortedByDecimal) {
                if (remainder <= 0) break
                allocated[type] = (allocated[type] ?: 0) + 1
                remainder--
            }
        } else if (remainder < 0) {
            // 理论上不应该发生，但做保护：从最多的题型减
            val sortedByCount = allocated.toList().sortedByDescending { it.second }
            var toRemove = -remainder
            for ((type, _) in sortedByCount) {
                if (toRemove <= 0) break
                val current = allocated[type] ?: 0
                if (current > 0) {
                    allocated[type] = current - 1
                    toRemove--
                }
            }
        }

        // 4. 应用池约束（迭代 1-2 轮）
        var iteration = 0
        while (iteration < 2) {
            var hasOverflow = false
            var totalOverflow = 0
            val overflowTypes = mutableListOf<String>()

            // 检查超池的题型
            for ((type, count) in allocated) {
                val poolSize = poolSizes[type] ?: 0
                if (count > poolSize) {
                    hasOverflow = true
                    val overflow = count - poolSize
                    totalOverflow += overflow
                    allocated[type] = poolSize
                    overflowTypes.add(type)
                }
            }

            if (!hasOverflow) break

            // 将超出的题数按比例分给其它题型（排除已满池的题型）
            if (totalOverflow > 0) {
                val availableTypes = allocated.keys.filter { type ->
                    val current = allocated[type] ?: 0
                    val pool = poolSizes[type] ?: 0
                    current < pool && type !in overflowTypes
                }

                if (availableTypes.isNotEmpty()) {
                    // 按目标比例分配溢出题数
                    val availableRatio = availableTypes.associateWith { type ->
                        targetRatio[type] ?: 0.0
                    }
                    val ratioSum = availableRatio.values.sum()

                    if (ratioSum > 0) {
                        var distributed = 0
                        for (type in availableTypes) {
                            if (distributed >= totalOverflow) break
                            val ratio = availableRatio[type] ?: 0.0
                            val share = (totalOverflow * ratio / ratioSum).toInt()
                            val current = allocated[type] ?: 0
                            val pool = poolSizes[type] ?: 0
                            val add = share.coerceAtMost(pool - current)
                            allocated[type] = current + add
                            distributed += add
                        }

                        // 如果还有剩余，按顺序补到第一个可用题型
                        if (distributed < totalOverflow) {
                            for (type in availableTypes) {
                                if (distributed >= totalOverflow) break
                                val current = allocated[type] ?: 0
                                val pool = poolSizes[type] ?: 0
                                if (current < pool) {
                                    allocated[type] = current + 1
                                    distributed++
                                }
                            }
                        }
                    }
                }
            }

            iteration++
        }

        // 5. 确保总和不超过 totalCount（受池限制）
        val finalTotal = allocated.values.sum()
        if (finalTotal > totalCount) {
            // 从最多的题型减
            var toRemove = finalTotal - totalCount
            val sortedByCount = allocated.toList().sortedByDescending { it.second }
            for ((type, _) in sortedByCount) {
                if (toRemove <= 0) break
                val current = allocated[type] ?: 0
                if (current > 0) {
                    allocated[type] = current - 1
                    toRemove--
                }
            }
        }

        return allocated
    }

    /**
     * 处理内容类型切换
     *
     * @param fromContentType 原内容类型
     * @param toContentType 目标内容类型
     * @param currentCounts 当前各题型题数
     * @return 切换后的各题型题数
     */
    fun handleContentTypeSwitch(
        fromContentType: String,
        toContentType: String,
        currentCounts: Map<String, Int>
    ): Map<String, Int> {
        return when {
            toContentType == "grammar" -> {
                // 切换到语法：所有非选择题归并到选择题
                val mcCount = currentCounts["multiple_choice"] ?: 0
                val otherCount = (currentCounts["typing"] ?: 0) +
                        (currentCounts["card_matching"] ?: 0) +
                        (currentCounts["sorting"] ?: 0)
                mapOf(
                    "multiple_choice" to (mcCount + otherCount),
                    "typing" to 0,
                    "card_matching" to 0,
                    "sorting" to 0
                )
            }
            fromContentType == "grammar" && toContentType != "grammar" -> {
                // 从语法切到单词/混合：使用默认比例（或恢复上次配置，由调用方决定）
                // 这里返回默认比例对应的题数（需要总题数，但这里只做比例转换）
                // 实际使用时，应该在 ViewModel 层结合总题数调用 invoke
                currentCounts // 暂时返回原值，由 ViewModel 层处理
            }
            else -> {
                // 单词 ↔ 混合：保持原配置
                currentCounts
            }
        }
    }
}

/**
 * 分配结果
 */
data class AllocationResult(
    /** 分配后的各题型题数 */
    val allocatedCounts: Map<String, Int>,
    /** 实际总题数（可能因池不足而小于目标） */
    val actualTotal: Int,
    /** 最大可能总题数（各池之和） */
    val maxPossibleTotal: Int,
    /** 目标总题数 */
    val targetTotal: Int,
    /** 各题型池大小 */
    val poolSizes: Map<String, Int>
) {
    /** 是否因池不足而无法达到目标总题数 */
    val isPoolLimited: Boolean
        get() = actualTotal < targetTotal && actualTotal < maxPossibleTotal

    /** 是否完全无法生成题目（所有池都为 0） */
    val isCompletelyEmpty: Boolean
        get() = maxPossibleTotal == 0
}
