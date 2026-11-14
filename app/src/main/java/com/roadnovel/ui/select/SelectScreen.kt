package com.roadnovel.ui.select

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.UiState
import com.roadnovel.ui.common.CardShape
import com.roadnovel.ui.common.GoButton
import com.roadnovel.ui.common.NightSky
import com.roadnovel.ui.common.SubButton
import com.roadnovel.ui.theme.MinTouch
import com.roadnovel.ui.theme.RN
import com.roadnovel.ui.theme.RoadNovelTheme

private data class GenreCard(val title: String, val desc: String, val storyId: String?)

private val GENRE_LABEL = mapOf("fantasy_adventure" to "✨ 판타지 모험")

/** 이야기 선택: ① 장르(이야기) ② 길이 → 출발 */
@Composable
fun SelectScreen(
    ui: UiState,
    onSelectStory: (String) -> Unit,
    onSelectLength: (Int) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = ui.stories.map {
        GenreCard(GENRE_LABEL[it.genre] ?: it.genre, "${it.title} · 가족 함께", it.id)
    } + listOf(
        GenreCard("🔍 미스터리", "준비 중 · 성인 운전자용", null),
        GenreCard("🎭 드라마", "준비 중", null),
    )

    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Section("① 장르를 골라요") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                cards.forEach { c ->
                    SelectableCard(
                        selected = c.storyId != null && c.storyId == ui.selectedStoryId,
                        enabled = c.storyId != null,
                        onClick = { c.storyId?.let(onSelectStory) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text(c.desc, style = MaterialTheme.typography.bodyMedium, color = RN.Sub)
                    }
                }
            }
        }
        Section("② 이야기 길이를 골라요") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(10 to "짧은 이동", 20 to "보통", 30 to "장거리").forEach { (min, desc) ->
                    Box(Modifier.weight(1f)) {
                        SelectableCard(
                            selected = ui.lengthMin == min,
                            onClick = { onSelectLength(min) },
                            modifier = Modifier.fillMaxWidth(),
                            centered = true,
                        ) {
                            Spacer(Modifier.height(6.dp))
                            Text("${min}분", fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Text(desc, style = MaterialTheme.typography.bodyMedium, color = RN.Sub)
                        }
                        if (ui.recommendedMin == min) {
                            Text(
                                "내비 추천",
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-16).dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(RN.Teal)
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                color = RN.OnTeal,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SubButton("← 처음으로", onClick = onBack)
            NaviHint(ui)
            Spacer(Modifier.weight(1f))
            GoButton("출발! 이야기 시작", onClick = onStart)
        }
    }
}

@Composable
private fun NaviHint(ui: UiState) {
    val eta = ui.etaMin
    val text = if (eta == null) {
        buildAnnotatedString { append("🧭 내비 경로가 없어요. 길이를 직접 골라 주세요") }
    } else {
        buildAnnotatedString {
            append("🧭 도착까지 ")
            withStyle(SpanStyle(color = RN.Teal, fontWeight = FontWeight.Bold)) { append("${eta}분") }
            append(" → ")
            withStyle(SpanStyle(color = RN.Teal, fontWeight = FontWeight.Bold)) { append("${ui.recommendedMin}분 이야기") }
            append("를 추천해요")
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = RN.Sub)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = RN.Sub)
        content()
    }
}

@Composable
private fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centered: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = MinTouch + 20.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CardShape)
            .background(if (selected) RN.SelectedBg else RN.Panel)
            .border(BorderStroke(2.dp, if (selected) RN.Gold else RN.Line), CardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) { content() }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SelectPreview() {
    RoadNovelTheme {
        NightSky()
        SelectScreen(UiState(etaMin = 23, lengthMin = 20), {}, {}, {}, {})
    }
}
