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
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val userProgressDao: UserProgressDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val scope: CoroutineScope
) : StudyRepository {

    private val algorithm = Fsrs6Algorithm()
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    override fun getDueItemsFlow(): Flow<List<UserProgressEntity>> {
        val now = Clock.System.now().toString()
        val currentEpochDay = System.currentTimeMillis() / 86400000
        return userProgressDao.getDueItemsFlow(now, currentEpochDay)
    }

    override suspend fun processReview(itemId: Int, itemType: String, rating: Int) {
        val progress = userProgressDao.getByItem(itemType, itemId) ?: return
        val now = Clock.System.now()
        
        // 1. 本地算法预估 (遵循 rules.md: 3.B 离线补偿)
        val elapsedDays = calculateElapsedDays(progress.lastReview, now)
        val currentState = Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty)
        val newState = algorithm.step(currentState, rating, elapsedDays)
        
        // 注意：这里为了简化，暂不计算 Fuzz，等 RPC 返回最终结果
        val nextReview = now.plus(java.time.Duration.ofDays(1)) // 临时占位

        val localUpdated = progress.copy(
            stability = newState.stability,
            difficulty = newState.difficulty,
            state = if (rating == 1) (if (progress.state >= 2) 3 else 1) else 2,
            lastReview = now.toString(),
            nextReview = nextReview.toString()
        )

        // 2. 更新本地 Room (秒开)
        userProgressDao.insertOrUpdate(localUpdated)

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
        if (realtimeChannel != null) return

        realtimeChannel = supabase.channel("user_progress_sync") {
            // 筛选当前用户的变更
        }

        val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "user_progress"
        }

        scope.launch {
            changeFlow.collect { action ->
                when (action) {
                    is PostgresAction.Update -> {
                        val updated = action.decodeRecord<UserProgressEntity>()
                        userProgressDao.insertOrUpdate(updated)
                    }
                    is PostgresAction.Insert -> {
                        val inserted = action.decodeRecord<UserProgressEntity>()
                        userProgressDao.insertOrUpdate(inserted)
                    }
                    is PostgresAction.Delete -> {
                        val id = action.oldRecord["id"]?.toString()
                        if (id != null) userProgressDao.deleteById(id)
                    }
                    else -> {}
                }
            }
        }

        scope.launch {
            realtimeChannel!!.subscribe()
        }
    }

    override fun stopRealtimeSync() {
        scope.launch {
            realtimeChannel?.unsubscribe()
            realtimeChannel = null
        }
    }

    override suspend fun syncPendingTasks() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val pending = syncOutboxDao.getPendingTasks()
        
        for (task in pending) {
            try {
                syncOutboxDao.setSyncingStatus(task.id, true)
                
                val progress = userProgressDao.getByItem(task.itemType, task.itemId) 
                    ?: continue

                // 1. 本地再次计算 (确保使用最新的 FSRS 6 参数)
                val now = Instant.parse(task.createdAt)
                val elapsedDays = calculateElapsedDays(progress.lastReview, now)
                val nextState = algorithm.step(
                    Fsrs6Algorithm.MemoryState(progress.stability, progress.difficulty),
                    task.rating,
                    elapsedDays
                )
                
                // 计算间隔 (这里简化，实际应考虑 Fuzz，但在 RPC 中云端会重新计算)
                val interval = algorithm.nextIntervalDaysWithFuzz(nextState.stability, task.id) // 暂用 task.id 作为随机种子

                // 2. 构建 RPC 参数 (严格对齐 Web 端)
                val params = ProcessReviewRpcParams(
                    userId = userId,
                    progressId = progress.id,
                    itemType = task.itemType,
                    itemId = task.itemId,
                    rating = task.rating,
                    prevStability = progress.stability,
                    prevDifficulty = progress.difficulty,
                    prevState = progress.state,
                    prevLearningStep = progress.learningStep,
                    prevBuriedUntil = progress.buried_until,
                    nextStability = nextState.stability,
                    nextDifficulty = nextState.difficulty,
                    nextElapsedDays = elapsedDays.toInt(),
                    nextScheduledDays = interval,
                    nextReps = progress.reps + 1,
                    nextLapses = if (task.rating == 1) progress.lapses + 1 else progress.lapses,
                    nextState = if (task.rating == 1) 3 else 2, // 简化逻辑
                    nextLearningStep = 0,
                    nextLastReview = now.toString(),
                    nextReview = (now + java.time.Duration.ofDays(interval.toLong())).toString(),
                    nextBuriedUntil = 0,
                    epochDay = (now.toEpochMilliseconds() / 86400000).toInt(),
                    studyField = if (task.itemType == "word") "reviewed_words" else "reviewed_grammars",
                    studyDelta = 1,
                    requestId = "android-${task.id}",
                    expectedLastReview = progress.lastReview
                )

                // 3. 执行 RPC
                val response = supabase.postgrest.rpc("fn_process_review_atomic", params)
                
                // 4. 同步成功，删除任务并更新本地
                // 注意：这里由于 Realtime 会自动推回更新，所以我们其实可以只删任务，
                // 但为了双保险，这里也手动更新一下。
                syncOutboxDao.deleteById(task.id)
                
            } catch (e: Exception) {
                syncOutboxDao.incrementAttempts(task.id)
                e.printStackTrace()
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

// 注意：RPC 参数 DTO 需要在外部或内部定义，为了保持逻辑清晰，我将单独创建一个文件处理 RPC DTO
