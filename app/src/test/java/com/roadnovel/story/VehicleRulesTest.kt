package com.roadnovel.story

import com.roadnovel.data.vehicle.Gear
import com.roadnovel.data.vehicle.effectiveSpeedKmh
import com.roadnovel.data.vehicle.isMoving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: 실제 차량 데이터(주행거리계 1km 단위 + 기어)로 이야기가 진행되는지 */
class VehicleRulesTest {

    private val story = loadStarMap()

    @Test
    fun `기어 P면 정차, 속도 값이 있으면 속도 우선, D인데 속도 모르면 주행`() {
        assertEquals(0f, effectiveSpeedKmh(speed = 40f, gear = Gear.PARK))
        assertEquals(40f, effectiveSpeedKmh(speed = 40f, gear = Gear.DRIVE))
        assertEquals(0f, effectiveSpeedKmh(speed = 0f, gear = Gear.DRIVE))
        assertNull(effectiveSpeedKmh(speed = null, gear = Gear.DRIVE))

        assertFalse(isMoving(speed = null, gear = Gear.PARK))
        assertTrue(isMoving(speed = null, gear = Gear.DRIVE))
        assertFalse(isMoving(speed = 0f, gear = Gear.DRIVE))
    }

    /** Virtual Console처럼 주행거리계를 1km씩 올리는 가짜 차 */
    private class OdometerCar(var km: Double = 100.0, var gear: Gear = Gear.PARK)

    private fun tick(e: StoryEngine, car: OdometerCar, sec: Double) {
        var left = sec
        while (left > 0) {
            e.tick(0.1, effectiveSpeedKmh(null, car.gear), car.km)
            left -= 0.1
        }
    }

    private fun readAll(e: StoryEngine) {
        while (true) {
            when (val a = e.nextAction()) {
                is EngineAction.Speak -> e.lineDone()
                is EngineAction.RequestAiLines -> e.supplyAiLines(emptyList())
                else -> return
            }
        }
    }

    @Test
    fun `주행거리계를 1km씩 올리면 장면이 넘어가고 평균 속도는 그대로`() {
        val e = StoryEngine(story, 10).apply { learnAverageSpeed = false }
        val car = OdometerCar()
        tick(e, car, 1.0)                     // 기준점 잡기 (100km)
        car.gear = Gear.DRIVE
        readAll(e)
        assertTrue(e.nextAction() is EngineAction.Wait)

        // 10분 이야기: 목표 5km, 가장 긴 경로 weight 5.4 → 장면1에 약 0.93km
        assertEquals(5.0 / 5.4, e.requiredKm(), 0.01)
        car.km += 1.0                          // 1km 한꺼번에 오름
        tick(e, car, 0.5)
        e.nextAction()
        assertEquals("s2_forest_gate", e.state.sceneId)
        // 속도를 모르는 동안 추정 거리가 조금 더해지지만, 주행거리계가 오르면 그만큼 빼므로 거의 1km
        assertEquals(1.0, e.state.totalKm, 0.01)
        assertEquals(StoryEngine.DEFAULT_KM_PER_MIN, e.state.avgKmPerMin, 0.0)
    }

    @Test
    fun `기어를 P로 바꾸면 5초 뒤 정차 이야기`() {
        val e = StoryEngine(story, 10).apply { learnAverageSpeed = false }
        val car = OdometerCar(gear = Gear.DRIVE)
        tick(e, car, 1.0)
        car.km += 1.0
        tick(e, car, 1.0)                      // 이번 주행에서 1km 달림

        car.gear = Gear.PARK
        tick(e, car, 4.0)
        assertEquals(LineSource.SCENE, (e.nextAction() as EngineAction.Speak).source)
        tick(e, car, 2.0)
        assertEquals(LineSource.INTERLUDE, (e.nextAction() as EngineAction.Speak).source)
    }

    @Test
    fun `출처를 바꿔 주행거리계가 크게 튀어도 거리로 세지 않는다`() {
        val e = StoryEngine(story, 10)
        e.tick(0.1, 0f, 12_345.0)  // Mock
        e.resetOdometerBaseline()
        e.tick(0.1, 0f, 100.0)     // Pleos로 전환
        e.tick(0.1, 0f, 100.0)
        assertEquals(0.0, e.state.totalKm, 0.0)
    }
}
