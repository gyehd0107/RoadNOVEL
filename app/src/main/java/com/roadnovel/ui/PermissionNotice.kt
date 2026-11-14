package com.roadnovel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.common.GoButton
import com.roadnovel.ui.theme.RN

/**
 * 차량 속도 권한을 묻기 전에 보여 주는 짧은 설명.
 * (AAOS에서는 차량 속도 권한이 "위치 정보" 권한 창으로 보여서, 왜 필요한지 먼저 알려 준다)
 */
@Composable
fun SpeedPermissionNotice(onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE605070C))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .clip(shape)
                .background(RN.Panel)
                .border(1.dp, RN.Line, shape)
                .padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("🚗 차량 속도 사용 안내", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "신호 대기처럼 차가 멈췄을 때를 알기 위해 차량 속도만 사용해요.\n위치는 저장하지 않아요.",
                color = RN.Sub, fontSize = 20.sp, lineHeight = 31.sp, textAlign = TextAlign.Center,
            )
            Text(
                "다음 화면에서 \"위치 정보\" 권한으로 보일 수 있어요. 거부해도 이야기는 들을 수 있어요.",
                color = RN.Dim, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            GoButton("확인", onClick = onContinue)
        }
    }
}
