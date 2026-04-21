package com.jian.nemo.core.data.local.dao

import androidx.room.*
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {

    @Query("SELECT * FROM user_progress WHERE id = :id")
    suspend fun getById(id: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE item_type = :itemType AND item_id = :itemId")
    suspend fun getByItem(itemType: String, itemId: Int): UserProgressEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: UserProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(progressList: List<UserProgressEntity>)

    @Query("DELETE FROM user_progress WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_progress")
    suspend fun deleteAll()
}
