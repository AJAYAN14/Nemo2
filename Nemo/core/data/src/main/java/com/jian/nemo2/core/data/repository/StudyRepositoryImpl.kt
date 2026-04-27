package com.jian.nemo2.core.data.repository

import com.jian.nemo2.core.data.manager.SupabaseSyncManager
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.data.local.dao.UserProgressDao
import com.jian.nemo2.core.data.local.dao.SyncOutboxDao
import com.jian.nemo2.core.data.local.entity.UserProgressEntity
import com.jian.nemo2.core.data.local.entity.SyncOutboxEntity
import com.jian.nemo2.core.domain.model.UserProgress
import com.jian.nemo2.core.domain.repository.StudyRepository
import com.jian.nemo2.core.domain.model.SyncProgress
import com.jian.nemo2.core.domain.model.sync.SyncMode
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.domain.repository.AuthRepository
import com.jian.nemo2.core.common.di.ApplicationScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SupabaseSyncManager,
    @ApplicationScope private val scope: CoroutineScope
) : StudyRepository {

    override fun getDueItemsFlow(): Flow<List<UserProgress>> {
        val now = Clock.System.now().toString()
        // 获取当前学习日（用于过滤已暂缓/已埋藏项）
        return settingsRepository.learningDayResetHourFlow.flatMapLatest { resetHour ->
            val currentEpochDay = DateTimeUtils.getLearningDay(resetHour)
            userProgressDao.getDueItemsFlow(now, currentEpochDay).map { list ->
                list.map { it.toDomain() }
            }
        }
    }

    override suspend fun getProgressSync(itemId: Long, itemType: String): UserProgress? {
        return userProgressDao.getProgressByItemId(itemId, itemType)?.toDomain()
    }

    override fun observeProgressByItemIds(itemIds: List<Long>, itemType: String): Flow<List<UserProgress>> {
        return userProgressDao.getProgressByItemIdsFlow(itemIds, itemType).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun processReview(itemId: Long, itemType: String, rating: Int, requestId: String?) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        
        // 1. 立即写入本地 Outbox (保证离线可用)
        val now = Clock.System.now().toString()
        
        @Serializable
        data class ReviewPayload(val rating: Int, val requestId: String?)
        val payload = Json.encodeToString(ReviewPayload(rating, requestId))
        
        val outbox = SyncOutboxEntity(
            itemType = itemType,
            itemId = itemId,
            rating = rating,
            actionType = "REVIEW",
            payload = payload,
            createdAt = now,
            requestId = requestId
        )
        syncOutboxDao.insert(outbox)

        // 2. 异步触发一次同步
        scope.launch {
            try {
                syncPendingTasks()
            } catch (e: Exception) {
                Log.e("StudyRepository", "背景同步失败: ${e.message}")
            }
        }
    }

    override suspend fun undoReview(payload: com.jian.nemo2.core.domain.model.sync.UndoPayload, itemId: Long, itemType: String) {
        val now = Clock.System.now().toString()
        
        val outbox = SyncOutboxEntity(
            itemType = itemType,
            itemId = itemId,
            rating = 0,
            actionType = "UNDO",
            payload = Json.encodeToString(payload),
            createdAt = now
        )
        syncOutboxDao.insert(outbox)

        // 2. 立即触发同步
        scope.launch { syncPendingTasks() }
    }

    override fun startRealtimeSync() {
        // Realtime 监听由 SupabaseSyncManager 处理，这里主要触发初始化同步
        scope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            syncManager.performSync(userId, force = false, mode = SyncMode.TWO_WAY).collect()
        }
    }

    override fun stopRealtimeSync() {
        // TODO: 实现停止逻辑
    }

    override suspend fun syncPendingTasks() {
        syncManager.processOutbox()
    }

    override suspend fun suspendItem(itemId: Long, itemType: String) {
        val now = Clock.System.now().toString()
        userProgressDao.updateProgressState(itemId, itemType, -1, now)
        
        val outbox = SyncOutboxEntity(
            itemType = itemType, 
            itemId = itemId, 
            rating = 0,
            actionType = "SUSPEND", 
            payload = "{}", 
            createdAt = now
        )
        syncOutboxDao.insert(outbox)
        scope.launch { syncPendingTasks() }
    }

    override suspend fun unsuspendItem(itemId: Long, itemType: String) {
        val now = Clock.System.now().toString()
        userProgressDao.updateProgressState(itemId, itemType, 0, now)

        val outbox = SyncOutboxEntity(
            itemType = itemType, 
            itemId = itemId, 
            rating = 0,
            actionType = "UNSUSPEND", 
            payload = "{}", 
            createdAt = now
        )
        syncOutboxDao.insert(outbox)
        scope.launch { syncPendingTasks() }
    }

    override suspend fun buryItem(itemId: Long, itemType: String, epochDay: Long) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()
        val buriedUntil = epochDay + 1
        
        // [Optimistic Update] 如果是学习中(1)或重学中(3)，重置步长
        val updatedProgress = if (progress.state == 1 || progress.state == 3) {
            progress.copy(buriedUntil = buriedUntil, learningStep = 0, updatedAt = now)
        } else {
            progress.copy(buriedUntil = buriedUntil, updatedAt = now)
        }
        userProgressDao.insert(updatedProgress)

        // 写入 Outbox
        val outbox = SyncOutboxEntity(
            itemType = itemType,
            itemId = itemId,
            rating = 0,
            actionType = "BURY",
            payload = buriedUntil.toString(),
            createdAt = now
        )
        syncOutboxDao.insert(outbox)
        scope.launch { syncPendingTasks() }
    }

    override suspend fun toggleFavorite(itemId: Long, itemType: String, isFavorite: Boolean) {
        val now = Clock.System.now().toString()
        userProgressDao.updateFavoriteStatus(itemId, itemType, isFavorite, now)

        val outbox = SyncOutboxEntity(
            itemType = itemType,
            itemId = itemId,
            rating = 0,
            actionType = "FAVORITE",
            payload = isFavorite.toString(),
            createdAt = now
        )
        syncOutboxDao.insert(outbox)
        scope.launch { syncPendingTasks() }
    }

    override suspend fun resetAllProgress(itemType: String) {
        val now = Clock.System.now().toString()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        userProgressDao.resetAllProgress(itemType, now)
        
        scope.launch {
            try {
                supabase.postgrest["user_progress"].update({ set("state", 0); set("stability", 0.0); set("difficulty", 0.0); set("reps", 0); set("lapses", 0); set("learning_step", 0); set("last_review", null as String?); set("next_review", now); set("updated_at", now) }) { filter { eq("user_id", userId); eq("item_type", itemType) } }
            } catch (e: Exception) { println("重置进度同步失败: ${e.message}") }
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
        return combine(
            settingsRepository.learningDayResetHourFlow,
            settingsRepository.learnAheadLimitFlow
        ) { resetHour, learnAheadMinutes ->
            resetHour to learnAheadMinutes
        }.flatMapLatest { (resetHour, learnAheadMinutes) ->
            // 使用用户设置的提前学习时间
            val bufferMs = learnAheadMinutes * 60 * 1000L
            val nowWithBuffer = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + bufferMs).toString()
            val currentEpochDay = DateTimeUtils.getLearningDay(resetHour)

            Log.d("StudyRepository", "getDueItemsByTypeAndLevelFlow: Type=$itemType, Level=$level, Buffer=$learnAheadMinutes min, EpochDay=$currentEpochDay")

            userProgressDao.getDueItemsByTypeAndLevelFlow(itemType, level, nowWithBuffer, currentEpochDay)
        }.map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getDueItemsByTypeAndLevel(itemType: String, level: String): List<UserProgress> {
        val resetHour = settingsRepository.learningDayResetHourFlow.first()
        val learnAheadMinutes = settingsRepository.learnAheadLimitFlow.first()
        
        // 增加超前学习缓冲区，确保刚评分过但在 learnAhead 范围内的词不会在重新进入界面时“消失”
        val bufferMs = learnAheadMinutes * 60 * 1000L
        val nowWithBuffer = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + bufferMs).toString()
        val currentEpochDay = DateTimeUtils.getLearningDay(resetHour)

        return userProgressDao.getDueItemsByTypeAndLevelSync(itemType, level, nowWithBuffer, currentEpochDay).map {
            it.toDomain()
        }
    }

    override suspend fun performFullSync() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncManager.performSync(userId, force = true, mode = SyncMode.TWO_WAY).first { it is SyncProgress.Completed || it is SyncProgress.Failed }
    }
}

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
