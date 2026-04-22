package com.jian.nemo.core.data.manager

import android.util.Log
import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.data.local.dao.*
import com.jian.nemo.core.data.local.entity.*
import com.jian.nemo.core.data.local.NemoDatabase
import com.jian.nemo.core.domain.model.SyncProgress
import com.jian.nemo.core.domain.model.SyncReport
import com.jian.nemo.core.domain.model.SyncStats
import com.jian.nemo.core.domain.repository.SettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.jian.nemo.core.domain.model.sync.SyncMode
import com.jian.nemo.core.data.model.sync.SyncMetadata
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import com.jian.nemo.core.common.di.ApplicationScope
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class SupabaseSyncManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val settingsRepository: SettingsRepository,
    private val database: NemoDatabase,
    private val syncMetadata: SyncMetadata,
    private val contentRepository: com.jian.nemo.core.domain.repository.ContentRepository,
    private val contentUpdateApplier: com.jian.nemo.core.domain.repository.ContentUpdateApplier,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    companion object {
        private const val TAG = "SupabaseSyncManager"
        private const val TABLE_USER_PROGRESS = "user_progress"
    }

    fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentUserOrNull()?.id
    }

    /**
     * 执行同步操作 (Native Mirror 模式)
     */
    suspend fun performSync(
        userId: String,
        force: Boolean = false,
        mode: SyncMode = SyncMode.TWO_WAY
    ): Flow<SyncProgress> = flow {
        emit(SyncProgress.Running("正在准备同步...", 0, 0))
        
        try {
            // 1. 字典内容同步 (全局同步，不依赖登录)
            performDictionarySyncInternal()

            // 3. 时间校验 (RPC)
            try {
                val serverTime = supabaseClient.postgrest.rpc("get_server_time").decodeAs<Long>()
                syncMetadata.updateServerTimeOffset(serverTime)
            } catch (e: Exception) {
                Log.w(TAG, "服务器时间校准失败", e)
            }

            emit(SyncProgress.Running("正在拉取用户进度...", 0, 0))
            
            // 4. 拉取所有进度并更新本地 (Native Mirror 核心逻辑)
            // 严格遵循 rules.md: 3.A/3.B，本地只作为一个视图，服务端是权威
            val lastSyncTime = settingsRepository.getLastSyncTime()
            val remoteProgress = mutableListOf<UserProgressEntity>()
            var offset = 0
            val pageSize = 1000
            
            while (true) {
                val batch = if (!force && lastSyncTime > 0L) {
                    // [Incremental Sync] 仅拉取上次同步后变更的数据
                    val safeTime = lastSyncTime - 60 * 1000L // 1分钟冗余
                    val lastSyncIso = kotlinx.datetime.Instant.fromEpochMilliseconds(safeTime).toString()
                    supabaseClient.postgrest[TABLE_USER_PROGRESS]
                        .select(columns = Columns.ALL) {
                            filter {
                                eq("user_id", userId)
                                gte("updated_at", lastSyncIso)
                            }
                            range(offset.toLong(), (offset + pageSize - 1).toLong())
                        }.decodeList<UserProgressEntity>()
                } else {
                    // [Full Sync] 全量拉取
                    supabaseClient.postgrest[TABLE_USER_PROGRESS]
                        .select(columns = Columns.ALL) {
                            filter { eq("user_id", userId) }
                            range(offset.toLong(), (offset + pageSize - 1).toLong())
                        }.decodeList<UserProgressEntity>()
                }
                
                remoteProgress.addAll(batch)
                Log.d(TAG, "fetchRemoteProgress: fetched ${batch.size} items (total: ${remoteProgress.size})")
                
                if (batch.size < pageSize) break
                offset += pageSize
            }
            
            database.withTransaction {
                // 这里使用 insertAll (OnConflictStrategy.REPLACE) 确保本地状态与服务器一致
                userProgressDao.insertAll(remoteProgress)
                
                // [Native Mirror] 增量拉取不处理软删除逻辑（假定数据主要由本地软清理或服务端极少硬删除）
            }

            settingsRepository.setLastSyncTime(DateTimeUtils.getCurrentCompensatedMillis())
            
            // 5. 自动启动 Realtime 监听 (如果未启动)
            startRealtimeSync(userId)
            
            emit(SyncProgress.Completed(SyncReport(timestamp = System.currentTimeMillis())))
            
        } catch (e: Exception) {
            Log.e(TAG, "同步失败", e)
            emit(SyncProgress.Failed(e.message ?: "Unknown error"))
        }
    }

    /**
     * 开启 Realtime 实时同步 (rules.md: 3.C)
     */
    fun startRealtimeSync(userId: String) {
        if (realtimeChannel != null) return

        Log.d(TAG, "启动 Realtime 同步: $userId")
        
        realtimeChannel = supabaseClient.channel("user_progress_realtime")
        
        val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE_USER_PROGRESS
            filter(FilterOperation("user_id", FilterOperator.EQ, userId))
        }

        scope.launch {
            changeFlow.collect { action ->
                Log.d(TAG, "收到 Realtime 变更: $action")
                handleRealtimeAction(action)
            }
        }

        scope.launch {
            try {
                realtimeChannel!!.subscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Realtime 订阅失败", e)
            }
        }
    }

    /**
     * 停止 Realtime 实时同步
     */
    fun stopRealtimeSync() {
        scope.launch {
            try {
                realtimeChannel?.unsubscribe()
                realtimeChannel = null
                Log.d(TAG, "已停止 Realtime 同步")
            } catch (e: Exception) {
                Log.e(TAG, "停止 Realtime 失败", e)
            }
        }
    }

    private suspend fun handleRealtimeAction(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert -> {
                    val entity = Json.decodeFromJsonElement<UserProgressEntity>(action.record)
                    userProgressDao.insert(entity)
                }
                is PostgresAction.Update -> {
                    val entity = Json.decodeFromJsonElement<UserProgressEntity>(action.record)
                    userProgressDao.insert(entity) // REPLACE 策略
                }
                is PostgresAction.Delete -> {
                    val id = action.oldRecord["id"]?.toString()?.replace("\"", "")
                    if (id != null) {
                        userProgressDao.deleteById(id)
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理 Realtime 变更失败", e)
        }
    }

    /**
     * 公开触发字典同步逻辑 (用于启动屏或预加载)
     */
    suspend fun performDictionarySync() {
        performDictionarySyncInternal()
    }

    private suspend fun performDictionarySyncInternal() {
        try {
            val levels = listOf("N1", "N2", "N3", "N4", "N5")
            val remoteVersion = contentRepository.getRemoteContentVersion()
            val lastVersion = settingsRepository.getLastContentVersion()
            
            // 自我修复逻辑：如果本地数据库为空，强制同步，忽略版本号对比
            val wordCount = database.wordDao().getCount()
            val grammarCount = database.grammarDao().getCount()
            val isDatabaseEmpty = wordCount == 0 || grammarCount == 0
            
            if (!isDatabaseEmpty && remoteVersion != null && remoteVersion <= lastVersion) {
                return
            }

            levels.forEach { level ->
                val remoteWords = contentRepository.fetchRemoteWords(level)
                if (remoteWords.isNotEmpty()) {
                    contentUpdateApplier.applyWords(level, remoteWords)
                }

                val remoteGrammars = contentRepository.fetchRemoteGrammars(level)
                if (remoteGrammars.isNotEmpty()) {
                    contentUpdateApplier.applyGrammars(level, remoteGrammars)
                }

                val remoteQuestions = contentRepository.fetchRemoteGrammarQuestions(level)
                if (remoteQuestions.isNotEmpty()) {
                    contentUpdateApplier.applyGrammarQuestions(level, remoteQuestions)
                }
            }

            if (remoteVersion != null) {
                settingsRepository.setLastContentVersion(remoteVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "词库同步失败", e)
        }
    }
}
