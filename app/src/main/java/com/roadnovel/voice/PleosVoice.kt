package com.roadnovel.voice

import android.content.Context
import android.util.Log
import com.roadnovel.story.VoiceStyle

/*
 * Pleos TextToSpeech / SpeechToText 구현 자리.
 *
 * TODO(pleos-check): 아직 Pleos 공식 문서(https://document.pleos.ai)에서 클래스·메서드 이름을
 *   확인하지 못했다 (문서 사이트가 자동으로 읽히지 않음). 지어내지 않기 위해 SDK를 부르는 코드는 없다.
 *   문서를 확인한 뒤 아래 순서로 채운다.
 *
 * CLAUDE.md 4장에 이미 정리된 내용 (확인 필요):
 *   - 저장소: settings.gradle.kts 에 maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
 *   - 의존성: implementation("ai.pleos.playground:TextToSpeech:<버전>"), ("ai.pleos.playground:SpeechToText:<버전>")
 *   - 권한: pleos.car.permission.STT_SERVICE (SpeechToText)
 *   - TTS: TextToSpeech(context, Mode.HYBRID) → initialize() → 사용 → release()
 *          HYBRID = 네트워크가 없으면 온디바이스로 전환 (터널 대비)
 *   - STT: 서버 인식은 registerApp 필요
 *
 * 그때까지는 Android 기본 구현을 그대로 쓴다. (Pleos 에뮬레이터에서는 Android 기본 TTS/STT가
 * 시스템에 등록된 Pleos 쪽 서비스로 연결되므로 실제로 소리가 난다)
 */

/** TODO(pleos-check): Pleos TextToSpeech SDK로 교체 */
class PleosNarrator(context: Context) : Narrator {
    private val delegate = AndroidNarrator(context)

    init {
        Log.w("PleosNarrator", "Pleos TTS SDK 미연결 → Android 기본 TTS 사용 (TODO pleos-check)")
    }

    override suspend fun speak(text: String, voice: VoiceStyle) = delegate.speak(text, voice)
    override fun stop() = delegate.stop()
    override fun setDucked(ducked: Boolean) = delegate.setDucked(ducked)
}

/** TODO(pleos-check): Pleos SpeechToText SDK로 교체 (registerApp 포함) */
class PleosVoiceInput(context: Context) : VoiceInput {
    private val delegate = AndroidVoiceInput(context)

    init {
        Log.w("PleosVoiceInput", "Pleos STT SDK 미연결 → Android 기본 음성 인식 사용 (TODO pleos-check)")
    }

    override suspend fun listen(timeoutSec: Int): String? = delegate.listen(timeoutSec)
}
