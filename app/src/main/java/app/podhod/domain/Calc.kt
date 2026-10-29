package app.podhod.domain

/** Значения одного подхода (вес — всегда в кг). */
data class SetValues(val weight: Double, val reps: Int)

data class SetData(val weight: Double, val reps: Int, val loadType: LoadType)

/**
 * Результат расчёта объёма.
 * [skippedSets] — подходы упражнений со своим весом / без веса, которые в объём не входят.
 * Если [isPartial], интерфейс обязан показать, что объём неполный.
 */
data class VolumeResult(val totalKg: Double, val countedSets: Int, val skippedSets: Int) {
    val isPartial: Boolean get() = skippedSets > 0
    val hasData: Boolean get() = countedSets > 0

    operator fun plus(o: VolumeResult) =
        VolumeResult(round2(totalKg + o.totalKg), countedSets + o.countedSets, skippedSets + o.skippedSets)

    companion object {
        val EMPTY = VolumeResult(0.0, 0, 0)
    }
}

object VolumeCalculator {
    /**
     * Объём = сумма (вес × повторения) по подходам упражнений типа WEIGHTED.
     * Подходы со своим весом и «только повторения» не угадываются и не учитываются, а считаются в skippedSets.
     * Передавать нужно только выполненные подходы.
     */
    fun volume(sets: Iterable<SetData>): VolumeResult {
        var total = 0.0
        var counted = 0
        var skipped = 0
        for (s in sets) {
            if (s.reps <= 0) continue
            if (s.loadType == LoadType.WEIGHTED) {
                if (s.weight > 0.0) {
                    total += s.weight * s.reps
                    counted++
                }
            } else {
                skipped++
            }
        }
        return VolumeResult(round2(total), counted, skipped)
    }
}

object OneRepMax {
    /** Выше этого числа повторений формула Эпли даёт слишком грубую оценку. */
    const val MAX_RELIABLE_REPS = 12

    /**
     * Оценочный 1ПМ по формуле Эпли: вес × (1 + повторения / 30).
     * Для одного повторения оценка равна самому весу. null — оценка неприменима.
     * Это ОЦЕНКА, а не фактический максимум; интерфейс обязан так её и подписывать.
     */
    fun estimate(weightKg: Double, reps: Int): Double? {
        if (!(weightKg > 0.0) || reps < 1 || reps > MAX_RELIABLE_REPS) return null
        if (reps == 1) return weightKg
        return round2(weightKg * (1.0 + reps / 30.0))
    }
}
