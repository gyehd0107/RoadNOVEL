package com.roadnovel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roadnovel.ui.RoadNovelRoot
import com.roadnovel.ui.theme.RoadNovelTheme
import dagger.hilt.android.AndroidEntryPoint

/** 화면 하나짜리 액티비티. 모든 화면은 Compose로 그린다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askMicPermission()
        setContent {
            RoadNovelTheme {
                RoadNovelRoot()
            }
        }
    }

    /**
     * 실행 중 허용이 필요한 권한: 마이크(음성 대답), 차량 속도.
     * 거부해도 앱은 동작한다 (버튼으로 선택, 기어로만 정차 판단).
     */
    private fun askMicPermission() {
        val needed = arrayOf(Manifest.permission.RECORD_AUDIO, CAR_SPEED)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) return
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }.launch(needed.toTypedArray())
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
