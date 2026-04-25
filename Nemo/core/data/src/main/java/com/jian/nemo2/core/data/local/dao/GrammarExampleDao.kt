package com.jian.nemo2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jian.nemo2.core.data.local.entity.GrammarExampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 语法例句数据访问对象
 */
@Dao
interface GrammarExampleDao {

    /**
     * 批量插入例句
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(examples: List<GrammarExampleEntity>)

    /**
     * 根据用法ID查询所有例句
     */
    @Query("SELECT * FROM grammar_examples WHERE usage_id = :usageId ORDER BY example_order")
    fun getExamplesByUsageId(usageId: Int): Flow<List<GrammarExampleEntity>>

    /**
     * 根据用法ID删除所有例句
     */
    @Query("DELETE FROM grammar_examples WHERE usage_id = :usageId")
    suspend fun deleteByUsageId(usageId: Int)

    /**
     * 批量删除指定用法ID列表的所有例句 (性能优化)
     */
    @Query("DELETE FROM grammar_examples WHERE usage_id IN (:usageIds)")
    suspend fun deleteByUsageIds(usageIds: List<Int>)

    /**
     * 清空所有例句
     */
    @Query("DELETE FROM grammar_examples")
    suspend fun deleteAll()

    /**
     * 获取例句总数（用于数据导入验证）
     */
    @Query("SELECT COUNT(*) FROM grammar_examples")
    suspend fun getExampleCount(): Int
}
