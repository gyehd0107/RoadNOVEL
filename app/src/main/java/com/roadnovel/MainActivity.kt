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

    /** 음성으로 선택지에 대답하려면 마이크 권한 필요. 거부해도 버튼으로 고를 수 있다 */
    private fun askMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.RECORD_AUDIO)
    }
}
