package com.jian.nemo.core.data.manager

import android.content.Context
import android.util.Log

import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.data.local.NemoDatabase
import com.jian.nemo.core.data.local.entity.GrammarWrongAnswerEntity
import com.jian.nemo.core.data.local.entity.WrongAnswerEntity
import com.jian.nemo.core.data.local.entity.UserProgressEntity
import com.jian.nemo.core.data.local.entity.WordEntity
import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.TestRecordEntity
import com.jian.nemo.core.data.local.entity.StudyRecordEntity
import com.jian.nemo.core.data.local.entity.FavoriteQuestionEntity
import com.jian.nemo.core.domain.model.*
import com.jian.nemo.core.domain.repository.SettingsRepository
import com.jian.nemo.core.domain.repository.DataExportRepository
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import android.util.Base64OutputStream
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString


/**
 * 导入结果
 */
data class ImportResult(
    val success: Boolean,
    val message: String
)

/**
 * 导入结果（带同步报告）
 */
data class ImportResultWithReport(
    val success: Boolean,
    val message: String,
    val report: SyncReport?
)

/**
 * 数据导出/导入管理器
 *
 * 负责将本地数据库数据导出为 JSON 格式（兼容旧版 Nemo），
 * 以及从 JSON 文件导入数据并恢复到本地数据库。
 */
