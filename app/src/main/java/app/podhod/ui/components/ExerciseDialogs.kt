@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.podhod.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.repo.DeleteExerciseResult
import app.podhod.data.repo.ExerciseDraft
import app.podhod.data.repo.SaveExerciseResult
import app.podhod.domain.Equipment
import app.podhod.domain.ExerciseType
import app.podhod.domain.LoadType
import app.podhod.domain.MuscleGroup
import app.podhod.domain.NameNormalizer
import kotlinx.coroutines.launch

/** Подпись для списка: «Грудь · Штанга». */
fun ExerciseEntity.subtitle(): String = primaryMuscle.title + " · " + equipment.title

fun List<ExerciseEntity>.filterBy(query: String, muscle: MuscleGroup?, favoritesOnly: Boolean): List<ExerciseEntity> {
    val q = NameNormalizer.key(query)
    return filter { e ->
        !e.isArchived &&
            (muscle == null || e.primaryMuscle == muscle || muscle in e.secondaryMuscles) &&
            (!favoritesOnly || e.isFavorite) &&
            (q.isEmpty() ||
                NameNormalizer.key(e.name).contains(q) ||
                NameNormalizer.key(e.primaryMuscle.title).contains(q) ||
                NameNormalizer.key(e.equipment.title).contains(q))
    }
}

@Composable
fun MuscleFilterRow(
    selected: MuscleGroup?,
    onSelect: (MuscleGroup?) -> Unit,
    favoritesOnly: Boolean,
    onFavorites: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavorites(!favoritesOnly) },
                label = { Text("Избранное") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(0.dp)) },
            )
        }
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Все") })
        }
        items(MuscleGroup.values().toList()) { g ->
            FilterChip(selected = selected == g, onClick = { onSelect(if (selected == g) null else g) }, label = { Text(g.title) })
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Поиск упражнения") {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Очистить поиск") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

/** Выбор одного или нескольких упражнений из библиотеки; здесь же можно создать своё. */
@Composable
fun ExercisePickerDialog(
    onDismiss: () -> Unit,
    onPicked: (List<Long>) -> Unit,
    title: String = "Выбор упражнений",
) {
    val container = LocalContainer.current
    val exercisesFlow = remember(container) { container.repository.observeExercises() }
    val exercises by exercisesFlow.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }

    val visible = remember(exercises, query, muscle, favoritesOnly) { exercises.filterBy(query, muscle, favoritesOnly) }

    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    },
                    actions = { TextButton(onClick = { creating = true }) { Text("Своё упражнение") } },
                )
            },
            bottomBar = {
                androidx.compose.material3.Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onPicked(selected) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .imePadding(),
                    ) {
                        Text(if (selected.isEmpty()) "Выберите упражнения" else "Добавить (${selected.size})")
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                MuscleFilterRow(muscle, { muscle = it }, favoritesOnly, { favoritesOnly = it })
                if (visible.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "Ничего не найдено",
                        text = "Измените поиск или создайте своё упражнение.",
                        actionLabel = "Создать упражнение",
                        onAction = { creating = true },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { e ->
                            val checked = e.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (checked) selected - e.id else selected + e.id }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { selected = if (checked) selected - e.id else selected + e.id })
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(e.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (e.isFavorite) Icon(Icons.Filled.Star, contentDescription = "В избранном", tint = MaterialTheme.colorScheme.tertiary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ExerciseEditorDialog(
            exercise = null,
            initialName = query,
            onDismiss = { creating = false },
            onSaved = { id ->
                creating = false
                if (id !in selected) selected = selected + id
                query = ""
            },
        )
    }
}

/** Создание и редактирование упражнения. [exercise] == null — новое. */
@Composable
fun ExerciseEditorDialog(
    exercise: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    initialName: String = "",
    onDeleted: (() -> Unit)? = null,
) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(exercise?.name ?: initialName) }
    var primary by rememberSaveable { mutableStateOf(exercise?.primaryMuscle ?: MuscleGroup.CHEST) }
    var secondary by rememberSaveable { mutableStateOf(exercise?.secondaryMuscles ?: emptyList()) }
    var type by rememberSaveable { mutableStateOf(exercise?.exerciseType ?: ExerciseType.COMPOUND) }
    var equipment by rememberSaveable { mutableStateOf(exercise?.equipment ?: Equipment.BARBELL) }
    var loadType by rememberSaveable { mutableStateOf(exercise?.loadType ?: LoadType.WEIGHTED) }
    var technique by rememberSaveable { mutableStateOf(exercise?.technique ?: "") }
    var notes by rememberSaveable { mutableStateOf(exercise?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            val draft = ExerciseDraft(
                id = exercise?.id, name = name, primary = primary, secondary = secondary,
                type = type, equipment = equipment, loadType = loadType,
                technique = technique, notes = notes,
            )
            when (val r = container.repository.saveExercise(draft)) {
                is SaveExerciseResult.Saved -> onSaved(r.id)
                is SaveExerciseResult.Duplicate -> error = "Такое упражнение уже есть: «${r.existingName}»"
                SaveExerciseResult.EmptyName -> error = "Введите название упражнения"
                SaveExerciseResult.NotFound -> onDismiss()
            }
        }
    }

    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (exercise == null) "Новое упражнение" else "Упражнение") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") } },
                    actions = { TextButton(onClick = { save() }) { Text("Сохранить") } },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80); error = null },
                    label = { Text("Название") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                LabeledSection("Основная мышечная группа") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuscleGroup.values().forEach { g ->
                            FilterChip(
                                selected = primary == g,
                                onClick = { primary = g; secondary = secondary - g },
                                label = { Text(g.title) },
                            )
                        }
                    }
                }
                LabeledSection("Дополнительные мышечные группы") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuscleGroup.values().filter { it != primary }.forEach { g ->
                            FilterChip(
                                selected = g in secondary,
                                onClick = { secondary = if (g in secondary) secondary - g else secondary + g },
                                label = { Text(g.title) },
                            )
                        }
                    }
                }
                LabeledSection("Тип упражнения") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExerciseType.values().forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.title) })
                        }
                    }
                }
                LabeledSection("Оборудование") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Equipment.values().forEach { eq ->
                            FilterChip(selected = equipment == eq, onClick = { equipment = eq }, label = { Text(eq.title) })
                        }
                    }
                }
                LabeledSection("Как учитывать нагрузку") {
                    Column {
                        LoadType.values().forEach { lt ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { loadType = lt }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = loadType == lt, onClick = { loadType = lt })
                                Column(Modifier.padding(start = 6.dp)) {
                                    Text(lt.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(lt.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = technique,
                    onValueChange = { technique = it.take(2000) },
                    label = { Text("Описание техники (необязательно)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = { Text("Заметки (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (exercise != null && onDeleted != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Удалить упражнение", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmDelete && exercise != null) {
        ConfirmDialog(
            title = "Удалить упражнение?",
            text = "Если упражнение есть в истории тренировок, оно будет скрыто из библиотеки, а прошлые результаты сохранятся. Из программ оно исчезнет.",
            confirmLabel = "Удалить",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    val r = container.repository.deleteExercise(exercise.id)
                    if (r == DeleteExerciseResult.ARCHIVED) {
                        info = "Упражнение скрыто из библиотеки. Его прошлые результаты остались в истории."
                    } else {
                        onDeleted?.invoke()
                    }
                }
            },
        )
    }
    info?.let {
        InfoDialog("Готово", it) { info = null; onDeleted?.invoke() }
    }
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

