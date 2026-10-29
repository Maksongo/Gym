package app.podhod.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.podhod.domain.ChartPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Округляет диапазон до «красивых» делений оси. */
internal fun niceRange(min: Double, max: Double, ticks: Int = 4): Triple<Double, Double, Double> {
    var lo = min
    var hi = max
    if (hi - lo < 1e-9) {
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
        lo -= pad
        hi += pad
    }
    val raw = (hi - lo) / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = mag * when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5.0 -> 5.0
        else -> 10.0
    }
    var niceLo = floor(lo / step) * step
    val niceHi = ceil(hi / step) * step
    if (min >= 0 && niceLo < 0) niceLo = 0.0
    return Triple(niceLo, niceHi, step)
}

/**
 * Линейный график по точкам тренировок. Ось X пропорциональна времени. По нажатию выбирается
 * ближайшая точка (индекс возвращается в [onSelect]), подпись выбранной точки показывает вызывающий код.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    yLabel: (Double) -> String,
    xLabel: (Long) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = textColor)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points, selectedIndex) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    val left = 52.dp.toPx()
                    val right = size.width - 12.dp.toPx()
                    val t0 = points.first().x
                    val t1 = points.last().x
                    fun px(p: ChartPoint): Float =
                        if (t1 == t0) (left + right) / 2f else left + (right - left) * ((p.x - t0).toFloat() / (t1 - t0).toFloat())
                    val nearest = points.indices.minByOrNull { abs(px(points[it]) - tap.x) }
                    onSelect(if (nearest == selectedIndex) null else nearest)
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val ys = points.map { it.y }
        val (lo, hi, step) = niceRange(ys.min(), ys.max())
        val left = 52.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val t0 = points.first().x
        val t1 = points.last().x

        fun xOf(p: ChartPoint): Float =
            if (t1 == t0) (left + right) / 2f else left + (right - left) * ((p.x - t0).toFloat() / (t1 - t0).toFloat())

        fun yOf(v: Double): Float = (bottom - (bottom - top) * ((v - lo) / (hi - lo)).toFloat())

        // Сетка и подписи оси Y.
        var v = lo
        var guard = 0
        while (v <= hi + step * 0.001 && guard++ < 20) {
            val y = yOf(v)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yLabel(v), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            v += step
        }

        // Подписи оси X: первая и последняя дата (и средняя, если места хватает).
        val xs = if (points.size >= 3 && t1 != t0) listOf(points.first(), points[points.size / 2], points.last()) else listOf(points.first(), points.last())
        var lastRight = -1f
        xs.distinctBy { it.x }.forEach { p ->
            val layout = measurer.measure(xLabel(p.x), labelStyle)
            var x = xOf(p) - layout.size.width / 2f
            x = x.coerceIn(left - 8.dp.toPx(), size.width - layout.size.width.toFloat())
            if (x > lastRight + 8.dp.toPx()) {
                drawText(layout, topLeft = Offset(x, bottom + 6.dp.toPx()))
                lastRight = x + layout.size.width
            }
        }

        // Линия.
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = Offset(xOf(p), yOf(p.y))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Точки.
        points.forEachIndexed { i, p ->
            val o = Offset(xOf(p), yOf(p.y))
            val selected = i == selectedIndex
            drawCircle(bg, radius = (if (selected) 8.dp else 5.5.dp).toPx(), center = o)
            drawCircle(lineColor, radius = (if (selected) 6.dp else 3.5.dp).toPx(), center = o)
        }
    }
}
