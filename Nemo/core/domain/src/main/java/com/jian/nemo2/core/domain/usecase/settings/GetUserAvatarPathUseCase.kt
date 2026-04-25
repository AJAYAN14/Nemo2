package com.jian.nemo2.core.domain.usecase.settings

import com.jian.nemo2.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUserAvatarPathUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<String> {
        return settingsRepository.userAvatarPathFlow
    }
}
