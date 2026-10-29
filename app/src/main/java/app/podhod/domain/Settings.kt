package app.podhod.domain

enum class ThemeMode(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/**
 * Единица отображения веса. В базе вес ВСЕГДА хранится в килограммах,
 * пересчёт выполняется только на границе интерфейса.
 */
enum class WeightUnit(val label: String, val kgPerUnit: Double) {
    KG("кг", 1.0),
    LB("lb", 0.45359237);

    fun fromKg(kg: Double): Double = round2(kg / kgPerUnit)

    fun toKg(value: Double): Double = value * kgPerUnit

    /** Меняет вес на [deltaDisplay] единиц отображения, не уходя в минус и за верхний предел. */
    fun stepKg(currentKg: Double, deltaDisplay: Double): Double {
        val next = round2(fromKg(currentKg) + deltaDisplay).coerceIn(0.0, InputRules.MAX_DISPLAY_WEIGHT)
        return toKg(next)
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val unit: WeightUnit = WeightUnit.KG,
    val restSeconds: Int = 90,
    val restEnabled: Boolean = true,
    val autoStartRest: Boolean = true,
    val keepScreenOn: Boolean = true,
    val vibrate: Boolean = true,
    val progressionEnabled: Boolean = true,
    /** Минимальный шаг увеличения веса в единицах отображения (кг или lb). */
    val progressionStep: Double = 2.5,
    /** Сколько тренировок подряд нужно выполнить цель, чтобы получить рекомендацию. */
    val progressionSessions: Int = 2,
) {
    val progressionStepKg: Double get() = unit.toKg(progressionStep)
}

fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
