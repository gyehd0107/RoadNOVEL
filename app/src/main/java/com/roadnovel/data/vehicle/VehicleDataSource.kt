package com.roadnovel.data.vehicle

import kotlinx.coroutines.flow.Flow

/** 차량 값 공급원. Pleos 구현(M5)과 Mock 구현이 있다. (CLAUDE.md 6장) */
interface VehicleDataSource {
    /** 누적 주행거리(km). 모르면 null */
    val odometerKm: Flow<Double?>

    /** 현재 속도(km/h). 모르면 null */
    val speedKmh: Flow<Float?>
}
