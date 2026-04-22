package com.jian.nemo.feature.test.domain.usecase

import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.domain.factory.TestQuestionFactory
import com.jian.nemo.core.domain.model.*
import com.jian.nemo.core.domain.repository.GrammarRepository
import com.jian.nemo.core.domain.repository.GrammarWrongAnswerRepository
import com.jian.nemo.core.domain.repository.SettingsRepository
import com.jian.nemo.core.domain.repository.WordRepository
import com.jian.nemo.core.domain.repository.WrongAnswerRepository
import com.jian.nemo.core.domain.usecase.test.GenerateCardMatchingQuestionsUseCase
import kotlinx.coroutines.flow.first
import com.jian.nemo.core.domain.model.QuestionType
import javax.inject.Inject
import kotlin.math.max

/**
 * 自适应综合测试生成器 (Adaptive Practice Engine)
 *
 * 基于 Mastery-Based (熟练度) + Flow (心流) + Error-Driven (错题驱动) 的 4层决策架构。
 *
 * Logic Phases:
 * 1. Item Selection: 遗忘风险 (DueScore) + 错题 + 新词
 * 2. Skill Determination: 识别 -> 映射 -> 结构 -> 产出
 * 3. Question Mapping: 技能 -> 题型 (含降级链)
 * 4. Scheduling: 心流节奏 + 间隔重复
 */
class GenerateAdaptiveTestUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val grammarRepository: GrammarRepository,
    private val wrongAnswerRepository: WrongAnswerRepository,
    private val grammarWrongAnswerRepository: GrammarWrongAnswerRepository,
    private val settingsRepository: SettingsRepository,
    private val generateCardMatchingQuestionsUseCase: GenerateCardMatchingQuestionsUseCase,
    private val testQuestionFactory: TestQuestionFactory
) {

    // ========== Data Structures ==========

    enum class TargetSkill {
        RECOGNITION,       // 识别 (Selection)
        MAPPING,           // 映射 (Card Matching)
        STRUCTURED_RECALL, // 结构化回忆 (Sorting)
        FREE_RECALL        // 主动产出 (Typing)
    }

    data class AdaptiveItem(
        val type: ItemType, // WORD or GRAMMAR
        val word: Word? = null,
        val grammar: Grammar? = null,
        val dueScore: Double,
        var targetSkill: TargetSkill = TargetSkill.RECOGNITION,
        var questionType: QuestionType = QuestionType.MULTIPLE_CHOICE,
        var isRepeat: Boolean = false
    ) {
        val id: Long get() = word?.id ?: grammar?.id ?: 0L
        val level: String get() = word?.level ?: grammar?.grammarLevel ?: "N5"
    }

    enum class ItemType { WORD, GRAMMAR }

    // Helper POJO
    data class DistractorPool(
        val words: List<Word> = emptyList(),
        val grammars: List<Grammar> = emptyList()
    )

    // ========== Core Method ==========

    suspend operator fun invoke(
        count: Int,
        levels: Set<String>,
        mode: TestMode,
        contentType: String = "mixed" // "words", "grammar", "mixed"
    ): List<TestQuestion> {

        // --- Phase 1: Item Selection ---
        val rawItems = selectItems(count, levels, contentType)

        // --- Prep Distractors ---
        // Fetch pool for factory
        val distractorPool = prepareDistractorPool(levels, contentType)

        // --- Phase 2: Skill Determination ---
        val skilledItems = determineSkills(rawItems)

        // --- Phase 3 & 4: Mapping & Scheduling ---
        // Combine mapping and scheduling to handle "Same Item Repeat" upgrades
        val scheduledQuestions = scheduleAndMap(skilledItems, count, mode, distractorPool)

        return scheduledQuestions
    }

    // ========== Phase 1: Item Selection ==========

    private suspend fun selectItems(count: Int, levels: Set<String>, contentType: String): List<AdaptiveItem> {
        val candidates = mutableListOf<AdaptiveItem>()
        val resetHour = settingsRepository.learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)

        // Optimize: Pre-fetch checks
        val levelList = levels.toList()

        // 1. Fetch Candidates (Words)
        if (contentType != "grammar") {
            // A. Wrong Answers (High Priority)
            val wrongIds = wrongAnswerRepository.getAllWrongWordIds()
            if (wrongIds.isNotEmpty()) {
                val wrongWords = wordRepository.getWordsByIds(wrongIds).filter { it.level in levels }
                candidates.addAll(wrongWords.map { createItem(it, today, isWrong = true) })
            }

            // B. Learned Words (Calculate Due Score)
            // Strategy: Pull learned words from target levels using optimized repository queries.
            levels.forEach { lvl ->
                val learnedPool = wordRepository.getAllLearnedWordsByLevel(lvl).first()
                candidates.addAll(learnedPool.map { createItem(it, today, isWrong = false) })
            }
        }

        // 2. Fetch Candidates (Grammar)
        if (contentType != "words") {
            val wrongIds = grammarWrongAnswerRepository.getAllWrongGrammarIds()
            if (wrongIds.isNotEmpty()) {
                val wrongGrammars = grammarRepository.getGrammarsByIds(wrongIds).filter { it.grammarLevel in levels }
                candidates.addAll(wrongGrammars.map { createItem(it, today, isWrong = true) })
            }

            levels.forEach { lvl ->
                val learnedPool = grammarRepository.getAllLearnedGrammarsByLevel(lvl).first()
                candidates.addAll(learnedPool.map { createItem(it, today, isWrong = false) })
            }
        }

        // If candidates are fewer than count, we might need new words/grammars?
        // Current logic focuses on "Review/Adaptive".
        // If low count, maybe pull random non-learned?
        // For strict adherence to provided code, we stick to Review logic but let's add a fallback if empty
        if (candidates.size < count) {
             // Fallback: Random words/grammars from level
             if (contentType != "grammar") {
                 levelList.forEach { lvl ->
                      val pool = wordRepository.getAllWordsByLevel(lvl).first()
                      candidates.addAll(pool.shuffled().take((count - candidates.size) + 1).map { createItem(it, today, false) })
                 }
             }
             if (contentType != "words") {
                 levelList.forEach { lvl ->
                      val pool = grammarRepository.getGrammarsByLevels(listOf(lvl)).first()
                      candidates.addAll(pool.shuffled().take((count - candidates.size) + 1).map { createItem(it, today, false) })
                 }
             }
        }

        // 3. Sort & Pick
        // Avoid dupes if wrong AND learned (shouldn't happen with IDs but safe check)
        val uniqueCandidates = candidates.distinctBy { it.type to it.id }

        val sortedCandidates = uniqueCandidates.sortedByDescending { it.dueScore }

        // Take top N * 1.5 to have some buffer for repeat logic
        return sortedCandidates.take((count * 1.5).toInt())
    }

    private suspend fun prepareDistractorPool(levels: Set<String>, contentType: String): DistractorPool {
        val levelList = levels.toList()
        val words = if (contentType != "grammar") {
            levelList.flatMap { wordRepository.getAllWordsByLevel(it).first() }
        } else emptyList()

        val grammars = if (contentType != "words") {
            grammarRepository.getGrammarsByLevels(levelList).first()
        } else emptyList()

        return DistractorPool(words, grammars)
    }

    private fun createItem(word: Word, today: Long, isWrong: Boolean): AdaptiveItem {
        val lastReviewed = word.lastReviewedDate
        val daysSinceReview = if (lastReviewed != null) max(1L, today - lastReviewed) else 10L // Default for old/unknown
        val levelValue = mapLevelToValue(word.level) // N5=1, N1=5

        // Formula: DueScore = Days * (1 + Level * 0.5)
        // Harder words (Level 5) decay faster / need sooner review
        var score = daysSinceReview * (1 + levelValue * 0.5)

        // Bonus for Wrong Answers
        if (isWrong) score += 1000.0

        return AdaptiveItem(ItemType.WORD, word = word, dueScore = score)
    }

    private fun createItem(grammar: Grammar, today: Long, isWrong: Boolean): AdaptiveItem {
        val daysSinceReview = 10L // Grammar doesn't track lastReviewedDate in same way? Assume 10.
        val levelValue = mapLevelToValue(grammar.grammarLevel)
        var score = daysSinceReview * (1 + levelValue * 0.5)
        if (isWrong) score += 1000.0
        return AdaptiveItem(ItemType.GRAMMAR, grammar = grammar, dueScore = score)
    }

    private fun mapLevelToValue(level: String): Int {
        return when (level.uppercase()) {
            "N5" -> 1
            "N4" -> 2
            "N3" -> 3
            "N2" -> 4
            "N1" -> 5
            else -> 3
        }
    }

    // ========== Phase 2: Skill Determination ==========

    private fun determineSkills(items: List<AdaptiveItem>): List<AdaptiveItem> {
        return items.map { item ->
            // Logic:
            // Level 0-1 (New/Easy) -> Recognition or Mapping
            // Level 2-3 (Familiar) -> Structured Recall or Mapping
            // Level 4-5 (Mastered) -> Free Recall or Structured Recall

            val proficiency = if (item.type == ItemType.WORD) item.word?.repetitionCount ?: 0 else 0

            val skill = when {
                proficiency <= 1 -> TargetSkill.RECOGNITION
                proficiency <= 3 -> if (item.type == ItemType.WORD) TargetSkill.MAPPING else TargetSkill.STRUCTURED_RECALL
                proficiency <= 6 -> TargetSkill.STRUCTURED_RECALL
                else -> TargetSkill.FREE_RECALL
            }

            // Adjust based on ItemType capability
            val adjustedSkill = if (item.type == ItemType.GRAMMAR) {
                // Grammar doesn't support Free Recall (Typing) or Mapping (Card) easily
                // AND Grammar Sorting (STRUCTURED_RECALL) is disabled by user request.
                // So all Grammar falls back to RECOGNITION.
                TargetSkill.RECOGNITION
            } else skill

            item.apply { targetSkill = adjustedSkill }
        }
    }

    // ========== Phase 3 & 4: Mapping & Scheduling ==========

    private suspend fun scheduleAndMap(
        candidates: List<AdaptiveItem>,
        targetCount: Int,
        mode: TestMode,
        distractorPool: DistractorPool
    ): List<TestQuestion> {
        val result = mutableListOf<TestQuestion>()
        val finalItems = candidates.take(targetCount).toMutableList() // Preliminary selection

        // 1. Flow Sorting (Warmup -> Challenge -> Sprint)
        // Sort by Skill Difficulty roughly: Recog(0) < Map(1) < Struct(2) < Free(3)
        // Warmup (20%): Easy
        // Challenge (50%): Hard
        // Sprint (30%): Easy/Medium

        val easyItems = finalItems.filter { it.targetSkill == TargetSkill.RECOGNITION || it.targetSkill == TargetSkill.MAPPING }
        val hardItems = finalItems.filter { it.targetSkill == TargetSkill.STRUCTURED_RECALL || it.targetSkill == TargetSkill.FREE_RECALL }

        val warmupCount = (targetCount * 0.2).toInt()
        val sprintCount = (targetCount * 0.3).toInt()
        val challengeCount = targetCount - warmupCount - sprintCount

        // Distribute items buckets
        val queue = mutableListOf<AdaptiveItem>()

        // Fill Warmup
        queue.addAll(easyItems.take(warmupCount))
        // Fill Challenge
        val remainingEasy = easyItems.drop(warmupCount)
        queue.addAll(hardItems.take(challengeCount))
        // Fill Sprint
        queue.addAll(hardItems.drop(challengeCount))
        queue.addAll(remainingEasy)

        // Truncate to exact count (simple bucket filling might over/under-flow)
        val sortedQueue = queue.take(targetCount)

        // 2. Generate Real Questions

        // Batch Card Matching?
        // If we have MAPPING skill items, grouping them for Card Matching (requires 5 items)
        val (mappingItems, otherItems) = sortedQueue.partition { it.targetSkill == TargetSkill.MAPPING && it.type == ItemType.WORD }

        // Generate Card Matching Questions
        if (mappingItems.isNotEmpty()) {
            val words = mappingItems.mapNotNull { it.word }
            // Must be multiples of 5 effectively or just feed all
            // The UseCase handles chunking or creating items
            val cmQuestions = generateCardMatchingQuestionsUseCase(words, shuffle = false)
            result.addAll(cmQuestions)
        }

        // Generate Others
        otherItems.forEach { item ->
            val q = mapToQuestion(item, mode, distractorPool)
            if (q != null) result.add(q)
        }

        // 3. Final Shuffle or Keep Flow?
        // Design says: Flow-Based. So keep order roughly.
        // CardMatching usually dumps questions.
        // We'll trust the order we built (Warmup -> Challenge -> Sprint)
        // But Card Matching was batch generated. We'll append it to the front as Warmup if Mapping is considered easy.
        // The queue distribution logic above sorted them. mappingItems were extracted from that queue.
        // We can just add result. But wait, `result` has CM questions added first.
        // Then `otherItems` are added.
        // `otherItems` preserves the Warmup/Challenge/Sprint order (minus the mapping items).
        // Card Matching is typically Easy/Warmup. So adding first is fine.

        return result
    }

    private fun mapToQuestion(item: AdaptiveItem, mode: TestMode, distractorPool: DistractorPool): TestQuestion? {
        val questionType = when (item.targetSkill) {
            TargetSkill.RECOGNITION -> QuestionType.MULTIPLE_CHOICE
            TargetSkill.MAPPING -> QuestionType.CARD_MATCHING // Handled separately
            TargetSkill.STRUCTURED_RECALL -> QuestionType.SORTING
            TargetSkill.FREE_RECALL -> QuestionType.TYPING
        }

        // Fallback Logic
        var finalType = questionType
        if (finalType == QuestionType.TYPING && item.type == ItemType.GRAMMAR) {
            finalType = QuestionType.SORTING // Typo fallback
        }

        return when (finalType) {
            QuestionType.MULTIPLE_CHOICE -> {
                if (item.type == ItemType.WORD) {
                    testQuestionFactory.createMultipleChoice(item.word!!, mode, distractorPool.words)
                } else {
                    testQuestionFactory.createGrammarMultipleChoice(item.grammar!!, mode, distractorPool.grammars)
                }
            }
            QuestionType.TYPING -> {
                if (item.type == ItemType.WORD) {
                    testQuestionFactory.createTyping(item.word!!)
                }
                else null
            }
            QuestionType.SORTING -> {
                 if (item.type == ItemType.WORD) {
                     testQuestionFactory.createSorting(item.word!!)
                 } else {
                     // Grammar Sorting Disabled
                     null
                 }
            }
            else -> null
        }
    }
}
