package com.roadnovel.di

import android.content.Context
import com.roadnovel.BuildConfig
import com.roadnovel.voice.AndroidNarrator
import com.roadnovel.voice.AndroidVoiceInput
import com.roadnovel.voice.Narrator
import com.roadnovel.voice.NoVoiceInput
import com.roadnovel.voice.PleosConfig
import com.roadnovel.voice.PleosNarrator
import com.roadnovel.voice.PleosVoiceInput
import com.roadnovel.voice.SubtitleNarrator
import com.roadnovel.voice.VoiceEngine
import com.roadnovel.voice.VoiceInput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 음성 엔진 고르기. app/build.gradle.kts 의 VOICE_ENGINE 한 줄만 바꾸면 된다.
 *   "android" : Android 기본 TTS/STT (일반 안드로이드 기기용)
 *   "pleos"   : Pleos TextToSpeech/SpeechToText SDK (기본값. Pleos 에뮬레이터에서는 이것만 소리가 남)
 *   "mock"    : 소리 없이 자막만 + 버튼으로만 선택
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    private val engine get() = VoiceEngine.from(BuildConfig.VOICE_ENGINE)

    private val pleosConfig get() = PleosConfig(
        mode = BuildConfig.PLEOS_MODE,
        clientId = BuildConfig.PLEOS_CLIENT_ID,
        clientSecret = BuildConfig.PLEOS_CLIENT_SECRET,
    )

    @Provides
    @Singleton
    fun narrator(@ApplicationContext context: Context): Narrator = when (engine) {
        VoiceEngine.ANDROID -> AndroidNarrator(context)
        VoiceEngine.PLEOS -> PleosNarrator(context, pleosConfig)
        VoiceEngine.MOCK -> SubtitleNarrator()
    }

    @Provides
    @Singleton
    fun voiceInput(@ApplicationContext context: Context): VoiceInput = when (engine) {
        VoiceEngine.ANDROID -> AndroidVoiceInput(context)
        VoiceEngine.PLEOS -> PleosVoiceInput(context, pleosConfig)
        VoiceEngine.MOCK -> NoVoiceInput()
    }
}
