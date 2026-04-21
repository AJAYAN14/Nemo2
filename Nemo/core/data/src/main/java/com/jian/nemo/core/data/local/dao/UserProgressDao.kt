package com.jian.nemo.core.data.local.dao

import androidx.room.*
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Delete
    suspend fun delete(progress: UserProgressEntity)

    @Query("SELECT * FROM user_progress")
    suspend fun getAllProgressSync(): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress WHERE id = :id")
    suspend fun getById(id: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE item_type = :itemType AND item_id = :itemId")
    suspend fun getProgressByItemId(itemId: String, itemType: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE item_type = :itemType AND item_id = :itemId LIMIT 1")
    suspend fun getByItem(itemType: String, itemId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE item_id = :itemId AND item_type = :itemType")
    fun getProgressByItemIdFlow(itemId: String, itemType: String): Flow<UserProgressEntity?>

    /**
     * 获取所有到期的项目 (镜像 Web 端的 getDueItems 逻辑)
     */
    @Query("""
        SELECT * FROM user_progress 
        WHERE state IN (0, 1, 2, 3) 
        AND next_review <= :nowWithBuffer 
        AND buried_until <= :currentEpochDay
        ORDER BY next_review ASC, id ASC
    """)
    fun getDueItemsFlow(nowWithBuffer: String, currentEpochDay: Long): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE is_favorite = 1")
    fun getFavoriteItemsFlow(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE state = -1")
    fun getLeechItemsFlow(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE item_type = :itemType AND item_id IN (:itemIds)")
    suspend fun getProgressByItemIds(itemIds: List<String>, itemType: String): List<UserProgressEntity>

    @Query("UPDATE user_progress SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE item_type = :itemType AND item_id = :itemId")
    suspend fun updateFavoriteStatus(itemId: String, itemType: String, isFavorite: Boolean, updatedAt: String)

    @Query("UPDATE user_progress SET state = :state, updated_at = :updatedAt WHERE item_type = :itemType AND item_id = :itemId")
    suspend fun updateProgressState(itemId: String, itemType: String, state: Int, updatedAt: String)

    @Query("UPDATE user_progress SET state = 0, stability = 0.0, difficulty = 0.0, elapsed_days = 0, scheduled_days = 0, reps = 0, lapses = 0, last_review = NULL, next_review = :nowIso, updated_at = :nowIso WHERE item_type = :itemType")
    suspend fun resetAllProgress(itemType: String, nowIso: String)

    @Query("UPDATE user_progress SET is_favorite = 0, updated_at = :nowIso WHERE item_type = :itemType")
    suspend fun clearAllFavorites(itemType: String, nowIso: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: UserProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progressList: List<UserProgressEntity>)

    @Query("DELETE FROM user_progress WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_progress")
    suspend fun deleteAll()

    @Query("SELECT * FROM user_progress WHERE item_type = :itemType")
    suspend fun getByItemTypeSync(itemType: String): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress WHERE item_id = :itemId AND item_type = :itemType LIMIT 1")
    suspend fun getByItemIdSync(itemId: String, itemType: String): UserProgressEntity?

    @Query("DELETE FROM user_progress WHERE item_id IN (:itemIds) AND item_type = :itemType")
    suspend fun deleteByItemIds(itemIds: List<String>, itemType: String)
}
