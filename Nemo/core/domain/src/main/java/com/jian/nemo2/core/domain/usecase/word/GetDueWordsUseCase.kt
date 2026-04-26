package com.jian.nemo2.core.domain.usecase.word

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.common.ext.asResult
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.domain.model.Word
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.domain.repository.WordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 获取到期复习单词 Use Case
 *
 * 业务规则:
 * 1. 筛选已学习的单词 (reps > 0, state IN (1, 2, 3))
 * 2. 筛选到期复习的单词 (nextReviewDate <= now)
 * 3. 按下次复习日期排序（最早的优先）
 * 4. 这里的“今日”遵循设置中的重置时间
 */
class GetDueWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * 获取到期复习的单词
     *
     * @return Flow<Result<List<Word>>> 到期复习单词列表,按复习日期排序
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(level: String): Flow<Result<List<Word>>> {
        return settingsRepository.learningDayResetHourFlow.flatMapLatest { resetHour ->
            val today = DateTimeUtils.getLearningDay(resetHour)
            wordRepository.getDueWords(today, level)
                .map { words ->
                    // 业务规则: 按下次复习日期排序（最早的优先）
                    // SQL 中已处理 next_review 和 buried_until
                    words.sortedBy { it.nextReviewDate }
                }
        }.asResult()
    }
}
