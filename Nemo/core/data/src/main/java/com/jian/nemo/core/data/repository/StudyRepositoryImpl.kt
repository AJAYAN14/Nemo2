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
import kotlin.time.Duration.Companion.minutes
import com.jian.nemo.core.domain.model.RatingAction

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

    override suspend fun processReview(itemId: Long, itemType: String, rating: Int) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now()
        
        // 1. 本地算法预估 (遵循 rules.md: 3.B 离线补偿)
        // [FOR OPTIMISTIC UI PREVIEW ONLY]
        // 这里的计算仅用于乐观 UI 更新和离线占位。真正的 FSRS 核心逻辑由服务端的 
        // fn_process_review_atomic_v3 (Logic Authority) 负责计算。
        val learningSteps = listOf(1, 10)
        val relearningSteps = listOf(10)

        val ratingAction = algorithm.evaluateRatingAction(
            state = progress.state,
            lapses = progress.lapses,
            currentStep = progress.learningStep,
            rating = rating,
            learningSteps = learningSteps,
            relearningSteps = relearningSteps
        )

        val newReps = progress.reps + 1
        val newLapses = if (rating == 1) progress.lapses + 1 else progress.lapses

        val nextReviewInstant: Instant
        val newStateInt: Int
        val newLearningStep: Int
        val finalStability: Double
        val finalDifficulty: Double

        when (ratingAction) {
            is RatingAction.Graduate -> {
                newStateInt = if (rating == 1) 3 else 2
                newLearningStep = 0
                
                val elapsedDays = calculateElapsedDays(progress.lastReview, now)
                val currentState = Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty)
                val newState = algorithm.step(currentState, rating, elapsedDays)
                
                finalStability = newState.stability
                finalDifficulty = newState.difficulty
                
                val intervalDays = if (rating == 1) {
                    0 
                } else {
                    val seed = algorithm.buildFsrsDeterministicSeed(progress.id, progress.reps)
                    algorithm.nextIntervalDaysWithFuzz(newState.stability, seed)
                }
                nextReviewInstant = now.plus(intervalDays.days)
            }
            is RatingAction.Requeue -> {
                newStateInt = if (rating == 1) {
                    if (progress.state == 2 || progress.state == 3) 3 else 1
                } else {
                    if (progress.state == 0) 1 else progress.state
                }
                newLearningStep = ratingAction.nextStep
                nextReviewInstant = now.plus(ratingAction.delayMins.minutes)
                
                // For sub-day steps, we still update memory state if there was elapsed time
                val elapsedDays = calculateElapsedDays(progress.lastReview, now)
                val currentState = Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty)
                val newState = algorithm.step(currentState, rating, elapsedDays)
                
                finalStability = newState.stability
                finalDifficulty = newState.difficulty
            }
            is RatingAction.Leech -> {
                newStateInt = -1 // Suspended
                newLearningStep = 0
                nextReviewInstant = now.plus(ratingAction.fallbackDelay.minutes)
                finalStability = progress.stability
                finalDifficulty = progress.difficulty
            }
        }

        val localUpdated = progress.copy(
            stability = finalStability,
            difficulty = finalDifficulty,
            state = newStateInt,
            learningStep = newLearningStep,
            reps = newReps,
            lapses = newLapses,
            lastReview = now.toString(),
            nextReview = nextReviewInstant.toString()
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

    override suspend fun suspendItem(itemId: Long, itemType: String) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = com.jian.nemo.core.common.util.DateTimeUtils.getCurrentCompensatedMillis().toString()

        // 1. 本地更新
        userProgressDao.updateProgressState(itemId, itemType, -1, now)

        // 2. 异步同步到 Supabase
        scope.launch {
            try {
                supabase.postgrest["user_progress"]
                    .update({
                        set("state", -1)
                        set("updated_at", now)
                    }) {
                        filter { eq("id", progress.id) }
                    }
            } catch (e: Exception) {
                println("暂停同步失败: ${e.message}")
            }
        }
    }

    override suspend fun unsuspendItem(itemId: Long, itemType: String) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = com.jian.nemo.core.common.util.DateTimeUtils.getCurrentCompensatedMillis().toString()

        // 1. 本地更新 (恢复到 New 状态)
        userProgressDao.updateProgressState(itemId, itemType, 0, now)

        // 2. 异步同步
        scope.launch {
            try {
                supabase.postgrest["user_progress"]
                    .update({
                        set("state", 0)
                        set("updated_at", now)
                    }) {
                        filter { eq("id", progress.id) }
                    }
            } catch (e: Exception) {
                println("取消暂停同步失败: ${e.message}")
            }
        }
    }

    override suspend fun buryItem(itemId: Long, itemType: String, epochDay: Long) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = com.jian.nemo.core.common.util.DateTimeUtils.getCurrentCompensatedMillis().toString()
        val buriedUntil = epochDay + 1

        // 1. 本地更新
        val updated = progress.copy(
            buriedUntil = buriedUntil,
            updatedAt = now
        )
        userProgressDao.insert(updated)

        // 2. 异步同步
        scope.launch {
            try {
                supabase.postgrest["user_progress"]
                    .update({
                        set("buried_until", buriedUntil)
                        set("updated_at", now)
                    }) {
                        filter { eq("id", progress.id) }
                    }
            } catch (e: Exception) {
                println("Bury 同步失败: ${e.message}")
            }
        }
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

    override suspend fun toggleFavorite(itemId: Long, itemType: String, isFavorite: Boolean) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()

        // 1. 本地更新
        userProgressDao.updateFavoriteStatus(itemId, itemType, isFavorite, now)

        // 2. 异步同步
        scope.launch {
            try {
                supabase.postgrest["user_progress"]
                    .update({
                        set("is_favorite", isFavorite)
                        set("updated_at", now)
                    }) {
                        filter { eq("id", progress.id) }
                    }
            } catch (e: Exception) {
                println("收藏状态同步失败: ${e.message}")
            }
        }
    }

    override suspend fun resetAllProgress(itemType: String) {
        val now = Clock.System.now().toString()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return

        // 1. 本地重置
        userProgressDao.resetAllProgress(itemType, now)

        // 2. 异步同步到服务器 (由于是全量重置，建议调用 RPC 或批量更新)
        scope.launch {
            try {
                // 重置逻辑：将该类型下该用户的所有进度 state 设为 0，并清空 FSRS 字段
                supabase.postgrest["user_progress"]
                    .update({
                        set("state", 0)
                        set("stability", 0.0)
                        set("difficulty", 0.0)
                        set("reps", 0)
                        set("lapses", 0)
                        set("learning_step", 0)
                        set("last_review", null as String?)
                        set("next_review", now)
                        set("updated_at", now)
                    }) {
                        filter {
                            eq("user_id", userId)
                            eq("item_type", itemType)
                        }
                    }
            } catch (e: Exception) {
                println("进度重置同步失败: ${e.message}")
            }
        }
    }

    override suspend fun clearAllFavorites(itemType: String) {
        val now = Clock.System.now().toString()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return

        // 1. 本地清空
        userProgressDao.clearAllFavorites(itemType, now)

        // 2. 异步同步
        scope.launch {
            try {
                supabase.postgrest["user_progress"]
                    .update({
                        set("is_favorite", false)
                        set("updated_at", now)
                    }) {
                        filter {
                            eq("user_id", userId)
                            eq("item_type", itemType)
                        }
                    }
            } catch (e: Exception) {
                println("清空收藏同步失败: ${e.message}")
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

fun UserProgress.toEntity() = UserProgressEntity(
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
