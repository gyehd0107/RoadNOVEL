package com.roadnovel.data.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 차량 값 공급원. Pleos 구현과 Mock 구현이 있다. (CLAUDE.md 6장) */
interface VehicleDataSource {
    /** 누적 주행거리(km). 모르면 null */
    val odometerKm: Flow<Double?>

    /** 현재 속도(km/h). 모르면 null */
    val speedKmh: Flow<Float?>

    /** 기어. 모르면 null (Mock은 속도 슬라이더만 쓰므로 null) */
    val gear: Flow<Gear?> get() = flowOf(null)
}

enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("?") }

/**
 * 엔진에 넣을 속도 (CLAUDE.md M5 판단 규칙)
 * - 기어 P → 정차 (속도 0으로 취급)
 * - 속도 값이 있으면 그 값
 * - 속도 값이 없고 기어 D/R → null (= 주행 중, 속도는 모름 → 정차로 보지 않음)
 */
fun effectiveSpeedKmh(speed: Float?, gear: Gear?): Float? = when {
    gear == Gear.PARK -> 0f
    speed != null -> speed
    else -> null
}

/** 화면 표시용: 지금 달리는 중인가 */
fun isMoving(speed: Float?, gear: Gear?): Boolean = when {
    gear == Gear.PARK -> false
    speed != null -> speed >= 0.5f
    else -> gear == Gear.DRIVE || gear == Gear.REVERSE
}
