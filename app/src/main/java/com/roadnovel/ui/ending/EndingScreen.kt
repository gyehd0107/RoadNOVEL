package com.roadnovel.ui.ending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.EndingUi
import com.roadnovel.ui.common.GoButton
import com.roadnovel.ui.common.formatTime
import com.roadnovel.ui.theme.RN

/** 결말: 배지, 결말 이름, 이번 여정 요약 */
@Composable
fun EndingScreen(e: EndingUi, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(e.ending.badge.ifEmpty { "⭐" }, fontSize = 80.sp)
        Text("에피소드 완성", color = RN.Gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 17.sp)
        Text(e.ending.name, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SumBox("함께 달린 거리", "%.1f km".format(e.totalKm))
            SumBox("들은 시간", formatTime(e.elapsedSec))
            SumBox("우리의 선택", e.choices.joinToString(" → ").ifEmpty { "-" })
        }
        Text(
            when {
                e.ending.unlockHint != null -> e.ending.unlockHint
                e.unseenCount > 0 -> "아직 못 본 결말이 ${e.unseenCount}개 있어요. 다음 여행에서 다른 선택을 해 보세요!"
                else -> "모든 결말을 모았어요! 🎉"
            },
            color = RN.Sub,
            fontSize = 17.sp,
        )
        GoButton("처음으로", onClick = onHome)
    }
}

@Composable
private fun SumBox(label: String, value: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(RN.Panel).border(1.dp, RN.Line, shape).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = RN.Sub, fontSize = 15.sp)
        Text(value, color = RN.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}
