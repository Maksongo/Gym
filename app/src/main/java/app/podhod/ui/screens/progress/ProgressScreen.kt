@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.ExerciseEntity
import app.podhod.domain.ChartMetric
import app.podhod.domain.ChartPoint
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.LoadType
import app.podhod.domain.NameNormalizer
import app.podhod.domain.Period
import app.podhod.domain.PreviousResultFormatter
import app.podhod.domain.ProgressCalculator
import app.podhod.domain.SessionPoint
import app.podhod.domain.SetValues
import app.podhod.domain.StatSet
import app.podhod.domain.WeightUnit
import app.podhod.ui.components.EmptyState
import app.podhod.ui.components.FullScreenDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.LineChart
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.SearchField
import app.podhod.ui.components.SectionHeader
import app.podhod.ui.components.StatTile
import app.podhod.ui.components.TabScaffold
import app.podhod.ui.components.appViewModel
import app.podhod.ui.components.countLabel
import app.podhod.ui.components.partialVolumeNote
import java.time.ZoneId
import kotlin.math.abs

private data class Tile(val label: String, val value: String, val caption: String? = null)

@Composable
fun ProgressScreen(onOpenSession: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val exerciseId by vm.exerciseId.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val exercises by vm.exercisesWithHistory.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.unit
    val zone = LocalZone.current
    var selecting by remember { mutableStateOf(false) }

    val selectedExercise = exerciseId?.let { id -> exercises.firstOrNull { it.id == id } }

    TabScaffold(
        title = "Прогресс",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
        },
    ) { padding ->
        val data = rows
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selecting = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedExercise?.name ?: "Все упражнения",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Period.values().forEach { p ->
                            FilterChip(selected = p == period, onClick = { vm.selectPeriod(p) }, label = { Text(p.title) })
                        }
                    }
                }
            }

            if (data == null) {
                item(key = "loading") { FullScreenLoading(Modifier.padding(top = 48.dp)) }
            } else if (data.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.BarChart,
                        title = "Недостаточно данных",
                        text = if (selectedExercise != null || period != Period.ALL) {
                            "За выбранный период нет выполненных подходов. Выберите больший период или другое упражнение."
                        } else {
                            "Прогресс появится после первой завершённой тренировки. Графики строятся только по реальным записям."
                        },
                    )
                }
            } else if (exerciseId == null) {
                overallItems(data, unit, zone)
            } else {
                exerciseItems(
                    data = data,
                    metric = metric,
                    unit = unit,
                    zone = zone,
                    onMetric = { vm.selectMetric(it) },
                    onOpenSession = onOpenSession,
                )
            }
        }
    }

    if (selecting) {
        ExerciseSelectDialog(
            list = exercises,
            selectedId = exerciseId,
            onSelect = {
                vm.selectExercise(it)
                selecting = false
            },
            onDismiss = { selecting = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Все упражнения
// ---------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.overallItems(
    data: List<StatSet>,
    unit: WeightUnit,
    zone: ZoneId,
) {
    item(key = "overall-tiles") {
        val summary = ProgressCalculator.summary(data)
        val tiles = listOf(
            Tile("Тренировок", summary.sessionsCount.toString()),
            Tile("Подходов", data.size.toString()),
            Tile(
                "Объём",
                if (summary.totalVolume.hasData) Fmt.volume(summary.totalVolume.totalKg, unit) else "—",
                if (summary.totalVolume.isPartial) partialVolumeNote(summary.totalVolume.skippedSets) else null,
            ),
        )
        TileGrid(tiles)
    }
    item(key = "overall-chart") {
        val weeks = ProgressCalculator.weeklyVolume(data, zone)
        val points = weeks.mapIndexed { i, w -> ChartPoint(w.weekStart, w.volume.totalKg, i.toLong()) }
        val anyVolume = weeks.any { it.volume.hasData }
        ChartCard(title = "Тренировочный объём по неделям, ${unit.label}") {
            if (points.size >= 2 && anyVolume) {
                var selected by remember(points) { mutableStateOf<Int?>(null) }
                LineChart(
                    points = points.map { it.copy(y = unit.fromKg(it.y)) },
                    yLabel = { Fmt.grouped(it, 0) },
                    xLabel = { DateFmt.numericShort(it, zone) },
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                selected?.let { i ->
                    val w = weeks[i]
                    Text(
                        "Неделя с " + DateFmt.dayMonth(w.weekStart, zone) + ": " +
                            if (w.volume.hasData) Fmt.volume(w.volume.totalKg, unit) else "нет данных об объёме",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (weeks.any { it.volume.isPartial }) {
                    Text(
                        "В объём не входят подходы упражнений со своим весом и без веса.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                NoData("Для графика нужны данные хотя бы за две недели. Продолжайте тренироваться, и график появится.")
            }
        }
    }
    item(key = "overall-hint") {
        Text(
            "Выберите упражнение выше, чтобы увидеть изменение рабочего веса, повторений, лучший результат и оценочный 1ПМ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Одно упражнение
// ---------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.exerciseItems(
    data: List<StatSet>,
    metric: ChartMetric,
    unit: WeightUnit,
    zone: ZoneId,
    onMetric: (ChartMetric) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val loadType = data.first().loadType
    val points = ProgressCalculator.perSession(data)
    val summary = ProgressCalculator.summary(data)

    item(key = "tiles") {
        val tiles = mutableListOf<Tile>()
        tiles += Tile("Тренировок", summary.sessionsCount.toString())
        if (loadType != LoadType.REPS_ONLY) {
            summary.maxWeightKg?.let {
                tiles += Tile(
                    if (loadType == LoadType.BODYWEIGHT) "Макс. доп. вес" else "Макс. вес за период",
                    Fmt.weight(it, unit),
                )
            }
        }
        summary.bestSet?.let { best ->
            tiles += Tile(
                "Лучший подход",
                PreviousResultFormatter.format(listOf(SetValues(best.weight, best.reps)), loadType, unit) ?: "—",
                DateFmt.dayMonth(best.startedAt, zone),
            )
        }
        summary.bestOneRmKg?.let {
            tiles += Tile(
                "1ПМ (оценка)",
                "≈ " + Fmt.weight(it, unit),
                "Формула Эпли. Это оценка, а не фактический максимум",
            )
        }
        summary.weightChangeKg?.let {
            if (loadType != LoadType.REPS_ONLY) tiles += Tile("Изменение веса", signed(it, unit))
        }
        summary.repsChange?.let { tiles += Tile("Изменение повторений", (if (it > 0) "+" else if (it < 0) "−" else "") + abs(it)) }
        val volumeSeries = ProgressCalculator.series(points, ChartMetric.VOLUME)
        if (summary.totalVolume.hasData) {
            tiles += Tile(
                "Объём за период",
                Fmt.volume(summary.totalVolume.totalKg, unit),
                if (summary.totalVolume.isPartial) partialVolumeNote(summary.totalVolume.skippedSets) else null,
            )
        } else if (loadType != LoadType.WEIGHTED) {
            tiles += Tile("Объём за период", "—", "Для этого упражнения объём (вес × повторения) не считается")
        }
        if (volumeSeries.size >= 2) {
            tiles += Tile("Изменение объёма", signed(volumeSeries.last().y - volumeSeries.first().y, unit, volume = true))
        }
        TileGrid(tiles)
    }

    item(key = "chart") {
        val series = ProgressCalculator.series(points, metric)
        val available = ChartMetric.values().filter { m ->
            when (m) {
                ChartMetric.WEIGHT -> loadType != LoadType.REPS_ONLY
                ChartMetric.ONE_RM -> loadType == LoadType.WEIGHTED
                else -> true
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { m ->
                    FilterChip(selected = m == metric, onClick = { onMetric(m) }, label = { Text(m.title) })
                }
            }
            ChartCard(title = chartTitle(metric, unit)) {
                if (series.size >= 2) {
                    var selected by remember(series) { mutableStateOf<Int?>(null) }
                    LineChart(
                        points = series.map { it.copy(y = displayValue(it.y, metric, unit)) },
                        yLabel = { yLabel(it, metric) },
                        xLabel = { DateFmt.numericShort(it, zone) },
                        selectedIndex = selected,
                        onSelect = { selected = it },
                    )
                    selected?.let { i ->
                        val p = series[i]
                        val point = points.firstOrNull { it.sessionId == p.sessionId }
                        Text(
                            DateFmt.dayMonth(p.x, zone) + ": " + valueText(p.y, metric, unit),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        point?.let {
                            Text(
                                PreviousResultFormatter.format(it.sets, it.loadType, unit).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (metric == ChartMetric.ONE_RM) {
                        Text(
                            "1ПМ рассчитан по формуле Эпли (вес × (1 + повторения / 30)) для подходов до " +
                                "${app.podhod.domain.OneRepMax.MAX_RELIABLE_REPS} повторений. Это оценка, а не фактический максимум.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (series.size == 1) {
                    NoData(
                        "Для графика нужно минимум две тренировки с этим упражнением в выбранном периоде. " +
                            "Сейчас есть одна: " + valueText(displayValue(series[0].y, metric, unit), metric, unit) + ".",
                    )
                } else {
                    NoData("Для выбранной метрики нет данных по этому упражнению.")
                }
            }
        }
    }

    item(key = "history-header") {
        SectionHeader("История подходов", modifier = Modifier.padding(horizontal = 0.dp))
    }
    items(points.reversed(), key = { "point-" + it.sessionId }) { p ->
        SessionPointRow(p, unit, zone) { onOpenSession(p.sessionId) }
    }
}

// ---------------------------------------------------------------------------------------------
// Вспомогательные composable и форматирование
// ---------------------------------------------------------------------------------------------

@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { t ->
                    StatTile(label = t.label, value = t.value, caption = t.caption, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun NoData(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SessionPointRow(p: SessionPoint, unit: WeightUnit, zone: ZoneId, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(DateFmt.weekdayDayMonth(p.startedAt, zone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(PreviousResultFormatter.format(p.sets, p.loadType, unit).orEmpty(), style = MaterialTheme.typography.bodyLarge)
            if (p.bestOneRmKg != null) {
                Text(
                    "1ПМ (оценка) ≈ " + Fmt.weight(p.bestOneRmKg, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExerciseSelectDialog(
    list: List<ExerciseEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(list, query) {
        val q = NameNormalizer.key(query)
        if (q.isEmpty()) list else list.filter { NameNormalizer.key(it.name).contains(q) }
    }
    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Упражнение") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "all") {
                        SelectRow("Все упражнения", selectedId == null) { onSelect(null) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (list.isEmpty()) {
                        item(key = "none") {
                            Text(
                                "Пока нет упражнений с выполненными подходами.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { e ->
                        SelectRow(e.name, selectedId == e.id) { onSelect(e.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = MaterialTheme.colorScheme.primary)
    }
}

private fun signed(value: Double, unit: WeightUnit, volume: Boolean = false): String {
    if (value == 0.0) return "без изменений"
    val sign = if (value > 0) "+" else "−"
    val text = if (volume) Fmt.volume(abs(value), unit) else Fmt.weight(abs(value), unit)
    return sign + text
}

/** Значение для оси графика: вес и объём переводятся в выбранные единицы. */
private fun displayValue(kgOrCount: Double, metric: ChartMetric, unit: WeightUnit): Double = when (metric) {
    ChartMetric.REPS -> kgOrCount
    else -> unit.fromKg(kgOrCount)
}

private fun yLabel(v: Double, metric: ChartMetric): String = when (metric) {
    ChartMetric.REPS -> Fmt.number(v, 0)
    ChartMetric.VOLUME -> Fmt.grouped(v, 0)
    else -> Fmt.number(v, 1)
}

private fun valueText(displayValue: Double, metric: ChartMetric, unit: WeightUnit): String = when (metric) {
    ChartMetric.REPS -> Fmt.number(displayValue, 0) + " повт."
    ChartMetric.VOLUME -> Fmt.grouped(displayValue, 1) + NBSP_UNIT + unit.label
    ChartMetric.ONE_RM -> "≈ " + Fmt.number(displayValue, 1) + NBSP_UNIT + unit.label
    ChartMetric.WEIGHT -> Fmt.number(displayValue, 1) + NBSP_UNIT + unit.label
}

private const val NBSP_UNIT = " "

private fun chartTitle(metric: ChartMetric, unit: WeightUnit): String = when (metric) {
    ChartMetric.WEIGHT -> "Рабочий (максимальный) вес по тренировкам, ${unit.label}"
    ChartMetric.REPS -> "Максимум повторений в подходе по тренировкам"
    ChartMetric.VOLUME -> "Тренировочный объём по тренировкам, ${unit.label}"
    ChartMetric.ONE_RM -> "Оценочный 1ПМ по тренировкам, ${unit.label}"
}
