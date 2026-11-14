package com.roadnovel.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import com.roadnovel.story.VoiceStyle
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServerListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServerListener

/*
 * Pleos TextToSpeech / SpeechToText SDK 3.3.1 구현.
 * 근거: docs/Pleos_음성SDK_정리.md + SDK jar에서 직접 확인한 공개 API (javap).
 *
 * - 인물별 목소리 높이·속도 API는 SDK에 없다 → 모두 같은 목소리로 읽고, 화면의 얼굴·이름으로 구분.
 *   (setSpeakerType(String)은 있지만 값이 문서에 없어서 쓰지 않음)
 * - 기본은 ON_DEVICE (앱 등록 없이 동작). HYBRID는 local.properties의 Client ID/Secret으로 registerApp.
 */

/** Pleos 음성 설정 (BuildConfig ← local.properties) */
data class PleosConfig(val mode: String, val clientId: String, val clientSecret: String) {
    val hybrid: Boolean get() = mode.equals("HYBRID", ignoreCase = true)
    val canRegister: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}

/** Pleos TTS로 읽기. 실패하면 그 줄은 자막만 보여 주고 넘어간다. */
class PleosNarrator(context: Context, private val config: PleosConfig) : Narrator {

    private val ready = CompletableDeferred<Boolean>()
    private val fallback = SubtitleNarrator()
    @Volatile private var current: CompletableDeferred<Boolean>? = null // true=정상 끝, false=오류

    private val tts = TextToSpeech(context.applicationContext, if (config.hybrid) TtsMode.HYBRID else TtsMode.ON_DEVICE)

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { current?.complete(true) }
        override fun onStop() { current?.complete(true) }
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "TTS 오류: $errorType")
            current?.complete(false)
        }
    }

    init {
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() {
                tts.addEventListener(events)
                tts.setLocale(Locale.KOREA)
                if (config.hybrid) registerForHybrid() else markReady(true)
            }
            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "TTS 초기화 실패: $errorType → 자막만")
                markReady(false)
            }
        })
    }

    /** HYBRID 모드: 앱 등록 후 서버 연결되면 준비 완료. Client ID가 없으면 온디바이스로만 */
    private fun registerForHybrid() {
        if (!config.canRegister) {
            Log.w(TAG, "HYBRID인데 Client ID/Secret 없음 (local.properties) → 등록 없이 진행")
            return markReady(true)
        }
        tts.registerApp(config.clientId, config.clientSecret, object : TtsServerListener {
            override fun onConnected() = markReady(true)
            override fun onFailed(errorType: TtsError) {
                // HYBRID는 서버가 안 되면 온디바이스로 전환된다 (문서)
                Log.w(TAG, "TTS 서버 연결 실패: $errorType → 온디바이스로")
                markReady(true)
            }
        })
    }

    private fun markReady(ok: Boolean) {
        Log.i(TAG, "Pleos TTS 준비 ${if (ok) "완료" else "실패"} (mode=${config.mode})")
        ready.complete(ok)
    }

    override suspend fun speak(text: String, voice: VoiceStyle) {
        val ok = withTimeoutOrNull(INIT_WAIT_MS) { ready.await() } ?: false
        if (!ok) return fallback.speak(text, voice)

        val done = CompletableDeferred<Boolean>()
        current = done
        val started = System.currentTimeMillis()
        try {
            // 인물별 목소리 API가 없어서 voice(pitch/rate)는 쓰지 않는다
            tts.speak(text, true)
            val limit = SubtitleNarrator.subtitleMillis(text, voice) * 2 + 5_000
            val success = withTimeoutOrNull(limit) { done.await() }
            Log.d(TAG, "읽기 끝: ${when (success) { true -> "onDone"; false -> "onError"; null -> "시간 초과" }} (${System.currentTimeMillis() - started}ms, ${text.length}자)")
            if (success != true) {
                // 오류·응답 없음 → 그 줄은 자막만 보여 주고 넘어감
                if (success == null) tts.stop()
                val left = SubtitleNarrator.subtitleMillis(text, voice) - (System.currentTimeMillis() - started)
                if (left > 0) delay(left)
            }
        } finally {
            if (current === done) current = null
        }
    }

    override fun stop() {
        current?.complete(true)
        runCatching { tts.stop() }
    }

    // TODO(M7): Pleos TTS에는 볼륨 API가 없다. 덕킹은 AudioFocus/AudioAttributes 쪽에서 처리
    override fun setDucked(ducked: Boolean) = Unit

    private companion object {
        const val TAG = "PleosNarrator"
        const val INIT_WAIT_MS = 5_000L
    }
}

