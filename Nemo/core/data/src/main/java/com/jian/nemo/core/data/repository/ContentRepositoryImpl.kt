package com.jian.nemo.core.data.repository

import android.util.Log
import com.jian.nemo.core.domain.repository.ContentRepository
import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : ContentRepository {

    override suspend fun getRemoteContentVersion(): Int? = withContext(Dispatchers.IO) {
        try {
            // 从 sync_meta 表获取内容版本
            val meta = supabase.postgrest["sync_meta"]
                .select()
                .decodeSingleOrNull<ContentMetaDto>()
            meta?.contentVersion
        } catch (e: Exception) {
            Log.w(TAG, "getRemoteContentVersion failed: ${e.message}")
            null
        }
    }

    override suspend fun fetchRemoteWords(level: String): List<WordDto> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["dictionary_words"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("level", level.uppercase())
                    }
                }.decodeList<WordDto>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchRemoteWords($level) failed", e)
            emptyList()
        }
    }

    override suspend fun fetchRemoteGrammars(level: String): List<GrammarDto> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["dictionary_grammars"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("level", level.uppercase())
                    }
                }.decodeList<GrammarDto>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchRemoteGrammars($level) failed", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ContentRepository"
    }
}

@Serializable
private data class ContentMetaDto(
    @SerialName("content_version") val contentVersion: Int
)
