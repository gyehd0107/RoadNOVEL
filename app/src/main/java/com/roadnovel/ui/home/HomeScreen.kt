package com.roadnovel.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.data.save.EarnedBadge
import com.roadnovel.ui.ResumeUi
import com.roadnovel.ui.common.NightSky
import com.roadnovel.ui.common.Panel
import com.roadnovel.ui.theme.RN
import com.roadnovel.ui.theme.RoadNovelTheme

/** 홈: 인사 문구 + 이어 듣기 카드 + 새 이야기 카드 + 모은 배지 */
@Composable
fun HomeScreen(
    resume: ResumeUi?,
    badges: List<EarnedBadge>,
    onResume: () -> Unit,
    onNewStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                buildAnnotatedString {
                    append("이번 여정, ")
                    withStyle(SpanStyle(color = RN.Gold)) { append("우리 가족만의 이야기") }
                    append("를\n만들어 볼까요?")
                },
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                "차가 달리는 만큼 이야기가 나아가고, 여러분의 선택이 결말을 바꿔요.",
                style = MaterialTheme.typography.bodyLarge,
                color = RN.Sub,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 이어 듣기 카드 (저장 기록이 있으면 맨 앞)
            if (resume != null) {
                Panel(
                    modifier = Modifier.weight(1f).heightIn(min = 160.dp),
                    background = Brush.linearGradient(listOf(Color(0xFF14302E), RN.Panel)),
                    border = RN.Teal.copy(alpha = 0.35f),
                    onClick = onResume,
                ) {
                    Text(
                        if (resume.isTemp) "⚡ 지난번에 갑자기 멈췄어요. 이어서 들을까요?" else "▶ 이어 듣기",
                        style = MaterialTheme.typography.labelMedium,
                        color = RN.Teal,
                    )
                    Text("${resume.storyTitle} · ${resume.sceneTitle}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${resume.lengthMin}분 이야기 · 남은 분량 약 ${resume.remainingMin}분 · " +
                            "지금까지 ${resume.choices.joinToString(" → ").ifEmpty { "선택 전" }} · " +
                            "%.1fkm 함께 달림".format(resume.totalKm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RN.Sub,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Panel(
                modifier = Modifier.weight(1f).heightIn(min = 160.dp),
                background = RN.GoldGradient,
                border = null,
                onClick = onNewStory,
            ) {
                Text("새 이야기", style = MaterialTheme.typography.labelMedium, color = RN.OnGold.copy(alpha = 0.75f))
                Text("✨ 이야기 고르기", style = MaterialTheme.typography.titleLarge, color = RN.OnGold)
                Text("장르와 길이를 고르면 바로 출발해요", style = MaterialTheme.typography.bodyMedium, color = RN.OnGold.copy(alpha = 0.75f))
            }
        }
        BadgeRow(badges)
    }
}

@Composable
private fun BadgeRow(badges: List<EarnedBadge>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("모은 결말 배지", color = RN.Sub, fontSize = 16.sp)
        if (badges.isEmpty()) {
            Text("아직 없어요 — 이야기를 끝까지 들으면 배지를 받아요", color = RN.Dim, fontSize = 16.sp)
        }
        badges.forEach { b ->
            val shape = RoundedCornerShape(999.dp)
            Text(
                "${b.badge} ${b.name}",
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, RN.Line, shape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    RoadNovelTheme {
        NightSky()
        HomeScreen(
            resume = ResumeUi("s", "별지도 탐험대", "버섯 마을", 20, listOf("버섯 마을"), 4.2, 12, isTemp = true),
            badges = listOf(EarnedBadge("s", "friend", "노래로 친구가 된 탐험대", "🎵")),
            onResume = {}, onNewStory = {},
        )
    }
}
