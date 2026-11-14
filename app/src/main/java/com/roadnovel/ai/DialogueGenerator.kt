package com.roadnovel.ai

import com.roadnovel.story.Story
import com.roadnovel.story.StoryLine

/**
 * 장면 사이의 짧은 추가 대사를 만든다 (CLAUDE.md 10장).
 * 이야기의 뼈대(사건·분기·결말)는 절대 바꾸지 않는다. 실패하면 빈 리스트.
 */
interface DialogueGenerator {
    /** 화면·로그용 이름 (예: "Pleos LLM", "Mock") */
    val name: String

    suspend fun extraLines(context: StoryContext, hint: String): List<StoryLine>
}

/** AI에게 알려 줄 지금 이야기 상황 */
data class StoryContext(
    val story: Story,
    val sceneId: String,
    val items: List<String> = emptyList(),
) {
    val scene get() = story.scene(sceneId)
}

/** CLAUDE.md 10장: 프롬프트에 항상 넣을 것 */
object PromptBuilder {
    const val MAX_LINES = 3
    const val MAX_CHARS = 40

    fun build(c: StoryContext, hint: String): String = buildString {
        val story = c.story
        val scene = c.scene
        appendLine("너는 6세 이상 어린이를 위한 차량용 오디오 동화의 대사 작가야.")
        appendLine()
        appendLine("[이야기] ${story.title}: ${story.summary}")
        appendLine("[등장인물] (아래 인물만 말할 수 있어)")
        story.characters.forEach { ch ->
            appendLine("- ${ch.id}: ${ch.name}${if (ch.desc.isNotBlank()) " - ${ch.desc}" else ""}")
        }
        appendLine("[지금 장면] ${scene.title} (${scene.place})")
        scene.lines.filter { it.condition == null }.forEach { l ->
            appendLine("  ${story.character(l.who)?.name ?: l.who}: ${l.text}")
        }
        appendLine()
        appendLine("[할 일] $hint")
        appendLine()
        appendLine("[규칙]")
        appendLine("- 6세 이상 어린이용. 무섭거나 폭력적인 표현은 절대 쓰지 마.")
        appendLine("- 2~3줄만. 각 줄은 ${MAX_CHARS}자 이내.")
        appendLine("- 줄거리를 바꾸지 마. 새 인물, 새 사건, 새 물건을 만들지 마.")
        appendLine("- 위 등장인물 id만 써.")
        appendLine("- 형식: 한 줄에 하나씩 `인물id|대사`. 다른 설명은 쓰지 마.")
        appendLine("예시:")
        appendLine("lumi|우와, 저기 반짝이는 게 보여!")
        append("bolt|삐빅, 나도 봤어!")
    }
}

/** `인물id|대사` 줄 단위 응답 → StoryLine. 형식이 틀린 줄은 버린다 */
object ResponseParser {
    private val LINE = Regex("""^\s*[-*\d.)\s]*`?([A-Za-z_][A-Za-z0-9_]*)\s*\|\s*(.+?)`?\s*$""")

    fun parse(response: String): List<StoryLine> = response.lines()
        .mapNotNull { LINE.find(it) }
        .map { m ->
            val text = m.groupValues[2].trim().trim('"', '\'', '“', '”').trim()
            StoryLine(who = m.groupValues[1].lowercase(), text = text)
        }
        .filter { it.text.isNotBlank() }
}
