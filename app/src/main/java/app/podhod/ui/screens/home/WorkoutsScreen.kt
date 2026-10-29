@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.ProgramWithItems
import app.podhod.data.db.SessionEntity
import app.podhod.data.db.SessionSummaryRow
import app.podhod.data.repo.FinishResult
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.LoadType
import app.podhod.domain.Period
import app.podhod.domain.WeightUnit
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.SectionHeader
import app.podhod.ui.components.StatTile
import app.podhod.ui.components.TabScaffold
import app.podhod.ui.components.appViewModel
import app.podhod.ui.components.countLabel
import app.podhod.ui.components.partialVolumeNote
import app.podhod.ui.components.rememberNowMillis
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun WorkoutsScreen(
    onOpenWorkout: (Long) -> Unit,
    onNewProgram: () -> Unit,
    onEditProgram: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { WorkoutsViewModel(it) }
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val names by vm.exerciseNames.collectAsStateWithLifecycle()
    val lastProgram by vm.lastUsedProgram.collectAsStateWithLifecycle()
    val active by vm.activeSession.collectAsStateWithLifecycle()
    val progress by vm.activeProgress.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.unit
    val zone = LocalZone.current
    val now by rememberNowMillis(60_000L)

    var pickProgram by remember { mutableStateOf(false) }
    var deleteProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var nothingCompleted by remember { mutableStateOf(false) }

    fun start(programId: Long?) {
        vm.start(programId) { sessionId, _ -> onOpenWorkout(sessionId) }
    }

    TabScaffold(
        title = "Тренировки",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
        },
    ) { padding ->
        val sessions = summaries
        val programList = programs
        if (sessions == null || programList == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@TabScaffold
        }

        if (sessions.isEmpty() && programList.isEmpty() && active == null) {
            FirstRun(
                modifier = Modifier.padding(padding),
                onCreateProgram = onNewProgram,
                onStartEmpty = { start(null) },
            )
            return@TabScaffold
        }

        val weekStart = DateFmt.startOfWeek(now, zone)
        val weekCount = sessions.count { it.startedAt >= weekStart }
        val from = period.fromMillis(now)
        val inPeriod = sessions.filter { it.startedAt >= from }
        val volumeKg = inPeriod.sumOf { it.volume }
        val skipped = inPeriod.sumOf { it.skippedSets }
        val last = sessions.firstOrNull()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val activeSession = active
            if (activeSession != null) {
                item(key = "active") {
                    ActiveSessionCard(
                        session = activeSession,
                        progress = progress,
                        zone = zone,
                        onContinue = { onOpenWorkout(activeSession.id) },
                        onFinish = {
                            if ((progress?.done ?: 0) == 0) nothingCompleted = true else confirmFinish = true
                        },
                        onCancel = { confirmCancel = true },
                    )
                }
            } else {
                item(key = "start") {
                    Button(
                        onClick = { if (programList.isEmpty()) start(null) else pickProgram = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Начать тренировку", style = MaterialTheme.typography.titleMedium)
                    }
                }
                val lp = lastProgram
                if (lp != null) {
                    val count = programList.firstOrNull { it.program.id == lp.id }?.items?.size ?: 0
                    item(key = "last-program") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Последняя программа",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(lp.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        countLabel(count, "упражнение", "упражнения", "упражнений"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilledTonalButton(onClick = { start(lp.id) }) { Text("Начать") }
                            }
                        }
                    }
                }
            }

            if (last != null) {
                item(key = "last-workout") {
                    LastWorkoutCard(last, unit, zone) { onOpenSession(last.id) }
                }
            }

            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = "На этой неделе",
                            value = countLabel(weekCount, "тренировка", "тренировки", "тренировок"),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Объём, " + period.title,
                            value = if (inPeriod.any { it.volume > 0.0 }) Fmt.volume(volumeKg, unit) else "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (skipped > 0) {
                        Text(
                            "Объём указан " + partialVolumeNote(skipped) + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Period.values().forEach { p ->
                            FilterChip(selected = p == period, onClick = { vm.setPeriod(p) }, label = { Text(p.title) })
                        }
                    }
                }
            }

            if (highlights.isNotEmpty()) {
                item(key = "highlights") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Прогресс за 30 дней", style = MaterialTheme.typography.titleSmall)
                            highlights.forEach { h ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(h.exerciseName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        changeText(h, unit),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "programs-header") {
                SectionHeader(
                    "Программы",
                    modifier = Modifier.padding(horizontal = 0.dp),
                    trailing = { TextButton(onClick = onNewProgram) { Text("Новая") } },
                )
            }
            if (programList.isEmpty()) {
                item(key = "programs-empty") {
                    Text(
                        "Программ пока нет. Создайте программу, чтобы запускать тренировку в один тап.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(programList, key = { "program-" + it.program.id }) { p ->
                    ProgramCard(
                        program = p,
                        names = names,
                        onStart = { start(p.program.id) },
                        onEdit = { onEditProgram(p.program.id) },
                        onCopy = { vm.duplicate(p.program.id) },
                        onDelete = { deleteProgramId = p.program.id },
                    )
                }
            }
        }
    }

    if (pickProgram) {
        val list = programs.orEmpty()
        AlertDialog(
            onDismissRequest = { pickProgram = false },
            title = { Text("Что тренируем?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("Без программы") },
                        supportingContent = { Text("Упражнения добавите по ходу тренировки") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            pickProgram = false
                            start(null)
                        },
                    )
                    HorizontalDivider()
                    list.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.program.name) },
                            supportingContent = {
                                Text(countLabel(p.items.size, "упражнение", "упражнения", "упражнений"))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                pickProgram = false
                                start(p.program.id)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickProgram = false }) { Text("Отмена") } },
        )
    }

    deleteProgramId?.let { id ->
        val name = programs.orEmpty().firstOrNull { it.program.id == id }?.program?.name.orEmpty()
        ConfirmDialog(
            title = "Удалить программу?",
            text = "Программа «$name» будет удалена. Завершённые тренировки по ней останутся в истории.",
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = {
                vm.delete(id)
                deleteProgramId = null
            },
            onDismiss = { deleteProgramId = null },
        )
    }

    val activeSession = active
    if (confirmFinish && activeSession != null) {
        val incomplete = ((progress?.total ?: 0) - (progress?.done ?: 0)).coerceAtLeast(0)
        ConfirmDialog(
            title = "Завершить тренировку?",
            text = if (incomplete > 0) {
                "Невыполненные подходы ($incomplete) не попадут в историю и будут удалены."
            } else {
                "Все подходы выполнены. Тренировка будет сохранена в истории."
            },
            confirmLabel = "Завершить",
            onConfirm = {
                confirmFinish = false
                vm.finish(activeSession.id) { r ->
                    if (r is FinishResult.Finished) onOpenSession(activeSession.id) else nothingCompleted = true
                }
            },
            onDismiss = { confirmFinish = false },
        )
    }
    if (nothingCompleted && activeSession != null) {
        ConfirmDialog(
            title = "Нет выполненных подходов",
            text = "Чтобы завершить тренировку, отметьте хотя бы один подход. " +
                "Если тренировка не нужна, отмените её.",
            confirmLabel = "Продолжить тренировку",
            dismissLabel = "Закрыть",
            onConfirm = {
                nothingCompleted = false
                onOpenWorkout(activeSession.id)
            },
            onDismiss = { nothingCompleted = false },
        )
    }
    if (confirmCancel && activeSession != null) {
        ConfirmDialog(
            title = "Отменить тренировку?",
            text = "Все данные этой тренировки будут удалены без возможности восстановления.",
            confirmLabel = "Удалить тренировку",
            dismissLabel = "Назад",
            destructive = true,
            onConfirm = {
                confirmCancel = false
                vm.cancel(activeSession.id)
            },
            onDismiss = { confirmCancel = false },
        )
    }
}

