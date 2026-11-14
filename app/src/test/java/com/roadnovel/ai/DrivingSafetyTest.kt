package com.roadnovel.ai

import com.roadnovel.story.StoryLine
import com.roadnovel.story.loadStarMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pleos 심사 기준: 차 안 위험 행동·위험 운전 유도 표현 금지 */
class DrivingSafetyTest {

    private val story = loadStarMap()

    @Test
    fun `창밖으로 손·머리·몸을 내밀게 하는 대사는 버린다`() {
        val lines = listOf(
            "창문 밖으로 손을 흔들어 봐!",
            "고개를 내밀고 별을 찾아보자!",
            "창밖으로 머리를 쏙 내밀어 봐",
            "창문을 열고 바람을 느껴 봐!",
            "안전벨트를 풀고 일어서 봐!",
        ).map { StoryLine("lumi", it) }
        val r = SafetyFilter.check(lines, story)
        assertTrue(r.kept.isEmpty())
        assertTrue(r.dropped.all { it.second == "차 안 위험 행동" })
    }

    @Test
    fun `과속·신호 위반·재촉 같은 위험 운전 유도는 버린다`() {
        val lines = listOf(
            "빨리 달려야 거인을 따라잡아!",
            "더 빨리! 더 빨리!",
            "빨간불이어도 그냥 가자!",
            "앞차를 추월해 버리자!",
        ).map { StoryLine("bolt", it) }
        val r = SafetyFilter.check(lines, story)
        assertTrue(r.kept.isEmpty())
        assertTrue(r.dropped.all { it.second == "위험 운전 유도" })
    }

    @Test
    fun `안전한 대사는 그대로 통과`() {
        val lines = listOf(
            StoryLine("lumi", "창밖을 봐! 별빛이 반짝여."),
            StoryLine("bolt", "삐빅, 천천히 가도 괜찮아."),
        )
        assertEquals(2, SafetyFilter.check(lines, story).kept.size)
    }

    @Test
    fun `별지도 탐험대 이야기의 모든 대사가 같은 기준을 통과한다`() {
        val texts = buildList {
            story.scenes.values.forEach { sc ->
                sc.lines.forEach { add(it.text) }
                sc.choice?.let { add(it.question) }
            }
            story.interludes.forEach { it.lines.forEach { l -> add(l.text) } }
        }
        val bad = texts.filter { t ->
            val c = t.replace(" ", "").lowercase()
            (SafetyFilter.BANNED + SafetyFilter.UNSAFE_IN_CAR + SafetyFilter.UNSAFE_DRIVING).any { it in c }
        }
        assertEquals("걸린 대사: $bad", emptyList<String>(), bad)
        assertEquals(44, texts.size)
    }
}