/** 시험용 녹음(PCM)을 마이크 대신 보낼 수 있는 입력기 (디버그 패널용) */
interface TestAudioInput {
    /** 16kHz·16bit·mono PCM. ON_DEVICE 모드에서만 동작 (문서) */
    fun sendTestAudio(pcm: ByteArray)
}

/** Pleos STT로 대답 듣기. onUpdated(stt, completed=true)를 최종 결과로 쓴다. */
class PleosVoiceInput(context: Context, private val config: PleosConfig) : VoiceInput, TestAudioInput {

    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var current: CompletableDeferred<String?>? = null
    /** 시험 음성을 보내는 중: 마이크 인식을 멈추며 생기는 "끝/오류" 이벤트는 무시 */
    @Volatile private var injecting = false

    private val stt = SpeechToText(context.applicationContext, if (config.hybrid) SttMode.HYBRID else SttMode.ON_DEVICE)

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() { Log.i(TAG, "인식 시작") }
        override fun onUpdated(stt: String, completed: Boolean) {
            if (completed) {
                Log.i(TAG, "최종 인식: \"$stt\"")
                if (injecting && stt.isBlank()) return // 멈춘 마이크 인식의 빈 결과
                injecting = false
                current?.complete(stt.ifBlank { null })
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            Log.i(TAG, "인식 끝${if (injecting) " (시험 음성 대기 중 → 무시)" else ""}")
            if (injecting) return
            // SDK는 "인식 끝"을 최종 결과보다 먼저 보낸다 (에뮬레이터 로그에서 확인)
            // → 잠깐 기다렸다가 그때도 결과가 없으면 실패로 처리
            val waiting = current ?: return
            mainHandler.postDelayed({ waiting.complete(null) }, FINAL_RESULT_GRACE_MS)
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT 오류: $errorType${if (injecting) " (시험 음성 대기 중 → 무시)" else ""}")
            if (!injecting) current?.complete(null)
        }
    }

    init {
        stt.initialize(object : SttInitListener {
            override fun onSuccess() {
                stt.addListener(results) // request() 전에 등록해야 이벤트를 놓치지 않음
                stt.setLocale(Locale.KOREA)
                if (config.hybrid && config.canRegister) {
                    stt.registerApp(config.clientId, config.clientSecret, object : SttServerListener {
                        override fun onConnected() = markReady(true)
                        override fun onFailed(errorType: SttError) {
                            Log.w(TAG, "STT 서버 연결 실패: $errorType → 온디바이스로")
                            markReady(true)
                        }
                    })
                } else {
                    markReady(true)
                }
            }
            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "STT 초기화 실패: $errorType → 버튼으로만")
                markReady(false)
            }
        })
    }

    private fun markReady(ok: Boolean) {
        Log.i(TAG, "Pleos STT 준비 ${if (ok) "완료" else "실패"} (mode=${config.mode})")
        ready.complete(ok)
    }

    override suspend fun listen(timeoutSec: Int): String? {
        val ok = withTimeoutOrNull(INIT_WAIT_MS) { ready.await() } ?: false
        if (!ok) return null
        val result = CompletableDeferred<String?>()
        current = result
        return try {
            stt.request() // 차량 마이크로 인식 시작
            withTimeoutOrNull(timeoutSec * 1000L) { result.await() }
        } finally {
            if (current === result) current = null
            injecting = false
            runCatching { stt.stop() }
        }
    }

    override fun sendTestAudio(pcm: ByteArray) {
        Log.i(TAG, "시험 음성 보내기 (${pcm.size} bytes)")
        injecting = true
        runCatching { stt.stop() } // 마이크 인식 중이면 멈추고
        stt.sendAudio(pcm)
        stt.completeAudioSend() // 마지막에 꼭 호출 (문서)
    }

    private companion object {
        const val TAG = "PleosVoiceInput"
        const val INIT_WAIT_MS = 5_000L
        const val FINAL_RESULT_GRACE_MS = 1_000L
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
}
