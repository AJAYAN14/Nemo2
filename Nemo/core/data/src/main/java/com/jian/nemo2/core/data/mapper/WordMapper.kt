package com.jian.nemo2.core.data.mapper

import com.jian.nemo2.core.data.local.entity.WordEntity
import com.jian.nemo2.core.data.local.entity.UserProgressEntity
import com.jian.nemo2.core.domain.model.Word
import com.jian.nemo2.core.common.util.DateTimeUtils

/**
 * WordEntity ↔ Word 映射器
 */
object WordMapper {

    /**
     * 将 WordEntity 和 UserProgressEntity 组合转换为 Domain Model
     */
    fun toDomainModel(entity: WordEntity, state: UserProgressEntity?): Word {
        return Word(
            id = entity.id,
            progressId = state?.id,
            japanese = entity.japanese,
            hiragana = entity.hiragana,
            chinese = entity.chinese,
            level = entity.level,
            pos = entity.pos,
            example1 = entity.example1,
            gloss1 = entity.gloss1,
            example2 = entity.example2,
            gloss2 = entity.gloss2,
            example3 = entity.example3,
            gloss3 = entity.gloss3,
            isDelisted = entity.isDelisted,
            // 进度信息从 UserProgress 获取
            repetitionCount = state?.reps ?: 0,
            stability = state?.stability ?: 0.0,
            difficulty = state?.difficulty ?: 0.0,
            interval = state?.scheduledDays ?: 0,
            nextReviewDate = state?.nextReview?.let { DateTimeUtils.isoToEpochDay(it) } ?: 0L,
            lastReviewedDate = state?.lastReview?.let { DateTimeUtils.isoToEpochDay(it) },
            firstLearnedDate = state?.createdAt?.let { DateTimeUtils.isoToEpochDay(it) },
            isFavorite = state?.isFavorite ?: false,
            isSkipped = state?.state == -1,
            buriedUntilDay = state?.buriedUntil ?: 0L,
            state = state?.state ?: 0,
            lastModifiedTime = state?.updatedAt?.let { DateTimeUtils.isoToMillis(it) } ?: 0L
        )
    }

    /**
     * 兼容旧版调用 (仅从 Entity 转换)
     */
    fun WordEntity.toDomainModel(): Word = toDomainModel(this, null)

    /**
     * Domain Model转Entity
     */
    fun Word.toEntity(): WordEntity {
        return WordEntity(
            id = id,
            japanese = japanese,
            hiragana = hiragana,
            chinese = chinese,
            level = level,
            pos = pos,
            example1 = example1,
            gloss1 = gloss1,
            example2 = example2,
            gloss2 = gloss2,
            example3 = example3,
            gloss3 = gloss3,
            isDelisted = isDelisted
        )
    }

    /**
     * 转换为进度实体
     */
    fun Word.toProgressEntity(userId: String): UserProgressEntity {
        return UserProgressEntity(
            id = progressId ?: "${userId}_word_${id}",
            userId = userId,
            itemType = "word",
            itemId = id,
            reps = repetitionCount,
            stability = stability,
            difficulty = difficulty,
            elapsedDays = 0, // Need to handle this
            scheduledDays = interval,
            lapses = lapses,
            state = if (isSkipped) -1 else state,
            learningStep = 0, // Need to handle this if needed
            nextReview = DateTimeUtils.epochDayToIso(nextReviewDate),
            lastReview = lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
            isFavorite = isFavorite,
            buriedUntil = buriedUntilDay,
            updatedAt = DateTimeUtils.millisToIso(lastModifiedTime),
            level = level,
            createdAt = firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: DateTimeUtils.millisToIso(lastModifiedTime)
        )
    }

    /**
     * 批量转换扩展函数
     */
    fun List<WordEntity>.toDomainModels(): List<Word> {
        return map { it.toDomainModel() }
    }
}
