package com.jian.nemo2.core.data.service

import android.util.Log
import com.jian.nemo2.core.common.util.SyncMessageBus
import com.jian.nemo2.core.domain.repository.AuthRepository
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.domain.repository.SyncRepository
import com.jian.nemo2.core.domain.service.SyncManager
import com.jian.nemo2.core.domain.service.SyncService
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

import com.jian.nemo2.core.data.manager.SupabaseSyncManager

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
    private var lastUserId: String? = null

    init {
        scope.launch {
            authRepository.getUserFlow().collect { user ->
                if (user != null && user.id != lastUserId) {
                    Log.d(TAG, "检测到用户登录/切换: ${user.id}，触发即时同步并启动 Realtime")
                    lastUserId = user.id
                    try {
                        // 登录后立即执行一次强制同步，确保数据即时展现
                        supabaseSyncManager.performSync(user.id, force = true).collect { }
                        // performSync 内部已经调用了 startRealtimeSync，但这里可以再次确保
                        supabaseSyncManager.startRealtimeSync(user.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "登录后即时同步失败", e)
                        // 即使同步失败，也应该尝试启动 Realtime
                        supabaseSyncManager.startRealtimeSync(user.id)
                    }
                } else if (user == null) {
                    Log.d(TAG, "用户登出，停止 Realtime 同步")
                    lastUserId = null
                    supabaseSyncManager.stopRealtimeSync()
                }
            }
        }
    }

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
        Log.d(TAG, "应用进入前台，触发字典同步检查与增量进度同步")

        scope.launch {
            // 1. 全局字典同步 (Best Practice: 启动即检查，不依赖登录)
            try {
                supabaseSyncManager.performDictionarySync()
            } catch (e: Exception) {
                Log.e(TAG, "启动字典同步失败", e)
            }

            // 2. 用户进度同步 (仅已登录用户)
            val user = authRepository.getCurrentUser()
            if (user != null) {
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
