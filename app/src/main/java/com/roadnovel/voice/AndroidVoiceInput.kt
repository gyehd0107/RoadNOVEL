package com.roadnovel.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android 기본 SpeechRecognizer로 대답 듣기 (한국어).
 * 권한이 없거나 인식기가 없으면 바로 null → 화면 버튼으로 고르게 된다.
 */
class AndroidVoiceInput(private val context: Context) : VoiceInput {

    override suspend fun listen(timeoutSec: Int): String? {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "마이크 권한 없음 → 버튼으로만 선택")
            return null
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "음성 인식기 없음 → 버튼으로만 선택")
            return null
        }
        // SpeechRecognizer는 메인 스레드에서만 써야 한다
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutSec * 1000L) { recognizeOnce() }
        }
    }

    private suspend fun recognizeOnce(): String? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        fun done(result: String?) {
            if (cont.isActive) cont.resume(result)
            recognizer.destroy()
        }
        cont.invokeOnCancellation { recognizer.cancel(); recognizer.destroy() }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                Log.i(TAG, "인식 결과: $list")
                // 후보를 모두 이어 붙여 키워드가 하나라도 걸리게 한다
                done(list.joinToString(" ").ifBlank { null })
            }
            override fun onError(error: Int) {
                Log.w(TAG, "인식 실패 (error=$error)")
                done(null)
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    private companion object {
        const val TAG = "AndroidVoiceInput"
    }
}
