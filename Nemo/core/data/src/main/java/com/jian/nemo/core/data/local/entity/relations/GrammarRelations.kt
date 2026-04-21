package com.jian.nemo.core.data.local.entity.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.GrammarExampleEntity
import com.jian.nemo.core.data.local.entity.GrammarUsageEntity
import com.jian.nemo.core.data.local.entity.UserProgressEntity

/**
 * 语法及其用法的关联数据类
 *
 * 用于一次性查询语法及其所有用法和例句
 */
data class GrammarWithUsages(
    @Embedded
    val grammar: GrammarEntity,

    @Relation(
        entity = GrammarUsageEntity::class,
        parentColumn = "id",
        entityColumn = "grammar_id"
    )
    val usages: List<UsageWithExamples>,

    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val state: UserProgressEntity?
)

/**
 * 用法及其例句的关联数据类
 */
data class UsageWithExamples(
    @Embedded
    val usage: GrammarUsageEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "usage_id"
    )
    val examples: List<GrammarExampleEntity>
)
