@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.data.db.SessionSummaryRow
import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.ui.components.EmptyState
import app.podhod.ui.components.FullScreenLoading
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.components.LocalZone
import app.podhod.ui.components.TabScaffold
import app.podhod.ui.components.countLabel
import app.podhod.ui.components.partialVolumeNote

private sealed class HistoryItem {
    data class Header(val label: String, val sessions: Int) : HistoryItem()
    data class Row(val row: SessionSummaryRow) : HistoryItem()
}

@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalContainer.current
    val flow = remember(container) { container.repository.observeSummaries() }
    val rows by flow.collectAsStateWithLifecycle(initialValue = null)
    val unit = LocalAppSettings.current.unit
    val zone = LocalZone.current

    TabScaffold(
        title = "История",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
        },
    ) { padding ->
        val list = rows
        if (list == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@TabScaffold
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "Пока нет завершённых тренировок",
                    text = "Здесь появятся все ваши тренировки с подходами, весами и объёмом. " +
                        "Начните тренировку на вкладке «Тренировки».",
                )
            }
            return@TabScaffold
        }

        val entries = remember(list, zone) {
            buildList<HistoryItem> {
                var currentMonth: String? = null
                val byMonth = list.groupBy { DateFmt.monthYear(it.startedAt, zone) }
                for (row in list) {
                    val month = DateFmt.monthYear(row.startedAt, zone)
                    if (month != currentMonth) {
                        currentMonth = month
                        add(HistoryItem.Header(month, byMonth[month]?.size ?: 0))
                    }
                    add(HistoryItem.Row(row))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                entries,
                key = { item ->
                    when (item) {
                        is HistoryItem.Header -> "h-" + item.label
                        is HistoryItem.Row -> "s-" + item.row.id
                    }
                },
            ) { item ->
                when (item) {
                    is HistoryItem.Header -> Text(
                        item.label + " · " + countLabel(item.sessions, "тренировка", "тренировки", "тренировок"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is HistoryItem.Row -> SessionRowCard(item.row, unit, zone) { onOpenSession(item.row.id) }
                }
            }
        }
    }
}

@Composable
private fun SessionRowCard(
    row: SessionSummaryRow,
    unit: app.podhod.domain.WeightUnit,
    zone: java.time.ZoneId,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            val whenText = buildString {
                append(DateFmt.weekdayDayMonth(row.startedAt, zone))
                append(" · ")
                append(DateFmt.time(row.startedAt, zone))
                row.finishedAt?.let { append(" · "); append(Fmt.duration(it - row.startedAt)) }
            }
            Text(whenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val parts = mutableListOf<String>()
            parts += countLabel(row.exerciseCount, "упражнение", "упражнения", "упражнений")
            parts += countLabel(row.setCount, "подход", "подхода", "подходов")
            if (row.volume > 0.0) parts += "объём " + Fmt.volume(row.volume, unit)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
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
