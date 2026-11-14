package com.roadnovel.story

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// assets/stories/*.json 의 모양을 그대로 옮긴 데이터 클래스들 (CLAUDE.md 7장)

@Serializable
data class Story(
    val id: String,
    val title: String,
    val genre: String = "",
    val audience: String = "",
    val ageMin: Int = 0,
    val version: Int = 1,
    val summary: String = "",
    val characters: List<StoryCharacter> = emptyList(),
    val start: String,
    val scenes: Map<String, Scene>,
    val interludes: List<Interlude> = emptyList(),
) {
    fun scene(id: String): Scene = scenes[id] ?: error("장면 '$id'이(가) 이야기 '${this.id}'에 없어요")
    fun character(id: String): StoryCharacter? = characters.firstOrNull { it.id == id }
}

@Serializable
data class StoryCharacter(
    val id: String,
    val name: String,
    val desc: String = "",
    val voice: VoiceStyle = VoiceStyle(),
)

/** 인물별 목소리 높이·속도 (TTS에 그대로 넘김) */
@Serializable
data class VoiceStyle(val pitch: Float = 1f, val rate: Float = 1f)

@Serializable
data class StoryLine(
    val who: String,
    val text: String,
    /** 예: "has:silver_key" — 그 아이템이 있을 때만 읽는다 */
    @SerialName("if") val condition: String? = null,
)

@Serializable
data class Scene(
    val chapter: Int = 1,
    val title: String = "",
    val place: String = "",
    /** 이 장면이 차지하는 거리 비중 */
    val weight: Double = 1.0,
    /** true면 10분 모드에서 건너뜀 */
    val optional: Boolean = false,
    val lines: List<StoryLine> = emptyList(),
    val next: String? = null,
    val choice: Choice? = null,
    val ending: Ending? = null,
    val gainItems: List<String> = emptyList(),
    val ai: AiSpec? = null,
) {
    /** 이 장면 다음에 갈 수 있는 장면 id들 */
    val nextIds: List<String>
        get() = choice?.options?.map { it.next } ?: listOfNotNull(next)
}

@Serializable
data class Choice(
    val question: String,
    val options: List<ChoiceOption>,
    val timeoutSec: Int = 20,
    val defaultIndex: Int = 0,
)

@Serializable
data class ChoiceOption(
    val label: String,
    /** 음성 인식 키워드 */
    val say: List<String> = emptyList(),
    val next: String,
    val setFlags: List<String> = emptyList(),
)

@Serializable
data class Ending(
    val id: String,
    val name: String,
    val badge: String = "",
    val unlockHint: String? = null,
)

@Serializable
data class AiSpec(val enabled: Boolean = false, val hint: String = "")

@Serializable
data class Interlude(
    val id: String,
    /** 지금은 "vehicle_stopped" 만 사용 */
    val trigger: String,
    val lines: List<StoryLine>,
)

const val TRIGGER_VEHICLE_STOPPED = "vehicle_stopped"
