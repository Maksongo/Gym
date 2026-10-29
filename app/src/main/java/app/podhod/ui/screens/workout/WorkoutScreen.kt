@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.db.WorkoutExerciseWithSets
import app.podhod.data.repo.FinishResult
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.RestTimer
import app.podhod.domain.RestTimerState
import app.podhod.domain.WeightUnit
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.EmptyState
import app.podhod.ui.components.ExercisePickerDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.InfoDialog
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.SetRow
import app.podhod.ui.components.SetTableHeader
import app.podhod.ui.components.TextInputDialog
import app.podhod.ui.components.appViewModel
import app.podhod.ui.components.elapsedClock
import app.podhod.ui.components.rememberNowMillis
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun WorkoutScreen(sessionId: Long, onBack: () -> Unit, onFinished: (Long) -> Unit) {
    val vm = appViewModel(key = "workout-$sessionId") { SessionViewModel(it, sessionId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()
    val timer by vm.restTimer.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val zone = LocalZone.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var pickExercise by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var nothingCompleted by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }
    var removeExerciseId by remember { mutableStateOf<Long?>(null) }
    var techniqueFor by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(vm) {
        vm.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(text) }
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Разрешение на уведомления нужно, чтобы таймер отдыха сообщил об окончании при свёрнутом приложении.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && settings.restEnabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(ui) { if (ui is SessionUi.Missing) onBack() }

    val ready = ui as? SessionUi.Ready
    val allSets = ready?.data?.exercises?.flatMap { it.sets }.orEmpty()
    val doneCount = allSets.count { it.isCompleted }

    fun requestFinish() {
        if (doneCount == 0) nothingCompleted = true else confirmFinish = true
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    if (ready != null) WorkoutTitle(ready.data.session.name, ready.data.session.startedAt)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = { restDialog = true }) {
                        Icon(Icons.Filled.Timer, contentDescription = "Таймер отдыха")
                    }
                    TextButton(onClick = { requestFinish() }) { Text("Завершить") }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Заметка к тренировке") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menu = false; notesDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Переименовать") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menu = false; renameDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Отменить тренировку", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menu = false; confirmCancel = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            RestTimerBar(
                timer = timer,
                onAdjust = { vm.adjustRest(it) },
                onStop = { vm.stopRest() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val s = ui) {
            SessionUi.Loading, SessionUi.Missing -> FullScreenLoading(Modifier.padding(padding))
            is SessionUi.Ready -> {
                val list = s.data.exercises.sortedBy { it.exercise.position }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (list.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Filled.FitnessCenter,
                                title = "В тренировке нет упражнений",
                                text = "Добавьте упражнения из библиотеки или создайте своё.",
                                actionLabel = "Добавить упражнение",
                                onAction = { pickExercise = true },
                            )
                        }
                    }
                    itemsIndexed(list, key = { _, we -> we.exercise.id }) { index, we ->
                        ExerciseCard(
                            data = we,
                            exercise = exercises[we.exercise.exerciseId],
                            previous = previous[we.exercise.id],
                            unit = settings.unit,
                            zone = zone,
                            isLast = index == list.lastIndex,
                            onToggle = { set, checked -> vm.toggleSet(set, checked, we.exercise.restSeconds) },
                            onWeight = { setId, kg -> vm.setWeight(setId, kg) },
                            onReps = { setId, reps -> vm.setReps(setId, reps) },
                            onDeleteSet = { vm.deleteSet(it) },
                            onAddSet = { vm.addSet(we.exercise.id) },
                            onNotes = { text -> vm.updateExerciseNotes(we.exercise.id, text) },
                            onApplySuggestion = { sug -> vm.applySuggestion(we.exercise.id, sug) },
                            onDismissSuggestion = { vm.dismissSuggestion(we.exercise.id) },
                            onRemove = { removeExerciseId = we.exercise.id },
                            onShowTechnique = { techniqueFor = exercises[we.exercise.exerciseId] },
                            onNext = { scope.launch { listState.scrollToItem(index + 1) } },
                        )
                    }
                    if (list.isNotEmpty()) {
                        item(key = "add-exercise") {
                            OutlinedButton(
                                onClick = { pickExercise = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text("  Добавить упражнение")
                            }
                        }
                        item(key = "finish") {
                            Button(
                                onClick = { requestFinish() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Завершить тренировку", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }
    }

    if (pickExercise) {
        ExercisePickerDialog(
            onDismiss = { pickExercise = false },
            onPicked = { ids ->
                pickExercise = false
                vm.addExercises(ids)
            },
        )
    }

    if (confirmFinish) {
        val incomplete = allSets.size - doneCount
        ConfirmDialog(
            title = "Завершить тренировку?",
            text = if (incomplete > 0) {
                "Выполнено подходов: $doneCount. Невыполненные подходы ($incomplete) не попадут в историю и будут удалены."
            } else {
                "Все подходы выполнены ($doneCount). Тренировка будет сохранена в истории."
            },
            confirmLabel = "Завершить",
            onConfirm = {
                confirmFinish = false
                scope.launch {
                    when (vm.finish()) {
                        is FinishResult.Finished -> onFinished(sessionId)
                        FinishResult.NothingCompleted -> nothingCompleted = true
                    }
                }
            },
            onDismiss = { confirmFinish = false },
        )
    }

    if (nothingCompleted) {
        ConfirmDialog(
            title = "Нет выполненных подходов",
            text = "Чтобы завершить тренировку, отметьте хотя бы один подход. " +
                "Если тренировка не нужна, её можно отменить.",
            confirmLabel = "Отменить тренировку",
            dismissLabel = "Продолжить",
            destructive = true,
            onConfirm = {
                nothingCompleted = false
                confirmCancel = true
            },
            onDismiss = { nothingCompleted = false },
        )
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Отменить тренировку?",
            text = "Все данные этой тренировки будут удалены без возможности восстановления.",
            confirmLabel = "Удалить тренировку",
            dismissLabel = "Назад",
            destructive = true,
            onConfirm = {
                confirmCancel = false
                scope.launch {
                    vm.delete()
                    onBack()
                }
            },
            onDismiss = { confirmCancel = false },
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

    techniqueFor?.let { e ->
        InfoDialog(e.name, e.technique ?: "Описание техники не добавлено.") { techniqueFor = null }
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

    if (restDialog) {
        RestDialog(
            initialSeconds = settings.restSeconds,
            restEnabled = settings.restEnabled,
            onEnabledChange = { enabled ->
                scope.launch { container.settings.update { it.copy(restEnabled = enabled) } }
            },
            onStart = { seconds ->
                restDialog = false
                vm.startRest(seconds)
            },
            onDismiss = { restDialog = false },
        )
    }
}

@Composable
private fun WorkoutTitle(name: String, startedAt: Long) {
    val now by rememberNowMillis(1000L)
    Column {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Время тренировки " + elapsedClock(now - startedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExerciseCard(
    data: WorkoutExerciseWithSets,
    exercise: ExerciseEntity?,
    previous: PreviousInfo?,
    unit: WeightUnit,
    zone: ZoneId,
    isLast: Boolean,
    onToggle: (app.podhod.data.db.SetEntity, Boolean) -> Unit,
    onWeight: (Long, Double) -> Unit,
    onReps: (Long, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onNotes: (String) -> Unit,
    onApplySuggestion: (app.podhod.domain.ProgressionSuggestion) -> Unit,
    onDismissSuggestion: () -> Unit,
    onRemove: () -> Unit,
    onShowTechnique: () -> Unit,
    onNext: () -> Unit,
) {
    val we = data.exercise
    val sets = data.sets.sortedBy { it.setNumber }
    val done = sets.count { it.isCompleted }
    val allDone = sets.isNotEmpty() && done == sets.size
    var menu by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(!we.notes.isNullOrBlank()) }
    var notesText by remember { mutableStateOf(we.notes.orEmpty()) }
    val title = exercise?.name ?: "Упражнение"
    val target = Fmt.target(we.targetSets, we.targetRepsMin, we.targetRepsMax, null, we.loadType, unit)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (target != null) {
                        Text(
                            "Цель: $target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "$done/${sets.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Действия с упражнением")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Заметка") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menu = false; showNotes = true },
                        )
                        if (!exercise?.technique.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Техника выполнения") },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { menu = false; onShowTechnique() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить упражнение", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }

            // Прошлый результат визуально отделён от текущих подходов.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    val text = if (previous?.text != null) {
                        val date = previous.date?.let { " (" + DateFmt.dayMonthShort(it, zone) + ")" }.orEmpty()
                        "Прошлый раз$date: ${previous.text}"
                    } else {
                        "Раньше это упражнение не выполнялось"
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val suggestion = previous?.suggestion
            if (suggestion != null && sets.none { it.weight >= suggestion.suggestedWeightKg - 1e-6 }) {
                SuggestionCard(
                    suggestion = suggestion,
                    unit = unit,
                    onApply = { onApplySuggestion(suggestion) },
                    onDismiss = onDismissSuggestion,
                )
            }

            SetTableHeader(loadType = we.loadType, unit = unit)
            sets.forEach { set ->
                key(set.id) {
                    SetRow(
                        number = set.setNumber,
                        set = set,
                        loadType = we.loadType,
                        unit = unit,
                        onWeightKg = { kg -> onWeight(set.id, kg) },
                        onReps = { r -> onReps(set.id, r) },
                        onToggle = { checked -> onToggle(set, checked) },
                        onDelete = { onDeleteSet(set.id) },
                    )
                }
            }

            if (showNotes) {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = {
                        notesText = it.take(500)
                        onNotes(notesText)
                    },
                    label = { Text("Заметка к упражнению") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddSet) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Подход")
                }
                if (!isLast) {
                    TextButton(onClick = onNext) {
                        Text("Следующее упражнение ")
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: app.podhod.domain.ProgressionSuggestion,
    unit: WeightUnit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Можно увеличить вес: " + Fmt.weight(suggestion.currentWeightKg, unit) +
                        " → " + Fmt.weight(suggestion.suggestedWeightKg, unit),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                "В последних ${suggestion.sessionsCount} тренировках подряд вы выполнили ${suggestion.targetSets} × ${suggestion.repsMax}. " +
                    "Совет основан только на записанных подходах: технику приложение не оценивает. Решение за вами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply) { Text("Применить") }
                TextButton(onClick = onDismiss) { Text("Не сейчас") }
            }
        }
    }
}

@Composable
private fun RestTimerBar(timer: RestTimerState?, onAdjust: (Int) -> Unit, onStop: () -> Unit) {
    if (timer == null) return
    val now by rememberNowMillis(250L)
    val expired = timer.isExpired(now)
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = if (expired) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (expired) 1f else timer.fractionLeft(now) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (expired) "Отдых закончен" else "Отдых",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (expired) "Пора на подход" else Fmt.clock(timer.remainingSeconds(now)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                if (!expired) {
                    OutlinedButton(onClick = { onAdjust(-RestTimer.STEP_SECONDS) }) { Text("−${RestTimer.STEP_SECONDS}") }
                    OutlinedButton(onClick = { onAdjust(RestTimer.STEP_SECONDS) }) { Text("+${RestTimer.STEP_SECONDS}") }
                }
                FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = if (expired) "Закрыть" else "Остановить таймер")
                }
            }
        }
    }
}

@Composable
private fun RestDialog(
    initialSeconds: Int,
    restEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onStart: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableStateOf(RestTimer.clampSeconds(initialSeconds)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер отдыха") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RestTimer.PRESETS_SECONDS.forEach { p ->
                        FilterChip(selected = seconds == p, onClick = { seconds = p }, label = { Text("$p") })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = { seconds = RestTimer.clampSeconds(seconds - RestTimer.STEP_SECONDS) }) {
                        Text("−${RestTimer.STEP_SECONDS}")
                    }
                    Text(Fmt.clock(seconds), style = MaterialTheme.typography.headlineMedium)
                    OutlinedButton(onClick = { seconds = RestTimer.clampSeconds(seconds + RestTimer.STEP_SECONDS) }) {
                        Text("+${RestTimer.STEP_SECONDS}")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Таймер после подхода", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (restEnabled) "Включён: запускается при отметке подхода"
                            else "Выключен: запускайте только вручную",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = restEnabled, onCheckedChange = onEnabledChange)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(seconds) }) { Text("Запустить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
