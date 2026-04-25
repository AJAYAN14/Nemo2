package com.jian.nemo2.core.data.local.dao

import androidx.room.*
import com.jian.nemo2.core.data.local.entity.SyncMetadataEntity

/**
 * 同步元数据 DAO
 *
 * 管理同步版本号、最后同步时间等信息
 */
@Dao
interface SyncMetadataDao {

    /**
     * 获取同步元数据 (单例,永远只有一条记录)
     */
    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    suspend fun get(): SyncMetadataEntity?

    /**
     * 插入或更新同步元数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    /**
     * 更新同步版本号和时间
     * @param version 新的同步版本号
     * @param time 同步时间戳
     * @param updatedAt 更新时间戳
     */
    @Query("""
        UPDATE sync_metadata
        SET sync_version = :version,
            last_sync_time = :time,
            updated_at = :updatedAt
        WHERE id = 1
    """)
    suspend fun updateVersion(
        version: Int,
        time: Long,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    /**
     * 删除同步元数据 (用于账号切换时清空)
     */
    @Query("DELETE FROM sync_metadata WHERE id = 1")
    suspend fun clear()
}
