package com.jian.nemo.core.domain.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单词云端数据传输对象
 */
@Serializable
data class WordDto(
    @SerialName("id")
    val id: Long,

    @SerialName("japanese")
    val japanese: String,

    @SerialName("hiragana")
    val hiragana: String,

    @SerialName("chinese")
    val chinese: String,

    @SerialName("level")
    val level: String,

    @SerialName("pos")
    val pos: String? = null,

    @SerialName("example_1")
    val example1: String? = null,

    @SerialName("gloss_1")
    val gloss1: String? = null,

    @SerialName("example_2")
    val example2: String? = null,

    @SerialName("gloss_2")
    val gloss2: String? = null,

    @SerialName("example_3")
    val example3: String? = null,

    @SerialName("gloss_3")
    val gloss3: String? = null,

    @SerialName("is_delisted")
    val isDelisted: Boolean = false
)

/**
 * 语法云端数据传输对象
 */
@Serializable
data class GrammarDto(
    @SerialName("id")
    val id: Long,

    @SerialName("raw_id")
    val rawId: String? = null,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("level")
    val level: String,
    
    @SerialName("is_delisted")
    val isDelisted: Boolean = false,
    
    @SerialName("content")
    val content: List<GrammarUsageDto> = emptyList()
)

@Serializable
data class GrammarUsageDto(
    @SerialName("subtype")
    val subtype: String? = null,
    
    @SerialName("connection")
    val connection: String,
    
    @SerialName("explanation")
    val explanation: String,
    
    @SerialName("notes")
    val notes: String? = null,
    
    @SerialName("examples")
    val examples: List<GrammarExampleDto> = emptyList()
)

@Serializable
data class GrammarExampleDto(
    @SerialName("sentence")
    val sentence: String,
    
    @SerialName("translation")
    val translation: String,
    
    @SerialName("source")
    val source: String? = null,
    
    @SerialName("isDialog")
    val isDialog: Boolean = false
)
