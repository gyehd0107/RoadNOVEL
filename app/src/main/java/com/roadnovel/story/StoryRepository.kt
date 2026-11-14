package com.roadnovel.story

import kotlinx.serialization.json.Json

/** 이야기 목록·내용을 가져오는 곳. 실제 구현은 data/story/AssetStoryRepository */
interface StoryRepository {
    suspend fun listStories(): List<Story>
    suspend fun load(id: String): Story
}

/** JSON 글자 → Story. 안드로이드 없이도 쓸 수 있어 테스트에서도 사용한다. */
object StoryJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Story = json.decodeFromString(Story.serializer(), text).also(::validate)

    /** 잘못 연결된 장면이 있으면 바로 알려 준다 (이야기 작성 실수 방지) */
    fun validate(story: Story) {
        val problems = mutableListOf<String>()
        if (story.start !in story.scenes) problems += "start '${story.start}' 장면이 없음"
        story.scenes.forEach { (id, sc) ->
            sc.nextIds.filter { it !in story.scenes }.forEach { problems += "$id → '$it' 장면이 없음" }
            if (sc.ending == null && sc.nextIds.isEmpty()) problems += "$id: next·choice·ending 중 하나는 있어야 함"
            sc.choice?.let { c ->
                if (c.defaultIndex !in c.options.indices) problems += "$id: defaultIndex가 범위를 벗어남"
            }
            if (sc.weight <= 0.0) problems += "$id: weight는 0보다 커야 함"
        }
        require(problems.isEmpty()) { "이야기 '${story.id}' 오류: ${problems.joinToString("; ")}" }
    }
}
