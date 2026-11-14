package com.roadnovel.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 진짜(또는 에뮬레이터) 차량 값.
 * - 주행거리: Pleos Vehicle SDK 2.0.3 getOdometer() — SDK jar에서 직접 확인한 API
 * - 기어: AAOS GEAR_SELECTION 우선 (Pleos SDK의 getGear()는 "Google GEAR_SELECTION을 쓰라"며 deprecated).
 *         AAOS 쪽이 지원되지 않으면 Pleos getGear()를 예비로 쓴다.
 * - 속도: Vehicle SDK에 공개 API가 없음 → AAOS CarPropertyManager PERF_VEHICLE_SPEED (지원될 때만)
 *
 * Vehicle SDK에는 주행거리·기어용 capability 확인 API가 없다 (CapabilityType은 SUNROOF/AMBIENT_LIGHT 뿐).
 * 그래서 "처음 읽기가 성공하면 지원"으로 판단한다. 둘 다 실패하면 [status]가 UNAVAILABLE.
 */
class PleosVehicleDataSource(private val context: Context) : VehicleDataSource {

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class Available(val odometer: Boolean, val gear: Boolean, val speed: Boolean) : Status
        data class Unavailable(val reason: String) : Status
    }

    private val _odometer = MutableStateFlow<Double?>(null)
    private val _speed = MutableStateFlow<Float?>(null)
    private val _gear = MutableStateFlow<Gear?>(null)
    private val _status = MutableStateFlow<Status>(Status.Idle)

    override val odometerKm: StateFlow<Double?> = _odometer.asStateFlow()
    override val speedKmh: StateFlow<Float?> = _speed.asStateFlow()
    override val gear: StateFlow<Gear?> = _gear.asStateFlow()
    val status: StateFlow<Status> = _status.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var odometerOk = false
    private var gearOk = false
    private var speedOk = false
    private var aaosGearOk = false
    /**
     * 속도 센서가 실제로 살아 있는지: 0이 아닌 값을 한 번이라도 받아야 속도를 믿는다.
     * (에뮬레이터 Virtual Console에는 속도 카드가 없어 속도가 계속 0 → 그대로 믿으면 기어 D여도 "정차"가 됨)
     */
    private var speedLive = false
    private var attempts = 0

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            value?.let { _odometer.value = it.toDouble() }
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "주행거리 갱신 실패", e)
        }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (aaosGearOk) return // AAOS GEAR_SELECTION을 받는 중이면 그쪽을 믿는다
            _gear.value = gear?.toGear()
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 갱신 실패", e)
        }
    }

    /** Pleos 차량을 고르면 한 번 호출. 이미 시작했으면 아무것도 안 함 */
    fun start() {
        if (_status.value != Status.Idle && _status.value !is Status.Unavailable) return
        _status.value = Status.Checking
        attempts = 0
        runCatching {
            vehicle = vehicle ?: Vehicle(context.applicationContext).also { it.initialize() }
        }.onFailure {
            return fail("Vehicle SDK 초기화 실패: ${it.message}")
        }
        startCarProperties()
        // initialize()는 서비스 연결이 비동기라 조금 뒤에 읽기를 시도하고, 실패하면 몇 번 더 해 본다
        main.postDelayed(::probe, FIRST_PROBE_MS)
    }

    private fun probe() {
        val v = vehicle ?: return
        attempts++
        var pending = 2
        fun oneDone() {
            if (--pending > 0) return
            when {
                odometerOk || gearOk -> {
                    _status.value = Status.Available(odometerOk, gearOk, speedOk)
                    Log.i(TAG, "Pleos 차량 데이터 사용: ${_status.value}")
                }
                attempts < MAX_ATTEMPTS -> main.postDelayed(::probe, RETRY_MS)
                else -> fail("주행거리·기어를 읽을 수 없음")
            }
        }
        runCatching {
            v.getOdometer().getCurrentDistanceDriven({ km ->
                main.post {
                    _odometer.value = km?.toDouble()
                    if (!odometerOk) v.getOdometer().registerDistanceDriven(distanceListener)
                    odometerOk = true
                    oneDone()
                }
            }, { e -> main.post { Log.w(TAG, "주행거리 읽기 실패 (${attempts}회): ${e.message}"); oneDone() } })
        }.onFailure { oneDone() }
        if (aaosGearOk) { gearOk = true; oneDone() } else runCatching {
            @Suppress("DEPRECATION")
            v.getGear().getCurrentGearSelectionState({ g ->
                main.post {
                    _gear.value = g?.toGear()
                    @Suppress("DEPRECATION")
                    if (!gearOk) v.getGear().registerGearSelection(gearListener)
                    gearOk = true
                    oneDone()
                }
            }, { e -> main.post { Log.w(TAG, "기어 읽기 실패 (${attempts}회): ${e.message}"); oneDone() } })
        }.onFailure { oneDone() }
    }

    /**
     * AAOS CarPropertyManager: 속도(PERF_VEHICLE_SPEED, 권한 CAR_SPEED)와 기어(GEAR_SELECTION, 권한 CAR_POWERTRAIN).
     * 지원·권한이 없으면 조용히 건너뛴다 (속도 없으면 기어로만 판단).
     */
    @Suppress("DEPRECATION") // registerCallback: minSdk 29라 새 API(subscribePropertyEvents)를 못 씀
    private fun startCarProperties() {
        val cpm = runCatching {
            Car.createCar(context.applicationContext)?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        }.getOrNull() ?: run {
            Log.w(TAG, "CarPropertyManager 없음")
            return
        }

        fun supported(id: Int, name: String): Boolean {
            val ok = runCatching { cpm.getCarPropertyConfig(id) != null }.getOrElse {
                Log.w(TAG, "$name 확인 실패: ${it.message}"); false
            }
            Log.i(TAG, "$name 지원: $ok")
            return ok
        }

        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "속도 권한(${Car.PERMISSION_SPEED}) 없음 → 기어로만 정차/주행 판단")
        } else if (supported(VehiclePropertyIds.PERF_VEHICLE_SPEED, "PERF_VEHICLE_SPEED")) {
            speedOk = runCatching {
                cpm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: CarPropertyValue<*>) {
                        (value.value as? Float)?.let { mps ->
                            val kmh = mps * 3.6f // m/s → km/h
                            if (kmh > 0.5f && !speedLive) {
                                speedLive = true
                                Log.i(TAG, "속도 센서 값 들어옴 → 이제 속도로 정차/주행 판단")
                            }
                            if (speedLive) _speed.value = kmh
                        }
                    }
                    override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
                }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            }.getOrDefault(false)
        }

        if (supported(VehiclePropertyIds.GEAR_SELECTION, "GEAR_SELECTION")) {
            aaosGearOk = runCatching {
                cpm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: CarPropertyValue<*>) {
                        (value.value as? Int)?.let { _gear.value = aaosGear(it) }
                    }
                    override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
                }, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            }.getOrDefault(false)
            if (aaosGearOk) gearOk = true
        }
    }

    /** android.car.VehicleGear 값 → Gear */
    private fun aaosGear(v: Int): Gear = when (v) {
        android.car.VehicleGear.GEAR_PARK -> Gear.PARK
        android.car.VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        android.car.VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        android.car.VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_FIRST~NINTH
    }

    private fun fail(reason: String) {
        Log.w(TAG, "Pleos 차량 데이터 사용 불가: $reason")
        _status.value = Status.Unavailable(reason)
    }

    private fun VehicleGear.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1~9
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val FIRST_PROBE_MS = 500L
        const val RETRY_MS = 700L
        const val MAX_ATTEMPTS = 6
    }
}
