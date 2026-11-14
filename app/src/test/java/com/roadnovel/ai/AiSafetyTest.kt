package com.roadnovel.ai

import com.roadnovel.story.StoryLine
import com.roadnovel.story.loadStarMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CLAUDE.md 10장: AI 대사 안전 규칙 */
class AiSafetyTest {

    private val story = loadStarMap()

    @Test
    fun `프롬프트에 인물 설정, 장면, 힌트, 아동 안전 규칙이 다 들어간다`() {
        val p = PromptBuilder.build(StoryContext(story, "s1_departure"), "출발하며 신나는 대화")
        listOf("루미", "볼트", "출발! 별빛 마을", "출발하며 신나는 대화", "6세 이상", "무섭거나 폭력적인", "2~3줄", "40자", "줄거리를 바꾸지 마", "인물id|대사")
            .forEach { assertTrue("프롬프트에 '$it' 없음", it in p) }
    }

    @Test
    fun `인물id 세로선 대사 형식만 받고 나머지는 버린다`() {
        val raw = """
            네, 대사를 만들어 볼게요!
            lumi|창밖 나무들도 손을 흔들어!
            - bolt | "삐빅, 출발 준비 완료."
            루미: 이건 형식이 틀렸어
            giant|
        """.trimIndent()
        assertEquals(
            listOf(StoryLine("lumi", "창밖 나무들도 손을 흔들어!"), StoryLine("bolt", "삐빅, 출발 준비 완료.")),
            ResponseParser.parse(raw),
        )
    }

    @Test
    fun `금칙어, 40자 초과, 없는 인물, 모르는 이름은 버리고 최대 3줄`() {
        val lines = listOf(
            StoryLine("lumi", "우와, 별빛이 반짝여!"),                       // 통과
            StoryLine("bolt", "거인이 우리를 잡아먹을지도 몰라!"),             // 금칙어
            StoryLine("dragon", "나는 용이다!"),                                // 없는 인물
            StoryLine("lumi", "가".repeat(41)),                                   // 40자 초과
            StoryLine("bolt", "민수야, 이리 와 봐!"),                            // 모르는 이름
            StoryLine("bolt", "삐빅, 마술처럼 길이 열렸어!"),                   // 통과 ('술' 단독은 금칙어 아님)
            StoryLine("lumi", "탐험대야, 출발하자!"),                            // 통과 (흔한 호칭)
            StoryLine("bolt", "하나 더!"),                                       // 3줄 초과
        )
        val r = SafetyFilter.check(lines, story)
        assertEquals(listOf("우와, 별빛이 반짝여!", "삐빅, 마술처럼 길이 열렸어!", "탐험대야, 출발하자!"), r.kept.map { it.text })
        assertEquals(listOf("금칙어", "없는 인물(dragon)", "40자 초과", "모르는 이름", "3줄 초과"), r.dropped.map { it.second })
    }

    @Test
    fun `Mock 대사도 안전 필터를 통과하고, 대사가 없는 장면은 빈 리스트`() = runBlocking {
        val mock = MockDialogueGenerator()
        assertEquals(2, mock.extraLines(StoryContext(story, "s1_departure"), "").size)
        assertEquals(2, mock.extraLines(StoryContext(story, "s3b_village"), "").size)
        assertTrue(mock.extraLines(StoryContext(story, "s4_fog_bridge"), "").isEmpty())
    }

    @Test
    fun `앞 엔진이 빈 결과면 대신 Mock을 쓴다`() = runBlocking {
        val empty = object : DialogueGenerator {
            override val name = "빈 LLM"
            override suspend fun extraLines(context: StoryContext, hint: String) = emptyList<StoryLine>()
        }
        val g = FallbackDialogueGenerator(empty, MockDialogueGenerator())
        assertEquals(2, g.extraLines(StoryContext(story, "s1_departure"), "").size)
        assertEquals("Mock(미리 쓴 대사)", g.lastUsed)
    }
}
