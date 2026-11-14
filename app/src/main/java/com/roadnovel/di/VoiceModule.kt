package com.roadnovel.di

import android.content.Context
import com.roadnovel.BuildConfig
import com.roadnovel.voice.AndroidNarrator
import com.roadnovel.voice.AndroidVoiceInput
import com.roadnovel.voice.Narrator
import com.roadnovel.voice.NoVoiceInput
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
 *   "android" : Android 기본 TTS/STT (기본값)
 *   "pleos"   : Pleos SDK (지금은 Android 기본으로 대신함 — TODO pleos-check)
 *   "mock"    : 소리 없이 자막만 + 버튼으로만 선택
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    private val engine get() = VoiceEngine.from(BuildConfig.VOICE_ENGINE)

    @Provides
    @Singleton
    fun narrator(@ApplicationContext context: Context): Narrator = when (engine) {
        VoiceEngine.ANDROID -> AndroidNarrator(context)
        VoiceEngine.PLEOS -> PleosNarrator(context)
        VoiceEngine.MOCK -> SubtitleNarrator()
    }

    @Provides
    @Singleton
    fun voiceInput(@ApplicationContext context: Context): VoiceInput = when (engine) {
        VoiceEngine.ANDROID -> AndroidVoiceInput(context)
        VoiceEngine.PLEOS -> PleosVoiceInput(context)
        VoiceEngine.MOCK -> NoVoiceInput()
    }
}
