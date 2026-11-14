package com.roadnovel.voice

import com.roadnovel.story.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    @Test
    fun `음성 엔진 이름은 대소문자 상관없이 고르고 모르면 android`() {
        assertEquals(VoiceEngine.PLEOS, VoiceEngine.from("pleos"))
        assertEquals(VoiceEngine.MOCK, VoiceEngine.from("MOCK"))
        assertEquals(VoiceEngine.ANDROID, VoiceEngine.from("없는엔진"))
    }

    @Test
    fun `자막만 모드는 말이 느린 인물일수록 오래 보여 준다`() {
        val text = "누가… 내 다리를 건너려고 하느냐…"
        val normal = SubtitleNarrator.subtitleMillis(text)
        val giant = SubtitleNarrator.subtitleMillis(text, VoiceStyle(pitch = 0.55f, rate = 0.85f))
        assertTrue(giant > normal)
        assertEquals(1200L + text.length * 95L + 400L, normal)
    }
}
