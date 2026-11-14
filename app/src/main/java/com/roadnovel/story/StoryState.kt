package com.roadnovel.story

import kotlinx.serialization.Serializable

/** 엔진이 지금 무엇을 하는 중인지 */
@Serializable
enum class Phase {
    /** 장면 대사를 읽는 중 */
    LINES,
    /** 대사는 끝났고 거리가 부족해서 AI 추가 대사를 기다리는 중 */
    AI_PENDING,
    /** 거리(또는 막힘 방지 시간)를 기다리는 중 */
    WAITING,
    /** 선택지 대답을 기다리는 중 */
    CHOICE,
    /** 결말 도착 */
    ENDED,
}

@Serializable
data class ChoiceRecord(val sceneId: String, val optionIndex: Int, val label: String, val method: ChoiceMethod)

@Serializable
enum class ChoiceMethod { VOICE, BUTTON, DEFAULT }

/**
 * 이야기 진행 상태 (CLAUDE.md 8장 8번). 그대로 저장(M3)했다가 다시 불러올 수 있다.
 * 위치 좌표는 담지 않는다 — 거리(km)만.
 */
@Serializable
data class StoryState(
    val storyId: String,
    val lengthMin: Int,
    val sceneId: String,
    val lineIndex: Int = 0,
    val flags: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val choices: List<ChoiceRecord> = emptyList(),
    /** 이야기를 시작한 뒤 달린 거리 */
    val totalKm: Double = 0.0,
    /** 이야기를 시작한 뒤 흐른 시간(차 기준 초) */
    val elapsedSec: Double = 0.0,
    val endingId: String? = null,
    // ---- 아래는 엔진 내부용 ----
    val phase: Phase = Phase.LINES,
    /** 지나온 장면들의 weight 합 */
    val doneWeight: Double = 0.0,
    /** 지금 장면을 시작했을 때의 totalKm */
    val sceneStartKm: Double = 0.0,
    /** 앞 선택지 장면에서 못 채우고 넘어온 거리 */
    val carryKm: Double = 0.0,
    /** 대사가 끝나고 기다린 시간(초) — 막힘 방지용 */
    val waitSec: Double = 0.0,
    /** 평균 속도(km/분). 처음엔 0.5 (=30km/h) */
    val avgKmPerMin: Double = StoryEngine.DEFAULT_KM_PER_MIN,
    val usedInterludes: List<String> = emptyList(),
    val aiUsedScenes: List<String> = emptyList(),
    /** 지나온 장면 id 순서 (지도 표시용) */
    val path: List<String> = emptyList(),
)
