package com.jian.nemo.core.data.repository

import android.util.Log
import com.jian.nemo.core.domain.repository.ContentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 词库云更新：从 Supabase Storage 拉取 content_version 与 word/grammar JSON
 *
 * 约定：桶名 [CONTENT_BUCKET]，文件 content_version.json、word/N1.json～N5.json、grammar/N1.json～N5.json
 */
@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json
) : ContentRepository {

    override suspend fun getRemoteContentVersion(): Int? = withContext(Dispatchers.IO) {
        try {
            val url = supabase.storage.from(CONTENT_BUCKET).publicUrl(CONTENT_VERSION_FILE)
            val bytes = URL(url).openStream().use { it.readBytes() }
            val dto = json.decodeFromString<ContentVersionDto>(bytes.decodeToString())
            dto.version
        } catch (e: Exception) {
            Log.w(TAG, "getRemoteContentVersion failed: ${e.message}")
            null
        }
    }

    override suspend fun downloadWordJson(level: String): String? = withContext(Dispatchers.IO) {
        try {
            val path = "word/$level.json"
            val url = supabase.storage.from(CONTENT_BUCKET).publicUrl(path)
            URL(url).openStream().use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            Log.w(TAG, "downloadWordJson($level) failed: ${e.message}")
            null
        }
    }

    override suspend fun downloadGrammarJson(level: String): String? = withContext(Dispatchers.IO) {
        try {
            val path = "grammar/$level.json"
            val url = supabase.storage.from(CONTENT_BUCKET).publicUrl(path)
            URL(url).openStream().use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            Log.w(TAG, "downloadGrammarJson($level) failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ContentRepository"
        const val CONTENT_BUCKET = "content"
        const val CONTENT_VERSION_FILE = "content_version.json"
    }
}

@Serializable
private data class ContentVersionDto(
    @SerialName("version") val version: Int
)
