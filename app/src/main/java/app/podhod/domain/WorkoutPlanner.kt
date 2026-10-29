package app.podhod.domain

object WorkoutPlanner {
    const val DEFAULT_SETS = 3

    /**
     * Формирует НЕвыполненные подходы для нового упражнения тренировки.
     *
     * Количество подходов: цель из программы, иначе столько, сколько было в прошлый раз, иначе 3.
     * Вес и повторения: из прошлого результата (по номеру подхода; если подходов больше —
     * берётся последний), иначе из цели программы. Старые результаты НИКОГДА не попадают
     * в новую тренировку как выполненные: это только заготовка значений.
     */
    fun initialSets(
        loadType: LoadType,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
        targetWeight: Double?,
        previous: List<SetValues>,
    ): List<SetValues> {
        val count = (targetSets?.takeIf { it > 0 }
            ?: previous.size.takeIf { it > 0 }
            ?: DEFAULT_SETS).coerceIn(1, InputRules.MAX_SETS)
        val fallbackReps = targetRepsMin ?: targetRepsMax ?: 0
        return List(count) { i ->
            val prev = previous.getOrNull(i) ?: previous.lastOrNull()
            val weight = if (loadType == LoadType.REPS_ONLY) 0.0
            else prev?.weight ?: targetWeight ?: 0.0
            val reps = prev?.reps ?: fallbackReps
            SetValues(InputRules.clampKg(weight), InputRules.clampReps(reps))
        }
    }
}
