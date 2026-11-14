package com.roadnovel.story

/** 엔진이 "지금 이걸 해 주세요" 하고 알려 주는 일 */
sealed interface EngineAction {
    /** 이 대사를 읽어 주세요. 다 읽으면 [StoryEngine.lineDone] */
    data class Speak(val line: StoryLine, val source: LineSource) : EngineAction

    /** AI 추가 대사를 만들어 주세요. 결과(실패면 빈 리스트)를 [StoryEngine.supplyAiLines]로 */
    data class RequestAiLines(val sceneId: String, val hint: String) : EngineAction

    /** 질문을 읽고 대답을 들어 주세요. [StoryEngine.answerChoice] 또는 [StoryEngine.pickChoice] */
    data class AskChoice(val sceneId: String, val choice: Choice, val attempt: Int) : EngineAction

    /** 거리가 부족해요. 배경음만 틀고 [StoryEngine.tick]을 계속 불러 주세요 */
    data class Wait(val remainingKm: Double) : EngineAction

    /** 결말 도착 */
    data class Finished(val ending: Ending) : EngineAction
}

/** SCENE=이야기 대사, INTERLUDE=정차 이야기, AI=언어 모델이 만든 대사, EXTRA=미리 써 둔 추가 대사 */
enum class LineSource { SCENE, INTERLUDE, AI, EXTRA }

sealed interface ChoiceResult {
    data class Picked(val index: Int, val option: ChoiceOption, val method: ChoiceMethod) : ChoiceResult
    /** 못 알아들었어요. 한 번 더 물어보세요 (다음 nextAction이 attempt=2 질문) */
    data object AskAgain : ChoiceResult
}

/**
 * 스토리 진행 규칙 (CLAUDE.md 8장). 안드로이드·코루틴 없이 순수 Kotlin.
 *
 * 쓰는 법:
 * 1. [nextAction]으로 할 일을 받는다 (여러 번 불러도 같은 답 — 처리할 때까지 그대로).
 * 2. 처리 결과를 [lineDone] / [supplyAiLines] / [answerChoice] / [pickChoice]로 알려 준다.
 * 3. 그동안 계속 [tick]으로 차량 값과 흐른 시간을 넣어 준다.
 */
