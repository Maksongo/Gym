package app.podhod.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

const val NBSP = ' '

object Fmt {
    /** 42 → «42», 4.5 → «4,5». Запятая — десятичный разделитель, как привыкли русскоязычные пользователи. */
    fun number(v: Double, maxDecimals: Int = 2): String {
        if (!v.isFinite()) return "0"
        val bd = BigDecimal.valueOf(v).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString().replace('.', ',')
    }

    /** Число с разделением разрядов неразрывным пробелом: 5120 → «5 120». */
    fun grouped(v: Double, maxDecimals: Int = 1): String {
        val s = number(v, maxDecimals)
        val parts = s.split(',')
        val intPart = parts[0]
        val negative = intPart.startsWith("-")
        val digits = if (negative) intPart.substring(1) else intPart
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(NBSP)
            sb.append(c)
        }
        val head = (if (negative) "-" else "") + sb
        return if (parts.size > 1) head + "," + parts[1] else head
    }

    /** Значение поля ввода: ноль показывается пустой строкой. */
    fun field(v: Double): String = if (v <= 0.0) "" else number(v)

    fun fieldInt(v: Int): String = if (v <= 0) "" else v.toString()

    fun weight(kg: Double, unit: WeightUnit): String = number(unit.fromKg(kg)) + NBSP + unit.label

    fun volume(kg: Double, unit: WeightUnit): String = grouped(unit.fromKg(kg), 1) + NBSP + unit.label

    /** 47 мин / 1 ч 05 мин. */
    fun duration(ms: Long): String {
        val totalMin = (ms.coerceAtLeast(0L) / 60_000L).toInt()
        if (totalMin < 60) return "$totalMin мин"
        val h = totalMin / 60
        val m = totalMin % 60
        return "$h ч ${m.toString().padStart(2, '0')} мин"
    }

    /** 90 → «01:30». */
    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return (s / 60).toString().padStart(2, '0') + ":" + (s % 60).toString().padStart(2, '0')
    }

    fun repsRange(min: Int?, max: Int?): String? = when {
        min != null && max != null && min > 0 && max > 0 ->
            if (min == max) "$min" else "$min–$max"
        max != null && max > 0 -> "до $max"
        min != null && min > 0 -> "от $min"
        else -> null
    }

    /** «Цель: 3 × 8–12» (+ вес). */
    fun target(sets: Int?, min: Int?, max: Int?, weightKg: Double?, loadType: LoadType, unit: WeightUnit): String? {
        val parts = mutableListOf<String>()
        val reps = repsRange(min, max)
        if (sets != null && sets > 0) parts += if (reps != null) "$sets × $reps" else "$sets подх."
        else if (reps != null) parts += "$reps повт."
        if (weightKg != null && weightKg > 0.0 && loadType != LoadType.REPS_ONLY) {
            parts += (if (loadType == LoadType.BODYWEIGHT) "+" else "") + weight(weightKg, unit)
        }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}

object PreviousResultFormatter {
    /**
     * «42 кг × 8, 8, 8» — подряд идущие подходы с одним весом объединяются;
     * «42 кг × 8, 8; 45 кг × 6» — если вес менялся. null — прошлого результата нет.
     */
    fun format(sets: List<SetValues>, loadType: LoadType, unit: WeightUnit): String? {
        if (sets.isEmpty()) return null
        if (loadType == LoadType.REPS_ONLY) {
            return sets.joinToString(", ") { it.reps.toString() } + " повт."
        }
        val groups = mutableListOf<Pair<Double, MutableList<Int>>>()
        for (s in sets) {
            val last = groups.lastOrNull()
            if (last != null && Math.abs(last.first - s.weight) < 1e-6) last.second += s.reps
            else groups += s.weight to mutableListOf(s.reps)
        }
        return groups.joinToString("; ") { (w, reps) ->
            val label = when {
                loadType == LoadType.BODYWEIGHT && w <= 0.0 -> "свой вес"
                loadType == LoadType.BODYWEIGHT -> "+" + Fmt.weight(w, unit)
                else -> Fmt.weight(w, unit)
            }
            label + " × " + reps.joinToString(", ")
        }
    }
}

object DateFmt {
    private val MONTHS_GEN = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTHS_NOM = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val MONTHS_SHORT = arrayOf(
        "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
    )
    private val DAYS_SHORT = arrayOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    private fun at(ms: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    fun dayMonth(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"
    }

    fun dayMonthShort(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"
    }

    fun weekdayDayMonth(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return "${DAYS_SHORT[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"
    }

    fun time(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return d.hour.toString().padStart(2, '0') + ":" + d.minute.toString().padStart(2, '0')
    }

    fun dateTime(ms: Long, zone: ZoneId): String = "${dayMonth(ms, zone)}, ${time(ms, zone)}"

    fun monthYear(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return "${MONTHS_NOM[d.monthValue - 1]} ${d.year}"
    }

    fun numeric(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return d.dayOfMonth.toString().padStart(2, '0') + "." + d.monthValue.toString().padStart(2, '0') + "." + d.year
    }

    fun numericShort(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return d.dayOfMonth.toString().padStart(2, '0') + "." + d.monthValue.toString().padStart(2, '0')
    }

    fun isoDateTime(ms: Long, zone: ZoneId): String {
        val d = at(ms, zone)
        return d.year.toString() + "-" + d.monthValue.toString().padStart(2, '0') + "-" +
            d.dayOfMonth.toString().padStart(2, '0') + " " + time(ms, zone)
    }

    fun startOfDay(ms: Long, zone: ZoneId): Long =
        at(ms, zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Начало календарной недели (понедельник 00:00). */
    fun startOfWeek(ms: Long, zone: ZoneId): Long =
        at(ms, zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun nextWeekStart(weekStart: Long, zone: ZoneId): Long =
        at(weekStart, zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Периоды для сводной статистики и графиков. */
enum class Period(val days: Int?, val title: String) {
    WEEK(7, "7 дн."),
    MONTH(30, "30 дн."),
    QUARTER(90, "3 мес."),
    HALF_YEAR(180, "6 мес."),
    YEAR(365, "Год"),
    ALL(null, "Всё");

    fun fromMillis(now: Long): Long = days?.let { now - it * 86_400_000L } ?: 0L
}
