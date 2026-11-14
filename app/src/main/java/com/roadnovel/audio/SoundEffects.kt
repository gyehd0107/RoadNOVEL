package com.roadnovel.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.roadnovel.R

/** 효과음 종류 */
enum class Sfx { SCENE, CHOICE, PICK, REST, ENDING }

/**
 * 효과음·배경음. 소리 파일은 tools/make_sounds.py로 직접 합성했다 (외부 음원 없음).
 */
interface SoundEffects {
    fun play(sfx: Sfx)

    /** 배경음 시작(또는 다시 재생) */
    fun startBgm()
    fun pauseBgm()
    fun stopBgm()

    /** 대사를 읽는 중이면 배경음을 더 작게 */
    fun setSpeaking(speaking: Boolean)

    /** 🔇 자막만 모드면 모두 끔 */
    fun setEnabled(enabled: Boolean)
}

/** 소리 없음 (테스트·Mock용) */
class NoSoundEffects : SoundEffects {
    override fun play(sfx: Sfx) = Unit
    override fun startBgm() = Unit
    override fun pauseBgm() = Unit
    override fun stopBgm() = Unit
    override fun setSpeaking(speaking: Boolean) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
}

class AndroidSoundEffects(context: Context) : SoundEffects {

    private val app = context.applicationContext
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.SCENE to R.raw.sfx_scene,
        Sfx.CHOICE to R.raw.sfx_choice,
        Sfx.PICK to R.raw.sfx_pick,
        Sfx.REST to R.raw.sfx_rest,
        Sfx.ENDING to R.raw.sfx_ending,
    ).mapValues { (_, res) -> pool.load(app, res, 1) }

    private var bgm: MediaPlayer? = null
    private var enabled = true
    private var speaking = false
    private var bgmWanted = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
    }

    override fun play(sfx: Sfx) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        if (id in loaded) pool.play(id, SFX_VOLUME, SFX_VOLUME, 1, 0, 1f).also { Log.d(TAG, "효과음 $sfx") }
    }

    override fun startBgm() {
        bgmWanted = true
        if (!enabled) return
        // MediaPlayer.create()는 이미 준비된 상태로 돌려줘서 그 뒤에는 AudioAttributes를 바꿀 수 없다
        // → 속성을 먼저 정하고 직접 prepare()
        val player = bgm ?: runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                app.resources.openRawResourceFd(R.raw.bgm_night).use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                isLooping = true
                prepare()
            }
        }.onFailure { Log.w(TAG, "배경음 준비 실패: ${it.message}") }.getOrNull()?.also { bgm = it }
        player ?: return
        applyVolume()
        if (!player.isPlaying) player.start().also { Log.d(TAG, "배경음 재생") }
    }

    override fun pauseBgm() {
        bgmWanted = false
        bgm?.takeIf { it.isPlaying }?.pause()?.also { Log.d(TAG, "배경음 일시정지") }
    }

    override fun stopBgm() {
        bgmWanted = false
        bgm?.run { runCatching { stop() }; release(); Log.d(TAG, "배경음 끝") }
        bgm = null
    }

    override fun setSpeaking(speaking: Boolean) {
        this.speaking = speaking
        applyVolume()
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) bgm?.takeIf { it.isPlaying }?.pause()
        else if (bgmWanted) startBgm()
    }

    private fun applyVolume() {
        val v = if (speaking) BGM_UNDER_VOICE else BGM_VOLUME
        bgm?.setVolume(v, v)
    }

    private companion object {
        const val TAG = "SoundEffects"
        const val SFX_VOLUME = 0.7f
        /** 기다리는 동안(배경음만) */
        const val BGM_VOLUME = 0.30f
        /** 대사를 읽는 동안 */
        const val BGM_UNDER_VOICE = 0.12f
    }
}