@Composable
private fun FirstRun(modifier: Modifier, onCreateProgram: () -> Unit, onStartEmpty: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Добро пожаловать в «Подходы»", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Создайте программу тренировок или сразу начните тренировку без программы. " +
                "Приложение запомнит веса и повторения и покажет их в следующий раз.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateProgram, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Создать программу") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onStartEmpty, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Начать тренировку без программы")
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: SessionEntity,
    progress: ActiveProgress?,
    zone: ZoneId,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Идёт тренировка",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(session.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            val doneText = progress?.let { " · выполнено ${it.done} из ${it.total}" }.orEmpty()
            Text(
                "Начата " + DateFmt.dateTime(session.startedAt, zone) + doneText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue, modifier = Modifier.weight(1f).height(48.dp)) { Text("Продолжить") }
                OutlinedButton(onClick = onFinish, modifier = Modifier.height(48.dp)) { Text("Завершить") }
            }
            TextButton(onClick = onCancel) {
                Text("Отменить тренировку", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LastWorkoutCard(row: SessionSummaryRow, unit: WeightUnit, zone: ZoneId, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Последняя тренировка · " + DateFmt.weekdayDayMonth(row.startedAt, zone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            val parts = mutableListOf<String>()
            parts += countLabel(row.exerciseCount, "упражнение", "упражнения", "упражнений")
            parts += countLabel(row.setCount, "подход", "подхода", "подходов")
            if (row.volume > 0.0) parts += "объём " + Fmt.volume(row.volume, unit)
            row.finishedAt?.let { parts += Fmt.duration(it - row.startedAt) }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.skippedSets > 0) {
                Text(
                    "Объём указан " + partialVolumeNote(row.skippedSets) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: ProgramWithItems,
    names: Map<Long, String>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val sorted = program.items.sortedBy { it.position }
    val preview = sorted.take(3).mapNotNull { names[it.exerciseId] }.joinToString(", ") +
        if (sorted.size > 3) "…" else ""

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(program.program.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    countLabel(sorted.size, "упражнение", "упражнения", "упражнений"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.isNotEmpty()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            FilledTonalButton(onClick = onStart) { Text("Начать") }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с программой") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Копировать") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = { menu = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun changeText(h: Highlight, unit: WeightUnit): String {
    val parts = mutableListOf<String>()
    val w = h.weightChangeKg
    if (h.loadType != LoadType.REPS_ONLY && w != null && w != 0.0) {
        parts += (if (w > 0) "+" else "−") + Fmt.weight(abs(w), unit)
    }
    val r = h.repsChange
    if (r != null && r != 0) {
        parts += (if (r > 0) "+" else "−") + abs(r) + " повт."
    }
    return parts.joinToString(", ")
}
