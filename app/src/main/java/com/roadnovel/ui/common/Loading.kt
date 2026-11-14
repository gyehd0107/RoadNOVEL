package com.roadnovel.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.theme.RN
import kotlinx.coroutines.delay

/** 버튼을 누른 뒤 이 시간 안에 반응이 없으면 로딩 표시 (심사 기준) */
const val LOADING_DELAY_MS = 2_000L

/**
 * [waiting]이 [LOADING_DELAY_MS] 넘게 계속 true면 그때부터 로딩 표시.
 * (금방 끝나는 작업에는 깜빡이지 않게)
 */
@Composable
fun LoadingAfterDelay(waiting: Boolean, text: String, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        show = false
        if (waiting) {
            delay(LOADING_DELAY_MS)
            show = true
        }
    }
    if (waiting && show) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(26.dp), color = RN.Gold, strokeWidth = 3.dp)
            Text(text, color = RN.Sub, fontSize = 18.sp)
        }
    }
}
