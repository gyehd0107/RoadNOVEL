package com.roadnovel.data.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 가짜 차량. 디버그 패널(M2)이나 테스트에서 속도를 바꾸고 시간을 흘려보낸다.
 * 안드로이드에 의존하지 않는다.
 */
class MockVehicleDataSource(startOdometerKm: Double = 12_345.0) : VehicleDataSource {

    private val _speed = MutableStateFlow<Float?>(0f)
    private val _odometer = MutableStateFlow<Double?>(startOdometerKm)

    override val speedKmh: StateFlow<Float?> = _speed.asStateFlow()
    override val odometerKm: StateFlow<Double?> = _odometer.asStateFlow()

    /** 시간 배속 (×1, ×10). 시연할 때 빨리 감기용 */
    var timeScale: Double = 1.0
        set(value) {
            require(value > 0) { "배속은 0보다 커야 해요" }
            field = value
        }

    fun setSpeed(kmh: Float) {
        _speed.value = kmh.coerceAtLeast(0f)
    }

    /** 정차 버튼 */
    fun stop() = setSpeed(0f)

    /**
     * 실제 시간 [realSec]초가 흘렀다고 알려 주면, 배속을 곱한 만큼 차를 움직인다.
     * @return 차 기준으로 흐른 시간(초)
     */
    fun advance(realSec: Double): Double {
        val simSec = realSec * timeScale
        val speed = _speed.value ?: 0f
        _odometer.value = (_odometer.value ?: 0.0) + speed / 3600.0 * simSec
        return simSec
    }
}
