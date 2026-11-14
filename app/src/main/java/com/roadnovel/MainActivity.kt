package com.roadnovel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.roadnovel.data.vehicle.DrivingRestriction
import android.content.Intent
import androidx.activity.viewModels
import com.roadnovel.ui.RoadNovelRoot
import com.roadnovel.ui.RoadNovelViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.roadnovel.ui.SpeedPermissionNotice
import com.roadnovel.ui.theme.RoadNovelTheme
import dagger.hilt.android.AndroidEntryPoint

/** 화면 하나짜리 액티비티. 모든 화면은 Compose로 그린다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var driving: DrivingRestriction

    /** 화면(Compose)에서 쓰는 것과 같은 ViewModel */
    private val vm: RoadNovelViewModel by viewModels()

    /** 차량 속도 권한을 묻기 전 설명 화면을 보여 줄지 */
    private var showSpeedNotice by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askPermissions()
        handleDemo(intent)
        setContent {
            RoadNovelTheme {
                Box {
                    RoadNovelRoot()
                    if (showSpeedNotice) {
                        SpeedPermissionNotice(onContinue = {
                            showSpeedNotice = false
                            request(missing())
                        })
                    }
                }
            }
        }
    }

    /**
     * 실행 중 허용이 필요한 권한: 마이크(음성 대답), 차량 속도(신호 대기 등 정차 판단).
     * 차량 속도는 AAOS에서 "위치 정보"로 보여서 먼저 설명 화면을 띄운다.
     * 거부해도 앱은 동작한다 (버튼으로 선택, 기어로만 정차 판단).
     */
    private fun askPermissions() {
        // 주행 중에는 설명 화면·권한 창을 띄우지 않고, 차가 멈췄을 때 묻는다
        if (driving.restricted.value) {
            lifecycleScope.launch {
                driving.restricted.first { !it }
                askPermissions()
            }
            return
        }
        val missing = missing()
        when {
            missing.isEmpty() -> Unit
            CAR_SPEED in missing -> showSpeedNotice = true
            else -> request(missing)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemo(intent)
    }

    /**
     * 녹화용 실주행 시연 명령 (디버그 빌드에서만):
     * adb shell am start -n com.roadnovel/.MainActivity --es demo drive|nav|normal
     */
    private fun handleDemo(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra(EXTRA_DEMO)?.let(vm::demoCommand)
    }

    private fun missing(): List<String> = listOf(Manifest.permission.RECORD_AUDIO, CAR_SPEED)
        .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun request(perms: List<String>) {
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
        const val EXTRA_DEMO = "demo"
    }
}
