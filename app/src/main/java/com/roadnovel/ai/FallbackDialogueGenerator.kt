package com.roadnovel.ai

/** (순수 Kotlin) 먼저 [primary]로 해 보고, 결과가 없으면 [fallback]. 결과에 누가 만들었는지가 함께 담긴다 */
class FallbackDialogueGenerator(
    private val primary: DialogueGenerator,
    private val fallback: DialogueGenerator,
) : DialogueGenerator {

    override val name get() = "${primary.name} → 안 되면 ${fallback.name}"

    override suspend fun generate(context: StoryContext, hint: String): ExtraLines {
        val first = runCatching { primary.generate(context, hint) }.getOrDefault(ExtraLines.NONE)
        return if (first.lines.isNotEmpty()) first else fallback.generate(context, hint)
    }
}
