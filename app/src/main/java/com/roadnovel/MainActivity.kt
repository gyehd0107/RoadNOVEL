package com.roadnovel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.roadnovel.ui.home.HomeScreen
import com.roadnovel.ui.theme.RoadNovelTheme
import dagger.hilt.android.AndroidEntryPoint

/** 화면 하나짜리 액티비티. 모든 화면은 Compose로 그린다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoadNovelTheme {
                HomeScreen()
            }
        }
    }
}
