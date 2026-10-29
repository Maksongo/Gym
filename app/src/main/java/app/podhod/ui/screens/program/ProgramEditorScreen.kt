@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.podhod.domain.Fmt
import app.podhod.domain.InputRules
import app.podhod.domain.LoadType
import app.podhod.domain.WeightUnit
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.DecimalInput
import app.podhod.ui.components.ExercisePickerDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.IntInput
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.ReorderState
import app.podhod.ui.components.Stepper
import app.podhod.ui.components.appViewModel
import app.podhod.ui.components.reorderHandle
import app.podhod.ui.components.weightStep
import kotlinx.coroutines.launch

@Composable
fun ProgramEditorScreen(programId: Long?, onClose: () -> Unit) {
    val vm = appViewModel(key = "program-" + (programId ?: 0L)) { ProgramEditorViewModel(it, programId) }
    val unit = LocalAppSettings.current.unit
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val reorder = remember(listState) {
        ReorderState(
            list = listState,
            headerCount = 1,
            itemCount = { vm.items.size },
            onMove = { from, to -> vm.move(from, to) },
        )
    }
    var picker by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(vm.missing) { if (vm.missing) onClose() }

    fun back() {
        if (vm.dirty) confirmExit = true else onClose()
    }

    fun save() {
        scope.launch {
            when (vm.save()) {
                is SaveOutcome.Saved -> onClose()
                SaveOutcome.NoExercises -> snackbar.showSnackbar("Добавьте в программу хотя бы одно упражнение")
            }
        }
    }

    BackHandler(enabled = vm.dirty) { confirmExit = true }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (programId == null) "Новая программа" else "Программа") },
                navigationIcon = {
                    IconButton(onClick = { back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { TextButton(onClick = { save() }) { Text("Сохранить") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!vm.loaded) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.setName(it) },
                    label = { Text("Название программы") },
                    placeholder = { Text("Например, «Тренировка А»") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            itemsIndexed(vm.items, key = { _, it -> it.key }) { index, item ->
                val dragging = reorder.draggingKey == item.key
                ProgramItemCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < vm.items.size - 1,
                    unit = unit,
                    dragging = dragging,
                    offsetPx = { if (reorder.draggingKey == item.key) reorder.dragOffset else 0f },
                    handle = Modifier.reorderHandle(reorder, item.key),
                    onChange = { transform -> vm.update(item.key, transform) },
                    onMoveUp = { vm.moveByKey(item.key, -1) },
                    onMoveDown = { vm.moveByKey(item.key, 1) },
                    onRemove = { vm.remove(item.key) },
                )
            }

            if (vm.items.isEmpty()) {
                item(key = "hint") {
                    Text(
                        "В программе пока нет упражнений. Добавьте их из библиотеки или создайте своё.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = { picker = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Добавить упражнения")
                }
            }
        }
    }

    if (picker) {
        ExercisePickerDialog(
            onDismiss = { picker = false },
            onPicked = { ids ->
                picker = false
                vm.addExercises(ids)
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Сохранить изменения?") },
            text = { Text("В программе есть несохранённые изменения.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    save()
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmExit = false
                    onClose()
                }) { Text("Не сохранять", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun ProgramItemCard(
    item: EditItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    unit: WeightUnit,
    dragging: Boolean,
    offsetPx: () -> Float,
    handle: Modifier,
    onChange: ((EditItem) -> EditItem) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offsetPx() },
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChange { it.copy(expanded = !it.expanded) } }
                    .padding(start = 4.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Перетащить, чтобы изменить порядок",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        Fmt.target(
                            sets = item.sets.takeIf { it > 0 },
                            min = item.repsMin.takeIf { it > 0 },
                            max = item.repsMax.takeIf { it > 0 },
                            weightKg = item.weightKg.takeIf { it > 0.0 },
                            loadType = item.loadType,
                            unit = unit,
                        ) ?: "Цели не заданы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (item.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (item.expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Действия с упражнением")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Выше") },
                            enabled = canMoveUp,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                            onClick = { menu = false; onMoveUp() },
                        )
                        DropdownMenuItem(
                            text = { Text("Ниже") },
                            enabled = canMoveDown,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                            onClick = { menu = false; onMoveDown() },
                        )
                        DropdownMenuItem(
                            text = { Text("Убрать из программы", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menu = false; confirmRemove = true },
                        )
                    }
                }
            }

            if (item.expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FieldRow("Подходы") {
                        Stepper(
                            onMinus = { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
                            onPlus = { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(InputRules.MAX_SETS)) } },
                            modifier = Modifier.weight(1f),
                        ) { m ->
                            IntInput(
                                value = item.sets,
                                onChange = { v -> onChange { it.copy(sets = v.coerceAtMost(InputRules.MAX_SETS)) } },
                                modifier = m,
                                placeholder = "3",
                            )
                        }
                    }
                    FieldRow("Повторения") {
                        IntInput(
                            value = item.repsMin,
                            onChange = { v -> onChange { it.copy(repsMin = v) } },
                            modifier = Modifier.weight(1f),
                            placeholder = "от",
                        )
                        Text("–", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
                        IntInput(
                            value = item.repsMax,
                            onChange = { v -> onChange { it.copy(repsMax = v) } },
                            modifier = Modifier.weight(1f),
                            placeholder = "до",
                        )
                    }
                    if (item.loadType != LoadType.REPS_ONLY) {
                        val step = weightStep(unit)
                        FieldRow(if (item.loadType == LoadType.BODYWEIGHT) "Доп. вес, ${unit.label}" else "Вес, ${unit.label}") {
                            Stepper(
                                onMinus = { onChange { it.copy(weightKg = unit.stepKg(it.weightKg, -step)) } },
                                onPlus = { onChange { it.copy(weightKg = unit.stepKg(it.weightKg, step)) } },
                                modifier = Modifier.weight(1f),
                            ) { m ->
                                DecimalInput(
                                    valueKg = item.weightKg,
                                    unit = unit,
                                    onChangeKg = { kg -> onChange { it.copy(weightKg = kg) } },
                                    modifier = m,
                                    placeholder = "—",
                                )
                            }
                        }
                    }
                    FieldRow("Отдых, с") {
                        IntInput(
                            value = item.restSeconds,
                            onChange = { v -> onChange { it.copy(restSeconds = v) } },
                            modifier = Modifier.weight(1f),
                            placeholder = "по умолч.",
                        )
                    }
                    OutlinedTextField(
                        value = item.notes,
                        onValueChange = { v -> onChange { it.copy(notes = v.take(500)) } },
                        label = { Text("Заметка к упражнению") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Убрать упражнение?",
            text = "«${item.name}» будет удалено из программы. Прошлые результаты в истории не пострадают.",
            confirmLabel = "Убрать",
            destructive = true,
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(104.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
