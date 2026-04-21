package com.jian.nemo.core.data.repository

import com.jian.nemo.core.domain.model.AppNotification
import com.jian.nemo.core.domain.repository.SettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程通知 Repository
 *
 * 从 Supabase notifications 表获取通知，与本地已读记录对比
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val settingsRepository: SettingsRepository
) {

    /**
     * 获取第一条未读且有效的通知
     *
     * @return 未读通知，若无则返回 null
     */
    suspend fun getActiveNotification(): AppNotification? = withContext(Dispatchers.IO) {
        try {
            val all = supabaseClient.postgrest["notifications"]
                .select {
                    filter {
                        eq("active", true)
                    }
                }
                .decodeList<AppNotification>()

            val dismissed = settingsRepository.dismissedNotificationIdsFlow.first()
            all.firstOrNull { it.id !in dismissed }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 标记通知为已读
     */
    suspend fun dismiss(id: String) {
        settingsRepository.addDismissedNotificationId(id)
    }
}
