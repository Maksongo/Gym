@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.podhod.ui.screens.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.ExerciseEntity
import app.podhod.domain.MuscleGroup
import app.podhod.ui.components.EmptyState
import app.podhod.ui.components.ExerciseEditorDialog
import app.podhod.ui.components.FullScreenDialog
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.components.MuscleFilterRow
import app.podhod.ui.components.SearchField
import app.podhod.ui.components.TabScaffold
import app.podhod.ui.components.filterBy
import app.podhod.ui.components.subtitle
import kotlinx.coroutines.launch

@Composable
fun ExercisesScreen(onOpenSettings: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val flow = remember(container) { repo.observeExercises() }
    val loaded by flow.collectAsStateWithLifecycle(initialValue = null)

    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }

    TabScaffold(
        title = "Упражнения",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Своё упражнение") },
            )
        },
    ) { padding ->
        val all = loaded
        if (all == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@TabScaffold
        }
        val visible = remember(all, query, muscle, favoritesOnly) {
            all.filterBy(query, muscle, favoritesOnly)
                .sortedWith(compareByDescending<ExerciseEntity> { it.isFavorite }.thenBy { it.name.lowercase() })
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            MuscleFilterRow(muscle, { muscle = it }, favoritesOnly, { favoritesOnly = it })
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FitnessCenter,
                    title = if (favoritesOnly && query.isBlank() && muscle == null) "Избранного пока нет" else "Ничего не найдено",
                    text = if (favoritesOnly && query.isBlank() && muscle == null) {
                        "Отмечайте звёздочкой упражнения, которые выполняете чаще всего."
                    } else {
                        "Измените поиск или фильтр. Если нужного упражнения нет в каталоге, создайте своё."
                    },
                    actionLabel = "Создать упражнение",
                    onAction = { creating = true },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                ) {
                    items(visible, key = { it.id }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { detailId = e.id }
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    e.subtitle() + if (e.isCustom) " · своё" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { scope.launch { repo.setFavorite(e.id, !e.isFavorite) } }) {
                                Icon(
                                    if (e.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (e.isFavorite) "Убрать из избранного" else "В избранное",
                                    tint = if (e.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    val selected = detailId?.let { id -> loaded?.firstOrNull { it.id == id } }
    if (selected != null) {
        ExerciseDetailDialog(
            exercise = selected,
            onDismiss = { detailId = null },
            onToggleFavorite = { scope.launch { repo.setFavorite(selected.id, !selected.isFavorite) } },
        )
    }

    if (creating) {
        ExerciseEditorDialog(
            exercise = null,
            initialName = query,
            onDismiss = { creating = false },
            onSaved = { id ->
                creating = false
                query = ""
                detailId = id
            },
        )
    }
}

@Composable
private fun ExerciseDetailDialog(
    exercise: ExerciseEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    FullScreenDialog(onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(exercise.name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (exercise.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (exercise.isFavorite) "Убрать из избранного" else "В избранное",
                                tint = if (exercise.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Fact("Основная мышечная группа", exercise.primaryMuscle.title)
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    Fact("Дополнительные группы", exercise.secondaryMuscles.joinToString(", ") { it.title })
                }
                Fact("Тип", exercise.exerciseType.title)
                Fact("Оборудование", exercise.equipment.title)
                Fact("Учёт нагрузки", exercise.loadType.title)
                if (!exercise.technique.isNullOrBlank()) Fact("Техника выполнения", exercise.technique)
                if (!exercise.notes.isNullOrBlank()) Fact("Заметки", exercise.notes)
                Fact("Источник", if (exercise.isCustom) "Своё упражнение" else "Стандартный каталог")

                Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Изменить") }
                OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
                    Text(if (exercise.isFavorite) "Убрать из избранного" else "Добавить в избранное")
                }
            }
        }
    }

    if (editing) {
        ExerciseEditorDialog(
            exercise = exercise,
            onDismiss = { editing = false },
            onSaved = { editing = false },
            onDeleted = {
                editing = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
