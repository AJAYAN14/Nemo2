package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.GrammarExampleEntity
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import com.jian.nemo.core.data.local.entity.relations.GrammarWithUsages
import com.jian.nemo.core.data.local.entity.relations.UsageWithExamples
import com.jian.nemo.core.domain.model.Grammar
import com.jian.nemo.core.domain.model.GrammarExample
import com.jian.nemo.core.domain.model.GrammarUsage

/**
 * GrammarEntity ↔ Grammar 映射器
 */
object GrammarMapper {

    /**
     * 关联实体转领域模型
     * GrammarWithUsages -> Grammar
     */
    fun GrammarWithUsages.toDomainModel(): Grammar {
        return Grammar(
            id = grammar.id,
            grammar = grammar.grammar,
            grammarLevel = grammar.grammarLevel,
            isDelisted = grammar.isDelisted,
            usages = usages.sortedBy { it.usage.usageOrder }.map { it.toDomainModel() },
            // 进度信息从关联的 state 获取
            repetitionCount = state?.reps ?: 0,
            interval = state?.scheduledDays ?: 0,
            stability = state?.stability ?: 0.0,
            difficulty = state?.difficulty ?: 0.0,
            nextReviewDate = state?.nextReview?.let { DateTimeUtils.isoToEpochDay(it) } ?: 0L,
            lastReviewedDate = state?.lastReview?.let { DateTimeUtils.isoToEpochDay(it) },
            firstLearnedDate = state?.createdAt?.let { DateTimeUtils.isoToEpochDay(it) },
            isFavorite = state?.isFavorite ?: false,
            isSkipped = state?.state == -1,
            buriedUntilDay = state?.buriedUntil?.let { DateTimeUtils.isoToEpochDay(it) } ?: 0L,
            lastModifiedTime = state?.updatedAt?.let { DateTimeUtils.isoToMillis(it) } ?: 0L
        )
    }

    /**
     * 用法关联实体转领域模型
     * UsageWithExamples -> GrammarUsage
     */
    fun UsageWithExamples.toDomainModel(): GrammarUsage {
        return GrammarUsage(
            subtype = usage.subtype,
            connection = usage.connection,
            explanation = usage.explanation,
            notes = usage.notes,
            examples = examples.sortedBy { it.exampleOrder }.map { it.toDomainModel() }
        )
    }

    /**
     * 例句实体转领域模型
     * GrammarExampleEntity -> GrammarExample
     */
    fun GrammarExampleEntity.toDomainModel(): GrammarExample {
        return GrammarExample(
            sentence = sentence,
            translation = translation,
            source = source,
            isDialog = isDialog
        )
    }

    /**
     * 领域模型转实体（仅主表）
     * Grammar -> GrammarEntity
     *
     * 注意：这个方法只转换主表数据（用于更新SRS状态等）
     * 不包含 usages 和 examples，因为这些需要单独插入
     */
    fun Grammar.toEntity(): GrammarEntity {
        return GrammarEntity(
            id = id,
            grammar = grammar,
            grammarLevel = grammarLevel,
            isDelisted = isDelisted
        )
    }

    /**
     * 转换为进度实体
     */
    fun Grammar.toProgressEntity(userId: String): UserProgressEntity {
        return UserProgressEntity(
            id = "${userId}_grammar_${id}",
            userId = userId,
            itemType = "grammar",
            itemId = id,
            reps = repetitionCount,
            stability = stability,
            difficulty = difficulty,
            elapsedDays = 0,
            scheduledDays = interval,
            lapses = 0,
            state = if (isSkipped) -1 else 0,
            learningStep = 0,
            nextReview = DateTimeUtils.epochDayToIso(nextReviewDate),
            lastReview = lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
            isFavorite = isFavorite,
            buriedUntil = if (buriedUntilDay > 0) DateTimeUtils.epochDayToIso(buriedUntilDay) else null,
            updatedAt = DateTimeUtils.millisToIso(lastModifiedTime),
            level = grammarLevel,
            createdAt = firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: DateTimeUtils.millisToIso(lastModifiedTime)
        )
    }

    /**
     * 批量转换扩展函数
     */
    fun List<GrammarWithUsages>.toDomainModels(): List<Grammar> {
        return map { it.toDomainModel() }
    }
}
