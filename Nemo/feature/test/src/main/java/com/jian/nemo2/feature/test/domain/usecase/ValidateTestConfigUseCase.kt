package com.jian.nemo2.feature.test.domain.usecase

import com.jian.nemo2.feature.test.domain.model.TestConfig
import com.jian.nemo2.feature.test.domain.model.TestContentType
import javax.inject.Inject

/**
 * 测试配置校验 UseCase
 */
class ValidateTestConfigUseCase @Inject constructor(
    private val queryAvailableDataCountUseCase: QueryAvailableDataCountUseCase
) {
    /**
     * 校验测试配置是否能够开始测试
     *
     * @param config 测试配置
     * @return 错误信息，如果校验通过则返回 null
     */
    suspend operator fun invoke(config: TestConfig): String? {
        // 1. 基础校验：等级选择
        if (config.testContentType != TestContentType.GRAMMAR && config.selectedWordLevels.isEmpty()) {
            return "请至少选择一个单词等级"
        }
        if (config.testContentType != TestContentType.WORDS && config.selectedGrammarLevels.isEmpty()) {
            return "请至少选择一个语法等级"
        }

        // 2. 数据量校验
        val (wordCount, grammarCount) = queryAvailableDataCountUseCase(config)

        if (config.testContentType == TestContentType.WORDS) {
            if (wordCount == 0) {
                return "当前范围内没有可用的单词"
            }
        } else if (config.testContentType == TestContentType.GRAMMAR) {
            if (grammarCount == 0) {
                return "当前范围内没有可用的语法"
            }
        } else {
            // 混合模式：总数校验
            if ((wordCount + grammarCount) == 0) {
                return "当前范围内没有可用的题目"
            }
        }

        return null
    }
}
