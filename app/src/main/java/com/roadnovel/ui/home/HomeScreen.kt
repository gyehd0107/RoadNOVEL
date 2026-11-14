package com.roadnovel.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roadnovel.ui.theme.MinTouch
import com.roadnovel.ui.theme.RoadNovelTheme

/** 홈 화면 (M0: 빈 껍데기). M2에서 이어 듣기 카드·이야기 선택을 붙인다. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "★ ROAD NOVEL",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "달리는 만큼 펼쳐지는 우리 가족 이야기",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { /* M2에서 이야기 선택 화면으로 이동 */ },
            enabled = false,
            modifier = Modifier.heightIn(min = MinTouch).widthIn(min = 320.dp),
        ) {
            Text("이야기 시작 (준비 중)", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// 가로형 차량 화면 크기로 미리보기
@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    RoadNovelTheme { HomeScreen() }
}
