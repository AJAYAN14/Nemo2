package com.jian.nemo2.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移: v12 -> v13
 *
 * 变更：
 * - words 表增加 is_delisted（方案一：用字段标记下架，不删 ID）
 * - grammars 表增加 is_delisted
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE words ADD COLUMN is_delisted INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE grammars ADD COLUMN is_delisted INTEGER NOT NULL DEFAULT 0")
    }
}
