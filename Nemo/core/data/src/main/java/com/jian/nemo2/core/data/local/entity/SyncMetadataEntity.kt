package com.jian.nemo2.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步元数据Entity
 *
 * 用于存储设备ID、同步版本号、最后同步时间等信息
 * 单例表,永远只有一条记录 (id = 1)
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val id: Int = 1,  // 固定为1,确保单例

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "sync_version")
    val syncVersion: Int,

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
