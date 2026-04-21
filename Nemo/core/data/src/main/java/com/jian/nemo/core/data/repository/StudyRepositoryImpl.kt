package com.jian.nemo.core.data.repository

import com.jian.nemo.core.data.local.dao.SyncOutboxDao
import com.jian.nemo.core.data.local.dao.UserProgressDao
import com.jian.nemo.core.data.local.entity.SyncOutboxEntity
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import com.jian.nemo.core.domain.algorithm.Fsrs6Algorithm
import com.jian.nemo.core.data.remote.model.ProcessReviewRpcParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.jian.nemo.core.data.manager.SupabaseSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import com.jian.nemo.core.common.di.ApplicationScope
import com.jian.nemo.core.domain.model.UserProgress
import com.jian.nemo.core.domain.repository.StudyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncManager: SupabaseSyncManager,
    @ApplicationScope private val scope: CoroutineScope
) : StudyRepository {

    private val algorithm = Fsrs6Algorithm()

    override fun getDueItemsFlow(): Flow<List<UserProgress>> {
        val now = Clock.System.now().toString()
        val currentEpochDay = System.currentTimeMillis() / 86400000
        return userProgressDao.getDueItemsFlow(now, currentEpochDay).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun processReview(itemId: String, itemType: String, rating: Int) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now()
        
        // 1. 本地算法预估 (遵循 rules.md: 3.B 离线补偿)
        // [FOR OPTIMISTIC UI PREVIEW ONLY]
        // 这里的计算仅用于乐观 UI 更新和离线占位。真正的 FSRS 核心逻辑由服务端的 
        // fn_process_review_atomic_v3 (Logic Authority) 负责计算。
        val elapsedDays = calculateElapsedDays(progress.lastReview, now)
        val currentState = Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty)
        val newState = algorithm.step(currentState, rating, elapsedDays)
        
        val newReps = if (rating == 1) progress.reps else progress.reps + 1
        val newLapses = if (rating == 1) progress.lapses + 1 else progress.lapses
        
        // [Offline Compensation] Accurate local Fuzz calculation for Optimistic UI
        val intervalDays = if (rating == 1) {
            0 // Again falls back to due immediately
        } else {
            val seed = algorithm.buildFsrsDeterministicSeed(progress.id, progress.reps)
            algorithm.nextIntervalDaysWithFuzz(newState.stability, seed)
        }
        val nextReview = now.plus(intervalDays.days)

        val localUpdated = progress.copy(
            stability = newState.stability,
            difficulty = newState.difficulty,
            state = if (rating == 1) (if (progress.state >= 2) 3 else 1) else 2,
            reps = newReps,
            lapses = newLapses,
            lastReview = now.toString(),
            nextReview = nextReview.toString()
        )

        // 2. 更新本地 Room (秒开)
        userProgressDao.insert(localUpdated)

        // 3. 入队 Outbox
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = rating,
                createdAt = now.toString()
            )
        )

        // 4. 触发异步同步
        scope.launch {
            syncPendingTasks()
        }
    }

    override fun startRealtimeSync() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncManager.startRealtimeSync(userId)
    }
    
    override fun stopRealtimeSync() {
        syncManager.stopRealtimeSync()
    }

    override suspend fun syncPendingTasks() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val pending = syncOutboxDao.getPendingTasks()
        
        for (task in pending) {
            val progress = userProgressDao.getByItem(task.itemType, task.itemId) 
                ?: continue

            try {
                syncOutboxDao.setSyncingStatus(task.id, true)

                // [Logic Authority] 遵循 rules.md: 3.B，不再由客户端计算稳定性/难度
                // 仅发送 rating 和必要元数据，由服务器 RPC (v3) 负责算法执行
                
                val params = mapOf(
                    "p_user_id" to userId,
                    "p_progress_id" to progress.id,
                    "p_rating" to task.rating,
                    "p_request_id" to "android-${task.id}",
                    "p_epoch_day" to (Instant.parse(task.createdAt).toEpochMilliseconds() / 86400000).toInt(),
                    "p_study_field" to (if (task.itemType == "word") "reviewed_words" else "reviewed_grammars"),
                    "p_expected_last_review" to progress.lastReview
                )

                // 调用服务端重构后的 v3 RPC
                supabase.postgrest.rpc("fn_process_review_atomic_v3", Json.encodeToJsonElement(params).jsonObject)
                
                // 同步成功，删除任务
                syncOutboxDao.deleteById(task.id)
                
            } catch (e: Exception) {
                if (e.message?.contains("STALE_DATA_CONFLICT") == true) {
                    // [Conflict Resolution] 服务器数据更新，本地数据陈旧。
                    // 强制拉取这条数据的最新状态并覆盖本地，成功后再删除本地的无效重试任务
                    try {
                        val remoteProgressResponse = supabase.postgrest["user_progress"]
                            .select { filter { eq("id", progress.id) } }
                            .decodeSingleOrNull<UserProgressEntity>()
                        if (remoteProgressResponse != null) {
                            userProgressDao.insert(remoteProgressResponse)
                            syncOutboxDao.deleteById(task.id)
                        }
                    } catch (fetchErr: Exception) {
                        fetchErr.printStackTrace()
                    }
                } else {
                    syncOutboxDao.incrementAttempts(task.id)
                    e.printStackTrace()
                }
            }
        }
    }

    private fun calculateElapsedDays(lastReview: String?, now: Instant): Double {
        if (lastReview == null) return 0.0
        val last = Instant.parse(lastReview)
        val diff = now.toEpochMilliseconds() - last.toEpochMilliseconds()
        return diff.toDouble() / (1000.0 * 60 * 60 * 24)
    }
}

// ========== Mapper Extensions ==========

fun UserProgressEntity.toDomain() = UserProgress(
    id = this.id,
    userId = this.userId,
    itemType = this.itemType,
    itemId = this.itemId,
    stability = this.stability,
    difficulty = this.difficulty,
    elapsedDays = this.elapsedDays,
    scheduledDays = this.scheduledDays,
    reps = this.reps,
    lapses = this.lapses,
    state = this.state,
    learningStep = this.learningStep,
    lastReview = this.lastReview,
    nextReview = this.nextReview,
    buriedUntil = this.buriedUntil,
    level = this.level,
    createdAt = this.createdAt
)
