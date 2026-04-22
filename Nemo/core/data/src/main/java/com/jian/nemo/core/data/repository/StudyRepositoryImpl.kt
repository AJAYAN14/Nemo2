package com.jian.nemo.core.data.repository

import com.jian.nemo.core.data.manager.SupabaseSyncManager
import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.data.local.dao.UserProgressDao
import com.jian.nemo.core.data.local.dao.SyncOutboxDao
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import com.jian.nemo.core.data.local.entity.SyncOutboxEntity
import com.jian.nemo.core.domain.model.UserProgress
import com.jian.nemo.core.domain.repository.StudyRepository
import com.jian.nemo.core.domain.algorithm.Fsrs6Algorithm
import com.jian.nemo.core.domain.model.RatingAction
import com.jian.nemo.core.domain.model.SyncProgress
import com.jian.nemo.core.domain.model.sync.SyncMode
import com.jian.nemo.core.domain.repository.SettingsRepository
import com.jian.nemo.core.common.di.ApplicationScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncManager: SupabaseSyncManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) : StudyRepository {

    private val algorithm = Fsrs6Algorithm()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDueItemsFlow(): Flow<List<UserProgress>> {
        return settingsRepository.learningDayResetHourFlow.flatMapLatest { resetHour ->
            val now = Clock.System.now().toString()
            val currentEpochDay = DateTimeUtils.getLearningDay(resetHour)
            userProgressDao.getDueItemsFlow(now, currentEpochDay).map { list ->
                list.map { it.toDomain() }
            }
        }
    }

    override suspend fun processReview(itemId: Long, itemType: String, rating: Int) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now()
        
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
                val intervalDays = if (rating == 1) 0 else {
                    val seed = algorithm.buildFsrsDeterministicSeed(progress.id, progress.reps)
                    algorithm.nextIntervalDaysWithFuzz(newState.stability, seed)
                }
                nextReviewInstant = now.plus(intervalDays.days)
            }
            is RatingAction.Requeue -> {
                newStateInt = if (rating == 1) (if (progress.state == 2 || progress.state == 3) 3 else 1) else (if (progress.state == 0) 1 else progress.state)
                newLearningStep = ratingAction.nextStep
                nextReviewInstant = now.plus(ratingAction.delayMins.minutes)
                val elapsedDays = calculateElapsedDays(progress.lastReview, now)
                val currentState = Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty)
                val newState = algorithm.step(currentState, rating, elapsedDays)
                finalStability = newState.stability
                finalDifficulty = newState.difficulty
            }
            is RatingAction.Leech -> {
                newStateInt = -1
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
        userProgressDao.insert(localUpdated)
        syncOutboxDao.insert(SyncOutboxEntity(itemId = itemId.toString(), itemType = itemType, rating = rating, createdAt = now.toString()))
        scope.launch { syncPendingTasks() }
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
        userProgressDao.updateProgressState(itemId, itemType, -1, now)
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("state", -1); set("updated_at", now) }) { filter { eq("id", progress.id) } }
            } catch (e: Exception) { println("暂停同步失败: ${e.message}") }
        }
    }

    override suspend fun unsuspendItem(itemId: Long, itemType: String) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = com.jian.nemo.core.common.util.DateTimeUtils.getCurrentCompensatedMillis().toString()
        userProgressDao.updateProgressState(itemId, itemType, 0, now)
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("state", 0); set("updated_at", now) }) { filter { eq("id", progress.id) } }
            } catch (e: Exception) { println("取消暂停同步失败: ${e.message}") }
        }
    }

    override suspend fun buryItem(itemId: Long, itemType: String, epochDay: Long) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = com.jian.nemo.core.common.util.DateTimeUtils.getCurrentCompensatedMillis().toString()
        val buriedUntil = epochDay + 1
        userProgressDao.insert(progress.copy(buriedUntil = buriedUntil, updatedAt = now))
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("buried_until", buriedUntil); set("updated_at", now) }) { filter { eq("id", progress.id) } }
            } catch (e: Exception) { println("Bury 同步失败: ${e.message}") }
        }
    }

    override suspend fun syncPendingTasks() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val pending = syncOutboxDao.getPendingTasks()
        for (task in pending) {
            val progress = userProgressDao.getByItem(task.itemType, task.itemId.toLongOrNull() ?: 0L) ?: continue
            try {
                syncOutboxDao.setSyncingStatus(task.id, true)
                @Serializable data class ReviewParams(val p_user_id: String, val p_progress_id: String, val p_rating: Int, val p_request_id: String, val p_epoch_day: Int, val p_study_field: String, val p_expected_last_review: String?)
                val params = ReviewParams(p_user_id = userId, p_progress_id = progress.id, p_rating = task.rating, p_request_id = "android-${task.id}", p_epoch_day = (Instant.parse(task.createdAt).toEpochMilliseconds() / 86400000).toInt(), p_study_field = (if (task.itemType == "word") "reviewed_words" else "reviewed_grammars"), p_expected_last_review = progress.lastReview)
                supabase.postgrest.rpc("fn_process_review_atomic_v3", params)
                syncOutboxDao.deleteById(task.id)
            } catch (e: Exception) {
                if (e.message?.contains("STALE_DATA_CONFLICT") == true) {
                    try {
                        val remote = supabase.postgrest["user_progress"].select { filter { eq("id", progress.id) } }.decodeSingleOrNull<UserProgressEntity>()
                        if (remote != null) { userProgressDao.insert(remote); syncOutboxDao.deleteById(task.id) }
                    } catch (fetchErr: Exception) { fetchErr.printStackTrace() }
                } else { syncOutboxDao.incrementAttempts(task.id); e.printStackTrace() }
            }
        }
    }

    override suspend fun toggleFavorite(itemId: Long, itemType: String, isFavorite: Boolean) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()
        userProgressDao.updateFavoriteStatus(itemId, itemType, isFavorite, now)
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("is_favorite", isFavorite); set("updated_at", now) }) { filter { eq("id", progress.id) } }
            } catch (e: Exception) { println("收藏状态同步失败: ${e.message}") }
        }
    }

    override suspend fun resetAllProgress(itemType: String) {
        val now = Clock.System.now().toString()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        userProgressDao.resetAllProgress(itemType, now)
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("state", 0); set("stability", 0.0); set("difficulty", 0.0); set("reps", 0); set("lapses", 0); set("learning_step", 0); set("last_review", null as String?); set("next_review", now); set("updated_at", now) }) { filter { eq("user_id", userId); eq("item_type", itemType) } }
            } catch (e: Exception) { println("进度重置同步失败: ${e.message}") }
        }
    }

    override suspend fun clearAllFavorites(itemType: String) {
        val now = Clock.System.now().toString()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        userProgressDao.clearAllFavorites(itemType, now)
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("is_favorite", false); set("updated_at", now) }) { filter { eq("user_id", userId); eq("item_type", itemType) } }
            } catch (e: Exception) { println("清空收藏同步失败: ${e.message}") }
        }
    }

    override suspend fun seedDailyNewItems(itemType: String, limit: Int, level: String, isRandom: Boolean, epochDay: Int) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        @Serializable data class SeedParams(val p_user_id: String, val p_item_type: String, val p_limit: Int, val p_level: String, val p_is_random: Boolean, val p_epoch_day: Int)
        try {
            val params = SeedParams(p_user_id = userId, p_item_type = itemType, p_limit = limit, p_level = level, p_is_random = isRandom, p_epoch_day = epochDay)
            supabase.postgrest.rpc("fn_seed_daily_new_items", params)
            // 播种后强制进行一次全量同步(force=true)，以规避时钟回拨或增量同步漏掉新条目的问题
            syncManager.performSync(userId, force = true, mode = SyncMode.TWO_WAY)
                .first { it is SyncProgress.Completed || it is SyncProgress.Failed }
        } catch (e: Exception) { Log.e("StudyRepository", "播种新词失败: ${e.message}", e) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDueItemsByTypeAndLevelFlow(itemType: String, level: String): Flow<List<UserProgress>> {
        return settingsRepository.learningDayResetHourFlow.flatMapLatest { resetHour ->
            val bufferMs = 12 * 60 * 60 * 1000L
            val nowWithBuffer = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + bufferMs).toString()
            val currentEpochDay = DateTimeUtils.getLearningDay(resetHour)
            
            Log.d("StudyRepository", "getDueItemsByTypeAndLevelFlow: Type=$itemType, Level=$level, EpochDay=$currentEpochDay")
            
            userProgressDao.getDueItemsByTypeAndLevelFlow(itemType, level, nowWithBuffer, currentEpochDay).map { list -> 
                list.map { it.toDomain() } 
            }
        }
    }

    override suspend fun performFullSync() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncManager.performSync(userId, force = true, mode = SyncMode.TWO_WAY).first { it is SyncProgress.Completed || it is SyncProgress.Failed }
    }

    private fun calculateElapsedDays(lastReview: String?, now: Instant): Double {
        if (lastReview == null) return 0.0
        val last = Instant.parse(lastReview)
        val diff = now.toEpochMilliseconds() - last.toEpochMilliseconds()
        return diff.toDouble() / (1000.0 * 60 * 60 * 24)
    }
}

fun UserProgressEntity.toDomain() = UserProgress(id = this.id, userId = this.userId, itemType = this.itemType, itemId = this.itemId, stability = this.stability, difficulty = this.difficulty, elapsedDays = this.elapsedDays, scheduledDays = this.scheduledDays, reps = this.reps, lapses = this.lapses, state = this.state, learningStep = this.learningStep, lastReview = this.lastReview, nextReview = this.nextReview, buriedUntil = this.buriedUntil, level = this.level, createdAt = this.createdAt)
fun UserProgress.toEntity() = UserProgressEntity(id = this.id, userId = this.userId, itemType = this.itemType, itemId = this.itemId, stability = this.stability, difficulty = this.difficulty, elapsedDays = this.elapsedDays, scheduledDays = this.scheduledDays, reps = this.reps, lapses = this.lapses, state = this.state, learningStep = this.learningStep, lastReview = this.lastReview, nextReview = this.nextReview, buriedUntil = this.buriedUntil, level = this.level, createdAt = this.createdAt)
