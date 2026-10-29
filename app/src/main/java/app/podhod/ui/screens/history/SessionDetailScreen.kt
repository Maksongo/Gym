@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.SetEntity
import app.podhod.data.db.WorkoutExerciseWithSets
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.LoadType
import app.podhod.domain.SessionStatus
import app.podhod.domain.SetData
import app.podhod.domain.VolumeCalculator
import app.podhod.domain.WeightUnit
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.SetRow
import app.podhod.ui.components.SetTableHeader
import app.podhod.ui.components.TextInputDialog
import app.podhod.ui.components.appViewModel
import app.podhod.ui.components.countLabel
import app.podhod.ui.components.partialVolumeNote
import app.podhod.ui.screens.workout.SessionUi
import app.podhod.ui.screens.workout.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val vm = appViewModel(key = "session-$sessionId") { SessionViewModel(it, sessionId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.unit
    val zone = LocalZone.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editMode by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }
    var copyDialog by remember { mutableStateOf(false) }
    var activeExists by remember { mutableStateOf<Long?>(null) }
    var removeExerciseId by remember { mutableStateOf<Long?>(null) }

    val ready = ui as? SessionUi.Ready

    LaunchedEffect(ui) {
        when (val s = ui) {
            SessionUi.Missing -> onBack()
            is SessionUi.Ready -> if (s.data.session.status == SessionStatus.IN_PROGRESS) onOpenWorkout(sessionId)
            SessionUi.Loading -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(ready?.data?.session?.name ?: "Тренировка", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                                contentDescription = if (editMode) "Закончить исправление" else "Исправить результаты",
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Ещё") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Повторить тренировку") },
                                    leadingIcon = { Icon(Icons.Filled.Replay, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            val r = vm.repeat()
                                            when {
                                                r == null -> snackbar.showSnackbar("Не удалось повторить тренировку")
                                                r.alreadyActive -> activeExists = r.sessionId
                                                else -> onOpenWorkout(r.sessionId)
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Копировать в новую программу") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = { menu = false; copyDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Переименовать") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { menu = false; renameDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Заметка к тренировке") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { menu = false; notesDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить тренировку", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (ready == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val data = ready.data
        val session = data.session
        val list = data.exercises.sortedBy { it.exercise.position }
        val volume = VolumeCalculator.volume(
            list.flatMap { we ->
                we.sets.filter { it.isCompleted && it.reps > 0 }.map { SetData(it.weight, it.reps, we.exercise.loadType) }
            },
        )
        val completedSets = list.sumOf { we -> we.sets.count { it.isCompleted } }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(DateFmt.weekdayDayMonth(session.startedAt, zone), style = MaterialTheme.typography.titleMedium)
                        val finished = session.finishedAt
                        val timeText = if (finished != null) {
                            DateFmt.time(session.startedAt, zone) + " – " + DateFmt.time(finished, zone) +
                                " · " + Fmt.duration(finished - session.startedAt)
                        } else {
                            "Начало: " + DateFmt.time(session.startedAt, zone)
                        }
                        Text(timeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            countLabel(list.size, "упражнение", "упражнения", "упражнений") + " · " +
                                countLabel(completedSets, "подход", "подхода", "подходов"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Тренировочный объём: " + if (volume.hasData) Fmt.volume(volume.totalKg, unit) else "—",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (volume.isPartial) {
                            Text(
                                "Объём указан " + partialVolumeNote(volume.skippedSets) + ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!session.notes.isNullOrBlank()) {
                            Text(session.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (editMode) {
                item(key = "edit-hint") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Режим исправления. Изменения сохраняются сразу и касаются только этой тренировки. " +
                                "Подходы без отметки ✓ не учитываются в истории и статистике.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            items(list, key = { it.exercise.id }) { we ->
                HistoryExerciseCard(
                    data = we,
                    name = exercises[we.exercise.exerciseId]?.name ?: "Упражнение",
                    unit = unit,
                    editMode = editMode,
                    onWeight = { setId, kg -> vm.setWeight(setId, kg) },
                    onReps = { setId, reps -> vm.setReps(setId, reps) },
                    onToggle = { set, checked -> vm.toggleSet(set, checked, null) },
                    onDeleteSet = { vm.deleteSet(it) },
                    onAddSet = { vm.addSet(we.exercise.id) },
                    onRemoveExercise = { removeExerciseId = we.exercise.id },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить тренировку?",
            text = "Тренировка и все её подходы будут удалены из истории без возможности восстановления.",
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    vm.delete()
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    removeExerciseId?.let { id ->
        ConfirmDialog(
            title = "Удалить упражнение?",
            text = "Упражнение и все его подходы будут удалены из этой тренировки.",
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = {
                removeExerciseId = null
                vm.removeExercise(id)
            },
            onDismiss = { removeExerciseId = null },
        )
    }

    activeExists?.let { id ->
        ConfirmDialog(
            title = "Есть незавершённая тренировка",
            text = "Сначала продолжите или завершите текущую тренировку, потом можно начать новую.",
            confirmLabel = "Открыть её",
            dismissLabel = "Закрыть",
            onConfirm = {
                activeExists = null
                onOpenWorkout(id)
            },
            onDismiss = { activeExists = null },
        )
    }

    if (renameDialog && ready != null) {
        TextInputDialog(
            title = "Название тренировки",
            label = "Название",
            initial = ready.data.session.name,
            onConfirm = {
                renameDialog = false
                vm.renameSession(it)
            },
            onDismiss = { renameDialog = false },
            maxLength = 80,
        )
    }

    if (notesDialog && ready != null) {
        TextInputDialog(
            title = "Заметка к тренировке",
            label = "Заметка",
            initial = ready.data.session.notes.orEmpty(),
            onConfirm = {
                notesDialog = false
                vm.updateSessionNotes(it)
            },
            onDismiss = { notesDialog = false },
            singleLine = false,
            allowEmpty = true,
        )
    }

    if (copyDialog && ready != null) {
        TextInputDialog(
            title = "Новая программа из тренировки",
            label = "Название программы",
            initial = ready.data.session.name,
            confirmLabel = "Создать",
            onConfirm = { name ->
                copyDialog = false
                scope.launch {
                    val id = vm.copyToProgram(name)
                    snackbar.showSnackbar(
                        if (id != null) "Программа создана. Она появилась на вкладке «Тренировки»" else "Не удалось создать программу",
                    )
                }
            },
            onDismiss = { copyDialog = false },
            maxLength = 80,
        )
    }
}

@Composable
private fun HistoryExerciseCard(
    data: WorkoutExerciseWithSets,
    name: String,
    unit: WeightUnit,
    editMode: Boolean,
    onWeight: (Long, Double) -> Unit,
    onReps: (Long, Int) -> Unit,
    onToggle: (SetEntity, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit,
) {
    val loadType = data.exercise.loadType
    val sets = data.sets.sortedBy { it.setNumber }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (editMode) {
                    IconButton(onClick = onRemoveExercise) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить упражнение", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!data.exercise.notes.isNullOrBlank()) {
                Text(data.exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (editMode) {
                SetTableHeader(loadType = loadType, unit = unit)
                sets.forEach { set ->
                    key(set.id) {
                        SetRow(
                            number = set.setNumber,
                            set = set,
                            loadType = loadType,
                            unit = unit,
                            onWeightKg = { kg -> onWeight(set.id, kg) },
                            onReps = { r -> onReps(set.id, r) },
                            onToggle = { checked -> onToggle(set, checked) },
                            onDelete = { onDeleteSet(set.id) },
                        )
                    }
                }
                TextButton(onClick = onAddSet) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Подход")
                }
            } else {
                val done = sets.filter { it.isCompleted && it.reps > 0 }
                if (done.isEmpty()) {
                    Text("Нет выполненных подходов", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                done.forEachIndexed { index, set ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(28.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(setLine(set, loadType, unit), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun setLine(set: SetEntity, loadType: LoadType, unit: WeightUnit): String = when (loadType) {
    LoadType.REPS_ONLY -> "${set.reps} повт."
    LoadType.WEIGHTED -> Fmt.weight(set.weight, unit) + " × " + set.reps
    LoadType.BODYWEIGHT ->
        (if (set.weight > 0.0) "+" + Fmt.weight(set.weight, unit) else "свой вес") + " × " + set.reps
}
