package com.jian.nemo.core.data.util

import android.util.Log
import com.jian.nemo.core.data.local.NemoDatabase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库初始化器
 *
 * 用于在应用启动时触发数据库创建，从而触发onCreate回调和数据导入
 *
 * 参考: 实现计划 03-数据导入与预填充.md
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val database: NemoDatabase
) {
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing database...")
            // 触发数据库创建 - 执行一个简单的查询
            // 使用getDueWordsCount是最轻量的查询，只返回count而非所有数据
            val todayIso = com.jian.nemo.core.common.util.DateTimeUtils.epochDayToIso(System.currentTimeMillis() / 86400000)
            database.wordDao().getDueWordsCount(todayIso).first()
            Log.d(TAG, "Database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Database initialization completed", e)
        }
    }

    companion object {
        private const val TAG = "DatabaseInitializer"
    }
}
