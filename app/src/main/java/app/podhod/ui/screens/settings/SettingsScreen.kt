@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.podhod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.podhod.data.repo.CurrentCounts
import app.podhod.data.repo.RestorePreview
import app.podhod.domain.AppSettings
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.InputRules
import app.podhod.domain.RestTimer
import app.podhod.domain.ThemeMode
import app.podhod.domain.WeightUnit
import app.podhod.domain.backup.BackupFormatException
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.InfoDialog
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.NumberFormField
import app.podhod.ui.components.countLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** Файл резервной копии больше этого размера считается ошибочным. */
private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

private class PendingRestore(val preview: RestorePreview, val current: CurrentCounts)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings = LocalAppSettings.current
    val zone = LocalZone.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var errorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(transform) }
    }

    fun say(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    suspend fun writeTo(uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Не удалось открыть файл для записи")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingText
        pendingText = null
        if (uri != null && text != null) {
            scope.launch {
                try {
                    writeTo(uri, text)
                    say("Файл сохранён")
                } catch (e: Exception) {
                    errorDialog = "Не удалось сохранить файл" to (e.message ?: "Неизвестная ошибка")
                }
            }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val text = pendingText
        pendingText = null
        if (uri != null && text != null) {
            scope.launch {
                try {
                    writeTo(uri, text)
                    say("Файл сохранён")
                } catch (e: Exception) {
                    errorDialog = "Не удалось сохранить файл" to (e.message ?: "Неизвестная ошибка")
                }
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val text = withContext(Dispatchers.IO) { readText(context, uri) }
                    val preview = container.backup.preview(text)
                    val current = container.backup.currentCounts()
                    pendingRestore = PendingRestore(preview, current)
                } catch (e: BackupFormatException) {
                    errorDialog = "Файл не подходит" to
                        ("Это не резервная копия приложения или файл повреждён. Ваши данные не изменены.\n\n" + (e.message ?: ""))
                } catch (e: Exception) {
                    errorDialog = "Не удалось прочитать файл" to
                        ("Ваши данные не изменены.\n\n" + (e.message ?: "Неизвестная ошибка"))
                } finally {
                    busy = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Оформление") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { m ->
                        FilterChip(selected = settings.theme == m, onClick = { update { it.copy(theme = m) } }, label = { Text(m.title) })
                    }
                }
            }

            Section("Единицы веса") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightUnit.values().forEach { u ->
                        FilterChip(
                            selected = settings.unit == u,
                            onClick = { update { it.copy(unit = u) } },
                            label = { Text(if (u == WeightUnit.KG) "Килограммы (кг)" else "Фунты (lb)") },
                        )
                    }
                }
                Hint("Данные всегда хранятся в килограммах, поэтому единицы можно менять без потери точности.")
            }

            Section("Таймер отдыха") {
                SettingSwitch(
                    title = "Таймер отдыха",
                    subtitle = "Показывать отсчёт отдыха между подходами",
                    checked = settings.restEnabled,
                    onChange = { v -> update { it.copy(restEnabled = v) } },
                )
                SettingSwitch(
                    title = "Запускать после подхода",
                    subtitle = "Таймер стартует, когда вы отмечаете подход выполненным",
                    checked = settings.autoStartRest,
                    enabled = settings.restEnabled,
                    onChange = { v -> update { it.copy(autoStartRest = v) } },
                )
                Text("Длительность отдыха по умолчанию", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RestTimer.PRESETS_SECONDS.forEach { s ->
                        FilterChip(
                            selected = settings.restSeconds == s,
                            onClick = { update { it.copy(restSeconds = s) } },
                            label = { Text("$s с") },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = {
                        update { it.copy(restSeconds = RestTimer.clampSeconds(it.restSeconds - RestTimer.STEP_SECONDS)) }
                    }) { Text("−${RestTimer.STEP_SECONDS}") }
                    Text(Fmt.clock(settings.restSeconds), style = MaterialTheme.typography.headlineMedium)
                    OutlinedButton(onClick = {
                        update { it.copy(restSeconds = RestTimer.clampSeconds(it.restSeconds + RestTimer.STEP_SECONDS)) }
                    }) { Text("+${RestTimer.STEP_SECONDS}") }
                }
                SettingSwitch(
                    title = "Вибрация",
                    subtitle = "Вибрировать, когда отдых закончился",
                    checked = settings.vibrate,
                    onChange = { v -> update { it.copy(vibrate = v) } },
                )
            }

            Section("Тренировка") {
                SettingSwitch(
                    title = "Не гасить экран",
                    subtitle = "Экран остаётся включённым на экране тренировки",
                    checked = settings.keepScreenOn,
                    onChange = { v -> update { it.copy(keepScreenOn = v) } },
                )
            }

            Section("Рекомендации по прогрессии") {
                SettingSwitch(
                    title = "Предлагать увеличить вес",
                    subtitle = "Только совет: вес никогда не меняется без вашего согласия",
                    checked = settings.progressionEnabled,
                    onChange = { v -> update { it.copy(progressionEnabled = v) } },
                )
                var stepText by rememberSaveable { mutableStateOf(Fmt.number(settings.progressionStep)) }
                NumberFormField(
                    label = "Минимальный шаг увеличения",
                    text = stepText,
                    onText = { t ->
                        stepText = t
                        val v = InputRules.parseDecimal(t)
                        if (v != null && v > 0.0) update { it.copy(progressionStep = v.coerceIn(0.1, 100.0)) }
                    },
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                    suffix = settings.unit.label,
                    supporting = "Например, 2,5 — минимальный блин на штангу",
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Тренировок подряд", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Сколько последних тренировок нужно выполнить цель по подходам и повторениям",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = {
                        update { it.copy(progressionSessions = (it.progressionSessions - 1).coerceAtLeast(1)) }
                    }) { Text("−") }
                    Text(
                        settings.progressionSessions.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    OutlinedButton(onClick = {
                        update { it.copy(progressionSessions = (it.progressionSessions + 1).coerceAtMost(10)) }
                    }) { Text("+") }
                }
                Hint("Приложение не оценивает технику выполнения: совет строится только на записанных подходах.")
            }

            Section("Данные и резервное копирование") {
                Hint(
                    "Все данные хранятся только на этом устройстве. Приложение не использует интернет и не отправляет " +
                        "тренировки на сервер. Резервную копию лучше иногда сохранять в облако или на компьютер самостоятельно.",
                )
                ActionButton("Создать резервную копию (JSON)", enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            pendingText = container.backup.exportJson()
                            saveJson.launch("podhody-backup-" + LocalDate.now() + ".json")
                        } catch (e: Exception) {
                            errorDialog = "Не удалось подготовить копию" to (e.message ?: "Неизвестная ошибка")
                        } finally {
                            busy = false
                        }
                    }
                }
                ActionButton("Восстановить из резервной копии", enabled = !busy) {
                    openBackup.launch(arrayOf("*/*"))
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ActionButton("Экспорт всех данных в JSON", enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            pendingText = container.backup.exportJson()
                            saveJson.launch("podhody-export-" + LocalDate.now() + ".json")
                        } catch (e: Exception) {
                            errorDialog = "Не удалось экспортировать" to (e.message ?: "Неизвестная ошибка")
                        } finally {
                            busy = false
                        }
                    }
                }
                ActionButton("Импорт из JSON", enabled = !busy) {
                    openBackup.launch(arrayOf("*/*"))
                }
                ActionButton("Экспорт истории в CSV (Excel)", enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            pendingText = container.backup.exportCsv(zone)
                            saveCsv.launch("podhody-history-" + LocalDate.now() + ".csv")
                        } catch (e: Exception) {
                            errorDialog = "Не удалось экспортировать" to (e.message ?: "Неизвестная ошибка")
                        } finally {
                            busy = false
                        }
                    }
                }
                Hint(
                    "JSON содержит всё: программы, свои упражнения, историю, подходы и настройки. " +
                        "CSV — только выполненные подходы (по строке на подход), для таблиц.",
                )
            }

            Section("О приложении") {
                Text("Подходы · версия " + versionName(context), style = MaterialTheme.typography.bodyLarge)
                Hint("Учёт силовых тренировок без рекламы, регистрации и подписки. Облачная синхронизация не включена.")
            }
        }
    }

    pendingRestore?.let { pr ->
        ConfirmDialog(
            title = "Заменить данные?",
            text = restoreMessage(pr, zone),
            confirmLabel = "Заменить данные",
            dismissLabel = "Отмена",
            destructive = true,
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    busy = true
                    try {
                        container.backup.restore(pr.preview.data)
                        say("Данные восстановлены")
                    } catch (e: Exception) {
                        errorDialog = "Не удалось восстановить данные" to
                            ("Текущие данные не изменены.\n\n" + (e.message ?: "Неизвестная ошибка"))
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }

    errorDialog?.let { (title, text) -> InfoDialog(title, text) { errorDialog = null } }
}

private fun restoreMessage(pr: PendingRestore, zone: java.time.ZoneId): String {
    val p = pr.preview
    val file = "Файл от " + DateFmt.dateTime(p.exportedAt, zone) + ": " +
        countLabel(p.programs, "программа", "программы", "программ") + ", " +
        countLabel(p.sessions, "тренировка", "тренировки", "тренировок") + ", " +
        countLabel(p.customExercises, "своё упражнение", "своих упражнения", "своих упражнений") + "."
    val c = pr.current
    val now = "Сейчас в приложении: " +
        countLabel(c.programs, "программа", "программы", "программ") + ", " +
        countLabel(c.sessions, "тренировка", "тренировки", "тренировок") + ", " +
        countLabel(c.customExercises, "своё упражнение", "своих упражнения", "своих упражнений") + "."
    return file + "\n\n" + now + "\n\n" +
        "Все текущие данные будут ЗАМЕНЕНЫ данными из файла. Это нельзя отменить. " +
        "Если нужно сохранить то, что есть сейчас, сначала отмените и создайте резервную копию."
}

private fun readText(context: Context, uri: Uri): String {
    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
    stream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) throw IOException("Файл слишком большой для резервной копии")
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }
}

private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
