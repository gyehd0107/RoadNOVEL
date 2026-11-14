package com.roadnovel.ai

import com.roadnovel.story.Story
import com.roadnovel.story.StoryLine

/**
 * 아동 안전 필터 (CLAUDE.md 10장). AI 대사는 반드시 이걸 통과해야 화면·음성으로 나간다.
 * - 금칙어가 들어 있으면 버림
 * - 차 안 위험 행동(창밖으로 손·머리 내밀기, 벨트 풀기 등)이나 위험 운전 유도(과속·신호 위반·재촉)면 버림
 * - 너무 긴 줄(40자 초과)은 버림, 최대 3줄
 * - 이야기에 없는 인물이 말하면 버림
 * - 이야기에 없는 다른 이름을 부르는 것 같으면 버림
 */
object SafetyFilter {

    /** 어린이 이야기에 맞지 않는 말 (공백을 지우고 비교) */
    val BANNED = listOf(
        "죽", "피가", "피를", "피투성", "칼", "총", "살인", "살해", "귀신", "유령", "시체", "해골",
        "공포", "비명", "저주", "악마", "지옥", "폭발", "폭탄", "때리", "때려", "죽여", "찔러", "잡아먹",
        "술을", "술마", "담배", "마약", "바보", "멍청", "꺼져", "닥쳐", "싫어해", "미워",
        "kill", "blood", "die", "dead", "gun",
    )

    /** 차 안 위험 행동: 창문 밖으로 손·머리·몸 내밀기 등 (공백을 지우고 비교) */
    val UNSAFE_IN_CAR = listOf(
        "창문밖으로", "창밖으로손", "창밖으로팔", "창밖으로머리", "창밖으로고개", "창밖으로몸", "창밖으로얼굴",
        "밖으로손", "밖으로팔", "밖으로머리", "밖으로고개", "밖으로몸",
        "손을내밀", "팔을내밀", "머리를내밀", "고개를내밀", "몸을내밀", "얼굴을내밀",
        "창문을열고", "창문열어", "창문을열어", "안전벨트를풀", "벨트를풀", "벨트풀어", "일어서", "일어나서춤",
    )

    /** 위험 운전 유도: 과속·신호 위반·운전자 재촉 */
    val UNSAFE_DRIVING = listOf(
        "빨리달려", "더빨리", "빨리가", "빨리빨리", "과속", "속도를올려", "속도올려", "밟아", "액셀",
        "신호무시", "신호를무시", "빨간불", "추월", "앞차를따라", "끼어들", "운전사아저씨", "운전하는엄마", "운전하는아빠",
    )

    data class Result(val kept: List<StoryLine>, val dropped: List<Pair<StoryLine, String>>)

    fun check(lines: List<StoryLine>, story: Story): Result {
        val ids = story.characters.map { it.id }.toSet()
        val names = story.characters.map { it.name.replace(" ", "") }
        val kept = mutableListOf<StoryLine>()
        val dropped = mutableListOf<Pair<StoryLine, String>>()
        for (line in lines) {
            val reason = reasonToDrop(line, ids, names)
            when {
                reason != null -> dropped += line to reason
                kept.size >= PromptBuilder.MAX_LINES -> dropped += line to "3줄 초과"
                else -> kept += line
            }
        }
        return Result(kept, dropped)
    }

    private fun reasonToDrop(line: StoryLine, ids: Set<String>, names: List<String>): String? {
        val compact = line.text.replace(" ", "").lowercase()
        return when {
            line.who !in ids -> "없는 인물(${line.who})"
            line.text.length > PromptBuilder.MAX_CHARS -> "${PromptBuilder.MAX_CHARS}자 초과"
            BANNED.any { it in compact } -> "금칙어"
            UNSAFE_IN_CAR.any { it in compact } -> "차 안 위험 행동"
            UNSAFE_DRIVING.any { it in compact } -> "위험 운전 유도"
            mentionsUnknownName(line.text, names) -> "모르는 이름"
            else -> null
        }
    }

    /**
     * "○○야," "○○님" 처럼 누군가를 부르는데 그 이름이 등장인물이 아니면 의심.
     * ("괜찮아." 같은 말끝을 이름으로 오해하지 않게, 야/아 뒤에 쉼표가 올 때만 부르는 말로 본다)
     */
    private val CALL = Regex("""(?:^|\s)([가-힣]{2,4})(?:(?:야|아),|님[,!\s])""")
    private val COMMON_CALLS = setOf("친구", "탐험대", "여러분", "거인", "요정", "엄마", "아빠", "얘들", "우리")

    private fun mentionsUnknownName(text: String, names: List<String>): Boolean =
        CALL.findAll("$text ").any { m ->
            val who = m.groupValues[1]
            who !in COMMON_CALLS && names.none { it.contains(who) || who.contains(it) }
        }
}
