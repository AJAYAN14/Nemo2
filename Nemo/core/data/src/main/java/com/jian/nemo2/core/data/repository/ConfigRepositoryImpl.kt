package com.jian.nemo2.core.data.repository

import android.content.Context
import com.jian.nemo2.core.domain.model.AppUpdateConfig
import com.jian.nemo2.core.domain.repository.ConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConfigRepository 的 Supabase 实现
 */
@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : ConfigRepository {

    override suspend fun getUpdateConfig(): AppUpdateConfig? = withContext(Dispatchers.IO) {
        try {
            // 从 app_config 表中获取配置，按 version_code 倒序排列取最新一条
            supabaseClient.postgrest["app_config"]
                .select {
                    order("version_code", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<AppUpdateConfig>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1 // 默认降级版本号
        }
    }
}
