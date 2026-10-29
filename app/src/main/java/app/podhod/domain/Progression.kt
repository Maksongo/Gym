package app.podhod.domain

/** Итог одной прошлой тренировки по упражнению: только ВЫПОЛНЕННЫЕ подходы и цель на тот момент. */
data class SessionResult(
    val startedAt: Long,
    val targetSets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val sets: List<SetValues>,
)

data class ProgressionSuggestion(
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val sessionsCount: Int,
    val targetSets: Int,
    val repsMax: Int,
)

/**
 * Необязательная рекомендация повысить рабочий вес.
 * Опирается ТОЛЬКО на записанные подходы: техника выполнения приложением не оценивается.
 * Вес сам по себе никогда не меняется — рекомендацию пользователь принимает вручную.
 */
object ProgressionAdvisor {
    private const val EPS = 1e-6

    /**
     * @param sessionsNewestFirst прошлые завершённые тренировки с этим упражнением, свежие первыми
     * @param requiredSessions сколько последних тренировок подряд должны выполнить цель
     * @param stepKg минимальный шаг увеличения веса в кг
     */
    fun suggest(
        loadType: LoadType,
        sessionsNewestFirst: List<SessionResult>,
        requiredSessions: Int,
        stepKg: Double,
    ): ProgressionSuggestion? {
        if (loadType == LoadType.REPS_ONLY) return null
        if (!(stepKg > 0.0)) return null
        val need = requiredSessions.coerceAtLeast(1)
        if (sessionsNewestFirst.size < need) return null

        val recent = sessionsNewestFirst.take(need)
        // Цель берём из самой свежей тренировки: оцениваем выполнение ТЕКУЩЕГО диапазона.
        val latest = recent.first()
        val targetSets = latest.targetSets?.takeIf { it > 0 } ?: return null
        val repsMax = latest.repsMax?.takeIf { it > 0 } ?: return null

        var workingWeight: Double? = null
        for (session in recent) {
            val lifted = session.sets.filter { it.reps > 0 }
            if (lifted.isEmpty()) return null
            val top = lifted.maxOf { it.weight }
            if (loadType == LoadType.WEIGHTED && !(top > 0.0)) return null
            val working = lifted.filter { Math.abs(it.weight - top) < EPS }
            if (working.size < targetSets) return null
            if (working.any { it.reps < repsMax }) return null
            val known = workingWeight
            if (known == null) workingWeight = top
            else if (Math.abs(known - top) >= EPS) return null // вес менялся — серия «подряд» прервана
        }
        val current = workingWeight ?: return null
        return ProgressionSuggestion(
            currentWeightKg = current,
            suggestedWeightKg = round2(current + stepKg),
            sessionsCount = need,
            targetSets = targetSets,
            repsMax = repsMax,
        )
    }
}
