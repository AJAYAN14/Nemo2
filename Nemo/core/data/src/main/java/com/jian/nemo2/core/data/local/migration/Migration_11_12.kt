package com.jian.nemo2.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移: v11 -> v12
 *
 * 变更：
 * - 为 grammar_wrong_answers 表增加题目快照字段 (question_type, question_text, options_json, explanation)
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 增加题目类型字段，默认为 multiple_choice
        database.execSQL("ALTER TABLE grammar_wrong_answers ADD COLUMN question_type TEXT NOT NULL DEFAULT 'multiple_choice'")

        // 增加题干文本字段，默认为空
        database.execSQL("ALTER TABLE grammar_wrong_answers ADD COLUMN question_text TEXT NOT NULL DEFAULT ''")

        // 增加选项 JSON 字段，默认为空数组
        database.execSQL("ALTER TABLE grammar_wrong_answers ADD COLUMN options_json TEXT NOT NULL DEFAULT '[]'")

        // 增加解析字段，允许为空
        database.execSQL("ALTER TABLE grammar_wrong_answers ADD COLUMN explanation TEXT")
    }
}
