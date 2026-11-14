package com.roadnovel.story

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryEngineTest {

    private val story = loadStarMap()

    private fun drive(lengthMin: Int = 20, speed: Float = 30f) =
        TestDrive(StoryEngine(story, lengthMin)).speed(speed)

    /** 지금 장면 대사를 시간 없이 바로 다 읽어 버린다 */
    private fun readAllLinesInstantly(e: StoryEngine) {
        while (true) {
            val a = e.nextAction()
            if (a is EngineAction.Speak && a.source == LineSource.SCENE) e.lineDone() else return
        }
    }

    // ------------------------------------------------------------------
    // 1) 10분 모드에서 optional 장면이 빠지는지
    // ------------------------------------------------------------------
    @Test
    fun `10분 모드는 optional 장면을 건너뛴다`() {
        val castle10 = drive(10).playToEnd("성", "노래").state.path
        assertEquals(listOf("s1_departure", "s2_forest_gate", "s3a_castle", "s4_fog_bridge", "e1_friend"), castle10)

        val village10 = drive(10).playToEnd("마을", "노래").state.path
        assertFalse("s3b2_village_market" in village10)
    }

    @Test
    fun `20분 30분 모드는 optional 장면을 들려준다`() {
        assertTrue("s3a2_castle_tower" in drive(20).playToEnd("성", "노래").state.path)
        assertTrue("s3b2_village_market" in drive(30).playToEnd("마을", "노래").state.path)
    }

    // ------------------------------------------------------------------
    // 2) 거리가 부족하면 다음 장면으로 안 넘어가는지
    // ------------------------------------------------------------------
    @Test
    fun `거리가 부족하면 대사가 끝나도 다음 장면으로 가지 않는다`() {
        val d = drive(20, speed = 30f)
        val e = d.engine
        readAllLinesInstantly(e)

        // 거리 부족 → 먼저 AI 추가 대사 요청 (실패 = 빈 리스트)
        assertTrue(e.nextAction() is EngineAction.RequestAiLines)
        e.supplyAiLines(emptyList())

        // 20분 × 0.5km/분 = 10km, 가장 긴 경로 weight 6.2 → 장면1에 약 1.61km 필요
        assertEquals(10.0 / 6.2, e.requiredKm(), 0.01)

        d.run(100.0) // 30km/h로 100초 = 약 0.83km
        assertTrue(e.nextAction() is EngineAction.Wait)
        assertEquals("s1_departure", e.state.sceneId)

        d.run(100.0) // 합계 약 1.67km → 충족
        val a = e.nextAction()
        assertEquals("s2_forest_gate", e.state.sceneId)
        assertTrue(a is EngineAction.Speak && a.source == LineSource.SCENE)
    }

    @Test
    fun `AI 추가 대사는 기다리는 동안 읽고 장면당 한 번만 요청한다`() {
        val d = drive(20)
        val e = d.engine
        readAllLinesInstantly(e)
        assertTrue(e.nextAction() is EngineAction.RequestAiLines)
        e.supplyAiLines(listOf(StoryLine("bolt", "안전벨트 확인!")))

        val a = e.nextAction()
        assertTrue(a is EngineAction.Speak && a.source == LineSource.AI)
        e.lineDone()
        assertTrue(e.nextAction() is EngineAction.Wait)
    }

    // ------------------------------------------------------------------
    // 3) 정차하면 interlude가 한 번만 (출발 전 정차는 무시)
    // ------------------------------------------------------------------
    @Test
    fun `출발 전 정차는 정차 이야기를 틀지 않는다`() {
        val d = drive(20, speed = 0f)
        d.run(60.0)
        val a = d.engine.nextAction()
        assertTrue(a is EngineAction.Speak && a.source == LineSource.SCENE)
        assertTrue(d.engine.state.usedInterludes.isEmpty())
    }

    @Test
    fun `이어 듣기 직후 주차 중에는 정차 이야기를 틀지 않는다`() {
        val first = drive(20, speed = 60f)
        first.run(120.0) // 2km 달린 상태로 저장
        val restored = TestDrive(StoryEngine(story, 20, restore = first.engine.state)).speed(0f)
        restored.run(60.0) // 다음 주행: 아직 주차 중
        val a = restored.engine.nextAction()
        assertTrue(a is EngineAction.Speak && a.source == LineSource.SCENE)

        restored.speed(40f).run(30.0) // 출발했다가
        restored.speed(0f).run(6.0)   // 정차하면 그때는 나옴
        val b = restored.engine.nextAction()
        assertTrue(b is EngineAction.Speak && b.source == LineSource.INTERLUDE)
    }

    @Test
    fun `정차하면 정차 이야기를 한 번만 튼다`() {
        val d = drive(20, speed = 60f)
        val e = d.engine
        d.run(60.0) // 1km 달림

        // 5초 미만 정차는 무시
        d.speed(0f).run(4.0)
        assertSceneLine(e)

        // 5초 이상 정차 → 첫 번째 정차 이야기
        d.run(2.0)
        val first = playInterlude(e)
        assertEquals(2, first)
        assertEquals(listOf("i_stop_campfire"), e.state.usedInterludes)

        // 계속 서 있어도 다시 틀지 않음
        d.run(120.0)
        assertSceneLine(e)

        // 다시 달렸다가 또 정차 → 두 번째 정차 이야기
        d.speed(40f).run(10.0)
        d.speed(0f).run(6.0)
        assertEquals(2, playInterlude(e))
        assertEquals(listOf("i_stop_campfire", "i_stop_snack"), e.state.usedInterludes)

        // 다 써 버리면 더 이상 없음
        d.speed(40f).run(10.0)
        d.speed(0f).run(6.0)
        assertSceneLine(e)
    }

    private fun assertSceneLine(e: StoryEngine) {
        val a = e.nextAction()
        assertTrue("장면 대사여야 함: $a", a is EngineAction.Speak && a.source == LineSource.SCENE)
    }

    /** 정차 이야기를 다 읽고 몇 줄이었는지 돌려줌 */
    private fun playInterlude(e: StoryEngine): Int {
        var n = 0
        while (true) {
            val a = e.nextAction()
            if (a is EngineAction.Speak && a.source == LineSource.INTERLUDE) {
                e.lineDone(); n++
            } else return n
        }
    }

    @Test
    fun `출발 전 주차 시간은 평균 속도에 넣지 않는다`() {
        val d = drive(20, speed = 0f)
        d.run(600.0) // 10분 주차
        assertEquals(StoryEngine.DEFAULT_KM_PER_MIN, d.engine.state.avgKmPerMin, 0.0)
        assertEquals(10.0 / 6.2, d.engine.requiredKm(), 0.01)
    }

    // ------------------------------------------------------------------
    // 4) 막힘 방지 시간이 지나면 넘어가는지
    // ------------------------------------------------------------------
    @Test
    fun `꽉 막힌 길에서도 막힘 방지 시간이 지나면 다음 장면으로 간다`() {
        val d = drive(20, speed = 1f) // 거의 못 가지만 정차는 아님
        val e = d.engine
        readAllLinesInstantly(e)
        e.nextAction()
        e.supplyAiLines(emptyList())

        // (weight 1 × 20분 / 전체 weight 6.2) × 1.5 = 약 4.84분
        val limit = e.stallLimitSec()
        assertEquals(1.0 * 20 / 6.2 * 1.5 * 60, limit, 0.01)

        d.run(limit - 10)
        assertTrue(e.nextAction() is EngineAction.Wait)
        assertEquals("s1_departure", e.state.sceneId)

        d.run(11.0)
        e.nextAction()
        assertEquals("s2_forest_gate", e.state.sceneId)
        assertTrue("거리는 여전히 부족했어야 함", e.state.totalKm < 0.2)
    }

    // ------------------------------------------------------------------
    // 5) 선택지 키워드 인식과 시간 초과 시 기본값
    // ------------------------------------------------------------------
    private fun atFirstChoice(): StoryEngine {
        val d = drive(20, speed = 60f)
        val a = d.playUntilChoiceOrEnd()
        assertTrue(a is EngineAction.AskChoice && a.attempt == 1)
        return d.engine
    }

    @Test
    fun `선택지 장면은 거리를 기다리지 않고 바로 묻고 못 채운 거리는 다음 장면으로 넘긴다`() {
        val d = drive(20, speed = 30f)
        val e = d.engine
        val ask = d.playUntilChoiceOrEnd() // 장면2 대사 3줄(12초) 읽자마자 질문
        assertTrue(ask is EngineAction.AskChoice)
        assertEquals("s2_forest_gate", e.state.sceneId)

        val leftover = e.requiredKm() - e.movedInScene()
        assertTrue("장면2 거리는 거의 못 채웠어야 함: $leftover", leftover > 1.0)

        e.answerChoice("성")
        assertEquals("s3a_castle", e.state.sceneId)
        assertEquals(leftover, e.state.carryKm, 1e-9)
        // 다음 장면 필요 거리 = 넘어온 거리 + 성 장면 거리(1.2 × kmPerWeight)
        assertEquals(leftover + 1.2 * e.kmPerWeight(), e.requiredKm(), 1e-9)

        // 성 장면을 다 끝내고 다음으로 가면 넘어온 거리는 사라진다
        d.playUntilChoiceOrEnd()
        assertEquals(0.0, e.state.carryKm, 0.0)
    }

    @Test
    fun `대답 속 키워드로 선택지를 고른다`() {
        val cases = mapOf(
            "오래된 성으로 가자" to 0,
            "성!" to 0,
            "첫번째" to 0,
            "두 번째요" to 1,
            "버섯 마을 갈래" to 1,
            "2번" to 1,
        )
        cases.forEach { (heard, expected) ->
            val r = atFirstChoice().answerChoice(heard)
            assertTrue(heard, r is ChoiceResult.Picked && r.index == expected && r.method == ChoiceMethod.VOICE)
        }
    }

    @Test
    fun `선택하면 플래그가 붙고 그 장면으로 간다`() {
        val e = atFirstChoice()
        e.answerChoice("마을")
        assertEquals("s3b_village", e.state.sceneId)
        assertEquals(listOf("visited_village"), e.state.flags)
        assertEquals("버섯 마을", e.state.choices.single().label)
        assertTrue("fairy_song" in e.state.items) // 장면에 들어가면 아이템 획득
    }

    @Test
    fun `못 알아들으면 한 번 더 묻고 그래도 안 되면 기본값`() {
        val e = atFirstChoice()
        assertEquals(ChoiceResult.AskAgain, e.answerChoice("음… 잘 모르겠어"))
        val again = e.nextAction()
        assertTrue(again is EngineAction.AskChoice && again.attempt == 2)

        val r = e.answerChoice(null) // 시간 초과
        assertTrue(r is ChoiceResult.Picked && r.index == 0 && r.method == ChoiceMethod.DEFAULT)
        assertEquals("s3a_castle", e.state.sceneId)
    }

    @Test
    fun `시간 초과 뒤 두 번째에 알아들으면 그걸 고른다`() {
        val e = atFirstChoice()
        assertEquals(ChoiceResult.AskAgain, e.answerChoice(null))
        val r = e.answerChoice("버섯")
        assertTrue(r is ChoiceResult.Picked && r.index == 1 && r.method == ChoiceMethod.VOICE)
    }

    @Test
    fun `화면 버튼으로도 고를 수 있다`() {
        val e = atFirstChoice()
        val r = e.pickChoice(1)
        assertEquals(ChoiceMethod.BUTTON, r.method)
        assertEquals("s3b_village", e.state.sceneId)
    }

    // ------------------------------------------------------------------
    // 6) has:아이템 조건 대사
    // ------------------------------------------------------------------
    @Test
    fun `has 조건 대사는 아이템이 있을 때만 나온다`() {
        val song = "버섯 마을 요정들의 노래야! 내가 한 글자도 안 틀리고 기억했지."
        val key = "은빛 열쇠로 거인의 상자를 열자, 그 안에서 북쪽별이 반짝 떠올랐어요."

        assertTrue(song in drive().playToEnd("마을", "노래").texts)   // 노래 있음
        assertFalse(song in drive().playToEnd("성", "노래").texts)    // 노래 없음
        assertTrue(key in drive().playToEnd("성", "수수께끼").texts)  // 열쇠 있음
        assertFalse(key in drive().playToEnd("마을", "수수께끼").texts) // 열쇠 없음
    }

    // ------------------------------------------------------------------
    // 7) 별지도 탐험대: 처음부터 3가지 결말 모두 도달
    // ------------------------------------------------------------------
    @Test
    fun `별지도 탐험대의 결말 3개에 모두 도착할 수 있다`() {
        val friend = drive().playToEnd("버섯 마을", "노래 불러 줄래")
        val riddle = drive().playToEnd("성", "수수께끼")
        val sneak = drive().playToEnd("1번", "살금살금")

        assertEquals("friend", friend.ending.id)
        assertEquals("riddle", riddle.ending.id)
        assertEquals("sneak", sneak.ending.id)
        listOf(friend, riddle, sneak).forEach {
            assertEquals(Phase.ENDED, it.state.phase)
            assertEquals(it.ending.id, it.state.endingId)
            assertEquals(2, it.state.choices.size)
        }
        assertEquals(
            setOf("friend", "riddle", "sneak"),
            story.scenes.values.mapNotNull { it.ending?.id }.toSet(),
        )
    }

    @Test
    fun `아무 대답이 없어도 기본값으로 결말까지 간다`() {
        val r = drive().playToEnd() // 모든 질문에 대답 없음
        assertEquals("friend", r.ending.id)
        assertTrue(r.state.choices.all { it.method == ChoiceMethod.DEFAULT })
    }

    @Test
    fun `30km per h로 달리면 고른 길이에 가깝게 끝난다`() {
        // 가장 긴 경로(성→탑→노래)를 고르면 결말 장면을 뺀 거리만큼 달린다
        val min20 = drive(20).playToEnd("성", "노래").state.elapsedSec / 60
        val min10 = drive(10).playToEnd("성", "노래").state.elapsedSec / 60
        println("20분 모드 실제 %.1f분, 10분 모드 실제 %.1f분".format(min20, min10))
        assertTrue("20분 모드: $min20", min20 in 15.0..22.0)
        assertTrue("10분 모드: $min10", min10 in 7.0..12.0)
    }

    // ------------------------------------------------------------------
    // 기타: 저장했다가 이어서 (M3 대비)
    // ------------------------------------------------------------------
    @Test
    fun `상태를 저장했다가 다시 불러와 이어서 끝낼 수 있다`() {
        val first = drive(20, speed = 60f)
        first.playUntilChoiceOrEnd()
        first.engine.answerChoice("마을")
        val json = Json.encodeToString(StoryState.serializer(), first.engine.state)

        val restored = StoryState.serializer().let { Json.decodeFromString(it, json) }
        val second = TestDrive(StoryEngine(story, 20, restore = restored)).speed(60f)
        assertEquals("s3b_village", second.engine.state.sceneId)
        val r = second.playToEnd("노래")
        assertEquals("friend", r.ending.id)
    }

    @Test
    fun `남은 분량은 처음엔 전체 길이이고 진행할수록 줄어든다`() {
        val d = drive(20, speed = 60f)
        assertEquals(20, d.engine.remainingMinutesEstimate())
        d.playUntilChoiceOrEnd()
        d.engine.answerChoice("성")
        // 지나온 weight 2 / 전체 6.2 → 남은 4.2/6.2 × 20 = 13.5 → 14분
        assertEquals(14, d.engine.remainingMinutesEstimate())
        assertEquals(0, d.playToEnd("노래").state.let { StoryEngine(story, 20, it).remainingMinutesEstimate() })
    }

    @Test
    fun `장면 연결이 잘못된 JSON은 거부한다`() {
        val broken = story.copy(scenes = story.scenes + ("s1_departure" to story.scene("s1_departure").copy(next = "없는장면")))
        val err = runCatching { StoryJson.validate(broken) }.exceptionOrNull()
        assertNotEquals(null, err)
    }
}
