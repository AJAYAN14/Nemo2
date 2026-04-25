package com.jian.nemo2.core.data.remote

import com.jian.nemo2.core.domain.model.LearningSession
import com.jian.nemo2.core.data.remote.model.LearningSessionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRemoteDataSourceImpl @Inject constructor(
    private val supabase: SupabaseClient
) : SessionRemoteDataSource {

    private val table = supabase.postgrest["learning_sessions"]

    override suspend fun getSession(itemType: String, level: String): LearningSession? {
        val user = supabase.auth.currentUserOrNull() ?: return null

        return try {
            val response = table.select {
                filter {
                    eq("user_id", user.id)
                    eq("item_type", itemType)
                    eq("level", level)
                }
            }.decodeSingleOrNull<LearningSessionDto>()

            response?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remote session", e)
            null
        }
    }

    override suspend fun saveSession(session: LearningSession) {
        val user = supabase.auth.currentUserOrNull() ?: return

        try {
            val stepsJson = JsonObject(session.steps.mapValues { JsonPrimitive(it.value) })

            val dto = LearningSessionDto(
                userId = user.id,
                itemType = session.itemType,
                level = session.level,
                itemIds = session.itemIds,
                currentIndex = session.currentIndex,
                steps = stepsJson,
                waitingUntil = session.waitingUntil
            )

            table.upsert(dto) {
                onConflict = "user_id,item_type,level"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save remote session", e)
        }
    }

    override suspend fun updateCurrentIndex(itemType: String, level: String, currentIndex: Int) {
        val user = supabase.auth.currentUserOrNull() ?: return

        try {
            table.update({
                set("current_index", currentIndex)
                // Use built-in now() for updated_at by leaving it blank so database default trigger runs
                // Actually supabase postgres might not have a trigger unless we added it, but let's just update index for now
            }) {
                filter {
                    eq("user_id", user.id)
                    eq("item_type", itemType)
                    eq("level", level)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update current index", e)
        }
    }

    override suspend fun clearSession(itemType: String, level: String) {
        val user = supabase.auth.currentUserOrNull() ?: return

        try {
            table.delete {
                filter {
                    eq("user_id", user.id)
                    eq("item_type", itemType)
                    eq("level", level)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear remote session", e)
        }
    }

    companion object {
        private const val TAG = "SessionRemoteDS"
    }
}
