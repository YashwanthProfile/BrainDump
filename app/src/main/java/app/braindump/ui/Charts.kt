package app.braindump.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/**
 * Rounded bars on faint tracks. [dim] fades individual bars (for example nights that missed the goal),
 * and [goal] draws a dashed line at that value.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    dim: List<Boolean>? = null,
    goal: Float? = null,
    height: Int = 120,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val n = values.size
            if (n == 0) return@Canvas
            val max = maxOf(values.maxOrNull() ?: 0f, goal ?: 0f, 1f)
            val slot = size.width / n
            val bw = minOf(slot * 0.56f, 28.dp.toPx())
            val radius = CornerRadius(bw / 2, bw / 2)
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(color.copy(alpha = 0.10f), Offset(x, 0f), Size(bw, size.height), radius)
                if (v > 0f) {
                    val h = maxOf(size.height * (v / max), bw)
                    val faded = dim?.getOrNull(i) == true
                    drawRoundRect(if (faded) color.copy(alpha = 0.40f) else color, Offset(x, size.height - h), Size(bw, h), radius)
                }
            }
            if (goal != null && goal > 0f) {
                val y = size.height - size.height * (goal / max)
                drawLine(
                    color.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A grid of dots: one row per label, one column per day. true = filled, false = hollow, null = not yet. */
@Composable
fun DotGrid(rows: List<Pair<String, List<Boolean?>>>, columns: List<String>, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(84.dp))
            columns.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        rows.forEach { (name, cells) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
                cells.forEach { on ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(
                                when (on) {
                                    true -> color
                                    false -> color.copy(alpha = 0.14f)
                                    null -> Color.Transparent
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

/** A month drawn as small rounded squares, Monday first. Brighter squares mean more was written that day. */
@Composable
fun PixelMonth(month: YearMonth, counts: Map<String, Int>, color: Color, modifier: Modifier = Modifier, cell: Int = 14) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val total = offset + month.lengthOfMonth()
    val weeks = (total + 6) / 7
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val day = w * 7 + d - offset + 1
                    val bg = if (day < 1 || day > month.lengthOfMonth()) Color.Transparent else {
                        val n = counts[month.atDay(day).toString()] ?: 0
                        when {
                            n == 0 -> color.copy(alpha = 0.10f)
                            n <= 2 -> color.copy(alpha = 0.45f)
                            n <= 4 -> color.copy(alpha = 0.75f)
                            else -> color
                        }
                    }
                    Box(Modifier.size(cell.dp).clip(RoundedCornerShape(4.dp)).background(bg))
                }
            }
        }
    }
}
