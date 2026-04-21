package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jian.nemo.core.domain.model.GrammarQuestionType

@Entity(tableName = "grammar_questions")
data class GrammarQuestionEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "target_grammar_id")
    val targetGrammarId: String,
    
    @ColumnInfo(name = "target_usage_index")
    val targetUsageIndex: Int,
    
    val type: GrammarQuestionType,
    val question: String,
    val options: List<String>,
    
    @ColumnInfo(name = "correct_index")
    val correctIndex: Int,
    
    val explanation: String?
)
