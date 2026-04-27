package com.jian.nemo2.feature.learning.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.domain.repository.StudyRecordRepository
import com.jian.nemo2.core.domain.service.SrsCalculator
import com.jian.nemo2.core.domain.usecase.grammar.GetDueGrammarsUseCase
import com.jian.nemo2.core.domain.usecase.grammar.UpdateGrammarUseCase
import com.jian.nemo2.core.domain.repository.StudyRepository
import com.jian.nemo2.core.domain.usecase.word.GetDueWordsUseCase
import com.jian.nemo2.core.domain.usecase.word.UpdateWordUseCase
import com.jian.nemo2.feature.learning.domain.LearningQueueManager
import com.jian.nemo2.feature.learning.domain.QueueSelectionResult
import com.jian.nemo2.feature.learning.domain.SrsIntervalPreview
import com.jian.nemo2.feature.learning.presentation.LearningItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Review status
 */
enum class ReviewStatus {
    Loading,
    Reviewing,
    Waiting,
    SessionCompleted
}

/**
 * Review UI State
 */
data class ReviewUiState(
    val status: ReviewStatus = ReviewStatus.Loading,
    val reviewItems: List<ReviewPreviewItem> = emptyList(),
    val currentIndex: Int = 0,
    val currentItem: ReviewPreviewItem? = null,
    val isAnswerShown: Boolean = false,
    val isProcessing: Boolean = false,
    val totalCompleted: Int = 0,
    val error: String? = null,
    val waitingUntil: Long = 0L,

    // UI Helpers
    val ratingIntervals: Map<Int, String> = emptyMap()
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val getDueWordsUseCase: GetDueWordsUseCase,
    private val getDueGrammarsUseCase: GetDueGrammarsUseCase,
    private val studyRecordRepository: StudyRecordRepository,
    private val learningQueueManager: LearningQueueManager,
    private val studyRepository: StudyRepository,
    private val srsCalculator: SrsCalculator,
    private val settingsRepository: SettingsRepository,
    private val srsIntervalPreview: SrsIntervalPreview,
    private val updateWordUseCase: UpdateWordUseCase,
    private val updateGrammarUseCase: UpdateGrammarUseCase
) : ViewModel() {

    companion object {
        private const val LEECH_ACTION_SKIP = "skip"
        private const val LEECH_ACTION_BURY_TODAY = "bury_today"
    }

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    // ========== Relearning 内部状态 ==========

    /** 重学步进追踪 (ItemId -> StepIndex) */
    private val _relearningSteps = mutableMapOf<Long, Int>()
    /** 重学到期时间 (ItemId -> DueTime Epoch Millis) */
    private val _relearningDueTimes = mutableMapOf<Long, Long>()

    /** 重学步进配置 (分钟列表) */
    private var _relearningStepsConfig: List<Int> = listOf(1, 10)

    /** 会话锁定的学习日 */
    private var _sessionLockedDay: Long? = null
    private var _resetHour: Int = 4
    private var _learnAheadLimitMinutes: Int = 20
    private var _leechThreshold: Int = 8
    private var _leechAction: String = LEECH_ACTION_SKIP

    private val fsrs6Algorithm = com.jian.nemo2.core.domain.algorithm.Fsrs6Algorithm()

    init {
        loadData()
    }

    // ========== 数据加载 ==========

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ReviewStatus.Loading) }

            try {
                // 0. 加载配置
                _resetHour = settingsRepository.learningDayResetHourFlow.first()
                _sessionLockedDay = DateTimeUtils.getLearningDay(_resetHour)
                _learnAheadLimitMinutes = settingsRepository.learnAheadLimitFlow.first().coerceAtLeast(0)
                _leechThreshold = settingsRepository.leechThresholdFlow.first().coerceAtLeast(1)
                _leechAction = settingsRepository.leechActionFlow.first()

                val relearningStepsStr = settingsRepository.relearningStepsFlow.first()
                _relearningStepsConfig = parseSteps(relearningStepsStr)

                // 1. Get Due Words
                val wordLevel = settingsRepository.preferredWordLevelFlow.first()
                val dueWordsResult = getDueWordsUseCase(wordLevel).first { it !is Result.Loading }
                val dueWords = if (dueWordsResult is Result.Success) dueWordsResult.data else emptyList()

                // 2. Get Due Grammars
                val grammarLevel = settingsRepository.preferredGrammarLevelFlow.first()
                val dueGrammarsResult = getDueGrammarsUseCase(grammarLevel).first { it !is Result.Loading }
                val dueGrammars = if (dueGrammarsResult is Result.Success) dueGrammarsResult.data else emptyList()

                // 3. 全局混排
                val combinedList = (dueWords.map { ReviewPreviewItem.WordItem(it) } +
                    dueGrammars.map { ReviewPreviewItem.GrammarItem(it) })
                    .sortedWith(compareBy<ReviewPreviewItem> { it.dueDay }.thenBy { it.itemId })

                if (combinedList.isNotEmpty()) {
                    selectNext(combinedList, 0)
                } else {
                    _uiState.update {
                        it.copy(
                            status = ReviewStatus.SessionCompleted,
                            reviewItems = emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(status = ReviewStatus.Reviewing, error = "加载失败: ${e.message}")
                }
            }
        }
    }

    // ========== UI 交互 ==========

    fun showAnswer() {
        _uiState.update { it.copy(isAnswerShown = true) }
    }

    /**
     * 手动从等待状态恢复
     */
    fun resumeFromWaiting() {
        _uiState.update { it.copy(waitingUntil = 0L) }
        selectNext(_uiState.value.reviewItems, _uiState.value.currentIndex)
    }

    // ========== 核心方法：评分 ==========

    /**
     * 评分 (对齐 Anki Relearning Steps)
     *
     * - quality < 3: Again → Lapse Penalty + 进入/重新进入 Relearning Steps
     * - quality 3-4 (重学中): Hard/Good → Step 流转
     * - quality 3-4 (正常复习): Hard/Good → 直接 SRS 更新
     * - quality 5: Easy → 直接毕业/SRS 更新
     */
    fun rateItem(quality: Int) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            try {
                val currentItem = _uiState.value.currentItem ?: run {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }

                val itemId = currentItem.itemId
                val isRelearning = _relearningSteps.containsKey(itemId)

                // Native mirror: submit to server
                studyRepository.processReview(
                    itemId = itemId,
                    itemType = if (currentItem is ReviewPreviewItem.WordItem) "word" else "grammar",
                    rating = quality,
                    requestId = java.util.UUID.randomUUID().toString()
                )

                val currentStep = _relearningSteps[itemId] ?: 0
                val lapses = when (currentItem) {
                    is ReviewPreviewItem.WordItem -> settingsRepository.wordLapsesFlow.first()[itemId] ?: currentItem.word.lapses
                    is ReviewPreviewItem.GrammarItem -> settingsRepository.grammarLapsesFlow.first()[itemId] ?: currentItem.grammar.lapses
                }

                val state = if (isRelearning) 3 else 2 // Relearning or Review

                val action = fsrs6Algorithm.evaluateRatingAction(
                    state = state,
                    lapses = lapses,
                    currentStep = currentStep,
                    rating = quality,
                    learningSteps = emptyList(), // Not used for Review
                    relearningSteps = _relearningStepsConfig,
                    leechThreshold = _leechThreshold,
                    leechAction = _leechAction
                )

                when (action) {
                    is com.jian.nemo2.core.domain.model.RatingAction.Graduate -> {
                        println("复习毕业 (Graduate): ${currentItem.displayName}")
                        _relearningSteps.remove(itemId)
                        _relearningDueTimes.remove(itemId)
                        
                        val today = _sessionLockedDay ?: DateTimeUtils.getLearningDay(_resetHour)
                        
                        if (currentItem is ReviewPreviewItem.WordItem) {
                            val srsResult = srsCalculator.calculate(currentItem.word, quality, today)
                            val word = currentItem.word.copy(
                                interval = srsResult.interval,
                                repetitionCount = srsResult.repetitionCount,
                                lapses = if (quality == 1) currentItem.word.lapses + 1 else currentItem.word.lapses,
                                stability = srsResult.stability,
                                difficulty = srsResult.difficulty,
                                nextReviewDate = srsResult.nextReviewDate,
                                lastReviewedDate = srsResult.lastReviewedDate,
                                lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                            )
                            updateWordUseCase(word)
                            studyRecordRepository.incrementReviewedWords(1)
                        } else {
                            val grammarItem = currentItem as ReviewPreviewItem.GrammarItem
                            val srsResult = srsCalculator.calculate(grammarItem.grammar, quality, today)
                            val grammar = grammarItem.grammar.copy(
                                interval = srsResult.interval,
                                repetitionCount = srsResult.repetitionCount,
                                lapses = if (quality == 1) grammarItem.grammar.lapses + 1 else grammarItem.grammar.lapses,
                                stability = srsResult.stability,
                                difficulty = srsResult.difficulty,
                                nextReviewDate = srsResult.nextReviewDate,
                                lastReviewedDate = srsResult.lastReviewedDate,
                                lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                            )
                            updateGrammarUseCase(grammar)
                            studyRecordRepository.incrementReviewedGrammars(1)
                        }
                        
                        _uiState.update { it.copy(totalCompleted = it.totalCompleted + 1) }
                        removeCurrentAndMoveNext()
                    }
                    is com.jian.nemo2.core.domain.model.RatingAction.Requeue -> {
                        println("重学 (Requeue): ${currentItem.displayName}, nextStep=${action.nextStep}, delay=${action.delayMins}m")
                        _relearningSteps[itemId] = action.nextStep
                        _relearningDueTimes[itemId] = System.currentTimeMillis() + action.delayMins * 60 * 1000L
                        
                        if (quality == 1) {
                            when (currentItem) {
                                is ReviewPreviewItem.WordItem -> settingsRepository.incrementWordLapse(itemId)
                                is ReviewPreviewItem.GrammarItem -> settingsRepository.incrementGrammarLapse(itemId)
                            }
                        }
                        
                        reQueueToEnd(currentItem)
                    }
                    is com.jian.nemo2.core.domain.model.RatingAction.Leech -> {
                        println("钉子户 (Leech): ${currentItem.displayName}")
                        _relearningSteps.remove(itemId)
                        _relearningDueTimes.remove(itemId)
                        handleLeech(currentItem.toLearningItem())
                    }
                }
            } catch (e: Exception) {
                println("❌ 复习评分异常: ${e.message}")
                _uiState.update {
                    it.copy(isProcessing = false, error = "评分异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 处理钉子户 (Leech)
     *
     * 累计失败达到阈值后，按配置执行 skip 或 bury_today
     */
    private suspend fun handleLeech(learningItem: LearningItem) {
        val today = _sessionLockedDay ?: DateTimeUtils.getLearningDay(_resetHour)
        val action = if (_leechAction == LEECH_ACTION_BURY_TODAY) LEECH_ACTION_BURY_TODAY else LEECH_ACTION_SKIP

        when (learningItem) {
            is LearningItem.WordItem -> {
                val updatedWord = if (action == LEECH_ACTION_BURY_TODAY) {
                    learningItem.word.copy(
                        nextReviewDate = today + 1,
                        lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                    )
                } else {
                    learningItem.word.copy(
                        isSkipped = true,
                        lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                    )
                }
                updateWordUseCase(updatedWord)
            }
            is LearningItem.GrammarItem -> {
                val updatedGrammar = if (action == LEECH_ACTION_BURY_TODAY) {
                    learningItem.grammar.copy(
                        nextReviewDate = today + 1,
                        lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                    )
                } else {
                    learningItem.grammar.copy(
                        isSkipped = true,
                        lastModifiedTime = DateTimeUtils.getCurrentCompensatedMillis()
                    )
                }
                updateGrammarUseCase(updatedGrammar)
            }
        }

        _uiState.update {
            val message = if (action == LEECH_ACTION_BURY_TODAY) {
                "已暂埋钉子户到明天: ${learningItem.displayName}"
            } else {
                "已暂停钉子户: ${learningItem.displayName}"
            }
            it.copy(error = message)
        }

        removeCurrentAndMoveNext()
    }

    // ========== 队列操作 ==========

    /**
     * 重入队到末尾并前进
     *
     * 将当前项移除，追加到列表末尾，然后选择下一项。
     */
    private fun reQueueToEnd(requeuedItem: ReviewPreviewItem) {
        val state = _uiState.value
        val currentIndex = state.currentIndex

        val newList = state.reviewItems.toMutableList()
        if (currentIndex in newList.indices) {
            newList.removeAt(currentIndex)
        }
        // 追加到末尾
        newList.add(requeuedItem)

        selectNext(newList, currentIndex)
    }

    /**
     * 移除当前项并移到下一个 (毕业/直接通过时使用)
     */
    private fun removeCurrentAndMoveNext() {
        val state = _uiState.value
        val currentIndex = state.currentIndex

        val newList = state.reviewItems.toMutableList()
        if (currentIndex in newList.indices) {
            newList.removeAt(currentIndex)
        }

        if (newList.isEmpty()) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    status = ReviewStatus.SessionCompleted,
                    reviewItems = emptyList()
                )
            }
            return
        }

        selectNext(newList, currentIndex)
    }

    /**
     * 选择下一项并更新 UI (严格调度版)
     */
    private fun selectNext(newList: List<ReviewPreviewItem>, preferredIndex: Int) {
        if (newList.isEmpty()) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    status = ReviewStatus.SessionCompleted,
                    reviewItems = emptyList()
                )
            }
            return
        }

        // 调用严格的时间调度管理器
        val selection = learningQueueManager.selectNextItem(
            items = newList,
            getDueTime = { _relearningDueTimes[it.itemId] ?: 0L },
            now = System.currentTimeMillis(),
            learnAheadLimitMs = _learnAheadLimitMinutes * 60 * 1000L,
            preferredIndex = preferredIndex
        )

        when (selection) {
            is QueueSelectionResult.Next -> {
                val nextItem = selection.item
                val nextIndex = selection.index

                _uiState.update {
                    it.copy(
                        status = ReviewStatus.Reviewing,
                        isProcessing = false,
                        reviewItems = newList,
                        currentIndex = nextIndex,
                        currentItem = nextItem,
                        isAnswerShown = false,
                        waitingUntil = 0L,
                        ratingIntervals = calculateIntervalsSync(nextItem)
                    )
                }
            }
            is QueueSelectionResult.Wait -> {
                _uiState.update {
                    it.copy(
                        status = ReviewStatus.Waiting,
                        isProcessing = false,
                        reviewItems = newList,
                        waitingUntil = selection.waitingUntil
                    )
                }
            }
            is QueueSelectionResult.Empty -> {
                _uiState.update {
                    it.copy(
                        status = ReviewStatus.SessionCompleted,
                        isProcessing = false,
                        reviewItems = emptyList()
                    )
                }
            }
        }
    }

    // ========== 间隔预览 ==========

    /**
     * 同步计算间隔预览 (修复原版异步返回空 Map 的 Bug)
     *
     * 复用 SrsIntervalPreview，正确显示重学中卡片的步进时间
     */
    private fun calculateIntervalsSync(item: ReviewPreviewItem): Map<Int, String> {
        val today = _sessionLockedDay ?: DateTimeUtils.getLearningDay(_resetHour)
        val itemId = item.itemId // Now String

        return when (item) {
            is ReviewPreviewItem.WordItem -> srsIntervalPreview.calculate(
                item = item.word,
                itemId = itemId,
                steps = _relearningSteps,
                learningStepsConfig = listOf(1, 10), // 复习模块不使用新词学习步骤
                relearningStepsConfig = _relearningStepsConfig,
                today = today
            )
            is ReviewPreviewItem.GrammarItem -> srsIntervalPreview.calculate(
                item = item.grammar,
                itemId = itemId,
                steps = _relearningSteps,
                learningStepsConfig = listOf(1, 10),
                relearningStepsConfig = _relearningStepsConfig,
                today = today
            )
        }
    }

    // ========== 工具方法 ==========

    private fun parseSteps(stepsStr: String): List<Int> {
        return stepsStr.split(" ", ",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(1, 10) }
    }
}

