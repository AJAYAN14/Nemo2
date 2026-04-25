package com.jian.nemo2.core.data.manager

import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.data.local.entity.*
import com.jian.nemo2.core.domain.model.*
import kotlin.math.max

/**
 * 智能同步合并器
 * 实现基于时间戳和语义的智能数据合并策略
 */
object SmartSyncMerger {

    sealed class MergeResult<out T> {
        data class LocalKept<T>(val data: T) : MergeResult<T>()
        data class RemoteUpdated<T>(val data: T) : MergeResult<T>()

        fun data(): T = when(this) {
            is LocalKept -> data
            is RemoteUpdated -> data
        }
    }

    /**
     * 合并用户学习进度 (单词或语法)
     * 策略：LWW (最后写入获胜)
     */
    fun mergeUserProgress(
        local: UserProgressEntity,
        remote: WordProgress // 暂时保持 WordProgress/GrammarProgress 作为 DTO 来源
    ): MergeResult<UserProgressEntity> {
        val localTime = DateTimeUtils.isoToMillis(local.updatedAt ?: "2000-01-01T00:00:00Z")
        val remoteTime = remote.lastModifiedTime

        return if (remoteTime > localTime) {
            MergeResult.RemoteUpdated(local.copy(
                reps = remote.srsLevel,
                stability = remote.stability.toDouble(),
                difficulty = remote.difficulty.toDouble(),
                scheduledDays = remote.interval,
                nextReview = DateTimeUtils.epochDayToIso(remote.nextReviewDate),
                createdAt = remote.firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: local.createdAt,
                lastReview = remote.lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
                isFavorite = remote.isFavorite,
                state = if (remote.isSkipped) -1 else if (remote.srsLevel > 0) 2 else 0,
                updatedAt = DateTimeUtils.millisToIso(remoteTime)
            ))
        } else {
            MergeResult.LocalKept(local)
        }
    }

    /**
     * 重载支持 GrammarProgress
     */
    fun mergeUserProgress(
        local: UserProgressEntity,
        remote: GrammarProgress
    ): MergeResult<UserProgressEntity> {
        val localTime = DateTimeUtils.isoToMillis(local.updatedAt ?: "2000-01-01T00:00:00Z")
        val remoteTime = remote.lastModifiedTime

        return if (remoteTime > localTime) {
            MergeResult.RemoteUpdated(local.copy(
                reps = remote.srsLevel,
                stability = remote.stability.toDouble(),
                difficulty = remote.difficulty.toDouble(),
                scheduledDays = remote.interval,
                nextReview = DateTimeUtils.epochDayToIso(remote.nextReviewDate),
                createdAt = remote.firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: local.createdAt,
                lastReview = remote.lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
                isFavorite = remote.isFavorite,
                state = if (remote.srsLevel > 0) 2 else 0,
                updatedAt = DateTimeUtils.millisToIso(remoteTime)
            ))
        } else {
            MergeResult.LocalKept(local)
        }
    }

    /**
     * 合并单词错题本
     * 策略：LWW (基于 timestamp)
     */
    fun mergeWrongAnswer(
        local: WrongAnswerEntity,
        remote: SyncWrongAnswerDto
    ): MergeResult<WrongAnswerEntity> {
        val localTime = local.timestamp
        val remoteTime = remote.timestamp

        return if (remoteTime > localTime) {
            MergeResult.RemoteUpdated(local.copy(
                testMode = remote.testMode,
                userAnswer = remote.userAnswer,
                correctAnswer = remote.correctAnswer,
                consecutiveCorrectCount = remote.consecutiveCorrectCount,
                isDeleted = remote.isDeleted,
                deletedTime = remote.deletedTime,
                timestamp = remoteTime
            ))
        } else {
            MergeResult.LocalKept(local)
        }
    }

    /**
     * 合并语法错题本
     */
    fun mergeGrammarWrongAnswer(
        local: GrammarWrongAnswerEntity,
        remote: SyncGrammarWrongAnswerDto
    ): MergeResult<GrammarWrongAnswerEntity> {
        val localTime = local.timestamp
        val remoteTime = remote.timestamp

        return if (remoteTime > localTime) {
            MergeResult.RemoteUpdated(local.copy(
                testMode = remote.testMode,
                userAnswer = remote.userAnswer,
                correctAnswer = remote.correctAnswer,
                consecutiveCorrectCount = remote.consecutiveCorrectCount,
                isDeleted = remote.isDeleted,
                deletedTime = remote.deletedTime,
                timestamp = remoteTime
            ))
        } else {
           MergeResult.LocalKept(local)
        }
    }

    /**
     * 合并测试记录
     */
    fun mergeTestRecord(
        local: TestRecordEntity,
        remote: SyncTestRecordDto
    ): MergeResult<TestRecordEntity> {
        val localTime = local.timestamp
        val remoteTime = remote.timestamp

        return if (remoteTime > localTime) {
            MergeResult.RemoteUpdated(local.copy(
                date = remote.date,
                totalQuestions = remote.totalQuestions,
                correctAnswers = remote.correctAnswers,
                testMode = remote.testMode,
                isDeleted = remote.isDeleted,
                deletedTime = remote.deletedTime,
                timestamp = remoteTime
            ))
        } else {
            MergeResult.LocalKept(local)
        }
    }

    /**
     * 合并学习记录
     * 策略：累加数据采用 MAX 合并（确保努力不白费），状态字段采用 LWW
     */
    /**
     * 合并学习记录
     * 策略：累加数据采用 MAX 合并（确保努力不白费），状态字段采用 LWW
     */
    fun mergeStudyRecord(
        local: StudyRecordEntity,
        remote: SyncStudyRecordDto
    ): MergeResult<StudyRecordEntity> {
        val localTime = local.timestamp
        val remoteTime = remote.timestamp

        // 学习记录特殊：即使时间戳旧，某些字段也可能需要合并（如 Max 逻辑）
        // 但这里简化处理：如果 Remote 更大且由 Remote 触发，通常视为 Update
        // 不过 SyncManager 的逻辑是：如果 Merge 结果 != local，就更新 DB
        // 我们的重点是 Push 逻辑：如果 local 包含更完整的数据（= MAX 结果就是 Local），那就不需要 Pull

        // 计算合并结果
        val merged = local.copy(
            // 统计数据：取最大值
            learnedWords = max(local.learnedWords, remote.learnedWords),
            learnedGrammars = max(local.learnedGrammars, remote.learnedGrammars),
            reviewedWords = max(local.reviewedWords, remote.reviewedWords),
            reviewedGrammars = max(local.reviewedGrammars, remote.reviewedGrammars),
            skippedWords = max(local.skippedWords, remote.skippedWords),
            skippedGrammars = max(local.skippedGrammars, remote.skippedGrammars),
            testCount = max(local.testCount, remote.testCount),

            // 逻辑删除与时间戳：LWW
            isDeleted = if (remoteTime > localTime) remote.isDeleted else local.isDeleted,
            deletedTime = if (remoteTime > localTime) remote.deletedTime else local.deletedTime,
            timestamp = max(localTime, remoteTime)
        )

        return if (merged != local) {
            // 本地状态发生了变化（要么是 Remote 更大，要么是 Max合并产生了新值）
             MergeResult.RemoteUpdated(merged)
        } else {
             MergeResult.LocalKept(local)
        }
    }
}
