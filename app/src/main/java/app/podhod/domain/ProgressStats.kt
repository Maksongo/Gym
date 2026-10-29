package app.podhod.domain

import java.time.ZoneId

/** Строка статистики: один выполненный подход в завершённой тренировке. */
data class StatSet(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: Long,
    val loadType: LoadType,
    val weight: Double,
    val reps: Int,
)

enum class ChartMetric(val title: String) {
    WEIGHT("Вес"),
    REPS("Повторения"),
    VOLUME("Объём"),
    ONE_RM("1ПМ (оценка)"),
}

/** Итог упражнения за одну тренировку. */
data class SessionPoint(
    val sessionId: Long,
    val startedAt: Long,
    val loadType: LoadType,
    val sets: List<SetValues>,
    val maxWeightKg: Double,
    val maxReps: Int,
    val totalReps: Int,
    val volume: VolumeResult,
    val bestOneRmKg: Double?,
)

data class ChartPoint(val x: Long, val y: Double, val sessionId: Long)

data class ProgressSummary(
    val sessionsCount: Int,
    val maxWeightKg: Double?,
    val bestSet: StatSet?,
    val bestOneRmKg: Double?,
    val totalVolume: VolumeResult,
    val weightChangeKg: Double?,
    val repsChange: Int?,
)

data class WeekVolume(val weekStart: Long, val volume: VolumeResult)

object ProgressCalculator {

    /** Группирует подходы по тренировкам; результат отсортирован по времени (старые первыми). */
    fun perSession(rows: List<StatSet>): List<SessionPoint> =
        rows.groupBy { it.sessionId }.map { (sessionId, sets) ->
            val loadType = sets.first().loadType
            val values = sets.map { SetValues(it.weight, it.reps) }
            val volume = VolumeCalculator.volume(sets.map { SetData(it.weight, it.reps, it.loadType) })
            val oneRm = if (loadType == LoadType.WEIGHTED) {
                sets.mapNotNull { OneRepMax.estimate(it.weight, it.reps) }.maxOrNull()
            } else null
            SessionPoint(
                sessionId = sessionId,
                startedAt = sets.minOf { it.startedAt },
                loadType = loadType,
                sets = values,
                maxWeightKg = sets.maxOf { it.weight },
                maxReps = sets.maxOf { it.reps },
                totalReps = sets.sumOf { it.reps },
                volume = volume,
                bestOneRmKg = oneRm,
            )
        }.sortedWith(compareBy({ it.startedAt }, { it.sessionId }))

    fun metricValue(p: SessionPoint, metric: ChartMetric): Double? = when (metric) {
        ChartMetric.WEIGHT -> if (p.loadType == LoadType.REPS_ONLY || p.maxWeightKg <= 0.0) null else p.maxWeightKg
        ChartMetric.REPS -> p.maxReps.toDouble().takeIf { it > 0 }
        ChartMetric.VOLUME -> if (p.volume.hasData) p.volume.totalKg else null
        ChartMetric.ONE_RM -> p.bestOneRmKg
    }

    /** Точки графика; тренировки, где метрика неприменима, пропускаются, а не заменяются нулями. */
    fun series(points: List<SessionPoint>, metric: ChartMetric): List<ChartPoint> =
        points.mapNotNull { p -> metricValue(p, metric)?.let { ChartPoint(p.startedAt, it, p.sessionId) } }

    /** Лучший подход: самый тяжёлый, при равенстве — с большим числом повторений. Для «только повторения» — больше повторений. */
    fun bestSet(rows: List<StatSet>): StatSet? {
        if (rows.isEmpty()) return null
        return if (rows.first().loadType == LoadType.REPS_ONLY) {
            rows.maxByOrNull { it.reps }
        } else {
            rows.maxWithOrNull(compareBy<StatSet>({ it.weight }, { it.reps }))
        }
    }

    fun summary(rows: List<StatSet>): ProgressSummary {
        val points = perSession(rows)
        val weights = points.mapNotNull { metricValue(it, ChartMetric.WEIGHT) }
        val reps = points.mapNotNull { metricValue(it, ChartMetric.REPS) }
        val volume = points.fold(VolumeResult.EMPTY) { acc, p -> acc + p.volume }
        return ProgressSummary(
            sessionsCount = points.size,
            maxWeightKg = weights.maxOrNull(),
            bestSet = bestSet(rows),
            bestOneRmKg = points.mapNotNull { it.bestOneRmKg }.maxOrNull(),
            totalVolume = volume,
            weightChangeKg = if (weights.size >= 2) round2(weights.last() - weights.first()) else null,
            repsChange = if (reps.size >= 2) (reps.last() - reps.first()).toInt() else null,
        )
    }

    /** Недельный объём; недели без тренировок внутри диапазона заполняются нулями (не пропускаются). */
    fun weeklyVolume(rows: List<StatSet>, zone: ZoneId, maxWeeks: Int = 104): List<WeekVolume> {
        if (rows.isEmpty()) return emptyList()
        val byWeek = rows.groupBy { DateFmt.startOfWeek(it.startedAt, zone) }
            .mapValues { (_, list) -> VolumeCalculator.volume(list.map { SetData(it.weight, it.reps, it.loadType) }) }
        val first = byWeek.keys.min()
        val last = byWeek.keys.max()
        val out = ArrayList<WeekVolume>()
        var cursor = first
        while (cursor <= last && out.size < 5000) {
            out += WeekVolume(cursor, byWeek[cursor] ?: VolumeResult.EMPTY)
            cursor = DateFmt.nextWeekStart(cursor, zone)
        }
        return out.takeLast(maxWeeks)
    }
}
