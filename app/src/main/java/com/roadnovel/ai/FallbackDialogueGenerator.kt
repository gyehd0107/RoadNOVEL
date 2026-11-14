package com.roadnovel.ai

import com.roadnovel.story.StoryLine

/** (순수 Kotlin) 먼저 [primary]로 해 보고, 결과가 없으면 [fallback]. 마지막에 어느 쪽을 썼는지 [lastUsed]에 남긴다 */
class FallbackDialogueGenerator(
    private val primary: DialogueGenerator,
    private val fallback: DialogueGenerator,
) : DialogueGenerator {

    override val name get() = "${primary.name} → 안 되면 ${fallback.name}"

    @Volatile var lastUsed: String = "-"
        private set

    override suspend fun extraLines(context: StoryContext, hint: String): List<StoryLine> {
        val first = runCatching { primary.extraLines(context, hint) }.getOrDefault(emptyList())
        if (first.isNotEmpty()) {
            lastUsed = primary.name
            return first
        }
        lastUsed = fallback.name
        return fallback.extraLines(context, hint)
    }
}
