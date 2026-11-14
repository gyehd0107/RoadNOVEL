package com.roadnovel.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.roadnovel.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 오디오 포커스 상태 (CLAUDE.md 11장) */
enum class FocusState {
    /** 포커스 없음 (재생 전) */
    NONE,
    /** 우리가 소리를 내도 됨 */
    GAINED,
    /** 내비 안내 등 잠깐 끼어듦 (LOSS_TRANSIENT_CAN_DUCK) */
    DUCK,
    /** 전화 등 잠깐 멈춰야 함 (LOSS_TRANSIENT) */
    PAUSE,
    /** 다른 미디어 앱이 가져감 (LOSS) — 사용자가 다시 누를 때까지 멈춤 */
    LOST,
}

/**
 * 이야기 재생용 오디오 포커스.
 * 이야기는 말소리(CONTENT_TYPE_SPEECH)인 미디어(USAGE_MEDIA)로 포커스를 잡는다.
 */
class AudioFocusController(context: Context) {

    private val am = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(FocusState.NONE)
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val next = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusState.GAINED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusState.DUCK
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusState.PAUSE
            AudioManager.AUDIOFOCUS_LOSS -> FocusState.LOST
            else -> return@OnAudioFocusChangeListener
        }
        Log.i(TAG, "포커스 변화: ${name(change)} → $next")
        _state.value = next
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        // 덕킹을 시스템에 맡기지 않고 직접 처리 (TTS 볼륨을 못 줄여서 낭독은 잠깐 멈춤)
        .setWillPauseWhenDucked(true)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
        .build()

    /** 이야기 재생을 시작할 때 */
    fun request() {
        val r = am.requestAudioFocus(request)
        Log.i(TAG, "포커스 요청 결과: ${when (r) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "허용"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "나중에"
            else -> "거부"
        }}")
        _state.value = when (r) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> FocusState.GAINED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> FocusState.PAUSE
            else -> FocusState.LOST
        }
    }

    /** 이야기를 끝낼 때 */
    fun abandon() {
        am.abandonAudioFocusRequest(request)
        _state.value = FocusState.NONE
    }

    private fun name(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        else -> change.toString()
    }

    private companion object {
        const val TAG = "AudioFocus"
    }
}

/**
 * 시연·시험용: 내비 안내가 끼어드는 상황을 진짜 오디오 포커스로 흉내 낸다.
 * USAGE_ASSISTANCE_NAVIGATION_GUIDANCE로 잠깐(GAIN_TRANSIENT_MAY_DUCK) 포커스를 잡고
 * 짧은 안내음을 낸 뒤 놓는다 → 시스템이 이야기 쪽에 포커스 이벤트를 보낸다.
 */
class NavPromptSimulator(context: Context) {

    private val app = context.applicationContext
    private val am = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
    private val tone = pool.load(app, R.raw.sfx_choice, 1)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener({ }, main)
        .build()

    /** [durationMs] 동안 내비 안내가 나오는 것처럼 */
    fun play(durationMs: Long = 3_000) {
        val r = am.requestAudioFocus(request)
        Log.i("NavPromptSim", "내비 안내 흉내: 포커스 요청 ${if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "허용" else "거부($r)"}")
        // "띵동" 두 번 = 안내 음성 자리
        pool.play(tone, 1f, 1f, 1, 0, 1f)
        main.postDelayed({ pool.play(tone, 1f, 1f, 1, 0, 1f) }, 1_200)
        main.postDelayed({
            am.abandonAudioFocusRequest(request)
            Log.i("NavPromptSim", "내비 안내 끝: 포커스 돌려줌")
        }, durationMs)
    }
}
