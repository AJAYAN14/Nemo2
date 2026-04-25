package com.jian.nemo.core.data.remote.model

import com.jian.nemo.core.domain.model.LearningSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.datetime.Instant

@Serializable
data class LearningSessionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("item_type") val itemType: String,
    @SerialName("level") val level: String,
    @SerialName("item_ids") val itemIds: List<Long> = emptyList(),
    @SerialName("current_index") val currentIndex: Int = 0,
    @SerialName("steps") val steps: JsonObject? = null,
    @SerialName("waiting_until") val waitingUntil: Long = 0L,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain(): LearningSession {
        // Parse steps json to map
        val parsedSteps = mutableMapOf<String, Int>()
        steps?.forEach { (key, element) ->
            if (element is JsonPrimitive && element.isString) {
                 element.content.toIntOrNull()?.let { parsedSteps[key] = it }
            } else if (element is JsonPrimitive) {
                 element.longOrNull?.toInt()?.let { parsedSteps[key] = it }
            }
        }
        
        return LearningSession(
            id = id.orEmpty(),
            userId = userId.orEmpty(),
            itemType = itemType,
            level = level,
            itemIds = itemIds,
            currentIndex = currentIndex,
            steps = parsedSteps,
            waitingUntil = waitingUntil,
            updatedAt = updatedAt?.let { try { Instant.parse(it).toEpochMilliseconds() } catch(e: Exception) { 0L } } ?: 0L
        )
    }
}