// ========== ReviewPreviewItem 辅助扩展 ==========

/** 获取项目 ID */
val ReviewPreviewItem.itemId: Long
    get() = when (this) {
        is ReviewPreviewItem.WordItem -> word.id
        is ReviewPreviewItem.GrammarItem -> grammar.id
    }

/** 获取显示名称 */
val ReviewPreviewItem.displayName: String
    get() = when (this) {
        is ReviewPreviewItem.WordItem -> word.japanese
        is ReviewPreviewItem.GrammarItem -> grammar.grammar
    }

/** 转换为 LearningItem (供 LearningScheduler 使用) */
fun ReviewPreviewItem.toLearningItem(step: Int = 0, dueTime: Long = 0L): LearningItem {
    return when (this) {
        is ReviewPreviewItem.WordItem -> LearningItem.WordItem(word, step, dueTime)
        is ReviewPreviewItem.GrammarItem -> LearningItem.GrammarItem(grammar, step, dueTime)
    }
}

fun LearningItem.toReviewPreviewItem(): ReviewPreviewItem {
    return when (this) {
        is LearningItem.WordItem -> ReviewPreviewItem.WordItem(word)
        is LearningItem.GrammarItem -> ReviewPreviewItem.GrammarItem(grammar)
    }
}

/** 获取到期学习日（用于全局混排） */
val ReviewPreviewItem.dueDay: Long
    get() = when (this) {
        is ReviewPreviewItem.WordItem -> word.nextReviewDate
        is ReviewPreviewItem.GrammarItem -> grammar.nextReviewDate
    }
