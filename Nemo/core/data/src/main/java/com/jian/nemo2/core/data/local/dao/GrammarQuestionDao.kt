package com.jian.nemo2.core.data.local.dao

import androidx.room.*
import com.jian.nemo2.core.data.local.entity.GrammarQuestionEntity

@Dao
interface GrammarQuestionDao {
    @Query("SELECT * FROM grammar_questions WHERE id = :id")
    suspend fun getById(id: String): GrammarQuestionEntity?

    @Query("SELECT * FROM grammar_questions WHERE target_grammar_id = :grammarId")
    suspend fun getByGrammarId(grammarId: String): List<GrammarQuestionEntity>

    @Query("SELECT * FROM grammar_questions WHERE id LIKE :levelPrefix || '%'")
    suspend fun getByLevel(levelPrefix: String): List<GrammarQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: GrammarQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<GrammarQuestionEntity>)

    @Query("DELETE FROM grammar_questions WHERE target_grammar_id = :grammarId")
    suspend fun deleteByGrammarId(grammarId: String)

    @Query("DELETE FROM grammar_questions")
    suspend fun clearAll()
}
