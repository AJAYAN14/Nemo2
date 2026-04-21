package com.jian.nemo.core.data.local.dao

import androidx.room.*
import com.jian.nemo.core.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert
    suspend fun insert(entity: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY id ASC")
    fun getAllFlow(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE is_syncing = 0 ORDER BY id ASC")
    suspend fun getPendingTasks(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE is_syncing = 0")
    suspend fun getPendingCount(): Int

    @Query("UPDATE sync_outbox SET is_syncing = :isSyncing WHERE id = :id")
    suspend fun setSyncingStatus(id: Long, isSyncing: Boolean)

    @Delete
    suspend fun delete(entity: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, is_syncing = 0 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)
}
