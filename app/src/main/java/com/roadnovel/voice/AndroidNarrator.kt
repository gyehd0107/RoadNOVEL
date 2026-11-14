package com.roadnovel.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.roadnovel.story.VoiceStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android 기본 TextToSpeech로 읽기. 인물마다 목소리 높이(pitch)·속도(rate)를 바꾼다.
 * (Pleos 에뮬레이터에서는 시스템 TTS 엔진이 Pleos 쪽 서비스로 연결된다)
 * TTS를 못 쓰면(엔진 없음, 한국어 없음) 자동으로 자막만 모드로 동작한다.
 */
class AndroidNarrator(private val context: Context) : Narrator {

    private val ready = CompletableDeferred<Boolean>()
    private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val fallback = SubtitleNarrator()
    @Volatile private var volume = 1f

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(id: String) = Unit
        override fun onDone(id: String) = finish(id)
        override fun onStop(id: String, interrupted: Boolean) = finish(id)
        @Deprecated("Deprecated in Java")
        override fun onError(id: String) = finish(id)
        override fun onError(id: String, errorCode: Int) = finish(id)
    }

    @Volatile private var tts: TextToSpeech = createTts(engine = null)

    /**
     * 기본 엔진으로 먼저 시도. 실패하면 TTS_SERVICE 를 받는 서비스를 직접 찾아 한 번 더 시도한다.
     * (에뮬레이터처럼 엔진 목록이 비어 있는 경우 대비)
     */
    private fun createTts(engine: String?): TextToSpeech {
        lateinit var created: TextToSpeech
        created = TextToSpeech(context.applicationContext, { status ->
            Log.i(TAG, "TTS onInit engine=${engine ?: "기본"} status=$status")
            when {
                status == TextToSpeech.SUCCESS -> ready.complete(setupKorean())
                engine == null -> fallbackEngine()?.let { tts = createTts(it) } ?: ready.complete(false)
                else -> ready.complete(false)
            }
        }, engine)
        created.setOnUtteranceProgressListener(progress)
        return created
    }

    private fun fallbackEngine(): String? {
        val found = context.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }
            .distinct()
        Log.i(TAG, "기본 TTS 엔진 없음 → TTS_SERVICE 후보: $found")
        return found.firstOrNull()
    }


    private fun setupKorean(): Boolean {
        val r = tts.setLanguage(Locale.KOREAN)
        val ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        Log.i(TAG, "TTS 준비 완료 (엔진=${tts.defaultEngine}, 한국어=${if (ok) "OK" else "없음"})")
        return ok
    }

    private fun finish(id: String) {
        waiting.remove(id)?.let { if (it.isActive) it.resume(Unit) }
    }

    override suspend fun speak(text: String, voice: VoiceStyle) {
        // 엔진 준비를 최대 3초까지 기다리고, 안 되면 자막만
        val ok = withTimeoutOrNull(3_000) { ready.await() } ?: false
        if (!ok) return fallback.speak(text, voice)

        // 소리가 안 끝나는 경우 대비: 넉넉한 시간이 지나면 넘어간다
        val limit = SubtitleNarrator.subtitleMillis(text, voice) * 2 + 5_000
        val finished = withTimeoutOrNull(limit) {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                waiting[id] = cont
                cont.invokeOnCancellation { waiting.remove(id); tts.stop() }
                tts.setPitch(voice.pitch.coerceIn(0.5f, 2f))
                tts.setSpeechRate(voice.rate.coerceIn(0.5f, 2f))
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
                    finish(id)
                }
            }
        }
        if (finished == null) tts.stop()
    }

    override fun stop() {
        tts.stop()
        waiting.keys.toList().forEach(::finish)
    }

    override fun setDucked(ducked: Boolean) {
        volume = if (ducked) DUCK_VOLUME else 1f
    }

    private companion object {
        const val TAG = "AndroidNarrator"
        const val DUCK_VOLUME = 0.25f
    }
}