@Singleton
class DataExportManager @Inject constructor(
    private val database: NemoDatabase,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : DataExportRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }



    /**
     * 流式导出数据到文件
     *
     * 采用流式处理管道：
     * Database Cursor -> JsonWriter -> OutputStreamWriter -> GZIPOutputStream -> Base64OutputStream -> FileOutputStream
     *
     * @param userId 用户ID
     * @param outputFile 输出文件
     * @return 输出文件
     */
    suspend fun exportDataToFile(userId: String = "default_user", outputFile: java.io.File): java.io.File = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始流式导出数据到文件: ${outputFile.absolutePath}")

        val totalStudyDays = settingsRepository.totalStudyDaysFlow.first()
        val dailyStreak = settingsRepository.dailyStreakFlow.first()
        val maxTestStreak = settingsRepository.maxTestStreakFlow.first()
        val testStreak = settingsRepository.testStreakFlow.first()

        var wordCount = 0
        var grammarCount = 0

        try {
            database.withTransaction {
                FileOutputStream(outputFile).use { fileOs ->
                    Base64OutputStream(fileOs, Base64.NO_WRAP).use { base64Os ->
                        GZIPOutputStream(base64Os).use { gzipOs ->
                            java.io.BufferedWriter(OutputStreamWriter(gzipOs, Charsets.UTF_8)).use { writer ->

                                writer.write("{")

                                writer.write("\"exportInfo\":")
                                writer.write(json.encodeToString(ExportInfo()))
                                writer.write(",")

                                writer.write("\"userData\":{")

                                writer.write("\"profile\":")
                                writer.write(json.encodeToString(UserProfile(userId, "User", "")))
                                writer.write(",")

                                writer.write("\"settings\":null,")

                                writer.write("\"wordProgress\":[")
                                var isFirstWord = true
                                database.wordDao().getExportWordsCursor().use { cursor ->
                                    val idIdx = cursor.getColumnIndexOrThrow("id")
                                    val repIdx = cursor.getColumnIndexOrThrow("repetitionCount")
                                    val stabIdx = cursor.getColumnIndexOrThrow("stability")
                                    val diffIdx = cursor.getColumnIndexOrThrow("difficulty")
                                    val intIdx = cursor.getColumnIndexOrThrow("interval")
                                    val nextReviewIdx = cursor.getColumnIndexOrThrow("nextReviewDate")
                                    val favIdx = cursor.getColumnIndexOrThrow("isFavorite")
                                    val skipIdx = cursor.getColumnIndexOrThrow("isSkipped")
                                    val lastModIdx = cursor.getColumnIndexOrThrow("lastModifiedTime")
                                    val lastRevIdx = cursor.getColumnIndexOrThrow("lastReviewedDate")
                                    val firstLearnIdx = cursor.getColumnIndexOrThrow("firstLearnedDate")
                                    val japIdx = cursor.getColumnIndexOrThrow("japanese")
                                    val hiraIdx = cursor.getColumnIndexOrThrow("hiragana")
                                    val chiIdx = cursor.getColumnIndexOrThrow("chinese")
                                    val lvlIdx = cursor.getColumnIndexOrThrow("level")
                                    val delIdx = cursor.getColumnIndexOrThrow("isDeleted")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstWord) writer.write(",")
                                        isFirstWord = false

                                        val word = WordProgress(
                                            wordId = cursor.getInt(idIdx),
                                            srsLevel = cursor.getInt(repIdx),
                                            stability = cursor.getFloat(stabIdx),
                                            difficulty = cursor.getFloat(diffIdx),
                                            interval = cursor.getInt(intIdx),
                                            nextReviewDate = cursor.getLong(nextReviewIdx),
                                            isFavorite = cursor.getInt(favIdx) == 1,
                                            isSkipped = cursor.getInt(skipIdx) == 1,
                                            lastModifiedTime = cursor.getLong(lastModIdx),
                                            lastReviewedDate = if (cursor.isNull(lastRevIdx)) null else cursor.getLong(lastRevIdx),
                                            firstLearnedDate = if (cursor.isNull(firstLearnIdx)) null else cursor.getLong(firstLearnIdx),
                                            isDeleted = cursor.getInt(delIdx) == 1,
                                            deletedTime = cursor.getLong(cursor.getColumnIndexOrThrow("deletedTime")),
                                            japanese = cursor.getString(japIdx),
                                            hiragana = cursor.getString(hiraIdx),
                                            chinese = cursor.getString(chiIdx),
                                            level = cursor.getString(lvlIdx)
                                        )
                                        writer.write(json.encodeToString(word))
                                        wordCount++
                                    }
                                }
                                writer.write("],")

                                writer.write("\"grammarProgress\":[")
                                var isFirstGrammar = true
                                database.grammarDao().getExportGrammarsCursor().use { cursor ->
                                    val idIdx = cursor.getColumnIndexOrThrow("id")
                                    val repIdx = cursor.getColumnIndexOrThrow("repetitionCount")
                                    val stabIdx = cursor.getColumnIndexOrThrow("stability")
                                    val diffIdx = cursor.getColumnIndexOrThrow("difficulty")
                                    val intIdx = cursor.getColumnIndexOrThrow("interval")
                                    val nextReviewIdx = cursor.getColumnIndexOrThrow("nextReviewDate")
                                    val favIdx = cursor.getColumnIndexOrThrow("isFavorite")
                                    val lastModIdx = cursor.getColumnIndexOrThrow("lastModifiedTime")
                                    val lastRevIdx = cursor.getColumnIndexOrThrow("lastReviewedDate")
                                    val firstLearnIdx = cursor.getColumnIndexOrThrow("firstLearnedDate")
                                    val gramIdx = cursor.getColumnIndexOrThrow("grammar")
                                    val lvlIdx = cursor.getColumnIndexOrThrow("grammar_level")
                                    val delIdx = cursor.getColumnIndexOrThrow("isDeleted")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstGrammar) writer.write(",")
                                        isFirstGrammar = false

                                        val grammar = GrammarProgress(
                                            grammarId = cursor.getInt(idIdx),
                                            srsLevel = cursor.getInt(repIdx),
                                            stability = cursor.getFloat(stabIdx),
                                            difficulty = cursor.getFloat(diffIdx),
                                            interval = cursor.getInt(intIdx),
                                            nextReviewDate = cursor.getLong(nextReviewIdx),
                                            isFavorite = cursor.getInt(favIdx) == 1,
                                            lastModifiedTime = cursor.getLong(lastModIdx),
                                            lastReviewedDate = if (cursor.isNull(lastRevIdx)) null else cursor.getLong(lastRevIdx),
                                            firstLearnedDate = if (cursor.isNull(firstLearnIdx)) null else cursor.getLong(firstLearnIdx),
                                            isDeleted = cursor.getInt(delIdx) == 1,
                                            deletedTime = cursor.getLong(cursor.getColumnIndexOrThrow("deletedTime")),
                                            grammar = cursor.getString(gramIdx),
                                            grammarLevel = cursor.getString(lvlIdx)
                                        )
                                        writer.write(json.encodeToString(grammar))
                                        grammarCount++
                                    }
                                }
                                writer.write("],")

                                writer.write("\"wrongAnswers\":{")
                                writer.write("\"words\":[")
                                var isFirstWaWord = true
                                database.wrongAnswerDao().getExportWrongAnswersCursor().use { cursor ->
                                    val wIdIdx = cursor.getColumnIndexOrThrow("word_id")
                                    val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
                                    val modeIdx = cursor.getColumnIndexOrThrow("test_mode")
                                    val userAnsIdx = cursor.getColumnIndexOrThrow("user_answer")
                                    val corrAnsIdx = cursor.getColumnIndexOrThrow("correct_answer")
                                    val uuidIdx = cursor.getColumnIndexOrThrow("uuid")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstWaWord) writer.write(",")
                                        isFirstWaWord = false

                                        val item = WrongAnswerItem(
                                            wordId = cursor.getInt(wIdIdx),
                                            timestamp = cursor.getLong(tsIdx),
                                            testMode = cursor.getString(modeIdx),
                                            userAnswer = cursor.getString(userAnsIdx),
                                            correctAnswer = cursor.getString(corrAnsIdx),
                                            uuid = cursor.getString(uuidIdx)
                                        )
                                        writer.write(json.encodeToString(item))
                                    }
                                }
                                writer.write("],")

                                writer.write("\"grammars\":[")
                                var isFirstWaGrammar = true
                                database.grammarWrongAnswerDao().getExportWrongAnswersCursor().use { cursor ->
                                    val gIdIdx = cursor.getColumnIndexOrThrow("grammar_id")
                                    val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
                                    val modeIdx = cursor.getColumnIndexOrThrow("test_mode")
                                    val userAnsIdx = cursor.getColumnIndexOrThrow("user_answer")
                                    val corrAnsIdx = cursor.getColumnIndexOrThrow("correct_answer")
                                    val uuidIdx = cursor.getColumnIndexOrThrow("uuid")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstWaGrammar) writer.write(",")
                                        isFirstWaGrammar = false

                                        val item = GrammarWrongAnswerItem(
                                            grammarId = cursor.getInt(gIdIdx),
                                            timestamp = cursor.getLong(tsIdx),
                                            testMode = cursor.getString(modeIdx),
                                            userAnswer = cursor.getString(userAnsIdx),
                                            correctAnswer = cursor.getString(corrAnsIdx),
                                            uuid = cursor.getString(uuidIdx)
                                        )
                                        writer.write(json.encodeToString(item))
                                    }
                                }
                                writer.write("]")
                                writer.write("},")

                                writer.write("\"testRecords\":[")
                                var isFirstTestRecord = true
                                database.testRecordDao().getExportTestRecordsCursor().use { cursor ->
                                    val dateIdx = cursor.getColumnIndexOrThrow("date")
                                    val totIdx = cursor.getColumnIndexOrThrow("total_questions")
                                    val corrIdx = cursor.getColumnIndexOrThrow("correct_answers")
                                    val modeIdx = cursor.getColumnIndexOrThrow("test_mode")
                                    val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
                                    val uuidIdx = cursor.getColumnIndexOrThrow("uuid")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstTestRecord) writer.write(",")
                                        isFirstTestRecord = false

                                        val item = TestRecordItem(
                                            date = cursor.getLong(dateIdx),
                                            totalQuestions = cursor.getInt(totIdx),
                                            correctAnswers = cursor.getInt(corrIdx),
                                            testMode = cursor.getString(modeIdx),
                                            timestamp = cursor.getLong(tsIdx),
                                            uuid = cursor.getString(uuidIdx)
                                        )
                                        writer.write(json.encodeToString(item))
                                    }
                                }
                                writer.write("],")

                                writer.write("\"studyRecords\":[")
                                var isFirstStudyRecord = true
                                database.studyRecordDao().getExportStudyRecordsCursor().use { cursor ->
                                    val dateIdx = cursor.getColumnIndexOrThrow("date")
                                    val lwIdx = cursor.getColumnIndexOrThrow("learned_words")
                                    val lgIdx = cursor.getColumnIndexOrThrow("learned_grammars")
                                    val rwIdx = cursor.getColumnIndexOrThrow("reviewed_words")
                                    val rgIdx = cursor.getColumnIndexOrThrow("reviewed_grammars")
                                    val swIdx = cursor.getColumnIndexOrThrow("skipped_words")
                                    val sgIdx = cursor.getColumnIndexOrThrow("skipped_grammars")
                                    val tcIdx = cursor.getColumnIndexOrThrow("test_count")
                                    val tsIdx = cursor.getColumnIndexOrThrow("timestamp")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstStudyRecord) writer.write(",")
                                        isFirstStudyRecord = false

                                        val item = StudyRecordItem(
                                            date = cursor.getLong(dateIdx),
                                            learnedWords = cursor.getInt(lwIdx),
                                            learnedGrammars = cursor.getInt(lgIdx),
                                            reviewedWords = cursor.getInt(rwIdx),
                                            reviewedGrammars = cursor.getInt(rgIdx),
                                            skippedWords = cursor.getInt(swIdx),
                                            skippedGrammars = cursor.getInt(sgIdx),
                                            testCount = cursor.getInt(tcIdx),
                                            timestamp = cursor.getLong(tsIdx)
                                        )
                                        writer.write(json.encodeToString(item))
                                    }
                                }
                                writer.write("],")

                                writer.write("\"favoriteQuestions\":[")
                                var isFirstFavorite = true
                                database.favoriteQuestionDao().getExportFavoritesCursor().use { cursor ->
                                    val idIdx = cursor.getColumnIndexOrThrow("id")
                                    val grammarIdIdx = cursor.getColumnIndexOrThrow("grammar_id")
                                    val jsonIdIdx = cursor.getColumnIndexOrThrow("json_id")
                                    val questionTypeIdx = cursor.getColumnIndexOrThrow("question_type")
                                    val questionTextIdx = cursor.getColumnIndexOrThrow("question_text")
                                    val optionsJsonIdx = cursor.getColumnIndexOrThrow("options_json")
                                    val correctAnswerIdx = cursor.getColumnIndexOrThrow("correct_answer")
                                    val explanationIdx = cursor.getColumnIndexOrThrow("explanation")
                                    val tsIdx = cursor.getColumnIndexOrThrow("timestamp")

                                    while (cursor.moveToNext()) {
                                        if (!isFirstFavorite) writer.write(",")
                                        isFirstFavorite = false

                                        val item = FavoriteQuestionItem(
                                            id = cursor.getInt(idIdx),
                                            grammarId = if (cursor.isNull(grammarIdIdx)) null else cursor.getInt(grammarIdIdx),
                                            jsonId = if (cursor.isNull(jsonIdIdx)) null else cursor.getString(jsonIdIdx),
                                            questionType = cursor.getString(questionTypeIdx),
                                            questionText = cursor.getString(questionTextIdx),
                                            optionsJson = cursor.getString(optionsJsonIdx),
                                            correctAnswer = cursor.getString(correctAnswerIdx),
                                            explanation = if (cursor.isNull(explanationIdx)) null else cursor.getString(explanationIdx),
                                            timestamp = cursor.getLong(tsIdx)
                                        )
                                        writer.write(json.encodeToString(item))
                                    }
                                }
                                writer.write("],")

                                writer.write("\"studyStreak\":$dailyStreak,")
                                writer.write("\"testStreak\":$testStreak,")
                                writer.write("\"maxTestStreak\":$maxTestStreak,")
                                writer.write("\"totalStudyDays\":$totalStudyDays")

                                writer.write("}")
                                writer.write("}")
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "流式导出完成: 单词$wordCount, 语法$grammarCount")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "流式导出失败", e)
            throw e
        }
    }


    /**
     * 导入数据
     * @param dataString 可能是压缩的 Base64 字符串，也可能是原始 JSON
     */
    suspend fun importData(dataString: String): ImportResult = withContext(Dispatchers.IO) {
        var importedWords = 0
        var importedGrammars = 0
        try {
            Log.d(TAG, "开始导入数据...")

            val jsonString = if (SyncCompression.isCompressed(dataString)) {
                SyncCompression.decompress(dataString)
            } else {
                dataString
            }

            val exportData = json.decodeFromString<NemoExportData>(jsonString)
            val userData = exportData.userData

            database.withTransaction {
                val wordDao = database.wordDao()
                val userProgressDao = database.userProgressDao()
                
                // 加载全文映射
                val localWordStates = userProgressDao.getByItemTypeSync("word").associateBy { it.itemId }
                val allLocalWords = wordDao.getAllWordsSync()
                val localWordIdMap = allLocalWords.associateBy { it.id }
                val localWordSemanticGroups = allLocalWords.groupBy { "${it.level}_${it.japanese}" }

                val wordIdRedirectMap = mutableMapOf<Int, Int>()
                userData.wordProgress.forEach { remoteWord ->
                    val remoteKey = "${remoteWord.level}_${remoteWord.japanese}"
                    
                    // 确定目标本地 ID
                    val targetLocalId = when {
                        localWordIdMap.containsKey(remoteWord.wordId) -> remoteWord.wordId
                        localWordSemanticGroups.containsKey(remoteKey) -> {
                            val candidates = localWordSemanticGroups[remoteKey]!!
                            // 精确匹配 ID 或匹配 ID 偏移量 (处理旧版 ID)
                            candidates.find { it.id == remoteWord.wordId }?.id 
                                ?: candidates.find { it.id % 10000 == remoteWord.wordId % 10000 }?.id 
                                ?: candidates.first().id
                        }
                        else -> remoteWord.wordId
                    }
                    wordIdRedirectMap[remoteWord.wordId] = targetLocalId

                    if (!localWordIdMap.containsKey(targetLocalId) && 
                        !localWordSemanticGroups.containsKey(remoteKey) && 
                        !remoteWord.japanese.isNullOrBlank()) {
                        wordDao.insert(WordEntity(
                            id = targetLocalId,
                            japanese = remoteWord.japanese ?: "",
                            hiragana = remoteWord.hiragana ?: "",
                            chinese = remoteWord.chinese ?: "",
                            level = remoteWord.level ?: "N5"
                        ))
                    }

                    val localState = localWordStates[targetLocalId]
                    if (localState != null) {
                        val result = SmartSyncMerger.mergeUserProgress(localState, remoteWord)
                        if (result is SmartSyncMerger.MergeResult.RemoteUpdated) {
                            userProgressDao.insert(result.data)
                        }
                    } else {
                        userProgressDao.insert(UserProgressEntity(
                            id = "import_word_${targetLocalId}",
                            userId = "default_user",
                            itemType = "word",
                            itemId = targetLocalId,
                            reps = remoteWord.srsLevel,
                            stability = remoteWord.stability.toDouble(),
                            difficulty = remoteWord.difficulty.toDouble(),
                            elapsedDays = 0,
                            scheduledDays = remoteWord.interval,
                            lapses = 0,
                            state = if (remoteWord.isSkipped) -1 else if (remoteWord.srsLevel > 0) 2 else 0,
                            learningStep = 0,
                            nextReview = DateTimeUtils.epochDayToIso(remoteWord.nextReviewDate),
                            lastReview = remoteWord.lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
                            isFavorite = remoteWord.isFavorite,
                            buriedUntil = null,
                            updatedAt = DateTimeUtils.millisToIso(remoteWord.lastModifiedTime),
                            level = remoteWord.level ?: "N5",
                            createdAt = remoteWord.firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: DateTimeUtils.millisToIso(remoteWord.lastModifiedTime)
                        ))
                    }
                }
                importedWords = userData.wordProgress.size

                val grammarDao = database.grammarDao()
                
                // 加载全文映射，用于语义匹配
                val localGrammarStates = userProgressDao.getByItemTypeSync("grammar").associateBy { it.itemId }
                val allLocalGrammars = grammarDao.getAllGrammarsSync()
                val localGrammarIdMap = allLocalGrammars.associateBy { it.id }
                val localGrammarSemanticGroups = allLocalGrammars.groupBy { "${(it.grammarLevel ?: "N5").uppercase()}_${it.grammar}" }

                val grammarIdRedirectMap = mutableMapOf<Int, Int>()
                userData.grammarProgress.forEach { remoteGrammar ->
                    val remoteKey = "${(remoteGrammar.grammarLevel ?: "N5").uppercase()}_${remoteGrammar.grammar ?: ""}"
                    
                    // 确定目标本地 ID
                    val targetLocalId = when {
                        localGrammarIdMap.containsKey(remoteGrammar.grammarId) -> remoteGrammar.grammarId
                        localGrammarSemanticGroups.containsKey(remoteKey) -> {
                            val candidates = localGrammarSemanticGroups[remoteKey]!!
                            // 优先精确匹配 ID，否则匹配 ID 偏移量（针对旧版 ID），最后选第一个
                            candidates.find { it.id == remoteGrammar.grammarId }?.id
                                ?: candidates.find { it.id % 10000 == remoteGrammar.grammarId % 10000 }?.id
                                ?: candidates.first().id
                        }
                        else -> remoteGrammar.grammarId
                    }
                    grammarIdRedirectMap[remoteGrammar.grammarId] = targetLocalId

                    // 如果本地完全没有这个语法条目且内容不为空，则插入
                    if (!localGrammarIdMap.containsKey(targetLocalId) && 
                        !localGrammarSemanticGroups.containsKey(remoteKey) && 
                        !remoteGrammar.grammar.isNullOrBlank()) {
                        grammarDao.insert(GrammarEntity(
                            id = targetLocalId,
                            grammar = remoteGrammar.grammar ?: "",
                            grammarLevel = remoteGrammar.grammarLevel ?: "N5"
                        ))
                    }

                    val localState = localGrammarStates[targetLocalId]
                    if (localState != null) {
                         val result = SmartSyncMerger.mergeUserProgress(localState, remoteGrammar)
                         if (result is SmartSyncMerger.MergeResult.RemoteUpdated) {
                            userProgressDao.insert(result.data)
                         }
                    } else {
                         userProgressDao.insert(UserProgressEntity(
                             id = "import_grammar_${targetLocalId}",
                             userId = "default_user",
                             itemType = "grammar",
                             itemId = targetLocalId,
                             reps = remoteGrammar.srsLevel,
                             stability = remoteGrammar.stability.toDouble(),
                             difficulty = remoteGrammar.difficulty.toDouble(),
                             elapsedDays = 0,
                             scheduledDays = remoteGrammar.interval,
                             lapses = 0,
                             state = if (remoteGrammar.srsLevel > 0) 2 else 0,
                             learningStep = 0,
                             nextReview = DateTimeUtils.epochDayToIso(remoteGrammar.nextReviewDate),
                             lastReview = remoteGrammar.lastReviewedDate?.let { DateTimeUtils.epochDayToIso(it) },
                             isFavorite = remoteGrammar.isFavorite,
                             buriedUntil = null,
                             updatedAt = DateTimeUtils.millisToIso(remoteGrammar.lastModifiedTime),
                             level = remoteGrammar.grammarLevel ?: "N5",
                             createdAt = remoteGrammar.firstLearnedDate?.let { DateTimeUtils.epochDayToIso(it) } ?: DateTimeUtils.millisToIso(remoteGrammar.lastModifiedTime)
                         ))
                    }
                }
                importedGrammars = userData.grammarProgress.size

                val wrongAnswerDao = database.wrongAnswerDao()
                val localWrongAnswers = wrongAnswerDao.getAllWrongAnswersSync().associateBy { it.uuid }
                userData.wrongAnswers.words.forEach { remote ->
                    val local = localWrongAnswers[remote.uuid]
                    if (local == null) {
                        wrongAnswerDao.insert(WrongAnswerEntity(
                            id = 0,
                            wordId = wordIdRedirectMap[remote.wordId] ?: remote.wordId,
                            testMode = remote.testMode ?: "",
                            userAnswer = remote.userAnswer ?: "",
                            correctAnswer = remote.correctAnswer ?: "",
                            uuid = remote.uuid ?: java.util.UUID.randomUUID().toString(),
                            timestamp = remote.timestamp
                        ))
                    } else if (remote.timestamp > local.timestamp) {
                        wrongAnswerDao.insert(local.copy(
                            testMode = remote.testMode ?: local.testMode,
                            userAnswer = remote.userAnswer ?: local.userAnswer,
                            correctAnswer = remote.correctAnswer ?: local.correctAnswer,
                            timestamp = remote.timestamp
                        ))
                    }
                }

                val grammarWrongAnswerDao = database.grammarWrongAnswerDao()
                val localGrammarWrongAnswers = grammarWrongAnswerDao.getAllWrongAnswersSync().associateBy { it.uuid }
                userData.wrongAnswers.grammars.forEach { remote ->
                    val local = localGrammarWrongAnswers[remote.uuid]
                    if (local == null) {
                        grammarWrongAnswerDao.insert(GrammarWrongAnswerEntity(
                            id = 0,
                            grammarId = grammarIdRedirectMap[remote.grammarId] ?: remote.grammarId,
                            testMode = remote.testMode ?: "",
                            userAnswer = remote.userAnswer ?: "",
                            correctAnswer = remote.correctAnswer ?: "",
                            uuid = remote.uuid ?: java.util.UUID.randomUUID().toString(),
                            timestamp = remote.timestamp
                        ))
                    } else if (remote.timestamp > local.timestamp) {
                        grammarWrongAnswerDao.insert(local.copy(
                            testMode = remote.testMode ?: local.testMode,
                            userAnswer = remote.userAnswer ?: local.userAnswer,
                            correctAnswer = remote.correctAnswer ?: local.correctAnswer,
                            timestamp = remote.timestamp
                        ))
                    }
                }

                val favoriteQuestionDao = database.favoriteQuestionDao()
                val localFavorites = favoriteQuestionDao.getAllFavoriteQuestionsSync().associateBy { it.jsonId ?: it.id.toString() }
                userData.favoriteQuestions.forEach { remote ->
                    val local = localFavorites[remote.jsonId ?: remote.id.toString()]
                    if (local == null) {
                        favoriteQuestionDao.insert(FavoriteQuestionEntity(
                            id = 0,
                            grammarId = remote.grammarId?.let { grammarIdRedirectMap[it] ?: it },
                            jsonId = remote.jsonId,
                            questionType = remote.questionType,
                            questionText = remote.questionText,
                            optionsJson = remote.optionsJson,
                            correctAnswer = remote.correctAnswer,
                            explanation = remote.explanation,
                            timestamp = remote.timestamp
                        ))
                    }
                }

                val testRecordDao = database.testRecordDao()
                val localTestRecords = testRecordDao.getAllTestRecordsSync().associateBy { it.uuid }
                userData.testRecords.forEach { remote ->
                    val local = localTestRecords[remote.uuid]
                    if (local == null) {
                        testRecordDao.insert(TestRecordEntity(
                            id = 0,
                            date = remote.date,
                            totalQuestions = remote.totalQuestions,
                            correctAnswers = remote.correctAnswers,
                            testMode = remote.testMode,
                            uuid = remote.uuid ?: java.util.UUID.randomUUID().toString(),
                            timestamp = remote.timestamp
                        ))
                    } else if (remote.timestamp > local.timestamp) {
                        testRecordDao.insert(local.copy(
                            date = remote.date,
                            totalQuestions = remote.totalQuestions,
                            correctAnswers = remote.correctAnswers,
                            testMode = remote.testMode,
                            timestamp = remote.timestamp
                        ))
                    }
                }

                val studyRecordDao = database.studyRecordDao()
                val localStudyRecords = studyRecordDao.getAllStudyRecordsSync().associateBy { it.date }
                userData.studyRecords.forEach { remote ->
                    val local = localStudyRecords[remote.date]
                    if (local == null) {
                        studyRecordDao.insert(StudyRecordEntity(
                            date = remote.date,
                            learnedWords = remote.learnedWords,
                            learnedGrammars = remote.learnedGrammars,
                            reviewedWords = remote.reviewedWords,
                            reviewedGrammars = remote.reviewedGrammars,
                            skippedWords = remote.skippedWords,
                            skippedGrammars = remote.skippedGrammars,
                            testCount = remote.testCount,
                            timestamp = remote.timestamp
                        ))
                    } else if (remote.timestamp > local.timestamp) {
                        studyRecordDao.insert(local.copy(
                            learnedWords = maxOf(local.learnedWords, remote.learnedWords), // 倾向于保留更大数据或 LWW
                            learnedGrammars = maxOf(local.learnedGrammars, remote.learnedGrammars),
                            reviewedWords = maxOf(local.reviewedWords, remote.reviewedWords),
                            reviewedGrammars = maxOf(local.reviewedGrammars, remote.reviewedGrammars),
                            skippedWords = maxOf(local.skippedWords, remote.skippedWords),
                            skippedGrammars = maxOf(local.skippedGrammars, remote.skippedGrammars),
                            testCount = maxOf(local.testCount, remote.testCount),
                            timestamp = remote.timestamp
                        ))
                    }
                }

                userData.totalStudyDays?.let { totalStudyDays ->
                    settingsRepository.restoreStudyStats(
                        totalStudyDays = totalStudyDays,
                        dailyStreak = userData.studyStreak ?: 0,
                        lastStudyDate = userData.studyRecords.maxOfOrNull { it.date } ?: 0L,
                        maxTestStreak = userData.maxTestStreak ?: 0,
                        testStreak = userData.testStreak ?: 0
                    )
                }
            }

            // 导入成功后，执行一次数据去重修复，清理可能已存在的重复条目
            repairDataDuplicates()

            Log.d(TAG, "导入完成: 单词$importedWords, 语法$importedGrammars")
            ImportResult(true, "导入成功！\n更新单词: $importedWords\n更新语法: $importedGrammars\n已执行数据自动优化修复。")

        } catch (e: Exception) {
            Log.e(TAG, "导入失败", e)
            ImportResult(false, "导入失败: ${e.message}")
        }
    }

    override suspend fun exportDataToUri(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val tempFile = java.io.File(context.cacheDir, "temp_export_uri_${System.currentTimeMillis()}.json.gz")
        try {
            val uri = Uri.parse(uriString)
            exportDataToFile("default_user", tempFile)

            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                Log.e(TAG, "导出失败: 无法打开输出流 uri=$uri")
                return@withContext false
            }

            outputStream.use { os ->
                java.io.FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出到文件失败", e)
            false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    override suspend fun importDataFromUri(uriString: String): String = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        content.append(line)
                        line = reader.readLine()
                    }
                }
            }
            importData(content.toString()).message
        } catch (e: Exception) {
            Log.e(TAG, "从文件读取失败", e)
            "读取文件失败: ${e.message}"
        }
    }

    /**
     * 数据自动去重修复工具
     * 解决语义重复但 ID 不一致的问题
     */
    suspend fun repairDataDuplicates() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始执行数据去重修复...")
            database.withTransaction {
                val grammarDao = database.grammarDao()
                val userProgressDao = database.userProgressDao()
                
                // 1. 处理语法重复
                val allGrammars = grammarDao.getAllGrammarsSync()
                val groups = allGrammars.groupBy { "${(it.grammarLevel ?: "N5").uppercase()}_${it.grammar}" }
                
                groups.forEach { (key, entities) ->
                    if (entities.size > 1) {
                        Log.d(TAG, "发现重复语法标题: $key, 数量: ${entities.size}")
                        
                        val standardEntities = entities.filter { it.id >= 10000 }.sortedBy { it.id }
                        val redundantEntities = entities.filter { it.id < 10000 }.sortedBy { it.id }
                        
                        if (redundantEntities.isNotEmpty()) {
                            val standardTarget = standardEntities.firstOrNull() ?: redundantEntities.first()
                            val duplicateIds = redundantEntities.map { it.id }.filter { it != standardTarget.id }
                            
                            if (duplicateIds.isNotEmpty()) {
                                val grammarWrongAnswerDao = database.grammarWrongAnswerDao()
                                val favoriteQuestionDao = database.favoriteQuestionDao()
                                
                                val standardState = userProgressDao.getByItemIdSync(standardTarget.id, "grammar")
                                var mergedState = standardState
                                
                                duplicateIds.forEach { dupId ->
                                    grammarWrongAnswerDao.migrateGrammarId(dupId, standardTarget.id)
                                    favoriteQuestionDao.migrateGrammarId(dupId, standardTarget.id)
                                    
                                    val dupState = userProgressDao.getByItemIdSync(dupId, "grammar")
                                    if (dupState != null) {
                                        mergedState = if (mergedState == null) {
                                            dupState.copy(id = "merged_grammar_${standardTarget.id}", itemId = standardTarget.id)
                                        } else {
                                            val localTime = DateTimeUtils.isoToMillis(mergedState!!.updatedAt ?: "2000-01-01T00:00:00Z")
                                            val remoteTime = DateTimeUtils.isoToMillis(dupState.updatedAt ?: "2000-01-01T00:00:00Z")
                                            if (remoteTime > localTime) {
                                                dupState.copy(id = mergedState!!.id, itemId = standardTarget.id, isFavorite = mergedState!!.isFavorite || dupState.isFavorite)
                                            } else {
                                                mergedState!!.copy(isFavorite = mergedState!!.isFavorite || dupState.isFavorite)
                                            }
                                        }
                                    }
                                }
                                
                                val finalState = mergedState
                                if (finalState != null) {
                                    userProgressDao.insert(finalState)
                                }
                                
                                grammarDao.deleteByIds(duplicateIds)
                                userProgressDao.deleteByItemIds(duplicateIds, "grammar")
                                Log.d(TAG, "已清理语法冗余项: $key, 保留标准 ID: ${standardTarget.id}, 删除重复 ID: $duplicateIds")
                            }
                        } else if (standardEntities.size > 1) {
                            Log.d(TAG, "检测到多个同名标准项 ($key)，已确认为合法项目，不做去重处理。")
                        }
                    }
                }

                // 2. 对单词执行相同逻辑 (略，如果需要可以增加)
            }
            Log.d(TAG, "数据去重修复完成")
        } catch (e: Exception) {
            Log.e(TAG, "数据修复失败", e)
        }
    }

    companion object {
        private const val TAG = "DataExportManager"
    }
}
