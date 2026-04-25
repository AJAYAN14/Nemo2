package com.jian.nemo2.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.data.datastore.PreferencesKeys
import com.jian.nemo2.core.domain.repository.SettingsRepository
import com.jian.nemo2.core.domain.repository.SessionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.jian.nemo2.core.domain.model.AppSettings
import com.jian.nemo2.core.domain.model.TestPreferences
import kotlinx.coroutines.flow.map
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 设置 Repository 实现
 *
 * 管理用户配置和学习统计（连续学习天数等）
 */
import com.jian.nemo2.core.data.local.NemoDatabase
import com.jian.nemo2.core.data.local.dao.WordDao
import com.jian.nemo2.core.data.local.dao.GrammarDao

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val database: NemoDatabase,
    private val wordDao: WordDao,
    private val grammarDao: GrammarDao,
    private val userProgressDao: com.jian.nemo2.core.data.local.dao.UserProgressDao
) : SettingsRepository {
    // ========== 用户设置 ==========

    /** 用户头像路径 Flow */
    override val userAvatarPathFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_AVATAR_PATH] ?: ""
    }

    /**
     * 设置用户头像路径
     */
    override suspend fun setUserAvatarPath(path: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_AVATAR_PATH] = path
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * 清除用户头像
     */
    override suspend fun clearUserAvatar() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_AVATAR_PATH)
        }
    }

