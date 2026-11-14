package com.roadnovel.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roadnovel.ui.common.NightSky
import com.roadnovel.ui.common.Panel
import com.roadnovel.ui.theme.RN
import com.roadnovel.ui.theme.RoadNovelTheme

/** 홈: 인사 문구 + 큰 카드. (이어 듣기 카드·배지는 M3) */
@Composable
fun HomeScreen(onNewStory: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, androidx.compose.ui.Alignment.CenterVertically),
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
            Panel(
                modifier = Modifier.weight(1f).heightIn(min = 160.dp),
                background = Brush.linearGradient(listOf(RN.Panel, RN.Panel)),
                enabled = false,
            ) {
                Text("이어 듣기", style = MaterialTheme.typography.labelMedium, color = RN.Teal)
                Text("▶ 저장된 이야기 없음", style = MaterialTheme.typography.titleLarge)
                Text("이야기를 저장하면 다음 주행에서 이어서 들을 수 있어요", style = MaterialTheme.typography.bodyMedium, color = RN.Sub)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    RoadNovelTheme {
        NightSky()
        HomeScreen(onNewStory = {})
    }
}
