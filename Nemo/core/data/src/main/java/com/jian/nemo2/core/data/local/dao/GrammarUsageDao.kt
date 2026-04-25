package com.jian.nemo2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jian.nemo2.core.data.local.entity.GrammarUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 语法用法数据访问对象
 */
@Dao
interface GrammarUsageDao {

    /**
     * 批量插入用法
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<GrammarUsageEntity>): List<Long>

    /**
     * 根据语法ID查询所有用法
     */
    @Query("SELECT * FROM grammar_usages WHERE grammar_id = :grammarId ORDER BY usage_order")
    fun getUsagesByGrammarId(grammarId: Long): Flow<List<GrammarUsageEntity>>

    /**
     * 根据语法ID删除所有用法
     */
    @Query("DELETE FROM grammar_usages WHERE grammar_id = :grammarId")
    suspend fun deleteByGrammarId(grammarId: Long)

    /**
     * 批量删除指定语法ID列表的所有用法 (性能优化)
     */
    @Query("DELETE FROM grammar_usages WHERE grammar_id IN (:grammarIds)")
    suspend fun deleteByGrammarIds(grammarIds: List<Long>)

    /**
     * 清空所有用法
     */
    @Query("DELETE FROM grammar_usages")
    suspend fun deleteAll()

    /**
     * 获取用法总数（用于数据导入验证）
     */
    @Query("SELECT COUNT(*) FROM grammar_usages")
    suspend fun getUsageCount(): Int
}
