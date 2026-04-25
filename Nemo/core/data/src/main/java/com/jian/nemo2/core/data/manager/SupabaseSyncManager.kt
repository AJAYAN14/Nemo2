package com.jian.nemo2.core.data.manager

import android.util.Log
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.data.local.dao.*
import com.jian.nemo2.core.data.local.entity.*
import com.jian.nemo2.core.data.local.NemoDatabase
import com.jian.nemo2.core.domain.model.SyncProgress
import com.jian.nemo2.core.domain.model.SyncReport
import com.jian.nemo2.core.domain.repository.SettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.jian.nemo2.core.domain.model.sync.SyncMode
import com.jian.nemo2.core.data.model.sync.SyncMetadata
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import com.jian.nemo2.core.common.di.ApplicationScope
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.realtime.RealtimeChannel

@Singleton
class SupabaseSyncManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val settingsRepository: SettingsRepository,
    private val database: NemoDatabase,
    private val syncMetadata: SyncMetadata,
    private val contentRepository: com.jian.nemo2.core.domain.repository.ContentRepository,
    private val contentUpdateApplier: com.jian.nemo2.core.domain.repository.ContentUpdateApplier,
    private val syncOutboxDao: SyncOutboxDao,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var activeRealtimeUserId: String? = null
    private var realtimeJob: kotlinx.coroutines.Job? = null

    private val _dataUpdatedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataUpdatedEvent: Flow<Unit> = _dataUpdatedEvent.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
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
            // 0. [Duolingo-style] 优先处理本地待上传任务 (Push before Pull)
            processOutbox()

            // 1. 字典内容同步 (全局同步，不依赖登录)
            performDictionarySyncInternal()

            // 2. [重要] 清理可能存在的旧版错误 ID 数据 (itemId 为 0 或格式错误的记录)
            // 由于迁移了 ID 类型，旧数据可能会导致关联失败
            if (force) {
                database.withTransaction {
                    userProgressDao.deleteAll()
                }
            }

            // 3. 时间校验 (RPC)
            try {
                val serverTime = supabaseClient.postgrest.rpc("get_server_time").decodeAs<Long>()
                syncMetadata.updateServerTimeOffset(serverTime)
            } catch (e: Exception) {
                Log.w(TAG, "服务器时间校准失败", e)
            }

            emit(SyncProgress.Running("正在拉取用户进度...", 0, 0))

            // 4. [NEW] 应用设置同步 (PULL/PUSH)
            // 保证学习参数(步进,保留率)在多端同步
            try {
                syncSettings(userId)
            } catch (e: Exception) {
                Log.e(TAG, "设置同步失败, 但继续同步进度", e)
            }

            // 5. 拉取所有进度并更新本地 (Native Mirror 核心逻辑)
            // 严格遵循 rules.md: 3.A/3.B，本地只作为一个视图，服务端是权威
            val lastSyncTime = if (force) 0L else settingsRepository.getLastSyncTime()
            val remoteProgress = mutableListOf<UserProgressEntity>()
            var offset = 0
            val pageSize = 1000

            while (true) {
                val batch = if (lastSyncTime > 0L) {
                    // [Incremental Sync] 仅拉取上次同步后变更的数据
                    val safeTime = lastSyncTime - 60 * 1000L // 1分钟冗余
                    val lastSyncIso = kotlinx.datetime.Instant.fromEpochMilliseconds(safeTime).toString()
                    Log.d(TAG, "fetchRemoteProgress: [Incremental] since $lastSyncIso (safeTime=$safeTime)")
                    supabaseClient.postgrest[TABLE_USER_PROGRESS]
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.ALL) {
                            filter {
                                eq("user_id", userId)
                                gte("updated_at", lastSyncIso)
                            }
                            range(offset.toLong(), (offset + pageSize - 1).toLong())
                        }.decodeList<UserProgressEntity>()
                } else {
                    // [Full Sync] 全量拉取
                    supabaseClient.postgrest[TABLE_USER_PROGRESS]
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.ALL) {
                            filter {
                                eq("user_id", userId)
                            }
                            range(offset.toLong(), (offset + pageSize - 1).toLong())
                        }.decodeList<UserProgressEntity>()
                }

                remoteProgress.addAll(batch)
                Log.d(TAG, "fetchRemoteProgress: User=$userId, BatchSize=${batch.size}, TotalSize=${remoteProgress.size}")

                if (batch.size < pageSize) break
                offset += pageSize
            }

            database.withTransaction {
                // 这里使用 insertAll (OnConflictStrategy.REPLACE) 确保本地状态与服务器一致
                if (remoteProgress.isNotEmpty()) {
                    userProgressDao.insertAll(remoteProgress)
                    Log.d(TAG, "fetchRemoteProgress: Inserted/Updated ${remoteProgress.size} items into local DB")
                } else {
                    Log.d(TAG, "fetchRemoteProgress: No new items to insert")
                }
            }

            settingsRepository.setLastSyncTime(DateTimeUtils.getCurrentCompensatedMillis())

            // 6. 自动启动 Realtime 监听 (如果未启动)
            startRealtimeSync(userId)

            emit(SyncProgress.Completed(SyncReport(timestamp = System.currentTimeMillis())))

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "同步失败", e)
            emit(SyncProgress.Failed(e.message ?: "Unknown error"))
        }
    }

    /**
     * 开启 Realtime 实时同步 (rules.md: 3.C)
     */
    fun startRealtimeSync(userId: String) {
        val currentChannel = realtimeChannel
        // 如果已经为当前用户启动过，且状态正常，则跳过
        if (currentChannel != null && activeRealtimeUserId == userId) {
            val status = currentChannel.status.value
            if (status == RealtimeChannel.Status.SUBSCRIBED ||
                status == RealtimeChannel.Status.SUBSCRIBING) {
                Log.d(TAG, "Realtime 同步已为用户 $userId 启动 (Status: $status)，跳过")
                return
            } else {
                Log.w(TAG, "Realtime 通道状态异常 ($status)，准备重新订阅")
            }
        }

        // 如果用户变更或状态异常，先停止并清理旧的
        if (realtimeChannel != null) {
            Log.d(TAG, "重置旧的 Realtime 订阅")
            stopRealtimeSync()
        }

        Log.d(TAG, "启动 Realtime 同步: $userId")
        activeRealtimeUserId = userId

        realtimeChannel = supabaseClient.realtime.channel("user_progress_realtime")

        val progressChangeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE_USER_PROGRESS
            filter(FilterOperation("user_id", FilterOperator.EQ, userId))
        }

        val settingsChangeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "user_settings"
            filter(FilterOperation("user_id", FilterOperator.EQ, userId))
        }

        realtimeJob = scope.launch {
            launch {
                progressChangeFlow.collect { action ->
                    Log.d(TAG, "收到 UserProgress Realtime 变更: $action")
                    handleRealtimeAction(action)
                }
            }
            launch {
                settingsChangeFlow.collect { action ->
                    Log.d(TAG, "收到 UserSettings Realtime 变更: $action")
                    // 设置变更时直接触发设置同步逻辑
                    syncSettings(userId)
                }
            }
        }

        scope.launch {
            try {
                realtimeChannel!!.subscribe()
                Log.i(TAG, "Realtime 订阅请求已发送: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Realtime 订阅异常失败", e)
            }
        }
    }

    /**
     * 停止 Realtime 实时同步
     */
    fun stopRealtimeSync() {
        Log.d(TAG, "停止 Realtime 同步...")
        realtimeJob?.cancel()
        realtimeJob = null

        val channel = realtimeChannel
        realtimeChannel = null
        activeRealtimeUserId = null

        scope.launch {
            try {
                channel?.unsubscribe()
                Log.d(TAG, "已停止 Realtime 同步并注销通道")
            } catch (e: Exception) {
                Log.e(TAG, "停止 Realtime 失败", e)
            }
        }
    }

    /**
     * 处理待同步任务队列 (Duolingo-style Blood Transfusion)
     */
    suspend fun processOutbox() {
        val userId = getCurrentUserId() ?: return
        val pending = syncOutboxDao.getPendingTasks()
        if (pending.isEmpty()) return

        Log.d(TAG, "开始处理离线任务队列, 待同步数量: ${pending.size}")
        val resetHour = settingsRepository.learningDayResetHourFlow.first()

        for (task in pending) {
            try {
                syncOutboxDao.setSyncingStatus(task.id, true)

                val progress = userProgressDao.getByItem(task.itemType, task.itemId)
                if (progress == null) {
                    Log.w(TAG, "任务对应进度不存在, 跳过: ${task.itemId}")
                    syncOutboxDao.deleteById(task.id)
                    continue
                }

                // 计算学习日 (对齐 Web 逻辑)
                val epochDay = DateTimeUtils.getLearningDay(resetHour).toInt()

                when (task.actionType) {
                    "REVIEW" -> {
                        // 自动判断学习/复习字段
                        val studyField = if (progress.reps == 1) {
                            if (task.itemType == "word") "learned_words" else "learned_grammars"
                        } else {
                            if (task.itemType == "word") "reviewed_words" else "reviewed_grammars"
                        }

                        @Serializable
                        data class ReviewParams(
                            val p_user_id: String,
                            val p_progress_id: String,
                            val p_rating: Int,
                            val p_request_id: String,
                            val p_epoch_day: Int,
                            val p_study_field: String,
                            val p_expected_last_review: String?
                        )

                        val params = ReviewParams(
                            p_user_id = userId,
                            p_progress_id = progress.id,
                            p_rating = task.rating,
                            p_request_id = "android-${task.id}-${System.currentTimeMillis()}", 
                            p_epoch_day = epochDay,
                            p_study_field = studyField,
                            p_expected_last_review = task.expectedLastReview
                        )

                        Log.d(TAG, "执行原子评分 RPC: ${task.itemId} (Rating=${task.rating})")
                        val result = supabaseClient.postgrest.rpc("fn_process_review_atomic_v3", params).decodeSingleOrNull<UserProgressEntity>()

                        if (result != null) {
                            userProgressDao.insert(result)
                            Log.i(TAG, "评分上传成功: ${task.itemId}")
                        }
                    }
                    "SUSPEND" -> {
                        supabaseClient.postgrest[TABLE_USER_PROGRESS]
                            .update({ set("state", -1); set("updated_at", task.createdAt) }) {
                                filter { eq("id", progress.id) }
                            }
                        Log.i(TAG, "暂停状态上传成功: ${task.itemId}")
                    }
                    "UNSUSPEND" -> {
                        // 取消暂停逻辑：重置为 New 状态 (state=0, stats=0)
                        supabaseClient.postgrest[TABLE_USER_PROGRESS]
                            .update({
                                set("state", 0)
                                set("stability", 0.0)
                                set("difficulty", 0.0)
                                set("reps", 0)
                                set("lapses", 0)
                                set("last_review", null as String?)
                                set("next_review", task.createdAt)
                                set("updated_at", task.createdAt)
                            }) {
                                filter { eq("id", progress.id) }
                            }
                        Log.i(TAG, "取消暂停(重置)上传成功: ${task.itemId}")
                    }
                    "BURY" -> {
                        val buriedUntil = task.payload?.toLongOrNull() ?: (epochDay + 1).toLong()
                        supabaseClient.postgrest[TABLE_USER_PROGRESS]
                            .update({ set("buried_until", buriedUntil); set("updated_at", task.createdAt) }) {
                                filter { eq("id", progress.id) }
                            }
                        Log.i(TAG, "Bury 状态上传成功: ${task.itemId}")
                    }
                    "FAVORITE" -> {
                        val isFavorite = task.payload?.toBoolean() ?: false
                        supabaseClient.postgrest[TABLE_USER_PROGRESS]
                            .update({ set("is_favorite", isFavorite); set("updated_at", task.createdAt) }) {
                                filter { eq("id", progress.id) }
                            }
                        Log.i(TAG, "收藏状态上传成功: ${task.itemId}")
                    }
                }
                syncOutboxDao.deleteById(task.id)
            } catch (e: Exception) {
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("STALE_DATA_CONFLICT")) {
                    Log.w(TAG, "检测到数据版本冲突 (STALE_DATA_CONFLICT), 放弃本地任务并拉取云端最新状态: ${task.itemId}")
                    syncOutboxDao.deleteById(task.id)
                    // 触发单条数据刷新
                    try {
                        val remote = supabaseClient.postgrest[TABLE_USER_PROGRESS]
                            .select { filter { eq("user_id", userId); eq("item_id", task.itemId); eq("item_type", task.itemType) } }
                            .decodeSingleOrNull<UserProgressEntity>()
                        if (remote != null) userProgressDao.insert(remote)
                    } catch (f: Exception) { /* ignore */ }
                } else {
                    Log.e(TAG, "上传评分失败, 稍后重试: ${task.itemId}", e)
                    syncOutboxDao.incrementAttempts(task.id)
                    syncOutboxDao.setSyncingStatus(task.id, false)
                }
            }
        }
    }

    private suspend fun handleRealtimeAction(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert -> {
                    val entity = json.decodeFromJsonElement<UserProgressEntity>(action.record)
                    userProgressDao.insert(entity)
                }
                is PostgresAction.Update -> {
                    val entity = json.decodeFromJsonElement<UserProgressEntity>(action.record)
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
            _dataUpdatedEvent.emit(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "处理 Realtime 变更失败", e)
        }
    }

    /**
     * 同步应用设置 (Bi-directional)
     */
    private suspend fun syncSettings(userId: String) {
        Log.d(TAG, "开始同步应用设置: $userId")

        // 1. 获取本地快照
        val localSettings = settingsRepository.getAppSettingsSnapshot()

        var isDecodingError = false
        val remoteData: SyncAppSettingsDto? = try {
            supabaseClient.postgrest["user_settings"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<SyncAppSettingsDto>()
        } catch (e: Exception) {
            Log.e(TAG, "解析云端设置 JSON 失败，同步已中止以保护云端数据", e)
            isDecodingError = true
            null
        }

        if (isDecodingError) {
            // 解析失败时，绝对不能执行 push，否则会用本地旧数据覆盖云端
            return
        }

        if (remoteData == null) {
            // 只有在明确知道云端没有数据（返回为 null 且无异常）时，才推送本地初始值
            Log.d(TAG, "云端无设置记录，执行首次推送")
            pushSettingsToCloud(userId, localSettings)
            return
        }

        // 3. 冲突解决 (基于修改时间戳)
        // 远程使用 ISO8601 字符串 (由 Supabase 默认生成或 Web 端传 ISO 串)
        // 为了兼容性，我们主要看本地的 lastSettingsModifiedTime 是否大于远程的记录

        val remoteSettings = remoteData.settings
        val remoteModTime = remoteSettings.lastSettingsModifiedTime

        Log.d(TAG, "设置同步对比: Local=${localSettings.lastSettingsModifiedTime}, Remote=$remoteModTime")

        if (localSettings.lastSettingsModifiedTime > remoteModTime) {
            // 本地较新，推送
            Log.i(TAG, "本地设置较新 (${localSettings.lastSettingsModifiedTime} > $remoteModTime)，推送到云端")
            pushSettingsToCloud(userId, localSettings)
        } else if (remoteModTime > localSettings.lastSettingsModifiedTime) {
            // 远程较新，拉取
            Log.i(TAG, "远程设置较新 ($remoteModTime > ${localSettings.lastSettingsModifiedTime})，应用到本地")
            settingsRepository.applyAppSettingsSnapshot(remoteSettings)
        } else {
            Log.d(TAG, "设置已是最新 ($localSettings.lastSettingsModifiedTime)，无需操作")
        }
    }

    private suspend fun pushSettingsToCloud(userId: String, settings: com.jian.nemo2.core.domain.model.AppSettings) {
        try {
            val dto = SyncAppSettingsDto(
                userId = userId,
                settings = settings,
                updatedAt = com.jian.nemo2.core.common.util.DateTimeUtils.millisToIso(System.currentTimeMillis())
            )
            supabaseClient.postgrest["user_settings"].upsert(dto)
            Log.i(TAG, "设置推送云端成功")
        } catch (e: Exception) {
            Log.e(TAG, "推送设置失败", e)
        }
    }

    /**
     * 公开触发字典同步逻辑 (用于启动屏或预加载)
     */
    suspend fun performDictionarySync() {
        performDictionarySyncInternal()
    }

    private suspend fun performDictionarySyncInternal() {
        Log.d(TAG, "开始检查字典同步...")
        try {
            val remoteVersion = contentRepository.getRemoteContentVersion()
            val lastVersion = settingsRepository.getLastContentVersion()

            // 自我修复逻辑：如果本地数据库为空，强制同步，忽略版本号对比
            val wordCount = database.wordDao().getCount()
            val grammarCount = database.grammarDao().getCount()
            val isDatabaseEmpty = wordCount == 0 || grammarCount == 0

            Log.d(TAG, "本地词库状态: 单词=$wordCount, 语法=$grammarCount, Empty=$isDatabaseEmpty, RemoteV=$remoteVersion, LocalV=$lastVersion")

            if (!isDatabaseEmpty && remoteVersion != null && remoteVersion <= lastVersion) {
                Log.d(TAG, "词库已是最新，跳过同步")
                return
            }

            val startTime = System.currentTimeMillis()

            // [性能优化] 并发拉取所有数据类型 (3 个并发请求替代 15 个串行请求)
            lateinit var allWords: List<com.jian.nemo2.core.domain.model.dto.WordDto>
            lateinit var allGrammars: List<com.jian.nemo2.core.domain.model.dto.GrammarDto>
            lateinit var allQuestions: List<com.jian.nemo2.core.domain.model.dto.GrammarTestQuestionDto>

            coroutineScope {
                val wordsDeferred = async { contentRepository.fetchAllRemoteWords() }
                val grammarsDeferred = async { contentRepository.fetchAllRemoteGrammars() }
                val questionsDeferred = async { contentRepository.fetchAllRemoteGrammarQuestions() }

                allWords = wordsDeferred.await()
                allGrammars = grammarsDeferred.await()
                allQuestions = questionsDeferred.await()
            }

            val fetchTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "并发拉取完成: 单词=${allWords.size}, 语法=${allGrammars.size}, 题目=${allQuestions.size} (耗时 ${fetchTime}ms)")

            // [性能优化] 批量写入本地数据库 (使用事务优化的方法)
            val writeStartTime = System.currentTimeMillis()

            if (allWords.isNotEmpty()) {
                contentUpdateApplier.applyAllWords(allWords)
            }

            if (allGrammars.isNotEmpty()) {
                contentUpdateApplier.applyAllGrammars(allGrammars)
            }

            if (allQuestions.isNotEmpty()) {
                contentUpdateApplier.applyAllGrammarQuestions(allQuestions)
            }

            val writeTime = System.currentTimeMillis() - writeStartTime
            val totalTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "词库同步完成: 拉取=${fetchTime}ms, 写入=${writeTime}ms, 总计=${totalTime}ms")

            if (remoteVersion != null) {
                settingsRepository.setLastContentVersion(remoteVersion)
            }

        } catch (e: Exception) {
            Log.e(TAG, "词库同步失败", e)
        }
    }
}

