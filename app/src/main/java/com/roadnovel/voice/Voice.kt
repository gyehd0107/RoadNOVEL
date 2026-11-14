package com.roadnovel.voice

import com.roadnovel.story.VoiceStyle
import kotlinx.coroutines.delay

/** 이야기를 소리 내어 읽는 쪽 (TTS). CLAUDE.md 6장 */
interface Narrator {
    /** 다 읽을 때까지 기다린다. 실패해도 예외 없이 끝난다 */
    suspend fun speak(text: String, voice: VoiceStyle)
    fun stop()
    /** 내비 안내 등이 나올 때 소리를 줄임 */
    fun setDucked(ducked: Boolean)
}

/** 음성으로 대답을 듣는 쪽 (STT) */
interface VoiceInput {
    /** 인식된 문장 (후보가 여럿이면 공백으로 이어 붙임). 실패·시간 초과면 null */
    suspend fun listen(timeoutSec: Int): String?
}

/** 소리 없이 자막만: 글자 수만큼 기다린다 (TTS가 없을 때·"자막만" 모드) */
class SubtitleNarrator : Narrator {
    override suspend fun speak(text: String, voice: VoiceStyle) = delay(subtitleMillis(text, voice))
    override fun stop() = Unit
    override fun setDucked(ducked: Boolean) = Unit

    companion object {
        /** 프로토타입과 같은 계산 + 줄 사이 쉼. 말이 느린 인물은 조금 더 길게 */
        fun subtitleMillis(text: String, voice: VoiceStyle = VoiceStyle()): Long =
            ((1200L + text.length * 95L) / voice.rate.coerceIn(0.5f, 2f)).toLong() + 400L
    }
}

/** 음성 인식 없음: 화면 버튼으로만 고른다 */
class NoVoiceInput : VoiceInput {
    override suspend fun listen(timeoutSec: Int): String? = null
}

/** 어떤 음성 엔진을 쓸지 (app/build.gradle.kts 의 VOICE_ENGINE) */
enum class VoiceEngine { ANDROID, PLEOS, MOCK;
    companion object {
        fun from(name: String) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ANDROID
    }
}
