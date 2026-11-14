package com.roadnovel.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.DebugStats
import com.roadnovel.ui.common.formatTime
import com.roadnovel.ui.theme.RN
import kotlin.math.roundToInt

/** 시연용 Mock 차량 조종 패널 (재생 화면 오른쪽 아래, 접었다 폈다) */
@Composable
fun DebugPanel(
    open: Boolean,
    speedKmh: Float,
    timeScale: Int,
    etaMin: Int?,
    stats: DebugStats?,
    onToggle: () -> Unit,
    onSpeed: (Float) -> Unit,
    onTimeScale: (Int) -> Unit,
    onEta: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .width(if (open) 360.dp else 220.dp)
            .clip(shape)
            .background(Color(0xF00D111B))
            .border(2.dp, Color(0xFF34405E), shape),
    ) {
        // 머리글 (누르면 접기/펴기)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onToggle).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🚗 디버그 · ${speedKmh.roundToInt()}km/h ×$timeScale", color = RN.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (open) "▼" else "▲", color = RN.Sub)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabelRow("차량 속도", "${speedKmh.roundToInt()} km/h")
                Slider(
                    value = speedKmh,
                    onValueChange = { onSpeed((it / 5f).roundToInt() * 5f) },
                    valueRange = 0f..110f,
                    colors = SliderDefaults.colors(thumbColor = RN.Gold, activeTrackColor = RN.Gold),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Seg("■ 정차", speedKmh == 0f, Modifier.weight(1.3f), danger = true) { onSpeed(0f) }
                    listOf(30f, 60f, 100f).forEach { v ->
                        Seg("${v.toInt()}", speedKmh == v, Modifier.weight(1f)) { onSpeed(v) }
                    }
                }
                LabelRow("시간 배속", "×$timeScale")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 10, 60).forEach { m ->
                        Seg("×$m", timeScale == m, Modifier.weight(1f)) { onTimeScale(m) }
                    }
                }
                LabelRow("내비 도착 예정", etaMin?.let { "${it}분" } ?: "경로 없음")
                Slider(
                    value = (etaMin ?: 0).toFloat(),
                    onValueChange = { onEta(it.roundToInt()) },
                    valueRange = 5f..90f,
                    colors = SliderDefaults.colors(thumbColor = RN.Teal, activeTrackColor = RN.Teal),
                )
                if (stats != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        KV("이번 주행 거리", "%.2f km".format(stats.totalKm))
                        KV("이야기 경과 시간", formatTime(stats.elapsedSec))
                        KV("평균 속도 추정", "${stats.avgKmh.roundToInt()} km/h")
                        KV("weight 1당 거리", "%.2f km".format(stats.kmPerWeight))
                        KV("얻은 물건 / 깃발", stats.itemsAndFlags)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row {
        Text(label, color = RN.Sub, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = RN.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row {
        Text(k, color = RN.Sub, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = RN.Text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Seg(text: String, selected: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) RN.Gold else RN.Panel2)
            .border(1.dp, if (danger && !selected) RN.Rose.copy(alpha = 0.4f) else RN.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) RN.OnGold else if (danger) Color(0xFFFFC2C2) else RN.Text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
