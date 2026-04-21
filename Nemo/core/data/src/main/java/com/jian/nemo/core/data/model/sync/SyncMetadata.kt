package com.jian.nemo.core.data.model.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.jian.nemo.core.data.local.NemoDatabase
import com.jian.nemo.core.data.local.dao.SyncMetadataDao
import com.jian.nemo.core.data.local.entity.SyncMetadataEntity
import com.jian.nemo.core.common.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * 同步元数据管理器
 *
 * 管理设备ID、同步版本号、服务器时间偏移等信息
 */
@Singleton
class SyncMetadata @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: NemoDatabase
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_metadata", Context.MODE_PRIVATE)

    private val dao: SyncMetadataDao = database.syncMetadataDao()

    companion object {
        private const val TAG = "SyncMetadata"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_TIME_OFFSET = "server_time_offset"
    }

    private var cachedEntity: SyncMetadataEntity? = null

    /**
     * 设备唯一ID
     */
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = "${Build.MODEL}-${UUID.randomUUID().toString().substring(0, 8)}"
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
                Log.d(TAG, "生成新设备ID: $id")
            }
            return id!!
        }

    /**
     * 服务器时间偏移 (毫秒)
     * compensatedTime = currentTime + offset
     */
    var serverTimeOffset: Long
        get() = prefs.getLong(KEY_SERVER_TIME_OFFSET, 0L)
        set(value) {
            prefs.edit().putLong(KEY_SERVER_TIME_OFFSET, value).apply()
            DateTimeUtils.setServerTimeOffset(value)
        }

    /**
     * 更新服务器时间偏移
     * 基于服务器真实时间与本地系统时间的差值
     */
    fun updateServerTimeOffset(serverTime: Long) {
        val localTime = System.currentTimeMillis()
        val offset = serverTime - localTime
        serverTimeOffset = offset
        Log.i(TAG, "时间校准完成: Server=$serverTime, Local=$localTime, Offset=$offset ms")
    }

    /**
     * 获取当前同步版本号
     */
    suspend fun getSyncVersion(): Int {
        return getOrInitialize().syncVersion
    }

    /**
     * 获取上次同步时间
     */
    suspend fun getLastSyncTime(): Long {
        return getOrInitialize().lastSyncTime
    }

    /**
     * 更新同步版本号和时间
     * 注意: 应在 database.withTransaction {} 中调用, 确保原子性
     *
     * @param version 新的同步版本号
     * @param time 同步时间戳, 默认为补偿后的当前服务器时间
     */
    suspend fun updateSyncVersion(
        version: Int,
        time: Long = DateTimeUtils.getCurrentCompensatedMillis()
    ) {
        val rowsUpdated = dao.updateVersion(version, time)
        if (rowsUpdated == 0) {
            Log.w(TAG, "更新版本号失败, 插入首条记录")
            dao.upsert(
                SyncMetadataEntity(
                    id = 1,
                    deviceId = deviceId,
                    syncVersion = version,
                    lastSyncTime = time
                )
            )
        }
        cachedEntity = cachedEntity?.copy(syncVersion = version, lastSyncTime = time)
        Log.d(TAG, "更新同步版本号: $version, 时间: $time")
    }

    suspend fun clear() {
        dao.clear()
        cachedEntity = null
        Log.d(TAG, "清空同步元数据")
    }

    /**
     * 获取或初始化同步元数据
     */
    private suspend fun getOrInitialize(): SyncMetadataEntity {
        cachedEntity?.let { return it }

        var entity = dao.get()
        if (entity == null) {
            Log.d(TAG, "同步元数据不存在, 执行初始化")
            entity = SyncMetadataEntity(
                id = 1,
                deviceId = deviceId,
                syncVersion = 1,
                lastSyncTime = 0
            )
            dao.upsert(entity)
        }

        cachedEntity = entity
        return entity
    }
}
