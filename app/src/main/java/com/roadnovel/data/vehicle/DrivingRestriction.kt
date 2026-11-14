package com.roadnovel.data.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 중 화면 제한 (AAOS CarUxRestrictionsManager).
 * 차가 움직이는 등 시스템이 "운전자 주의 분산 최소화"를 요구하면 [restricted] = true.
 * 차량 서비스를 못 쓰면 false (Mock/일반 기기).
 */
class DrivingRestriction(context: Context) {

    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    /** 제한 중일 때 한 번에 보여도 되는 글자 수 (시스템 값, 모르면 120) */
    @Volatile var maxStringLength: Int = 120
        private set

    init {
        runCatching {
            val car = Car.createCar(context.applicationContext) ?: return@runCatching
            val mgr = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return@runCatching
            apply(mgr.currentCarUxRestrictions)
            mgr.registerListener { apply(it) }
        }.onFailure { Log.w(TAG, "UX 제한 서비스를 쓸 수 없음: ${it.message}") }
    }

    private fun apply(r: CarUxRestrictions?) {
        r ?: return
        maxStringLength = r.maxRestrictedStringLength
        if (_restricted.value != r.isRequiresDistractionOptimization) {
            Log.i(TAG, "주행 중 화면 제한: ${r.isRequiresDistractionOptimization} (활성 제한 0x${Integer.toHexString(r.activeRestrictions)})")
        }
        _restricted.value = r.isRequiresDistractionOptimization
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}
