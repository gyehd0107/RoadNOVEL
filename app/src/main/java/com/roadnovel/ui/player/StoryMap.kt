package com.roadnovel.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.story.Story
import com.roadnovel.ui.theme.RN

/**
 * 장면 그래프를 층으로 나눈 배치 (0..1 비율 좌표).
 * 층 = 시작에서 가장 먼 거리, 아래(출발)에서 위(결말)로 올라간다.
 */
internal fun layoutStoryMap(story: Story): Map<String, Offset> {
    val depth = mutableMapOf<String, Int>()
    fun visit(id: String, d: Int) {
        if ((depth[id] ?: -1) >= d) return
        depth[id] = d
        story.scenes[id]?.nextIds?.forEach { visit(it, d + 1) }
    }
    visit(story.start, 0)

    // 같은 층 안의 순서: 처음 발견한 순서 (부모 순서를 따라감)
    val order = mutableListOf<String>()
    val queue = ArrayDeque(listOf(story.start))
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (id in order) continue
        order += id
        story.scenes[id]?.nextIds?.forEach(queue::addLast)
    }
    val maxDepth = depth.values.maxOrNull() ?: 0
    val layers = order.groupBy { depth[it] ?: 0 }
    return buildMap {
        layers.forEach { (d, ids) ->
            ids.forEachIndexed { i, id ->
                val x = (i + 1f) / (ids.size + 1f)
                val y = if (maxDepth == 0) 0.5f else 0.92f - 0.84f * d / maxDepth
                put(id, Offset(x, y))
            }
        }
    }
}

/** 왼쪽 이야기 지도: 지나온 길은 금색, 지금 장면은 🚗 */
@Composable
fun StoryMap(story: Story, path: List<String>, currentId: String, lengthMin: Int, modifier: Modifier = Modifier) {
    val pos = remember(story.id) { layoutStoryMap(story) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, color = RN.Sub)
    val iconStyle = TextStyle(fontSize = 16.sp)
    val skipOptional = lengthMin <= 10

    Canvas(modifier) {
        fun at(id: String) = pos[id]?.let { Offset(it.x * size.width, it.y * size.height) }

        // 길
        story.scenes.forEach { (id, sc) ->
            val a = at(id) ?: return@forEach
            sc.nextIds.forEach { n ->
                val b = at(n) ?: return@forEach
                val walked = id in path && n in path
                drawLine(
                    color = if (walked) RN.Gold else RN.Line,
                    start = a, end = b,
                    strokeWidth = if (walked) 4.dp.toPx() else 2.dp.toPx(),
                    pathEffect = if (walked) null else PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                )
            }
        }
        // 장면 동그라미
        story.scenes.forEach { (id, sc) ->
            val c = at(id) ?: return@forEach
            val cur = id == currentId
            val been = id in path
            val skipped = sc.optional && skipOptional
            val r = when {
                cur -> 17.dp.toPx()
                sc.ending != null -> 15.dp.toPx()
                else -> 11.dp.toPx()
            }
            val alpha = if (skipped) 0.35f else 1f
            drawCircle(
                color = when { cur -> RN.Gold; been -> RN.Teal; else -> RN.Panel2 },
                radius = r, center = c, alpha = alpha,
            )
            drawCircle(color = RN.LineStrong, radius = r, center = c, style = Stroke(2.dp.toPx()), alpha = alpha)

            val icon = when {
                cur -> "🚗"
                sc.ending != null -> if (been) sc.ending.badge else "?"
                else -> null
            }
            icon?.let {
                val t = measurer.measure(it, iconStyle)
                drawText(t, topLeft = c - Offset(t.size.width / 2f, t.size.height / 2f))
            }
            val name = if (skipped) "(건너뜀)" else sc.place.let { p -> if (p.length > 6) p.take(6) + "…" else p }
            val t = measurer.measure(name, labelStyle.copy(color = if (cur) RN.Gold2 else RN.Sub))
            val dy = if (sc.ending != null) -(r + 4.dp.toPx() + t.size.height) else r + 4.dp.toPx()
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y + dy), alpha = alpha)
        }
    }
}
