package com.jian.nemo.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移: v14 -> v15
 *
 * 变更：
 * - word_study_states 表：移除 easiness_factor，添加 stability + difficulty (FSRS 6)
 * - grammar_study_states 表：移除 easiness_factor，添加 stability + difficulty (FSRS 6)
 *
 * 策略：使用 ALTER TABLE ADD COLUMN 添加新列，旧列保留（SQLite 不支持 DROP COLUMN）
 * 旧 easiness_factor 不再使用，新代码不引用它
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // word_study_states 添加 FSRS 字段
        database.execSQL("ALTER TABLE word_study_states ADD COLUMN stability REAL NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE word_study_states ADD COLUMN difficulty REAL NOT NULL DEFAULT 0")

        // grammar_study_states 添加 FSRS 字段
        database.execSQL("ALTER TABLE grammar_study_states ADD COLUMN stability REAL NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE grammar_study_states ADD COLUMN difficulty REAL NOT NULL DEFAULT 0")
    }
}
