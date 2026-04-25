package com.jian.nemo2.core.domain.service

import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.domain.model.SrsItem
import com.jian.nemo2.core.domain.model.SrsUpdateResult

/**
 * SRS (Spaced Repetition System) 算法计算器接口
 *
 * 当前实现: FSRS 6 (Free Spaced Repetition Scheduler)
 */
interface SrsCalculator {
    /**
     * 计算 SRS 更新结果
     *
     * @param item 当前 SRS 项目状态（Word 或 Grammar）
     * @param quality 回答质量 (1-4)
     *   - 1: 重来 (Again)
     *   - 2: 困难 (Hard)
     *   - 3: 良好 (Good)
     *   - 4: 简单 (Easy)
     * @param today 今天的 Epoch Day
     * @return SRS 更新后的状态（stability, difficulty, interval 等）
     */
    fun calculate(
        item: SrsItem,
        quality: Int,
        today: Long = DateTimeUtils.getCurrentEpochDay()
    ): SrsUpdateResult
}
