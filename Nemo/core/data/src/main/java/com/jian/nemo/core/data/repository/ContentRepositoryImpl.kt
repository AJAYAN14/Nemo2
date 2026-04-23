package com.jian.nemo.core.data.repository

import android.util.Log
import com.jian.nemo.core.domain.repository.ContentRepository
import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto
import com.jian.nemo.core.domain.model.dto.GrammarTestQuestionDto
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
        val allItems = mutableListOf<WordDto>()
        var offset = 0
        val pageSize = 1000
        try {
            while (true) {
                val batch = supabase.postgrest["dictionary_words"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("level", level.uppercase())
                        }
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<WordDto>()
                
                allItems.addAll(batch)
                Log.d(TAG, "fetchRemoteWords($level): fetched ${batch.size} items (total: ${allItems.size})")
                
                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchRemoteWords($level) failed", e)
            allItems
        }
    }

    override suspend fun fetchRemoteGrammars(level: String): List<GrammarDto> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<GrammarDto>()
        var offset = 0
        val pageSize = 1000
        try {
            while (true) {
                val batch = supabase.postgrest["dictionary_grammars"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("level", level.uppercase())
                        }
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<GrammarDto>()
                
                allItems.addAll(batch)
                Log.d(TAG, "fetchRemoteGrammars($level): fetched ${batch.size} items (total: ${allItems.size})")
                
                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchRemoteGrammars($level) failed", e)
            allItems
        }
    }

    override suspend fun fetchRemoteGrammarQuestions(level: String): List<GrammarTestQuestionDto> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<GrammarTestQuestionDto>()
        var offset = 0
        val pageSize = 1000
        try {
            val prefix = "GT_${level.uppercase()}_"
            while (true) {
                val batch = supabase.postgrest["grammar_questions"]
                    .select(columns = Columns.ALL) {
                        filter {
                            ilike("id", "$prefix%")
                        }
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<GrammarTestQuestionDto>()
                
                allItems.addAll(batch)
                Log.d(TAG, "fetchRemoteGrammarQuestions($level): fetched ${batch.size} items (total: ${allItems.size})")
                
                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchRemoteGrammarQuestions($level) failed", e)
            allItems
        }
    }

    // ========== 全量拉取实现 (性能优化) ==========

    override suspend fun fetchAllRemoteWords(): List<WordDto> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<WordDto>()
        var offset = 0
        val pageSize = 1000
        try {
            while (true) {
                val batch = supabase.postgrest["dictionary_words"]
                    .select(columns = Columns.ALL) {
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<WordDto>()

                allItems.addAll(batch)
                Log.d(TAG, "fetchAllRemoteWords: fetched ${batch.size} items (total: ${allItems.size})")

                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllRemoteWords failed", e)
            allItems
        }
    }

    override suspend fun fetchAllRemoteGrammars(): List<GrammarDto> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<GrammarDto>()
        var offset = 0
        val pageSize = 1000
        try {
            while (true) {
                val batch = supabase.postgrest["dictionary_grammars"]
                    .select(columns = Columns.ALL) {
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<GrammarDto>()

                allItems.addAll(batch)
                Log.d(TAG, "fetchAllRemoteGrammars: fetched ${batch.size} items (total: ${allItems.size})")

                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllRemoteGrammars failed", e)
            allItems
        }
    }

    override suspend fun fetchAllRemoteGrammarQuestions(): List<GrammarTestQuestionDto> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<GrammarTestQuestionDto>()
        var offset = 0
        val pageSize = 1000
        try {
            while (true) {
                val batch = supabase.postgrest["grammar_questions"]
                    .select(columns = Columns.ALL) {
                        range(offset.toLong(), (offset + pageSize - 1).toLong())
                    }.decodeList<GrammarTestQuestionDto>()

                allItems.addAll(batch)
                Log.d(TAG, "fetchAllRemoteGrammarQuestions: fetched ${batch.size} items (total: ${allItems.size})")

                if (batch.size < pageSize) break
                offset += pageSize
            }
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllRemoteGrammarQuestions failed", e)
            allItems
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

