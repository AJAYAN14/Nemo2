package com.jian.nemo2.core.data.manager

import android.util.Log
import com.jian.nemo2.core.data.local.NemoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据完整性管理器 (原 DataExportManager)
 *
 * 负责数据库维护、去重和一致性修复。
 * 导入导出功能已废弃，由 Native Mirror 实时同步替代。
 */
@Singleton
class DataExportManager @Inject constructor(
    private val database: NemoDatabase
) {

    /**
     * 修复数据库中的冗余重复数据
     *
     * 处理逻辑：
     * 1. 扫描 UserProgress, WrongAnswer, TestRecord 等表
     * 2. 识别主键重复或逻辑重复的项
     * 3. 仅保留最新的一条，删除其余项
     */
    suspend fun repairDataDuplicates() = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔍 开始检查并修复数据库重复数据...")

        try {
            // 1. 修复 UserProgress (以 id 为准)
            val allProgress = database.userProgressDao().getAllProgressSync()
            val progressGroups = allProgress.groupBy { it.id }
            progressGroups.entries.forEach { (id, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.updatedAt }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { database.userProgressDao().delete(it) }
                    Log.d(TAG, "🗑️ 已删除重复 UserProgress: id=$id, count=${toDelete.size}")
                }
            }

            // 2. 修复 TestRecord (以 id 为准)
            val allTestRecords = database.testRecordDao().getAllTestRecordsSync()
            val testGroups = allTestRecords.groupBy { it.id }
            testGroups.entries.forEach { (id, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { database.testRecordDao().delete(it) }
                    Log.d(TAG, "🗑️ 已删除重复 TestRecord: id=$id, count=${toDelete.size}")
                }
            }

            // 3. 修复 FavoriteQuestion (以 id 为准)
            val allFavorites = database.favoriteQuestionDao().getAllFavoriteQuestionsSync()
            val favGroups = allFavorites.groupBy { it.id }
            favGroups.entries.forEach { (id, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { database.favoriteQuestionDao().delete(it) }
                    Log.d(TAG, "🗑️ 已删除重复 FavoriteQuestion: id=$id, count=${toDelete.size}")
                }
            }

            // 4. 修复 WrongAnswer (以 id 为准)
            val allWrongAnswers = database.wrongAnswerDao().getAllWrongAnswersSync()
            val wrongGroups = allWrongAnswers.groupBy { it.id }
            wrongGroups.entries.forEach { (id, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { database.wrongAnswerDao().delete(it) }
                    Log.d(TAG, "🗑️ 已删除重复 WrongAnswer: id=$id, count=${toDelete.size}")
                }
            }

             // 5. 修复 GrammarWrongAnswer (以 id 为准)
            val allGrammarWrongAnswers = database.grammarWrongAnswerDao().getAllWrongAnswersSync()
            val grammarWrongGroups = allGrammarWrongAnswers.groupBy { it.id }
            grammarWrongGroups.entries.forEach { (id, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { database.grammarWrongAnswerDao().delete(it) }
                    Log.d(TAG, "🗑️ 已删除重复 GrammarWrongAnswer: id=$id, count=${toDelete.size}")
                }
            }

            Log.i(TAG, "✅ 数据库去重修复完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 修复重复数据失败: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "DataIntegrity"
    }
}
