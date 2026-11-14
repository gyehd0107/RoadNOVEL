package com.roadnovel.story

import com.roadnovel.data.vehicle.MockVehicleDataSource
import java.io.File
import kotlin.math.min

/** 테스트용: 실제 별지도 탐험대 JSON */
fun loadStarMap(): Story =
    StoryJson.parse(File("src/main/assets/stories/star_map_expedition.json").readText())

/** 가짜 차(MockVehicleDataSource)를 몰면서 엔진에 값을 넣어 주는 도우미 */
class TestDrive(val engine: StoryEngine, val car: MockVehicleDataSource = MockVehicleDataSource()) {

    fun speed(kmh: Float) = apply { car.setSpeed(kmh) }

    /** [sec]초 동안 1초씩 시간을 흘린다 */
    fun run(sec: Double, step: Double = 1.0) {
        var left = sec
        while (left > 1e-9) {
            val d = min(step, left)
            val simSec = car.advance(d)
            engine.tick(simSec, car.speedKmh.value, car.odometerKm.value)
            left -= d
        }
    }

    /** 대사 한 줄 읽는 데 걸리는 시간(초) */
    var lineSec = 4.0

    /** 다음 선택지나 결말이 나올 때까지 자동으로 듣고 달린다 */
    fun playUntilChoiceOrEnd(
        spoken: MutableList<EngineAction.Speak> = mutableListOf(),
        aiLines: (String) -> List<StoryLine> = { emptyList() },
        maxSteps: Int = 100_000,
    ): EngineAction {
        repeat(maxSteps) {
            when (val a = engine.nextAction()) {
                is EngineAction.Speak -> {
                    spoken += a
                    run(lineSec)
                    engine.lineDone()
                }
                is EngineAction.RequestAiLines -> engine.supplyAiLines(aiLines(a.sceneId))
                is EngineAction.Wait -> run(1.0)
                is EngineAction.AskChoice, is EngineAction.Finished -> return a
            }
        }
        error("이야기가 끝나지 않아요 (무한 반복?)")
    }

    /** 처음부터 결말까지. 선택지마다 [answers]를 차례로 말한다 (null = 대답 없음) */
    fun playToEnd(vararg answers: String?): PlayResult {
        val queue = ArrayDeque(answers.toList())
        val spoken = mutableListOf<EngineAction.Speak>()
        while (true) {
            when (val a = playUntilChoiceOrEnd(spoken)) {
                is EngineAction.AskChoice -> {
                    val heard = if (queue.isEmpty()) null else queue.removeFirst()
                    run(3.0) // 듣는 시간
                    engine.answerChoice(heard)
                }
                is EngineAction.Finished -> return PlayResult(a.ending, spoken, engine.state)
                else -> error("올 수 없음")
            }
        }
    }
}

data class PlayResult(val ending: Ending, val spoken: List<EngineAction.Speak>, val state: StoryState) {
    val texts: List<String> get() = spoken.map { it.line.text }
}
