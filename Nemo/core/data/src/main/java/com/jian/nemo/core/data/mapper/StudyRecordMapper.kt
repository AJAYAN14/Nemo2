package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.local.entity.StudyRecordEntity
import com.jian.nemo.core.domain.model.StudyRecord

/**
 * StudyRecordEntity ↔ StudyRecord 映射器
 */
object StudyRecordMapper {

    fun StudyRecordEntity.toDomainModel(): StudyRecord {
        return StudyRecord(
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

    fun List<StudyRecordEntity>.toDomainModels(): List<StudyRecord> {
        return map { it.toDomainModel() }
    }
}
