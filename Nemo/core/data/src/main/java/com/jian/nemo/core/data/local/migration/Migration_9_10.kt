package com.jian.nemo.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移 9 -> 10
 *
 * 核心变更：
 * 1. 创建 word_study_states 表
 * 2. 创建 grammar_study_states 表
 * 3. 从 words 和 grammars 表迁移用户学习状态数据
 * 4. 注意：此时不删除 words/grammars 中的旧字段，以防迁移失败需要回滚，
 *    字段清理将在后续版本(v11)中进行。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 创建 word_study_states 表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `word_study_states` (
                `word_id` INTEGER NOT NULL,
                `repetition_count` INTEGER NOT NULL DEFAULT 0,
                `easiness_factor` REAL NOT NULL DEFAULT 2.5,
                `interval` INTEGER NOT NULL DEFAULT 0,
                `next_review_date` INTEGER NOT NULL DEFAULT 0,
                `last_reviewed_date` INTEGER,
                `first_learned_date` INTEGER,
                `is_favorite` INTEGER NOT NULL DEFAULT 0,
                `is_skipped` INTEGER NOT NULL DEFAULT 0,
                `buried_until_day` INTEGER NOT NULL DEFAULT 0,
                `last_modified_time` INTEGER NOT NULL,
                `is_deleted` INTEGER NOT NULL DEFAULT 0,
                `deleted_time` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`word_id`),
                FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_word_study_states_next_review_date` ON `word_study_states` (`next_review_date`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_word_study_states_last_modified_time` ON `word_study_states` (`last_modified_time`)")

        // 2. 迁移单词进度数据
        // 只有 repetitionCount > 0, isFavorite == 1, isSkipped == 1 或 firstLearnedDate != null 的记录才需要迁移
        database.execSQL("""
            INSERT INTO word_study_states (
                word_id, repetition_count, easiness_factor, interval, next_review_date,
                last_reviewed_date, first_learned_date, is_favorite, is_skipped,
                buried_until_day, last_modified_time, is_deleted, deleted_time
            )
            SELECT
                id, repetitionCount, easinessFactor, interval, nextReviewDate,
                lastReviewedDate, firstLearnedDate, isFavorite, isSkipped,
                buriedUntilDay, lastModifiedTime, isDeleted, deletedTime
            FROM words
            WHERE repetitionCount > 0 OR isFavorite = 1 OR isSkipped = 1 OR firstLearnedDate IS NOT NULL
        """)

        // 3. 创建 grammar_study_states 表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `grammar_study_states` (
                `grammar_id` INTEGER NOT NULL,
                `repetition_count` INTEGER NOT NULL DEFAULT 0,
                `easiness_factor` REAL NOT NULL DEFAULT 2.5,
                `interval` INTEGER NOT NULL DEFAULT 0,
                `next_review_date` INTEGER NOT NULL DEFAULT 0,
                `last_reviewed_date` INTEGER,
                `first_learned_date` INTEGER,
                `is_favorite` INTEGER NOT NULL DEFAULT 0,
                `is_skipped` INTEGER NOT NULL DEFAULT 0,
                `buried_until_day` INTEGER NOT NULL DEFAULT 0,
                `last_modified_time` INTEGER NOT NULL,
                `is_deleted` INTEGER NOT NULL DEFAULT 0,
                `deleted_time` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`grammar_id`),
                FOREIGN KEY(`grammar_id`) REFERENCES `grammars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_study_states_next_review_date` ON `grammar_study_states` (`next_review_date`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_study_states_last_modified_time` ON `grammar_study_states` (`last_modified_time`)")

        // 4. 迁移语法进度数据
        database.execSQL("""
            INSERT INTO grammar_study_states (
                grammar_id, repetition_count, easiness_factor, interval, next_review_date,
                last_reviewed_date, first_learned_date, is_favorite, is_skipped,
                buried_until_day, last_modified_time, is_deleted, deleted_time
            )
            SELECT
                id, repetitionCount, interval, easinessFactor, nextReviewDate,
                lastReviewedDate, firstLearnedDate, isFavorite, isSkipped,
                buriedUntilDay, lastModifiedTime, isDeleted, deletedTime
            FROM grammars
            WHERE repetitionCount > 0 OR isFavorite = 1 OR isSkipped = 1 OR firstLearnedDate IS NOT NULL
        """)
    }
}
