package com.jian.nemo2.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 语法例句实体
 *
 * 存储语法用法的例句信息
 * 一个用法可以有多个例句
 *
 * 关联: GrammarUsageEntity (1:N)
 */
@Entity(
    tableName = "grammar_examples",
    foreignKeys = [
        ForeignKey(
            entity = GrammarUsageEntity::class,
            parentColumns = ["id"],
            childColumns = ["usage_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["usage_id"])]
)
data class GrammarExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * 关联的用法ID
     */
    @ColumnInfo(name = "usage_id")
    val usageId: Int,

    /**
     * 例句（日文）
     * 包含假名标注，格式：汉字[假名]
     */
    val sentence: String,

    /**
     * 翻译（中文）
     */
    val translation: String,

    /**
     * 来源（可选）
     * 例如："2010年12月真题"
     */
    val source: String?,

    /**
     * 是否为对话
     */
    @ColumnInfo(name = "is_dialog")
    val isDialog: Boolean,

    /**
     * 例句顺序（用于排序）
     */
    @ColumnInfo(name = "example_order")
    val exampleOrder: Int = 0
)
