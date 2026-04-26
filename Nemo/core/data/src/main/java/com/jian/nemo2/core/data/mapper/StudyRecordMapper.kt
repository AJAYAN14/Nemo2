package com.jian.nemo2.core.data.mapper

import com.jian.nemo2.core.data.local.entity.StudyRecordEntity
import com.jian.nemo2.core.domain.model.StudyRecord

/**
 * StudyRecordEntity ↔ StudyRecord 映射器
 */
object StudyRecordMapper {

    fun StudyRecordEntity.toDomainModel(): StudyRecord {
        return StudyRecord(
            id = id,
            userId = userId,
            date = date,
            learnedWords = learnedWords,
            learnedGrammars = learnedGrammars,
            reviewedWords = reviewedWords,
            reviewedGrammars = reviewedGrammars,
            skippedWords = skippedWords,
            skippedGrammars = skippedGrammars,
            testCount = testCount,
            timestamp = timestamp
        )
    }

    fun StudyRecord.toEntity(): StudyRecordEntity {
        return StudyRecordEntity(
            id = id,
            userId = userId,
            date = date,
            learnedWords = learnedWords,
            learnedGrammars = learnedGrammars,
            reviewedWords = reviewedWords,
            reviewedGrammars = reviewedGrammars,
            skippedWords = skippedWords,
            skippedGrammars = skippedGrammars,
            testCount = testCount,
            timestamp = timestamp,
            updatedAt = null // Will be set by sync or keep null for local
        )
    }

    fun List<StudyRecordEntity>.toDomainModels(): List<StudyRecord> {
        return map { it.toDomainModel() }
    }
}
