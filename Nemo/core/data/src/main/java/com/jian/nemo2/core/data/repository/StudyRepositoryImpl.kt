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
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncManager: SupabaseSyncManager,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) : StudyRepository {

    override fun observeProgressByItemIds(itemIds: List<Long>, itemType: String): Flow<List<UserProgress>> {
        return userProgressDao.getProgressByItemIdsFlow(itemIds, itemType)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getProgressSync(itemId: Long, itemType: String): UserProgress? {
        return userProgressDao.getByItem(itemType, itemId)?.toDomain()
    }

    init {
        // [Native Mirror] 自动生命周期管理：监听用户状态并启动/停止 Realtime
        scope.launch {
            authRepository.getUserFlow().collect { user ->
                if (user != null) {
                    syncManager.startRealtimeSync(user.id)
                } else {
                    syncManager.stopRealtimeSync()
                }
            }
        }
    }

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

    override suspend fun processReview(itemId: Long, itemType: String, rating: Int, requestId: String?) {
        var progress = userProgressDao.getByItem(itemType, itemId)
        if (progress == null) {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            try {
                Log.d("StudyRepository", "Local progress not found for $itemType $itemId, fetching from remote to handle race condition")
                progress = supabase.postgrest["user_progress"]
                    .select { filter { eq("user_id", userId); eq("item_id", itemId); eq("item_type", itemType) } }
                    .decodeSingleOrNull<UserProgressEntity>()
                if (progress != null) {
                    userProgressDao.insert(progress)
                }
            } catch (e: Exception) {
                Log.e("StudyRepository", "Failed to fetch remote progress for review", e)
            }
        }
        if (progress == null) {
            Log.e("StudyRepository", "Progress completely missing for $itemType $itemId, aborting review")
            return
        }
        
        val oldLastReview = progress.lastReview
        val now = Clock.System.now()

        // [Optimistic Update] 立即在本地标记为已复习，以提供流畅体验
        // 这里的计算仅用于本地瞬间展现，最终结果将由 RPC 返回值覆盖
        val localUpdated = progress.copy(
            lastReview = now.toString(),
            // 将下次复习时间暂时设为明天，使其从今日列表中消失
            nextReview = now.plus(1.days).toString(),
            updatedAt = now.toString()
        )
        userProgressDao.insert(localUpdated)

        // [Atomic Sync] 写入离线队列
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = rating,
                createdAt = now.toString(),
                expectedLastReview = oldLastReview,
                requestId = requestId
            )
        )

        // 立即触发后台上传
        scope.launch {
            try {
                syncManager.processOutbox()
            } catch (e: Exception) {
                Log.e("StudyRepository", "即时同步评分失败，任务已在队列中: ${e.message}")
            }
        }
    }

    override suspend fun undoReview(payload: com.jian.nemo2.core.domain.model.sync.UndoPayload, itemId: Long, itemType: String) {
        val now = Clock.System.now().toString()
        
        // [Optimistic Update] 立即在本地还原状态
        val restoredProgress = UserProgress(
            id = "", // Dao doesn't strictly need ID if updating by itemType and itemId, but wait, Room needs the primary key `id` for REPLACE.
            userId = "", // Let's just fetch the existing one to get its DB ID
            itemType = itemType,
            itemId = itemId,
            stability = payload.stability,
            difficulty = payload.difficulty,
            reps = payload.reps,
            lapses = payload.lapses,
            state = payload.state,
            learningStep = payload.learningStep,
            lastReview = payload.lastReview,
            nextReview = payload.nextReview,
            elapsedDays = payload.elapsedDays,
            scheduledDays = payload.scheduledDays,
            buriedUntil = payload.buriedUntil,
            level = "",
            createdAt = ""
        )
        // Since we need to preserve existing db ID and level, let's fetch current:
        val existing = userProgressDao.getByItem(itemType, itemId)
        if (existing != null) {
            val updated = existing.copy(
                stability = payload.stability,
                difficulty = payload.difficulty,
                reps = payload.reps,
                lapses = payload.lapses,
                state = payload.state,
                learningStep = payload.learningStep,
                lastReview = payload.lastReview,
                nextReview = payload.nextReview,
                elapsedDays = payload.elapsedDays,
                scheduledDays = payload.scheduledDays,
                buriedUntil = payload.buriedUntil,
                updatedAt = now
            )
            userProgressDao.insert(updated)
        }

        // [Atomic Sync] 写入离线队列
        val jsonPayload = Json.encodeToString(payload)
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = 0,
                actionType = "UNDO",
                payload = jsonPayload,
                createdAt = now,
                requestId = payload.requestId
            )
        )

        // 立即触发后台上传
        scope.launch {
            try {
                syncManager.processOutbox()
            } catch (e: Exception) {
                Log.e("StudyRepository", "撤销同步失败，任务已在队列中: ${e.message}")
            }
        }
    }

    override fun startRealtimeSync() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        syncManager.startRealtimeSync(userId)

        // 监听来自 syncManager 的底层数据变更并转发到 Repository 信号
        scope.launch {
            // 注意：我们需要在 SyncManager 中也暴露一个 Flow，
            // 或者让 SyncManager 直接调用 Repository 的回调。
            // 这里我们先简单处理，后续在 SyncManager 中注入信号发送逻辑。
        }
    }

    override fun stopRealtimeSync() {
        syncManager.stopRealtimeSync()
    }

    override suspend fun suspendItem(itemId: Long, itemType: String) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()
        
        // [Optimistic Update]
        userProgressDao.updateProgressState(itemId, itemType, -1, now)
        
        // [Atomic Sync]
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = 0,
                actionType = "SUSPEND",
                createdAt = now
            )
        )
        
        scope.launch {
            try { syncManager.processOutbox() } catch (e: Exception) { Log.e("StudyRepository", "暂停同步失败: ${e.message}") }
        }
    }

    override suspend fun unsuspendItem(itemId: Long, itemType: String) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()
        
        // [Optimistic Update] 对齐 Web 端：恢复即重置 (Reset to New)
        val resetProgress = progress.copy(
            state = 0,
            stability = 0.0,
            difficulty = 0.0,
            reps = 0,
            lapses = 0,
            lastReview = null,
            nextReview = now,
            updatedAt = now
        )
        userProgressDao.insert(resetProgress)
        
        // [Atomic Sync]
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = 0,
                actionType = "UNSUSPEND",
                createdAt = now
            )
        )
        
        scope.launch {
            try { syncManager.processOutbox() } catch (e: Exception) { Log.e("StudyRepository", "取消暂停同步失败: ${e.message}") }
        }
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
        
        // [Atomic Sync]
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = 0,
                actionType = "BURY",
                payload = buriedUntil.toString(),
                createdAt = now
            )
        )
        
        scope.launch {
            try { syncManager.processOutbox() } catch (e: Exception) { Log.e("StudyRepository", "Bury 同步失败: ${e.message}") }
        }
    }

    override suspend fun syncPendingTasks() {
        syncManager.processOutbox()
    }

    override suspend fun toggleFavorite(itemId: Long, itemType: String, isFavorite: Boolean) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now().toString()
        
        // [Optimistic Update]
        userProgressDao.updateFavoriteStatus(itemId, itemType, isFavorite, now)
        
        // [Atomic Sync]
        syncOutboxDao.insert(
            SyncOutboxEntity(
                itemId = itemId,
                itemType = itemType,
                rating = 0,
                actionType = "FAVORITE",
                payload = isFavorite.toString(),
                createdAt = now
            )
        )
        
        scope.launch {
            try { syncManager.processOutbox() } catch (e: Exception) { Log.e("StudyRepository", "收藏状态同步失败: ${e.message}") }
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

            userProgressDao.getDueItemsByTypeAndLevelFlow(itemType, level, nowWithBuffer, currentEpochDay).map { list ->
                list.map { it.toDomain() }
            }
        }
    }

    override suspend fun getDueItemsByTypeAndLevel(itemType: String, level: String): List<UserProgress> {
        val resetHour = settingsRepository.learningDayResetHourFlow.first()
        val learnAheadMinutes = settingsRepository.learnAheadLimitFlow.first()
        
        // 使用用户设置的提前学习时间
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

    private fun calculateElapsedDays(lastReview: String?, now: Instant): Double {
        if (lastReview == null) return 0.0
        val last = Instant.parse(lastReview)
        val diff = now.toEpochMilliseconds() - last.toEpochMilliseconds()
        return diff.toDouble() / (1000.0 * 60 * 60 * 24)
    }
}

fun UserProgressEntity.toDomain() = UserProgress(id = this.id, userId = this.userId, itemType = this.itemType, itemId = this.itemId, stability = this.stability, difficulty = this.difficulty, elapsedDays = this.elapsedDays, scheduledDays = this.scheduledDays, reps = this.reps, lapses = this.lapses, state = this.state, learningStep = this.learningStep, lastReview = this.lastReview, nextReview = this.nextReview, buriedUntil = this.buriedUntil, level = this.level, createdAt = this.createdAt)
fun UserProgress.toEntity() = UserProgressEntity(id = this.id, userId = this.userId, itemType = this.itemType, itemId = this.itemId, stability = this.stability, difficulty = this.difficulty, elapsedDays = this.elapsedDays, scheduledDays = this.scheduledDays, reps = this.reps, lapses = this.lapses, state = this.state, learningStep = this.learningStep, lastReview = this.lastReview, nextReview = this.nextReview, buriedUntil = this.buriedUntil, level = this.level, createdAt = this.createdAt)
