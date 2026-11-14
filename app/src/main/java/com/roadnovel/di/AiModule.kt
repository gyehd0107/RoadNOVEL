package com.roadnovel.di

import android.content.Context
import com.roadnovel.BuildConfig
import com.roadnovel.ai.DialogueGenerator
import com.roadnovel.ai.FallbackDialogueGenerator
import com.roadnovel.ai.MockDialogueGenerator
import com.roadnovel.ai.PleosDialogueGenerator
import com.roadnovel.voice.PleosConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI 추가 대사 엔진 고르기. app/build.gradle.kts 의 AI_ENGINE 한 줄만 바꾸면 된다.
 *   "pleos" : Pleos LLM 먼저, 실패(빈 결과)하면 미리 쓴 대사
 *   "mock"  : 미리 쓴 대사만 (기본값: 에뮬레이터의 on-device LLM이 꺼져 있음)
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun dialogueGenerator(@ApplicationContext context: Context): DialogueGenerator =
        if (BuildConfig.AI_ENGINE.equals("pleos", ignoreCase = true)) {
            FallbackDialogueGenerator(
                PleosDialogueGenerator(
                    context,
                    PleosConfig(BuildConfig.PLEOS_MODE, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET),
                ),
                MockDialogueGenerator(),
            )
        } else {
            MockDialogueGenerator()
        }
}
