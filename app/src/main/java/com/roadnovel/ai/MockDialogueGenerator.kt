package com.roadnovel.ai

import com.roadnovel.story.StoryLine

/**
 * LLM 대신 장면별로 미리 써 둔 대사 (prototype/index.html 의 AI_LINES와 같은 내용).
 * Pleos LLM을 못 쓸 때 이걸로 동작한다. 같은 SafetyFilter를 거친다.
 */
class MockDialogueGenerator(
    private val lines: Map<String, List<StoryLine>> = STAR_MAP_LINES,
) : DialogueGenerator {

    override val name = "Mock(미리 쓴 대사)"

    override suspend fun extraLines(context: StoryContext, hint: String): List<StoryLine> =
        SafetyFilter.check(lines[context.sceneId].orEmpty(), context.story).kept

    companion object {
        val STAR_MAP_LINES = mapOf(
            "s1_departure" to listOf(
                StoryLine("bolt", "출발 전에 안전벨트 확인! 삐빅, 모두 철컥 완료."),
                StoryLine("lumi", "창밖 나무들도 우리한테 손 흔드는 것 같아!"),
            ),
            "s3a_castle" to listOf(
                StoryLine("bolt", "벽에 걸린 그림 속 기사가 윙크한 것 같아…"),
                StoryLine("lumi", "괜찮아, 저건 우리를 응원하는 거야!"),
            ),
            "s3b_village" to listOf(
                StoryLine("lumi", "요정들이 탐험대 이름을 노래로 불러 주고 있어!"),
                StoryLine("bolt", "삐빅, 기분 최고 모드 켜짐."),
            ),
        )
    }
}
