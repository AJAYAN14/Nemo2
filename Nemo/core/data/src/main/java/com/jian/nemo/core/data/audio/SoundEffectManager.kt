package com.jian.nemo.core.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class SoundEffectType {
    CORRECT,
    WRONG,
    COMPLETE
}

@Singleton
class SoundEffectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundEffectType, Int>()
    private var isLoaded = false

    // 这里假设音效文件放在 assets/audio/ 目录下
    // 实际项目中需要确保这些文件存在
    private val soundFiles = mapOf(
        SoundEffectType.CORRECT to "audio/correct.mp3",
        SoundEffectType.WRONG to "audio/wrong.mp3",
        SoundEffectType.COMPLETE to "audio/complete.mp3"
    )

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, _, _ ->
            isLoaded = true
        }

        loadSounds()
    }

    private fun loadSounds() {
        soundFiles.forEach { (type, filename) ->
            try {
                context.assets.openFd(filename).use { afd ->
                    val soundId = soundPool?.load(afd, 1) ?: 0
                    if (soundId != 0) {
                        soundMap[type] = soundId
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun play(type: SoundEffectType) {
        val soundId = soundMap[type]
        if (soundId != null && soundId != 0) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
             // Fallback or retry loading?
             // Currently simplified to just ignore or maybe log
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
