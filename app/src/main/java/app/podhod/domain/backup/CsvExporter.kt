package app.podhod.domain.backup

import app.podhod.domain.DateFmt
import app.podhod.domain.Fmt
import app.podhod.domain.LoadType
import app.podhod.domain.enumOrDefault
import java.time.ZoneId

/**
 * CSV для Excel/Google Таблиц: разделитель «;», десятичная запятая, UTF-8 с BOM.
 * Одна строка — один ВЫПОЛНЕННЫЙ подход завершённой тренировки. Вес всегда в кг.
 */
object CsvExporter {
    val HEADER = listOf(
        "Дата", "Начало", "Окончание", "Тренировка", "Упражнение",
        "Подход", "Вес, кг", "Повторения", "Объём, кг", "Учёт нагрузки",
    )

    fun build(data: BackupData, zone: ZoneId): String {
        val names = data.exercises.associate { it.id to it.name }
        val sb = StringBuilder("﻿")
        sb.append(HEADER.joinToString(";") { escape(it) }).append("\r\n")
        for (s in data.sessions.filter { it.status == "FINISHED" }.sortedBy { it.startedAt }) {
            for (we in s.exercises.sortedBy { it.position }) {
                val load = enumOrDefault(we.loadType, LoadType.WEIGHTED)
                val sets = we.sets.filter { it.isCompleted }.sortedBy { it.setNumber }
                for ((idx, st) in sets.withIndex()) {
                    val volume = if (load == LoadType.WEIGHTED && st.weight > 0 && st.reps > 0) {
                        Fmt.number(st.weight * st.reps)
                    } else ""
                    val cells = listOf(
                        DateFmt.numeric(s.startedAt, zone),
                        DateFmt.time(s.startedAt, zone),
                        s.finishedAt?.let { DateFmt.time(it, zone) } ?: "",
                        s.name,
                        names[we.exerciseId] ?: "",
                        (idx + 1).toString(),
                        if (load == LoadType.REPS_ONLY) "" else Fmt.number(st.weight),
                        st.reps.toString(),
                        volume,
                        when (load) {
                            LoadType.WEIGHTED -> "вес × повторения"
                            LoadType.BODYWEIGHT -> "свой вес + отягощение"
                            LoadType.REPS_ONLY -> "только повторения"
                        },
                    )
                    sb.append(cells.joinToString(";") { escape(it) }).append("\r\n")
                }
            }
        }
        return sb.toString()
    }

    fun escape(cell: String): String {
        val needsQuotes = cell.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        // Защита от формул в Excel: ячейки, начинающиеся с = + @ - (кроме чисел), экранируются апострофом.
        val safe = if (cell.isNotEmpty() && cell[0] in "=+@" ) "'$cell" else cell
        return if (needsQuotes || safe != cell) "\"" + safe.replace("\"", "\"\"") + "\"" else cell
    }
}
