package com.jian.nemo2.core.domain.usecase.content

import com.jian.nemo2.core.domain.repository.ContentRepository
import com.jian.nemo2.core.domain.repository.ContentUpdateApplier
import com.jian.nemo2.core.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * 检查云端词库版本，若有更新则下载并合并到本地
 *
 * @return 本次应用的云端版本号（未更新返回 null）
 */
class UpdateContentFromCloudUseCase @Inject constructor(
    private val contentRepository: ContentRepository,
    private val contentUpdateApplier: ContentUpdateApplier,
    private val settingsRepository: SettingsRepository
) {
    private val levels = listOf("N1", "N2", "N3", "N4", "N5")

    suspend fun run(): Int? {
        val remoteVersion = contentRepository.getRemoteContentVersion() ?: return null
        val lastVersion = settingsRepository.getLastContentVersion()
        if (remoteVersion <= lastVersion) return null

        for (level in levels) {
            val words = contentRepository.fetchRemoteWords(level)
            if (words.isNotEmpty()) {
                contentUpdateApplier.applyWords(level, words)
            }

            val grammars = contentRepository.fetchRemoteGrammars(level)
            if (grammars.isNotEmpty()) {
                contentUpdateApplier.applyGrammars(level, grammars)
            }

            val questions = contentRepository.fetchRemoteGrammarQuestions(level)
            if (questions.isNotEmpty()) {
                contentUpdateApplier.applyGrammarQuestions(level, questions)
            }
        }

        settingsRepository.setLastContentVersion(remoteVersion)
        return remoteVersion
    }
}
