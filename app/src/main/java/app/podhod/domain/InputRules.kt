package app.podhod.domain

/** Разбор и ограничение пользовательского ввода. Отрицательные и нечисловые значения отвергаются. */
object InputRules {
    const val MAX_DISPLAY_WEIGHT = 9999.0
    const val MAX_REPS = 999
    const val MAX_SETS = 50
    const val MAX_REST_SECONDS = 3600
    const val MIN_REST_SECONDS = 5

    private val DECIMAL = Regex("^(\\d+[.,]?\\d*|[.,]\\d+)$")

    /**
     * Фильтр для текстового поля веса: оставляет цифры и один разделитель (запятая),
     * не более 4 цифр целой части и 2 знаков после запятой. Минус и прочие символы отбрасываются.
     */
    fun sanitizeDecimal(raw: String): String {
        val out = StringBuilder()
        var hasSep = false
        var decimals = 0
        var intDigits = 0
        for (ch in raw) {
            when {
                ch in '0'..'9' -> {
                    if (hasSep) {
                        if (decimals < 2) {
                            out.append(ch)
                            decimals++
                        }
                    } else if (intDigits < 4) {
                        out.append(ch)
                        intDigits++
                    }
                }
                (ch == ',' || ch == '.') && !hasSep -> {
                    hasSep = true
                    out.append(',')
                }
            }
        }
        return out.toString()
    }

    /** Фильтр для поля повторений: только цифры, не более 3. */
    fun sanitizeInt(raw: String): String = raw.filter { it in '0'..'9' }.take(3)

    /** null — пусто или некорректно. Принимает и запятую, и точку. */
    fun parseDecimal(text: String): Double? {
        val t = text.trim()
        if (!DECIMAL.matches(t)) return null
        val v = t.replace(',', '.').toDoubleOrNull() ?: return null
        return if (v.isFinite() && v >= 0.0) v else null
    }

    fun parseInt(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 6 || !t.all { it in '0'..'9' }) return null
        return t.toIntOrNull()
    }

    fun clampReps(v: Int): Int = v.coerceIn(0, MAX_REPS)

    fun stepReps(current: Int, delta: Int): Int = clampReps(current + delta)

    fun clampKg(kg: Double): Double =
        if (kg.isFinite()) kg.coerceIn(0.0, MAX_DISPLAY_WEIGHT * 10) else 0.0
}

enum class SetProblem { NO_WEIGHT, NO_REPS }

object SetValidator {
    /** null — подход можно отметить выполненным. */
    fun problem(loadType: LoadType, weight: Double, reps: Int): SetProblem? {
        if (reps <= 0) return SetProblem.NO_REPS
        if (loadType == LoadType.WEIGHTED && !(weight > 0.0)) return SetProblem.NO_WEIGHT
        return null
    }
}
