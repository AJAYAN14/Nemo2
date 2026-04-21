package com.jian.nemo.core.data.service

import android.util.Log
import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.common.util.SyncEvent
import com.jian.nemo.core.common.util.SyncMessageBus
import com.jian.nemo.core.domain.repository.AuthRepository
import com.jian.nemo.core.domain.repository.SettingsRepository
import com.jian.nemo.core.domain.repository.SyncRepository
import com.jian.nemo.core.domain.service.SyncManager
import com.jian.nemo.core.domain.service.SyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

import com.jian.nemo.core.data.manager.SupabaseSyncManager

/**
 * 同步服务实现
 * 负责核心业务决策：是否开启同步、同步频率检查等。
 */
@Singleton
class SyncServiceImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val syncMessageBus: SyncMessageBus,
    private val supabaseSyncManager: SupabaseSyncManager
) : SyncService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SyncServiceImpl"
        private const val MIN_SYNC_INTERVAL = 60 * 1000L // 1分钟
    }

    override fun onLearningCompleted() {
        // [Native Mirror] 学习进度通过 Realtime 自动同步，不再需要手动触发 WorkManager
    }

    override fun onTestCompleted() {
        // [Native Mirror] 学习进度通过 Realtime 自动同步，不再需要手动触发 WorkManager
    }

    override fun onAppForeground() {
        Log.d(TAG, "应用进入前台，触发增量同步拉取最新进度")

        scope.launch {
            val user = authRepository.getCurrentUser()
            if (user != null) {
                // 冷启动/切前台时触发增量同步 (降低服务器压力，加快启动速度)
                try {
                    supabaseSyncManager.performSync(user.id, force = false).collect { }
                } catch (e: Exception) {
                    Log.e(TAG, "前台拉取增量同步失败", e)
                }
            }
        }
    }

    override fun onAppBackground() {
        // [Native Mirror] 后台处理逻辑已简化，主要依赖下次唤醒时的全量同步
    }
}