class StoryEngine(
    val story: Story,
    lengthMin: Int,
    restore: StoryState? = null,
) {
    init {
        require(lengthMin > 0) { "이야기 길이는 0분보다 길어야 해요" }
    }

    /** 10분 모드면 optional 장면을 건너뛴다 */
    private val skipOptional = lengthMin <= SHORT_MODE_MIN

    private var s: StoryState = restore ?: StoryState(
        storyId = story.id,
        lengthMin = lengthMin,
        sceneId = resolve(story.start),
    ).let { enterScene(it) }

    val state: StoryState get() = s

    /**
     * 평균 속도를 주행하면서 보정할지. 속도 값 없이 주행거리계(1km 단위)만 있을 때는 끈다
     * — 1km씩 뛰는 값으로는 평균이 터무니없이 커져 목표 거리가 늘어나기 때문.
     */
    var learnAverageSpeed: Boolean = true

    /** 차량 데이터 출처가 바뀌면(Mock ↔ Pleos) 주행거리계 기준점을 다시 잡는다 */
    fun resetOdometerBaseline() {
        lastOdometer = null
    }
    val currentScene: Scene get() = story.scene(s.sceneId)

    // ---- 저장하지 않는 순간 상태 ----
    private val extraQueue = ArrayDeque<Pair<StoryLine, LineSource>>()
    private var speaking: LineSource? = null
    private var choiceAttempt = 1
    private var pendingInterlude: Interlude? = null
    private var stoppedSec = 0.0
    private var stopHandled = false
    private var lastOdometer: Double? = null
    /** 이번 실행(앱을 켠 뒤)에 달린 거리. 저장하지 않는다 — 이어 듣기 직후 주차 중 정차 이야기 방지 */
    private var sessionKm = 0.0
    private val speedSamples = ArrayDeque<Pair<Double, Double>>() // (elapsedSec, totalKm)

    // =====================================================================
    // 할 일 알려 주기
    // =====================================================================

    fun nextAction(): EngineAction {
        if (s.phase == Phase.ENDED) return EngineAction.Finished(currentScene.ending!!)

        // 1) 끼어든 대사(정차 이야기, AI 대사)가 먼저
        extraQueue.firstOrNull()?.let { (line, src) ->
            speaking = src
            return EngineAction.Speak(line, src)
        }
        // 2) 선택지 대기 중이면 계속 질문
        if (s.phase == Phase.CHOICE) {
            return EngineAction.AskChoice(s.sceneId, currentScene.choice!!, choiceAttempt)
        }
        // 3) 정차했으면 정차 이야기 (대사와 대사 사이, 또는 기다리는 중에만)
        pendingInterlude?.let { it ->
            pendingInterlude = null
            s = s.copy(usedInterludes = s.usedInterludes + it.id)
            it.lines.forEach { line -> extraQueue.addLast(line to LineSource.INTERLUDE) }
            if (extraQueue.isNotEmpty()) return nextAction()
        }

        val scene = currentScene
        when (s.phase) {
            Phase.LINES -> {
                val lines = visibleLines(scene)
                if (s.lineIndex < lines.size) {
                    speaking = LineSource.SCENE
                    return EngineAction.Speak(lines[s.lineIndex], LineSource.SCENE)
                }
                // 대사 끝
                // 선택지 장면은 거리를 기다리지 않고 바로 질문 (못 채운 거리는 다음 장면으로)
                if (scene.choice != null) {
                    s = s.copy(phase = Phase.CHOICE)
                    choiceAttempt = 1
                    return nextAction()
                }
                if (movedInScene() >= requiredKm()) return afterDistance()
                // 거리 부족 → AI 추가 대사 한 번 (규칙 4)
                val ai = scene.ai
                if (ai != null && ai.enabled && s.sceneId !in s.aiUsedScenes) {
                    s = s.copy(phase = Phase.AI_PENDING, aiUsedScenes = s.aiUsedScenes + s.sceneId)
                    return EngineAction.RequestAiLines(s.sceneId, ai.hint)
                }
                s = s.copy(phase = Phase.WAITING, waitSec = 0.0)
                return nextAction()
            }
            Phase.AI_PENDING -> return EngineAction.RequestAiLines(s.sceneId, scene.ai?.hint.orEmpty())
            Phase.WAITING -> {
                val moved = movedInScene()
                val need = requiredKm()
                return if (moved >= need || s.waitSec >= stallLimitSec()) afterDistance()
                else EngineAction.Wait(need - moved)
            }
            Phase.CHOICE, Phase.ENDED -> error("여기엔 올 수 없어요")
        }
    }

    // =====================================================================
    // 처리 결과 받기
    // =====================================================================

    /** 방금 받은 Speak 대사를 다 읽었어요 */
    fun lineDone() {
        when (speaking) {
            LineSource.INTERLUDE, LineSource.AI, LineSource.EXTRA -> extraQueue.removeFirstOrNull()
            LineSource.SCENE -> s = s.copy(lineIndex = s.lineIndex + 1)
            null -> Unit
        }
        speaking = null
    }

    /** AI 대사 결과. 실패했으면 빈 리스트 */
    fun supplyAiLines(lines: List<StoryLine>, byModel: Boolean = true) {
        val source = if (byModel) LineSource.AI else LineSource.EXTRA
        if (s.phase != Phase.AI_PENDING) return
        lines.forEach { extraQueue.addLast(it to source) }
        s = s.copy(phase = Phase.WAITING, waitSec = 0.0)
    }

    /**
     * 음성 대답 처리. [heard]가 null이면 시간 초과.
     * 처음 실패하면 [ChoiceResult.AskAgain], 두 번째도 실패하면 defaultIndex.
     */
    fun answerChoice(heard: String?): ChoiceResult {
        check(s.phase == Phase.CHOICE) { "지금은 선택지를 기다리는 중이 아니에요" }
        val choice = currentScene.choice!!
        val index = matchOption(choice, heard)
        return when {
            index != null -> select(index, ChoiceMethod.VOICE)
            choiceAttempt < MAX_CHOICE_ATTEMPTS -> {
                choiceAttempt++
                ChoiceResult.AskAgain
            }
            else -> select(choice.defaultIndex, ChoiceMethod.DEFAULT)
        }
    }

    /** 화면 버튼(동승자용)으로 고름 */
    fun pickChoice(index: Int): ChoiceResult.Picked {
        check(s.phase == Phase.CHOICE) { "지금은 선택지를 기다리는 중이 아니에요" }
        require(index in currentScene.choice!!.options.indices) { "없는 선택지 번호: $index" }
        return select(index, ChoiceMethod.BUTTON)
    }

    // =====================================================================
    // 차량 값·시간 넣기
    // =====================================================================

    /**
     * [dtSec]초(차 기준)가 흘렀어요. 속도·주행거리는 모르면 null.
     * 거리는 주행거리계를 먼저 쓰고, 없으면 속도로 계산한다.
     */
    fun tick(dtSec: Double, speedKmh: Float?, odometerKm: Double?) {
        if (dtSec <= 0.0 || s.phase == Phase.ENDED) return

        val dKm = distanceDelta(dtSec, speedKmh, odometerKm)
        sessionKm += dKm
        val elapsed = s.elapsedSec + dtSec
        val total = s.totalKm + dKm
        s = s.copy(
            totalKm = total,
            elapsedSec = elapsed,
            // 막힘 방지 시간은 이번 주행에서 차가 한 번이라도 움직인 뒤에만 센다 (주차 중엔 이야기가 넘어가지 않게)
            waitSec = if (s.phase == Phase.WAITING && sessionKm > 0.0) s.waitSec + dtSec else s.waitSec,
            avgKmPerMin = if (learnAverageSpeed) updateAverage(elapsed, total) else s.avgKmPerMin,
        )
        updateStop(dtSec, speedKmh)
    }

    // =====================================================================
    // 계산 (테스트·화면에서도 볼 수 있게 공개)
    // =====================================================================

    /** 장면 weight 1 당 달려야 하는 거리 (규칙 2) */
    fun kmPerWeight(): Double {
        val targetKm = s.lengthMin * s.avgKmPerMin
        val totalWeight = s.doneWeight + longestWeight(s.sceneId)
        return targetKm / totalWeight.coerceAtLeast(MIN_WEIGHT)
    }

    /** 지금 장면에서 달려야 하는 거리 (= 선택지 장면에서 넘어온 거리 + 이 장면 거리) */
    fun requiredKm(): Double = s.carryKm + ownWeight() * kmPerWeight()

    /** 결말 장면은 자기 거리를 기다리지 않는다 (넘어온 거리만 채움) */
    private fun ownWeight(): Double = if (currentScene.ending != null) 0.0 else currentScene.weight

    fun movedInScene(): Double = s.totalKm - s.sceneStartKm

    /** 남은 분량(분) 어림값: 결말까지 가장 긴 경로 기준 (이어 듣기 카드용) */
    fun remainingMinutesEstimate(): Int {
        if (s.phase == Phase.ENDED) return 0
        val remaining = longestWeight(s.sceneId)
        val total = (s.doneWeight + remaining).coerceAtLeast(MIN_WEIGHT)
        return kotlin.math.ceil(s.lengthMin * remaining / total).toInt()
    }

    /** 이 시간 넘게 기다리면 거리와 상관없이 넘어감 (규칙 6) */
    fun stallLimitSec(): Double {
        val totalWeight = longestWeight(story.start).coerceAtLeast(MIN_WEIGHT)
        // 넘어온 거리도 weight로 바꿔서 더한다
        val carriedWeight = s.carryKm / kmPerWeight().coerceAtLeast(MIN_WEIGHT)
        return (ownWeight() + carriedWeight) * s.lengthMin / totalWeight * STALL_FACTOR * 60.0
    }

    /** 조건(has:아이템)에 맞는 대사만 */
    fun visibleLines(scene: Scene): List<StoryLine> = scene.lines.filter { isVisible(it) }

    // =====================================================================
    // 내부
    // =====================================================================

    private fun isVisible(line: StoryLine): Boolean {
        val cond = line.condition ?: return true
        return cond.startsWith("has:") && cond.removePrefix("has:") in s.items
    }

    /** 10분 모드면 optional 장면을 건너뛴 실제 장면 id */
    private fun resolve(id: String): String {
        var cur = id
        val seen = mutableSetOf<String>()
        while (skipOptional && story.scene(cur).optional && seen.add(cur)) {
            cur = story.scene(cur).next ?: break
        }
        return cur
    }

    /** [id]부터 결말까지 가장 긴 경로의 weight 합 */
    private fun longestWeight(id: String, seen: Set<String> = emptySet()): Double {
        if (id in seen) return 0.0
        val sc = story.scenes[id] ?: return 0.0
        val own = if (skipOptional && sc.optional) 0.0 else sc.weight
        val rest = sc.nextIds.maxOfOrNull { longestWeight(it, seen + id) } ?: 0.0
        return own + rest
    }

    private fun afterDistance(): EngineAction {
        currentScene.ending?.let { ending ->
            s = s.copy(phase = Phase.ENDED, endingId = ending.id)
            return EngineAction.Finished(ending)
        }
        goTo(currentScene.next!!)
        return nextAction()
    }

    private fun select(index: Int, method: ChoiceMethod): ChoiceResult.Picked {
        val option = currentScene.choice!!.options[index]
        val leftoverKm = (requiredKm() - movedInScene()).coerceAtLeast(0.0)
        s = s.copy(
            choices = s.choices + ChoiceRecord(s.sceneId, index, option.label, method),
            flags = (s.flags + option.setFlags).distinct(),
        )
        goTo(option.next, carryKm = leftoverKm)
        return ChoiceResult.Picked(index, option, method)
    }

    private fun goTo(nextId: String, carryKm: Double = 0.0) {
        s = enterScene(
            s.copy(
                doneWeight = s.doneWeight + currentScene.weight,
                sceneId = resolve(nextId),
                carryKm = carryKm,
            )
        )
    }

    /** 새 장면에 들어갈 때: 위치·대기 초기화, 아이템 받기 */
    private fun enterScene(st: StoryState): StoryState {
        val sc = story.scene(st.sceneId)
        return st.copy(
            lineIndex = 0,
            phase = Phase.LINES,
            sceneStartKm = st.totalKm,
            waitSec = 0.0,
            items = (st.items + sc.gainItems).distinct(),
            path = st.path + st.sceneId,
        )
    }

    private fun matchOption(choice: Choice, heard: String?): Int? {
        if (heard.isNullOrBlank()) return null
        val said = heard.normalize()
        // 음성 인식은 "2번"을 "두 번"으로 적기도 한다 → 숫자로 바꾼 문장도 함께 비교
        val saidDigits = said.koreanCountToDigits()
        // 들어 있는 키워드 중 가장 긴 것을 고른다 ("버섯 마을" > "마을")
        return choice.options.withIndex()
            .mapNotNull { (i, o) ->
                o.say.map { it.normalize() }
                    .filter { it.isNotEmpty() && (it in said || it in saidDigits) }
                    .maxOfOrNull { it.length }?.let { i to it }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun String.normalize() = lowercase().filterNot { it.isWhitespace() }

    /** "한번/두번/세번", "일번/삼번", "첫째/둘째/셋째" → "1번/2번/3번" (공백은 이미 지운 상태) */
    private fun String.koreanCountToDigits(): String = COUNT_WORDS.entries.fold(this) { acc, (word, digit) -> acc.replace(word, digit) }

    private fun distanceDelta(dtSec: Double, speedKmh: Float?, odometerKm: Double?): Double {
        val bySpeed = (speedKmh ?: 0f).coerceAtLeast(0f) / 3600.0 * dtSec
        val prev = lastOdometer
        lastOdometer = odometerKm ?: prev
        if (odometerKm == null || prev == null) return bySpeed
        val d = odometerKm - prev
        // 주행거리계는 1km 단위로 한꺼번에 오르기도 한다 (실차·Virtual Console).
        // 거꾸로 가거나 말이 안 되게 크게 튀면(출처 전환 등) 속도로 계산
        return if (d in 0.0..MAX_ODOMETER_JUMP_KM) d else bySpeed
    }

    /** 최근 5분 평균 속도로 보정 (규칙 2) */
    private fun updateAverage(elapsed: Double, total: Double): Double {
        // 아직 출발 전(주차 중)이면 평균을 계산하지 않는다
        if (total <= 0.0) {
            speedSamples.clear()
            return s.avgKmPerMin
        }
        speedSamples.addLast(elapsed to total)
        while (speedSamples.size > 1 && elapsed - speedSamples.first().first > AVG_WINDOW_SEC) {
            speedSamples.removeFirst()
        }
        val (t0, km0) = speedSamples.first()
        val span = elapsed - t0
        if (span < AVG_MIN_SPAN_SEC) return s.avgKmPerMin
        return ((total - km0) / (span / 60.0)).coerceIn(MIN_KM_PER_MIN, MAX_KM_PER_MIN)
    }

    /** 0.5km/h 미만이 5초 넘게 이어지면 정차 → 정차 이야기 하나 예약 (규칙 5) */
    private fun updateStop(dtSec: Double, speedKmh: Float?) {
        if (speedKmh == null) return
        if (speedKmh >= STOP_SPEED_KMH) {
            stoppedSec = 0.0
            stopHandled = false
            pendingInterlude = null // 아직 시작 안 했으면 취소 (이미 다시 달리는 중)
            return
        }
        stoppedSec += dtSec
        if (stoppedSec < STOP_HOLD_SEC || stopHandled) return
        if (sessionKm < MIN_KM_BEFORE_INTERLUDE) return // 이번 주행에서 출발 전 정차는 무시
        stopHandled = true // 한 번 정차에 한 번만
        pendingInterlude = story.interludes.firstOrNull {
            it.trigger == TRIGGER_VEHICLE_STOPPED && it.id !in s.usedInterludes
        }
    }

    companion object {
        const val SHORT_MODE_MIN = 10
        const val DEFAULT_KM_PER_MIN = 0.5
        const val STALL_FACTOR = 1.5
        const val MAX_CHOICE_ATTEMPTS = 2
        const val STOP_SPEED_KMH = 0.5f
        const val STOP_HOLD_SEC = 5.0
        const val MIN_KM_BEFORE_INTERLUDE = 0.3
        const val AVG_WINDOW_SEC = 300.0
        const val AVG_MIN_SPAN_SEC = 60.0
        const val MIN_KM_PER_MIN = 0.1   // 6km/h
        const val MAX_KM_PER_MIN = 2.5   // 150km/h
        const val MAX_ODOMETER_JUMP_KM = 50.0
        private const val MIN_WEIGHT = 1e-6

        // "이번"(=this time)은 일부러 넣지 않음
        private val COUNT_WORDS = linkedMapOf(
            "한번" to "1번", "두번" to "2번", "세번" to "3번", "네번" to "4번",
            "일번" to "1번", "삼번" to "3번", "사번" to "4번",
            "첫째" to "1번", "둘째" to "2번", "셋째" to "3번", "넷째" to "4번",
            "하나" to "1번", "둘" to "2번", "셋" to "3번",
        )
    }
}
