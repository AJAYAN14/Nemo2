package com.jian.nemo2.core.domain.usecase.audio

import com.jian.nemo2.core.domain.repository.AudioEffectType
import com.jian.nemo2.core.domain.repository.AudioRepository
import javax.inject.Inject

class PlaySoundEffectUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    operator fun invoke(type: AudioEffectType) {
        audioRepository.playSoundEffect(type)
    }
}
