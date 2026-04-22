package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 语法用法实体
 *
 * 存储一个语法点的具体用法信息
 * 一个语法点可以有多个用法（如"～あがる"有"完成"和"向上"两种用法）
 *
 * 关联: GrammarEntity (1:N)
 */
@Entity(
    tableName = "grammar_usages",
    foreignKeys = [
        ForeignKey(
            entity = GrammarEntity::class,
            parentColumns = ["id"],
            childColumns = ["grammar_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["grammar_id"])]
)
data class GrammarUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * 关联的语法ID
     */
    @ColumnInfo(name = "grammar_id")
    val grammarId: Long,

    /**
     * 用法子类型（可选）
     * 例如："完成"、"向上"、"强调" 等
     */
    val subtype: String?,

    /**
     * 接续方式
     * 例如："动词「ます形」＋あがる"
     */
    val connection: String,

    /**
     * 说明/解释
     * 例如：""……完成了""……好了"，表示动作的完成。"
     */
    val explanation: String,

    /**
     * 注意事项（可选）
     */
    val notes: String?,

    /**
     * 用法顺序（用于排序）
     */
    @ColumnInfo(name = "usage_order")
    val usageOrder: Int = 0
)
