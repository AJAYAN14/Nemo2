package com.jian.nemo2.core.data.local.util

import androidx.room.TypeConverter
import com.jian.nemo2.core.domain.model.GrammarQuestionType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromGrammarQuestionType(value: GrammarQuestionType): String {
        return value.name
    }

    @TypeConverter
    fun toGrammarQuestionType(value: String): GrammarQuestionType {
        return GrammarQuestionType.valueOf(value)
    }
}
