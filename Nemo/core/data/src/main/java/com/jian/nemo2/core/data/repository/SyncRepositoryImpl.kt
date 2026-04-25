package com.jian.nemo2.core.data.repository

import android.util.Log
import com.jian.nemo2.core.data.local.NemoDatabase
import com.jian.nemo2.core.domain.repository.SyncRepository
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.data.manager.SupabaseSyncManager
import com.jian.nemo2.core.domain.model.SyncProgress
import com.jian.nemo2.core.domain.model.sync.SyncMode
import com.jian.nemo2.core.domain.model.sync.SyncResult
import com.jian.nemo2.core.domain.model.sync.SyncErrorType
import com.jian.nemo2.core.common.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncManager: SupabaseSyncManager,
    private val database: NemoDatabase,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val externalScope: CoroutineScope
) : SyncRepository {

    private val _globalSyncProgress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    override val globalSyncProgress = _globalSyncProgress.asStateFlow()

    override fun startBackgroundSync(userId: String, force: Boolean, mode: SyncMode) {
        externalScope.launch {
            performSync(userId, force, mode).collect { progress ->
                _globalSyncProgress.value = progress
            }
        }
    }

    override suspend fun performSync(
        userId: String,
        force: Boolean,
        mode: SyncMode
    ): Flow<SyncProgress> = syncManager.performSync(userId, force, mode)

    override suspend fun performRestore(userId: String): Flow<SyncProgress> {
        // [MOD] Restore is now integrated into performSync or not needed in Native Mirror
        return performSync(userId, force = true, mode = SyncMode.PULL_ONLY)
    }

    override suspend fun checkAndRestoreCloudData(
        userId: String,
        force: Boolean,
        mode: SyncMode
    ): SyncResult {
        Log.d("SyncRepository", "请求检查并恢复云端数据: User $userId, force=$force, mode=$mode")
        return try {
            val progress = performSync(userId, force, mode).lastOrNull() ?: SyncProgress.Idle

            when (progress) {
                is SyncProgress.Completed -> SyncResult(
                    success = true,
                    message = "同步成功",
                    syncReport = progress.report
                )
                is SyncProgress.Failed -> SyncResult(
                    success = false,
                    message = progress.error,
                    errorType = SyncErrorType.UNKNOWN
                )
                else -> SyncResult(
                    success = true,
                    message = "同步完成"
                )
            }
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "解析同步结果失败: ${e.message}",
                errorType = SyncErrorType.UNKNOWN
            )
        }
    }

    override suspend fun deleteAllCloudData(userId: String): Boolean {
        // [MOD] This is a sensitive operation, need explicit implementation if still required
        Log.w("SyncRepository", "deleteAllCloudData is currently disabled in refactored sync")
        return false
    }

    override suspend fun hasUnsyncedChanges(sinceTimestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            // In Native Mirror, we mainly check if there are pending items in sync outbox
            val pendingChanges = database.syncOutboxDao().getPendingCount()
            pendingChanges > 0
        }
    }
}