/** 每日学习目标 Flow */
    override val dailyGoalFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DAILY_GOAL] ?: 20
    }

    /**
     * 设置每日目标 (改为次日生效)
     * @param goal 每日学习单词数
     */
    override suspend fun setDailyGoal(goal: Int) {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PENDING_DAILY_GOAL] = goal
            preferences[PreferencesKeys.PENDING_GOAL_SET_DATE] = today
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "已暂存每日单词目标: $goal (日期: $today)，将于下一个逻辑日生效")
    }

    /** 每日语法学习目标 Flow */
    override val grammarDailyGoalFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GRAMMAR_DAILY_GOAL] ?: 5
    }

    /**
     * 设置每日语法目标 (改为次日生效)
     * @param goal 每日学习语法条数
     */
    override suspend fun setGrammarDailyGoal(goal: Int) {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PENDING_GRAMMAR_DAILY_GOAL] = goal
            preferences[PreferencesKeys.PENDING_GOAL_SET_DATE] = today
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "已暂存每日语法目标: $goal (日期: $today)，将于下一个逻辑日生效")
    }

    /** 主题色 Flow (ARGB Long, null = 默认品牌蓝) */
    override val themeColorFlow: Flow<Long?> = dataStore.data.map { preferences ->
        if (preferences.contains(PreferencesKeys.THEME_COLOR)) {
            preferences[PreferencesKeys.THEME_COLOR]
        } else null
    }

    /**
     * 设置主题色
     * @param colorArgb ARGB Long 值，null 表示恢复默认
     */
    override suspend fun setThemeColor(colorArgb: Long?) {
        dataStore.edit { preferences ->
            if (colorArgb == null) {
                preferences.remove(PreferencesKeys.THEME_COLOR)
            } else {
                preferences[PreferencesKeys.THEME_COLOR] = colorArgb
            }
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    /** 深色模式 Flow (null = 跟随系统) */
    override val isDarkModeFlow: Flow<Boolean?> = dataStore.data.map { preferences ->
        if (preferences.contains(PreferencesKeys.IS_DARK_MODE)) {
            preferences[PreferencesKeys.IS_DARK_MODE]
        } else {
            null  // null = 跟随系统
        }
    }

    /**
     * 设置深色模式
     * @param enabled true=深色, false=浅色, null=跟随系统
     */
    override suspend fun setDarkMode(enabled: Boolean?) {
        dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.IS_DARK_MODE)
            } else {
                preferences[PreferencesKeys.IS_DARK_MODE] = enabled
            }
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val darkModeStrategyFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DARK_MODE_STRATEGY] ?: "system"
    }

    override suspend fun setDarkModeStrategy(strategy: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE_STRATEGY] = strategy
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val darkModeStartTimeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DARK_MODE_START_TIME] ?: "22:00"
    }

    override suspend fun setDarkModeStartTime(time: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE_START_TIME] = time
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val darkModeEndTimeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DARK_MODE_END_TIME] ?: "07:00"
    }

    override suspend fun setDarkModeEndTime(time: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE_END_TIME] = time
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    /** 动态颜色 Flow */
    override val isDynamicColorEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_DYNAMIC_COLOR_ENABLED] ?: false
    }

    /**
     * 设置动态颜色
     * @param enabled 是否启用（Android 12+）
     */
    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DYNAMIC_COLOR_ENABLED] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    /** 学习日重置时间 Flow (默认 4:00) */
    override val learningDayResetHourFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LEARNING_DAY_RESET_HOUR] ?: 4
    }

    /**
     * 设置学习日重置时间
     * @param hour 重置小时 (0-23)
     */
    override suspend fun setLearningDayResetHour(hour: Int) {
        val validHour = hour.coerceIn(0, 23)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEARNING_DAY_RESET_HOUR] = validHour
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "学习日重置时间已更新: $validHour:00")
    }

    // ========== 学习统计 ==========

    /** 连续学习天数 Flow */
    override val dailyStreakFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DAILY_STREAK] ?: 0
    }

    /** 最后学习日期 Flow */
    override val lastStudyDateFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_STUDY_DATE] ?: 0L
    }

    /** 累计学习天数 Flow */
    override val totalStudyDaysFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TOTAL_STUDY_DAYS] ?: 0
    }

    /**
     * 更新连续学习天数
     *
     * 逻辑:
     * 1. 如果今天已经更新过，不重复更新
     * 2. 如果昨天有学习，streak +1
     * 3. 如果超过1天没学习，streak重置为1
     *
     * 参考: 实施计划 04-DataStore配置管理.md 第201-242行
     */
    override suspend fun updateDailyStreak() {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)

        dataStore.edit { preferences ->
            val lastStudyDate = preferences[PreferencesKeys.LAST_STUDY_DATE] ?: 0L
            val currentStreak = preferences[PreferencesKeys.DAILY_STREAK] ?: 0

            when {
                lastStudyDate == today -> {
                    // 今天已经更新过，跳过
                    Log.d(TAG, "今天已更新过连续学习天数: $currentStreak")
                }
                lastStudyDate == today - 1 -> {
                    // 昨天也学习了，连续天数+1
                    val newStreak = currentStreak + 1
                    preferences[PreferencesKeys.DAILY_STREAK] = newStreak
                    preferences[PreferencesKeys.LAST_STUDY_DATE] = today
                    Log.i(TAG, "✨ 连续学习 $newStreak 天！")
                }
                else -> {
                    // 中断了，重置为1
                    preferences[PreferencesKeys.DAILY_STREAK] = 1
                    preferences[PreferencesKeys.LAST_STUDY_DATE] = today
                    Log.w(TAG, "连续学习中断，重新开始")
                }
            }

            // 更新累计学习天数
            if (lastStudyDate != today) {
                val totalDays = preferences[PreferencesKeys.TOTAL_STUDY_DAYS] ?: 0
                preferences[PreferencesKeys.TOTAL_STUDY_DAYS] = totalDays + 1

                // 🎯 目标次日生效逻辑: 在检测到跨天时，判断是否应迁移 Pending Goals
                migratePendingGoals(preferences, today)
            }
        }
    }

    /** 连续测试天数 Flow */
    override val testStreakFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TEST_STREAK] ?: 0
    }

    /** 最高连续测试天数 Flow */
    override val maxTestStreakFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MAX_TEST_STREAK] ?: 0
    }

    /**
     * 更新连续测试天数
     */
    override suspend fun updateTestStreak() {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)

        dataStore.edit { preferences ->
            val lastTestDate = preferences[PreferencesKeys.LAST_TEST_DATE] ?: 0L
            val currentStreak = preferences[PreferencesKeys.TEST_STREAK] ?: 0
            val maxStreak = preferences[PreferencesKeys.MAX_TEST_STREAK] ?: 0

            var newStreak = currentStreak

            when {
                lastTestDate == today -> {
                    // 今天已经更新过，跳过
                    Log.d(TAG, "今天已更新过连续测试天数: $currentStreak")
                }
                lastTestDate == today - 1 -> {
                    // 昨天也测试了，连续天数+1
                    newStreak = currentStreak + 1
                    preferences[PreferencesKeys.TEST_STREAK] = newStreak
                    preferences[PreferencesKeys.LAST_TEST_DATE] = today
                    Log.i(TAG, "✨ 连续测试 $newStreak 天！")
                }
                else -> {
                    // 中断了，重置为1
                    newStreak = 1
                    preferences[PreferencesKeys.TEST_STREAK] = 1
                    preferences[PreferencesKeys.LAST_TEST_DATE] = today
                    Log.w(TAG, "连续测试中断，重新开始")
                }
            }

            // 更新最高连续测试天数
            if (newStreak > maxStreak) {
                preferences[PreferencesKeys.MAX_TEST_STREAK] = newStreak
                Log.i(TAG, "🏆 新的最高连续测试记录: $newStreak")
            }
        }
    }

    /**
     * 检查日期是否变化
     *
     * 用于清空每日进度
     * @return true 如果日期变化
     */
    override suspend fun isDateChanged(): Boolean {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)
        val lastDate = dataStore.data.map { it[PreferencesKeys.LAST_STUDY_DATE] ?: 0L }.first()
        val changed = today != lastDate

        if (changed) {
            // 清理今日统计
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.TODAY_TESTED_WORD_IDS] = emptySet<String>()
                preferences[PreferencesKeys.TODAY_WRONG_WORD_IDS] = emptySet<String>()
                preferences[PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS] = emptySet<String>()
                preferences[PreferencesKeys.TODAY_WRONG_GRAMMAR_IDS] = emptySet<String>()

                // 🎯 目标次日生效逻辑: 在检测到日期变更时，判断是否应迁移 Pending Goals
                migratePendingGoals(preferences, today)
            }
        }

        return changed
    }

    override suspend fun restoreStudyStats(
        totalStudyDays: Int,
        dailyStreak: Int,
        lastStudyDate: Long,
        maxTestStreak: Int,
        testStreak: Int
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOTAL_STUDY_DAYS] = totalStudyDays
            preferences[PreferencesKeys.DAILY_STREAK] = dailyStreak
            preferences[PreferencesKeys.LAST_STUDY_DATE] = lastStudyDate
            preferences[PreferencesKeys.MAX_TEST_STREAK] = maxTestStreak
            preferences[PreferencesKeys.TEST_STREAK] = testStreak

            Log.d(TAG, "已恢复学习统计: 累计=$totalStudyDays, 连续=$dailyStreak, 最后=$lastStudyDate")
        }
    }

    // ========== 应用配置 ==========

    /** 是否首次启动 Flow */
    override val isFirstLaunchFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_FIRST_LAUNCH] ?: true
    }

    /**
     * 标记首次启动已完成
     */
    override suspend fun setFirstLaunchCompleted() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] = false
        }
    }

    // ========== 通知管理 ==========

    override val dismissedNotificationIdsFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DISMISSED_NOTIFICATION_IDS] ?: emptySet()
    }

    override suspend fun addDismissedNotificationId(id: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.DISMISSED_NOTIFICATION_IDS] ?: emptySet()
            preferences[PreferencesKeys.DISMISSED_NOTIFICATION_IDS] = current + id
        }
    }

    // ========== 今日复习记录 (临时统计) ==========

    override suspend fun addTodayTestedWordId(id: Long) {
        dataStore.edit { preferences ->
            val set = preferences[PreferencesKeys.TODAY_TESTED_WORD_IDS] ?: emptySet<String>()
            preferences[PreferencesKeys.TODAY_TESTED_WORD_IDS] = set + id.toString()
        }
    }

    override suspend fun addTodayWrongWordId(id: Long) {
        dataStore.edit { preferences ->
            val set = preferences[PreferencesKeys.TODAY_WRONG_WORD_IDS] ?: emptySet<String>()
            preferences[PreferencesKeys.TODAY_WRONG_WORD_IDS] = set + id.toString()
        }
    }

    override suspend fun addTodayTestedGrammarId(id: Long) {
        dataStore.edit { preferences ->
            val set = preferences[PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS] ?: emptySet<String>()
            preferences[PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS] = set + id.toString()
        }
    }

    override suspend fun addTodayWrongGrammarId(id: Long) {
        dataStore.edit { preferences ->
            val set = preferences[PreferencesKeys.TODAY_WRONG_GRAMMAR_IDS] ?: emptySet<String>()
            preferences[PreferencesKeys.TODAY_WRONG_GRAMMAR_IDS] = set + id.toString()
        }
    }

    // 🎯 P3修复: 获取今日测试的单词/语法ID
    override suspend fun getTodayTestedWordIds(): Set<Long> {
        return dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TODAY_TESTED_WORD_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet<Long>()
        }.first()
    }

    override suspend fun getTodayTestedGrammarIds(): Set<Long> {
        return dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet<Long>()
        }.first()
    }

    override suspend fun clearTodayTestedIds() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TODAY_TESTED_WORD_IDS] = emptySet<String>()
            preferences[PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS] = emptySet<String>()
        }
    }

    // ========== 测试配置实现 ==========

    private val currentTestModeFlow = MutableStateFlow<String?>(null)

    override fun setContextTestMode(mode: String?) {
        currentTestModeFlow.value = mode
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testPreferencesFlow: Flow<TestPreferences> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            TestPreferences(
                questionCount = preferences[PreferencesKeys.getTestQuestionCountKey(mode)] ?: 10,
                timeLimitMinutes = preferences[PreferencesKeys.getTestTimeLimitKey(mode)] ?: 10,
                shuffleQuestions = preferences[PreferencesKeys.getTestShuffleQuestionsKey(mode)] ?: true,
                shuffleOptions = preferences[PreferencesKeys.getTestShuffleOptionsKey(mode)] ?: true,
                autoAdvance = preferences[PreferencesKeys.getTestAutoAdvanceKey(mode)] ?: true,
                prioritizeWrong = preferences[PreferencesKeys.getTestPrioritizeWrongKey(mode)] ?: false,
                prioritizeNew = preferences[PreferencesKeys.getTestPrioritizeNewKey(mode)] ?: false,
                questionSource = preferences[PreferencesKeys.getTestQuestionSourceKey(mode)] ?: "today",
                wrongAnswerRemovalThreshold = preferences[PreferencesKeys.getTestWrongAnswerRemovalThresholdKey(mode)] ?: 0,
                testContentType = preferences[PreferencesKeys.getTestContentTypeKey(mode)] ?: "mixed",
                selectedWordLevels = (preferences[PreferencesKeys.getTestSelectedWordLevelsKey(mode)] ?: setOf("N5", "N4", "N3", "N2", "N1")).sorted(),
                selectedGrammarLevels = (preferences[PreferencesKeys.getTestSelectedGrammarLevelsKey(mode)] ?: setOf("N5", "N4", "N3", "N2", "N1")).sorted(),
                comprehensiveMultipleChoiceCount = preferences[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] ?: 4,
                comprehensiveTypingCount = preferences[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] ?: 3,
                comprehensiveCardMatchingCount = preferences[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] ?: 2,
                comprehensiveSortingCount = preferences[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] ?: 5
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testQuestionCountFlow: Flow<Int> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestQuestionCountKey(mode)] ?: 10
        }
    }

    override suspend fun setTestQuestionCount(count: Int) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestQuestionCountKey(mode)] = count
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testTimeLimitMinutesFlow: Flow<Int> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestTimeLimitKey(mode)] ?: 10
        }
    }

    override suspend fun setTestTimeLimitMinutes(minutes: Int) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestTimeLimitKey(mode)] = minutes
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testShuffleQuestionsFlow: Flow<Boolean> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestShuffleQuestionsKey(mode)] ?: true
        }
    }

    override suspend fun setTestShuffleQuestions(enabled: Boolean) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestShuffleQuestionsKey(mode)] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testShuffleOptionsFlow: Flow<Boolean> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestShuffleOptionsKey(mode)] ?: true
        }
    }

    override suspend fun setTestShuffleOptions(enabled: Boolean) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestShuffleOptionsKey(mode)] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testAutoAdvanceFlow: Flow<Boolean> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestAutoAdvanceKey(mode)] ?: true
        }
    }

    override suspend fun setTestAutoAdvance(enabled: Boolean) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestAutoAdvanceKey(mode)] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testPrioritizeWrongFlow: Flow<Boolean> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestPrioritizeWrongKey(mode)] ?: false
        }
    }

    override suspend fun setTestPrioritizeWrong(enabled: Boolean) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestPrioritizeWrongKey(mode)] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testPrioritizeNewFlow: Flow<Boolean> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestPrioritizeNewKey(mode)] ?: false
        }
    }

    override suspend fun setTestPrioritizeNew(enabled: Boolean) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestPrioritizeNewKey(mode)] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testQuestionSourceFlow: Flow<String> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestQuestionSourceKey(mode)] ?: "today"
        }
    }

    override suspend fun setTestQuestionSource(source: String) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestQuestionSourceKey(mode)] = source
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testWrongAnswerRemovalThresholdFlow: Flow<Int> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestWrongAnswerRemovalThresholdKey(mode)] ?: 0
        }
    }

    override suspend fun setTestWrongAnswerRemovalThreshold(threshold: Int) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestWrongAnswerRemovalThresholdKey(mode)] = threshold
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testContentTypeFlow: Flow<String> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestContentTypeKey(mode)] ?: "mixed"
        }
    }

    override suspend fun setTestContentType(type: String) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestContentTypeKey(mode)] = type
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testSelectedWordLevelsFlow: Flow<Set<String>> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestSelectedWordLevelsKey(mode)] ?: setOf("N5", "N4", "N3", "N2", "N1")
        }
    }

    override suspend fun setTestSelectedWordLevels(levels: Set<String>) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestSelectedWordLevelsKey(mode)] = levels
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val testSelectedGrammarLevelsFlow: Flow<Set<String>> = currentTestModeFlow.flatMapLatest { mode ->
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.getTestSelectedGrammarLevelsKey(mode)] ?: setOf("N5", "N4", "N3", "N2", "N1")
        }
    }

    override suspend fun setTestSelectedGrammarLevels(levels: Set<String>) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestSelectedGrammarLevelsKey(mode)] = levels
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override suspend fun saveTestConfig(
        questionCount: Int,
        timeLimitMinutes: Int,
        shuffleQuestions: Boolean,
        shuffleOptions: Boolean,
        autoAdvance: Boolean,
        prioritizeWrong: Boolean,
        prioritizeNew: Boolean,
        questionSource: String,
        wrongAnswerRemovalThreshold: Int,
        testContentType: String,
        selectedWordLevels: Set<String>,
        selectedGrammarLevels: Set<String>,
        comprehensiveMultipleChoiceCount: Int,
        comprehensiveTypingCount: Int,
        comprehensiveCardMatchingCount: Int,
        comprehensiveSortingCount: Int
    ) {
        val mode = currentTestModeFlow.value
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.getTestQuestionCountKey(mode)] = questionCount
            preferences[PreferencesKeys.getTestTimeLimitKey(mode)] = timeLimitMinutes
            preferences[PreferencesKeys.getTestShuffleQuestionsKey(mode)] = shuffleQuestions
            preferences[PreferencesKeys.getTestShuffleOptionsKey(mode)] = shuffleOptions
            preferences[PreferencesKeys.getTestAutoAdvanceKey(mode)] = autoAdvance
            preferences[PreferencesKeys.getTestPrioritizeWrongKey(mode)] = prioritizeWrong
            preferences[PreferencesKeys.getTestPrioritizeNewKey(mode)] = prioritizeNew
            preferences[PreferencesKeys.getTestQuestionSourceKey(mode)] = questionSource
            preferences[PreferencesKeys.getTestWrongAnswerRemovalThresholdKey(mode)] = wrongAnswerRemovalThreshold
            preferences[PreferencesKeys.getTestContentTypeKey(mode)] = testContentType
            preferences[PreferencesKeys.getTestSelectedWordLevelsKey(mode)] = selectedWordLevels
            preferences[PreferencesKeys.getTestSelectedGrammarLevelsKey(mode)] = selectedGrammarLevels
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] = comprehensiveMultipleChoiceCount
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] = comprehensiveTypingCount
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] = comprehensiveCardMatchingCount
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] = comprehensiveSortingCount
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    // ========== 综合测试各个题型数量 ==========

    override val comprehensiveTestMultipleChoiceCountFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] ?: 4
    }

    override suspend fun saveComprehensiveTestMultipleChoiceCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] = count
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val comprehensiveTestTypingCountFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] ?: 3
    }

    override suspend fun saveComprehensiveTestTypingCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] = count
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val comprehensiveTestCardMatchingCountFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] ?: 2
    }

    override suspend fun saveComprehensiveTestCardMatchingCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] = count
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val comprehensiveTestSortingCountFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] ?: 5
    }

    override suspend fun saveComprehensiveTestSortingCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] = count
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    // ========== 学习状态持久化 ==========

    override val lastLearningModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_LEARNING_MODE] ?: "word"
    }

    override suspend fun setLastLearningMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_LEARNING_MODE] = mode
        }
    }

    override val preferredWordLevelFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PREFERRED_WORD_LEVEL] ?: "N5"
    }

    override suspend fun setPreferredWordLevel(level: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_WORD_LEVEL] = level
        }
    }

    override val preferredGrammarLevelFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PREFERRED_GRAMMAR_LEVEL] ?: "N5"
    }

    override suspend fun setPreferredGrammarLevel(level: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_GRAMMAR_LEVEL] = level
        }
    }

    // ========== 学习会话持久化 (单词) ==========

    override suspend fun saveWordSession(ids: List<Long>, currentIndex: Int, level: String, steps: Map<Long, Int>, waitingUntil: Long) {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)
        val idsString = ids.joinToString(",")
        // Serialize steps: "id:step|id:step"
        val stepsString = steps.entries.joinToString("|") { "${it.key}:${it.value}" }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_WORD_IDS] = idsString
            preferences[PreferencesKeys.SESSION_CURRENT_INDEX] = currentIndex
            preferences[PreferencesKeys.SESSION_LEVEL] = level
            preferences[PreferencesKeys.SESSION_START_DATE] = today
            preferences[PreferencesKeys.SESSION_WORD_STEPS] = stepsString
            preferences[PreferencesKeys.SESSION_WAITING_UNTIL] = waitingUntil // 保存等待时间
        }
    }

    // ========== 新内容策略 ==========

    override val isRandomNewContentEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_RANDOM_NEW_CONTENT_ENABLED] ?: true
    }

    override suspend fun setRandomNewContentEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_RANDOM_NEW_CONTENT_ENABLED] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override suspend fun getWordSession(): Flow<SessionData?> {
        return dataStore.data.map { preferences ->
            val idsString = preferences[PreferencesKeys.SESSION_WORD_IDS] ?: ""
            val currentIndex = preferences[PreferencesKeys.SESSION_CURRENT_INDEX] ?: 0
            val level = preferences[PreferencesKeys.SESSION_LEVEL]
            val startDate = preferences[PreferencesKeys.SESSION_START_DATE] ?: 0L
            val stepsString = preferences[PreferencesKeys.SESSION_WORD_STEPS] ?: ""
            val resetHour = preferences[PreferencesKeys.LEARNING_DAY_RESET_HOUR] ?: 4
            val today = DateTimeUtils.getLearningDay(resetHour)

            // 只有当会话存在、不为空且是今天的会话时才返回
            if (idsString.isNotEmpty() && level != null && startDate == today) {
                try {
                    val ids = idsString.split(",").mapNotNull { it.toLongOrNull() }

                    val steps = if (stepsString.isNotEmpty()) {
                        stepsString.split("|").associate {
                            val parts = it.split(":")
                            parts[0].toLong() to parts[1].toInt()
                        }
                    } else {
                        emptyMap()
                    }

                    if (ids.isNotEmpty()) {
                         val waitingUntil = preferences[PreferencesKeys.SESSION_WAITING_UNTIL] ?: 0L
                         SessionData(ids, currentIndex, level, steps, waitingUntil)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    override suspend fun clearWordSession() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SESSION_WORD_IDS)
            preferences.remove(PreferencesKeys.SESSION_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_WORD_STEPS)
        }
    }


    // ========== 学习会话持久化 (语法) ==========

    override suspend fun saveGrammarSession(ids: List<Long>, currentIndex: Int, level: String, steps: Map<Long, Int>, waitingUntil: Long) {
        val resetHour = learningDayResetHourFlow.first()
        val today = DateTimeUtils.getLearningDay(resetHour)
        val idsString = ids.joinToString(",")
        val stepsString = steps.entries.joinToString("|") { "${it.key}:${it.value}" }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SESSION_GRAMMAR_IDS] = idsString
            preferences[PreferencesKeys.SESSION_GRAMMAR_CURRENT_INDEX] = currentIndex
            preferences[PreferencesKeys.SESSION_GRAMMAR_LEVEL] = level
            preferences[PreferencesKeys.SESSION_GRAMMAR_START_DATE] = today
            preferences[PreferencesKeys.SESSION_GRAMMAR_STEPS] = stepsString
            preferences[PreferencesKeys.SESSION_WAITING_UNTIL] = waitingUntil // 复用同一个 key
        }
    }

    override suspend fun getGrammarSession(): Flow<SessionData?> {
        return dataStore.data.map { preferences ->
            val idsString = preferences[PreferencesKeys.SESSION_GRAMMAR_IDS] ?: ""
            val currentIndex = preferences[PreferencesKeys.SESSION_GRAMMAR_CURRENT_INDEX] ?: 0
            val level = preferences[PreferencesKeys.SESSION_GRAMMAR_LEVEL]
            val startDate = preferences[PreferencesKeys.SESSION_GRAMMAR_START_DATE] ?: 0L
            val stepsString = preferences[PreferencesKeys.SESSION_GRAMMAR_STEPS] ?: ""
            val resetHour = preferences[PreferencesKeys.LEARNING_DAY_RESET_HOUR] ?: 4
            val today = DateTimeUtils.getLearningDay(resetHour)

            // 只有当会话存在、不为空且是今天的会话时才返回
            if (idsString.isNotEmpty() && level != null && startDate == today) {
                try {
                    val ids = idsString.split(",").mapNotNull { it.toLongOrNull() }

                    val steps = if (stepsString.isNotEmpty()) {
                        stepsString.split("|").associate {
                            val parts = it.split(":")
                            parts[0].toLong() to parts[1].toInt()
                        }
                    } else {
                        emptyMap()
                    }

                    if (ids.isNotEmpty()) {
                         val waitingUntil = preferences[PreferencesKeys.SESSION_WAITING_UNTIL] ?: 0L
                         SessionData(ids, currentIndex, level, steps, waitingUntil)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    override suspend fun clearGrammarSession() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_IDS)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_STEPS)
        }
    }

    // ========== Leech/Lapse Management (Phase 3) ==========

    override val wordLapsesFlow: Flow<Map<Long, Int>> = dataStore.data.map { preferences ->
        parseLapseMap(preferences[PreferencesKeys.KEY_WORD_LAPSES])
    }

    override val grammarLapsesFlow: Flow<Map<Long, Int>> = dataStore.data.map { preferences ->
        parseLapseMap(preferences[PreferencesKeys.KEY_GRAMMAR_LAPSES])
    }

    override suspend fun incrementWordLapse(wordId: Long) {
        dataStore.edit { preferences ->
            val currentMap = parseLapseMap(preferences[PreferencesKeys.KEY_WORD_LAPSES]).toMutableMap()
            val currentLapse = currentMap[wordId] ?: 0
            currentMap[wordId] = currentLapse + 1
            preferences[PreferencesKeys.KEY_WORD_LAPSES] = serializeLapseMap(currentMap)
            Log.d(TAG, "单词 Lapse +1: id=$wordId, newLapse=${currentMap[wordId]}")
        }
    }

    override suspend fun incrementGrammarLapse(grammarId: Long) {
        dataStore.edit { preferences ->
            val currentMap = parseLapseMap(preferences[PreferencesKeys.KEY_GRAMMAR_LAPSES]).toMutableMap()
            val currentLapse = currentMap[grammarId] ?: 0
            currentMap[grammarId] = currentLapse + 1
            preferences[PreferencesKeys.KEY_GRAMMAR_LAPSES] = serializeLapseMap(currentMap)
            Log.d(TAG, "语法 Lapse +1: id=$grammarId, newLapse=${currentMap[grammarId]}")
        }
    }

    override suspend fun resetWordLapse(wordId: Long) {
        dataStore.edit { preferences ->
            val currentMap = parseLapseMap(preferences[PreferencesKeys.KEY_WORD_LAPSES]).toMutableMap()
            if (currentMap.containsKey(wordId)) {
                currentMap.remove(wordId) // 或者设为 0
                preferences[PreferencesKeys.KEY_WORD_LAPSES] = serializeLapseMap(currentMap)
                Log.d(TAG, "单词 Lapse 重置: id=$wordId")
            }
        }
    }

    override suspend fun resetGrammarLapse(grammarId: Long) {
        dataStore.edit { preferences ->
            val currentMap = parseLapseMap(preferences[PreferencesKeys.KEY_GRAMMAR_LAPSES]).toMutableMap()
            if (currentMap.containsKey(grammarId)) {
                currentMap.remove(grammarId)
                preferences[PreferencesKeys.KEY_GRAMMAR_LAPSES] = serializeLapseMap(currentMap)
                Log.d(TAG, "语法 Lapse 重置: id=$grammarId")
            }
        }
    }

    // Helper to parse "id:count|id:count"
    private fun parseLapseMap(json: String?): Map<Long, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            json.split("|").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val id = parts[0].toLongOrNull()
                    if (id != null) id to parts[1].toInt() else null
                } else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Helper to serialize
    private fun serializeLapseMap(map: Map<Long, Int>): String {
        return map.entries.joinToString("|") { "${it.key}:${it.value}" }
    }

    // ========== 自动同步配置 ==========


    override val isAutoSyncEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_SYNC_ENABLED] ?: true
        }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SYNC_ENABLED] = enabled
        }
    }



    // ========== 恢复状态管理 ==========

    override val isRestoringFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_RESTORING] ?: false
    }

    override suspend fun setIsRestoring(isRestoring: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_RESTORING] = isRestoring
        }
    }

    // ========== 恢复断点续传 ==========

    override suspend fun setRestoreCheckpoint(table: String, offset: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESTORE_CHECKPOINT_TABLE] = table
            preferences[PreferencesKeys.RESTORE_CHECKPOINT_OFFSET] = offset
        }
        Log.d(TAG, "设置恢复断点: table=$table, offset=$offset")
    }

    override suspend fun getRestoreCheckpoint(): Pair<String, Int>? {
        val checkpoint = dataStore.data.map { preferences ->
            val table = preferences[PreferencesKeys.RESTORE_CHECKPOINT_TABLE]
            val offset = preferences[PreferencesKeys.RESTORE_CHECKPOINT_OFFSET]
            if (!table.isNullOrEmpty() && offset != null) {
                table to offset
            } else {
                null
            }
        }.first()
        return checkpoint
    }

    override suspend fun clearRestoreCheckpoint() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.RESTORE_CHECKPOINT_TABLE)
            preferences.remove(PreferencesKeys.RESTORE_CHECKPOINT_OFFSET)
        }
        Log.d(TAG, "已清除恢复断点")
    }



    override suspend fun repairLocalData(): Int {
        Log.d(TAG, "Starting local data repair (deduplication)...")
        var deletedCount = 0

        // 1. Repair Words
        try {
            val keepIds = wordDao.getDuplicateKeepIds()
            val allWords = wordDao.getAllWordsSync()
            val toDeleteIds = allWords.map { it.id }.filter { !keepIds.contains(it) }

            if (toDeleteIds.isNotEmpty()) {
                Log.d(TAG, "Found ${toDeleteIds.size} duplicate words. Deleting...")
                // Use physical delete for cleanup
                wordDao.deleteByIds(toDeleteIds)
                // 同步清理对应的状态表
                userProgressDao.deleteByItemIds(toDeleteIds, "word")
                deletedCount += toDeleteIds.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error repairing words", e)
        }

        // 2. Repair Grammars
        try {
            val keepIds = grammarDao.getDuplicateKeepIds()
            val allGrammars = grammarDao.getAllGrammarsSync()
            val toDeleteIds = allGrammars.map { it.id }.filter { !keepIds.contains(it) }

            if (toDeleteIds.isNotEmpty()) {
                Log.d(TAG, "Found ${toDeleteIds.size} duplicate grammars. Deleting...")
                grammarDao.deleteByIds(toDeleteIds)
                // 同步清理对应的状态表
                userProgressDao.deleteByItemIds(toDeleteIds, "grammar")
                deletedCount += toDeleteIds.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error repairing grammars", e)
        }

        Log.d(TAG, "Repair completed. Deleted $deletedCount items.")
        return deletedCount
    }

    override val lastSyncTimeFlow: Flow<Long> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIME] ?: 0L
        }

    override suspend fun setLastSyncTime(time: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIME] = time
        }
    }

    override suspend fun getLastSyncTime(): Long {
        return dataStore.data.map { it[PreferencesKeys.LAST_SYNC_TIME] ?: 0L }.first()
    }

    override val lastSyncSuccessFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_SUCCESS] ?: true
        }

    override suspend fun setLastSyncSuccess(success: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_SUCCESS] = success
        }
    }

    override val lastSyncErrorFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_ERROR] ?: ""
        }

    override suspend fun setLastSyncError(error: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_ERROR] = error
        }
    }

    override val lastRestoreTimeFlow: Flow<Long> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_RESTORE_TIME] ?: 0L
        }

    override suspend fun setLastRestoreTime(time: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_RESTORE_TIME] = time
        }
    }

    override suspend fun getLastContentVersion(): Int {
        return dataStore.data.map { it[PreferencesKeys.LAST_CONTENT_VERSION] ?: 0 }.first()
    }

    override suspend fun setLastContentVersion(version: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CONTENT_VERSION] = version
        }
    }

    override val lastSyncConflictCountFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_CONFLICT_COUNT] ?: 0
        }

    override suspend fun setLastSyncConflictCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_CONFLICT_COUNT] = count
        }
    }

    override val isSyncOnLearningCompleteFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SYNC_ON_LEARNING_COMPLETE] ?: true
        }

    override suspend fun setSyncOnLearningComplete(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_ON_LEARNING_COMPLETE] = enabled
        }
    }

    override val isSyncOnTestCompleteFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SYNC_ON_TEST_COMPLETE] ?: true
        }

    override suspend fun setSyncOnTestComplete(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_ON_TEST_COMPLETE] = enabled
        }
    }


    // ========== 学习高级设置 ==========

    override val learningStepsFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LEARNING_STEPS] ?: "1 10"
    }

    override suspend fun setLearningSteps(steps: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEARNING_STEPS] = steps
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "学习步进已更新: $steps")
    }

    override val learnAheadLimitFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LEARN_AHEAD_LIMIT] ?: 20
    }

    override suspend fun setLearnAheadLimit(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEARN_AHEAD_LIMIT] = minutes
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "提前学习限制已更新: $minutes mins")
    }

    override val leechThresholdFlow: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[PreferencesKeys.LEECH_THRESHOLD] ?: 8).coerceAtLeast(1)
    }

    override suspend fun setLeechThreshold(threshold: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEECH_THRESHOLD] = threshold.coerceAtLeast(1)
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "Leech阈值已更新: ${threshold.coerceAtLeast(1)}")
    }

    override val leechActionFlow: Flow<String> = dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.LEECH_ACTION] ?: "skip"
        if (raw == "skip" || raw == "bury_today") raw else "skip"
    }

    override suspend fun setLeechAction(action: String) {
        val normalized = if (action == "bury_today") "bury_today" else "skip"
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LEECH_ACTION] = normalized
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "Leech行为已更新: $normalized")
    }

    override val relearningStepsFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RELEARNING_STEPS] ?: "10"
    }

    override suspend fun setRelearningSteps(steps: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RELEARNING_STEPS] = steps
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val fsrsTargetRetentionFlow: Flow<Double> = dataStore.data.map { preferences ->
        (preferences[PreferencesKeys.FSRS_TARGET_RETENTION] ?: 0.9f).toDouble()
    }

    override suspend fun setFsrsTargetRetention(retention: Double) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FSRS_TARGET_RETENTION] = retention.toFloat()
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
        Log.d(TAG, "FSRS目标留存率已更新: $retention")
    }

    override suspend fun saveAdvancedLearningSettings(
        learningSteps: String,
        relearningSteps: String,
        learnAheadLimit: Int,
        leechThreshold: Int,
        leechAction: String,
        fsrsTargetRetention: Double
    ) {
        // 使用 NonCancellable 确保在页面关闭/ViewModel 销毁时，写入操作不会被取消
        withContext(NonCancellable) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.LEARNING_STEPS] = learningSteps
                preferences[PreferencesKeys.RELEARNING_STEPS] = relearningSteps
                preferences[PreferencesKeys.LEARN_AHEAD_LIMIT] = learnAheadLimit
                preferences[PreferencesKeys.LEECH_THRESHOLD] = leechThreshold.coerceAtLeast(1)
                preferences[PreferencesKeys.LEECH_ACTION] = leechAction
                preferences[PreferencesKeys.FSRS_TARGET_RETENTION] = fsrsTargetRetention.toFloat()

                // 更新修改时间戳，以便触发云端同步
                preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
            }
            Log.d(TAG, "高级学习设置已批量更新并记录时间戳")
        }
    }

    // ========== TTS 设置 ==========

    override val ttsSpeechRateFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TTS_SPEECH_RATE] ?: 1.0f
    }

    override suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_SPEECH_RATE] = rate
        }
    }

    override val ttsPitchFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TTS_PITCH] ?: 1.0f
    }

    override suspend fun setTtsPitch(pitch: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_PITCH] = pitch
        }
    }

    override val ttsVoiceNameFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TTS_VOICE_NAME]
    }

    override suspend fun setTtsVoiceName(voiceName: String?) {
        dataStore.edit { preferences ->
            if (voiceName == null) {
                preferences.remove(PreferencesKeys.TTS_VOICE_NAME)
            } else {
                preferences[PreferencesKeys.TTS_VOICE_NAME] = voiceName
            }
        }
    }

    override val isAutoPlayAudioEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.IS_AUTO_PLAY_AUDIO_ENABLED] ?: true }

    override suspend fun setAutoPlayAudioEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AUTO_PLAY_AUDIO_ENABLED] = enabled
        }
    }

    override val isShowAnswerDelayEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.IS_SHOW_ANSWER_DELAY_ENABLED] ?: false }

    override suspend fun setShowAnswerDelayEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SHOW_ANSWER_DELAY_ENABLED] = enabled
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override val showAnswerDelayMsFlow: Flow<Long> = dataStore.data
        .map { preferences ->
            when (preferences[PreferencesKeys.SHOW_ANSWER_DELAY_MS] ?: 5000L) {
                2000L, 3000L, 4000L, 5000L -> preferences[PreferencesKeys.SHOW_ANSWER_DELAY_MS] ?: 5000L
                else -> 5000L
            }
        }

    override suspend fun setShowAnswerDelayMs(ms: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_ANSWER_DELAY_MS] = when (ms) {
                2000L, 3000L, 4000L, 5000L -> ms
                else -> 5000L
            }
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    override suspend fun clearUserData() {
        dataStore.edit { preferences ->
            // 用户设置
            preferences.remove(PreferencesKeys.USER_AVATAR_PATH)
            preferences.remove(PreferencesKeys.DAILY_GOAL)
            preferences.remove(PreferencesKeys.GRAMMAR_DAILY_GOAL)

            // 学习统计
            preferences.remove(PreferencesKeys.DAILY_STREAK)
            preferences.remove(PreferencesKeys.LAST_STUDY_DATE)
            preferences.remove(PreferencesKeys.TOTAL_STUDY_DAYS)
            preferences.remove(PreferencesKeys.TEST_STREAK)
            preferences.remove(PreferencesKeys.MAX_TEST_STREAK)
            preferences.remove(PreferencesKeys.LAST_TEST_DATE)

            // 今日缓存
            preferences.remove(PreferencesKeys.TODAY_TESTED_WORD_IDS)
            preferences.remove(PreferencesKeys.TODAY_WRONG_WORD_IDS)
            preferences.remove(PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS)
            preferences.remove(PreferencesKeys.TODAY_WRONG_GRAMMAR_IDS)

            // 学习会话
            preferences.remove(PreferencesKeys.SESSION_WORD_IDS)
            preferences.remove(PreferencesKeys.SESSION_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_WORD_STEPS)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_IDS)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_STEPS)
            preferences.remove(PreferencesKeys.SESSION_WAITING_UNTIL)

            // Leech/Lapse
            preferences.remove(PreferencesKeys.KEY_WORD_LAPSES)
            preferences.remove(PreferencesKeys.KEY_GRAMMAR_LAPSES)

            // 学习配置
            preferences.remove(PreferencesKeys.LAST_LEARNING_MODE)
            preferences.remove(PreferencesKeys.IS_RANDOM_NEW_CONTENT_ENABLED)
            preferences.remove(PreferencesKeys.LEARNING_STEPS)
            preferences.remove(PreferencesKeys.RELEARNING_STEPS)
            preferences.remove(PreferencesKeys.LEARN_AHEAD_LIMIT)
            preferences.remove(PreferencesKeys.LEECH_THRESHOLD)
            preferences.remove(PreferencesKeys.LEECH_ACTION)
            preferences.remove(PreferencesKeys.PREFERRED_WORD_LEVEL)
            preferences.remove(PreferencesKeys.PREFERRED_GRAMMAR_LEVEL)

            // 测试配置
            preferences.remove(PreferencesKeys.TEST_QUESTION_COUNT)
            preferences.remove(PreferencesKeys.TEST_TIME_LIMIT_MINUTES)
            preferences.remove(PreferencesKeys.TEST_SHUFFLE_QUESTIONS)
            preferences.remove(PreferencesKeys.TEST_SHUFFLE_OPTIONS)
            preferences.remove(PreferencesKeys.TEST_AUTO_ADVANCE)
            preferences.remove(PreferencesKeys.TEST_PRIORITIZE_WRONG)
            preferences.remove(PreferencesKeys.TEST_PRIORITIZE_NEW)
            preferences.remove(PreferencesKeys.TEST_QUESTION_SOURCE)
            preferences.remove(PreferencesKeys.TEST_WRONG_ANSWER_REMOVAL_THRESHOLD)
            preferences.remove(PreferencesKeys.TEST_CONTENT_TYPE)
            preferences.remove(PreferencesKeys.TEST_SELECTED_WORD_LEVELS)
            preferences.remove(PreferencesKeys.TEST_SELECTED_GRAMMAR_LEVELS)

            // 综合测试配置
            preferences.remove(PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT)
            preferences.remove(PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT)
            preferences.remove(PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT)
            preferences.remove(PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT)

            // 同步状态 (保留 AUTO_SYNC_ENABLED)
            preferences.remove(PreferencesKeys.LAST_SYNC_TIME)
            preferences.remove(PreferencesKeys.LAST_SYNC_SUCCESS)
            preferences.remove(PreferencesKeys.LAST_SYNC_ERROR)
            preferences.remove(PreferencesKeys.LAST_RESTORE_TIME)
            preferences.remove(PreferencesKeys.LAST_SYNC_CONFLICT_COUNT)
            preferences.remove(PreferencesKeys.SYNC_ON_LEARNING_COMPLETE)
            preferences.remove(PreferencesKeys.SYNC_ON_TEST_COMPLETE)

            Log.w(TAG, "已清除所有用户相关数据 (保留设备配置)")
        }
    }


    override suspend fun resetLearningStats() {
        dataStore.edit { preferences ->
            // 连续学习统计
            preferences.remove(PreferencesKeys.DAILY_STREAK)
            preferences.remove(PreferencesKeys.TOTAL_STUDY_DAYS)
            preferences.remove(PreferencesKeys.LAST_STUDY_DATE)

            // 连续测试统计
            preferences.remove(PreferencesKeys.TEST_STREAK)
            preferences.remove(PreferencesKeys.MAX_TEST_STREAK)
            preferences.remove(PreferencesKeys.LAST_TEST_DATE)

            // 记忆算法参数 (Lapse)
            preferences.remove(PreferencesKeys.KEY_WORD_LAPSES)
            preferences.remove(PreferencesKeys.KEY_GRAMMAR_LAPSES)

            // 今日临时数据
            preferences.remove(PreferencesKeys.TODAY_TESTED_WORD_IDS)
            preferences.remove(PreferencesKeys.TODAY_WRONG_WORD_IDS)
            preferences.remove(PreferencesKeys.TODAY_TESTED_GRAMMAR_IDS)
            preferences.remove(PreferencesKeys.TODAY_WRONG_GRAMMAR_IDS)

            // 清除会话缓存
            preferences.remove(PreferencesKeys.SESSION_WORD_IDS)
            preferences.remove(PreferencesKeys.SESSION_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_WORD_STEPS)
            preferences.remove(PreferencesKeys.SESSION_WAITING_UNTIL)

            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_IDS)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_CURRENT_INDEX)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_LEVEL)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_START_DATE)
            preferences.remove(PreferencesKeys.SESSION_GRAMMAR_STEPS)

            Log.w(TAG, "已重置所有学习统计数据 (Streak, Lapses, Session)")
        }
    }

    // ========== App Settings Sync ==========

    override suspend fun getAppSettingsSnapshot(): AppSettings {
        val prefs = dataStore.data.first()
        return AppSettings(
            theme = if (prefs.contains(PreferencesKeys.IS_DARK_MODE)) {
                if (prefs[PreferencesKeys.IS_DARK_MODE] == true) "dark" else "light"
            } else "system",
            dailyGoal = prefs[PreferencesKeys.DAILY_GOAL] ?: 20,
            grammarDailyGoal = prefs[PreferencesKeys.GRAMMAR_DAILY_GOAL] ?: 5,
            isUnmasteredOnlyMode = false,

            isDynamicColorEnabled = prefs[PreferencesKeys.IS_DYNAMIC_COLOR_ENABLED] ?: true,
            learningDayResetHour = prefs[PreferencesKeys.LEARNING_DAY_RESET_HOUR] ?: 4,

            testQuestionCount = prefs[PreferencesKeys.TEST_QUESTION_COUNT] ?: 10,
            testTimeLimitMinutes = prefs[PreferencesKeys.TEST_TIME_LIMIT_MINUTES] ?: 10,
            testShuffleQuestions = prefs[PreferencesKeys.TEST_SHUFFLE_QUESTIONS] ?: true,
            testShuffleOptions = prefs[PreferencesKeys.TEST_SHUFFLE_OPTIONS] ?: true,
            testAutoAdvance = prefs[PreferencesKeys.TEST_AUTO_ADVANCE] ?: true,
            testPrioritizeWrong = prefs[PreferencesKeys.TEST_PRIORITIZE_WRONG] ?: false,
            testPrioritizeNew = prefs[PreferencesKeys.TEST_PRIORITIZE_NEW] ?: false,
            testQuestionSource = prefs[PreferencesKeys.TEST_QUESTION_SOURCE] ?: "today",
            testWrongAnswerRemovalThreshold = prefs[PreferencesKeys.TEST_WRONG_ANSWER_REMOVAL_THRESHOLD] ?: 0,
            testContentType = prefs[PreferencesKeys.TEST_CONTENT_TYPE] ?: "mixed",
            testSelectedWordLevels = prefs[PreferencesKeys.TEST_SELECTED_WORD_LEVELS] ?: setOf("N5", "N4", "N3", "N2", "N1"),
            testSelectedGrammarLevels = prefs[PreferencesKeys.TEST_SELECTED_GRAMMAR_LEVELS] ?: setOf("N5", "N4", "N3", "N2", "N1"),

            comprehensiveTestMultipleChoiceCount = prefs[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] ?: 4,
            comprehensiveTestTypingCount = prefs[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] ?: 3,
            comprehensiveTestCardMatchingCount = prefs[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] ?: 2,
            comprehensiveTestSortingCount = prefs[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] ?: 5,

            ttsSpeechRate = prefs[PreferencesKeys.TTS_SPEECH_RATE] ?: 1.0f,
            ttsPitch = prefs[PreferencesKeys.TTS_PITCH] ?: 1.0f,
            ttsVoiceName = prefs[PreferencesKeys.TTS_VOICE_NAME],
            isAutoPlayAudioEnabled = prefs[PreferencesKeys.IS_AUTO_PLAY_AUDIO_ENABLED] ?: true,

            learningSteps = (prefs[PreferencesKeys.LEARNING_STEPS] ?: "1 10")
                .split(" ")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() },
            learnAheadLimit = prefs[PreferencesKeys.LEARN_AHEAD_LIMIT] ?: 20,
            relearningSteps = (prefs[PreferencesKeys.RELEARNING_STEPS] ?: "10")
                .split(" ")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() },
            isRandomNewContentEnabled = prefs[PreferencesKeys.IS_RANDOM_NEW_CONTENT_ENABLED] ?: true,

            isSyncOnLearningComplete = prefs[PreferencesKeys.SYNC_ON_LEARNING_COMPLETE] ?: true,
            isSyncOnTestComplete = prefs[PreferencesKeys.SYNC_ON_TEST_COMPLETE] ?: true,
            fsrsTargetRetention = (prefs[PreferencesKeys.FSRS_TARGET_RETENTION] ?: 0.9f).toDouble(),
            leechThreshold = prefs[PreferencesKeys.LEECH_THRESHOLD] ?: 8,
            leechAction = prefs[PreferencesKeys.LEECH_ACTION] ?: "skip",
            isShowAnswerDelayEnabled = prefs[PreferencesKeys.IS_SHOW_ANSWER_DELAY_ENABLED] ?: false,
            lastSettingsModifiedTime = prefs[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] ?: 0L
        )
    }

    override suspend fun applyAppSettingsSnapshot(settings: AppSettings) {
        dataStore.edit { prefs ->
            // [MOD] 排除主题同步：不再从快照中应用主题设置
            /*
            when (settings.theme) {
                "dark" -> prefs[PreferencesKeys.IS_DARK_MODE] = true
                "light" -> prefs[PreferencesKeys.IS_DARK_MODE] = false
                else -> prefs.remove(PreferencesKeys.IS_DARK_MODE)
            }
            */

            prefs[PreferencesKeys.DAILY_GOAL] = settings.dailyGoal
            prefs[PreferencesKeys.GRAMMAR_DAILY_GOAL] = settings.grammarDailyGoal

            prefs[PreferencesKeys.IS_DYNAMIC_COLOR_ENABLED] = settings.isDynamicColorEnabled
            prefs[PreferencesKeys.LEARNING_DAY_RESET_HOUR] = settings.learningDayResetHour

            prefs[PreferencesKeys.TEST_QUESTION_COUNT] = settings.testQuestionCount
            prefs[PreferencesKeys.TEST_TIME_LIMIT_MINUTES] = settings.testTimeLimitMinutes
            prefs[PreferencesKeys.TEST_SHUFFLE_QUESTIONS] = settings.testShuffleQuestions
            prefs[PreferencesKeys.TEST_SHUFFLE_OPTIONS] = settings.testShuffleOptions
            prefs[PreferencesKeys.TEST_AUTO_ADVANCE] = settings.testAutoAdvance
            prefs[PreferencesKeys.TEST_PRIORITIZE_WRONG] = settings.testPrioritizeWrong
            prefs[PreferencesKeys.TEST_PRIORITIZE_NEW] = settings.testPrioritizeNew
            prefs[PreferencesKeys.TEST_QUESTION_SOURCE] = settings.testQuestionSource
            prefs[PreferencesKeys.TEST_WRONG_ANSWER_REMOVAL_THRESHOLD] = settings.testWrongAnswerRemovalThreshold
            prefs[PreferencesKeys.TEST_CONTENT_TYPE] = settings.testContentType
            prefs[PreferencesKeys.TEST_SELECTED_WORD_LEVELS] = settings.testSelectedWordLevels
            prefs[PreferencesKeys.TEST_SELECTED_GRAMMAR_LEVELS] = settings.testSelectedGrammarLevels

            prefs[PreferencesKeys.COMPREHENSIVE_TEST_MC_COUNT] = settings.comprehensiveTestMultipleChoiceCount
            prefs[PreferencesKeys.COMPREHENSIVE_TEST_TYPING_COUNT] = settings.comprehensiveTestTypingCount
            prefs[PreferencesKeys.COMPREHENSIVE_TEST_MATCHING_COUNT] = settings.comprehensiveTestCardMatchingCount
            prefs[PreferencesKeys.COMPREHENSIVE_TEST_SORTING_COUNT] = settings.comprehensiveTestSortingCount

            prefs[PreferencesKeys.TTS_SPEECH_RATE] = settings.ttsSpeechRate
            prefs[PreferencesKeys.TTS_PITCH] = settings.ttsPitch

            val voiceName = settings.ttsVoiceName
            if (voiceName != null) {
                prefs[PreferencesKeys.TTS_VOICE_NAME] = voiceName
            } else {
                prefs.remove(PreferencesKeys.TTS_VOICE_NAME)
            }

            prefs[PreferencesKeys.IS_AUTO_PLAY_AUDIO_ENABLED] = settings.isAutoPlayAudioEnabled

            prefs[PreferencesKeys.LEARNING_STEPS] = settings.learningSteps.joinToString(" ")
            prefs[PreferencesKeys.LEARN_AHEAD_LIMIT] = settings.learnAheadLimit
            prefs[PreferencesKeys.RELEARNING_STEPS] = settings.relearningSteps.joinToString(" ")
            prefs[PreferencesKeys.IS_RANDOM_NEW_CONTENT_ENABLED] = settings.isRandomNewContentEnabled
            prefs[PreferencesKeys.SYNC_ON_LEARNING_COMPLETE] = settings.isSyncOnLearningComplete
            prefs[PreferencesKeys.SYNC_ON_TEST_COMPLETE] = settings.isSyncOnTestComplete
            prefs[PreferencesKeys.FSRS_TARGET_RETENTION] = settings.fsrsTargetRetention.toFloat()
            prefs[PreferencesKeys.LEECH_THRESHOLD] = settings.leechThreshold.coerceAtLeast(1)
            prefs[PreferencesKeys.LEECH_ACTION] = settings.leechAction
            prefs[PreferencesKeys.IS_SHOW_ANSWER_DELAY_ENABLED] = settings.isShowAnswerDelayEnabled

            // Update timestamp
            prefs[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = settings.lastSettingsModifiedTime
        }
        Log.d(TAG, "已应用云端设置快照 (不含主题)")
    }

    override val lastSettingsModifiedTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] ?: 0L
    }

    override suspend fun updateLastSettingsModifiedTime() {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    // ========== 应用图标实现 ==========

    override val appIconFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_ICON] ?: "Nemo"
    }

    override suspend fun setAppIcon(iconName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_ICON] = iconName
            preferences[PreferencesKeys.LAST_SETTINGS_MODIFIED_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * 迁移待生效的目标
     */
    private fun migratePendingGoals(preferences: MutablePreferences, today: Long) {
        val setDate = preferences[PreferencesKeys.PENDING_GOAL_SET_DATE] ?: 0L
        // 核心逻辑：只有当当前逻辑日 > 设置暂存目标的逻辑日时，才执行激活转换
        if (today > setDate) {
            val pendingWordGoal = preferences[PreferencesKeys.PENDING_DAILY_GOAL]
            if (pendingWordGoal != null) {
                preferences[PreferencesKeys.DAILY_GOAL] = pendingWordGoal
                preferences.remove(PreferencesKeys.PENDING_DAILY_GOAL)
                Log.i(TAG, "📅 检测到跨天且确认目标已入库一整天，应用新的每日单词目标: $pendingWordGoal")
            }

            val pendingGrammarGoal = preferences[PreferencesKeys.PENDING_GRAMMAR_DAILY_GOAL]
            if (pendingGrammarGoal != null) {
                preferences[PreferencesKeys.GRAMMAR_DAILY_GOAL] = pendingGrammarGoal
                preferences.remove(PreferencesKeys.PENDING_GRAMMAR_DAILY_GOAL)
                Log.i(TAG, "📅 检测到跨天且确认目标已入库一整天，应用新的每日语法目标: $pendingGrammarGoal")
            }

            // 完成迁移后，同步清理暂存日期键值
            preferences.remove(PreferencesKeys.PENDING_GOAL_SET_DATE)
        } else {
            Log.d(TAG, "📅 检测到跨天，但目标是在当前逻辑日内设置的，顺延至下一个重置点生效 (今日: $today, 设置日: $setDate)")
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
    }
}
