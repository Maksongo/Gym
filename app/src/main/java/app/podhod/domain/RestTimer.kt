package app.podhod.domain

/**
 * Состояние таймера отдыха хранится как АБСОЛЮТНОЕ время окончания.
 * Поэтому он корректно «идёт» при сворачивании приложения, блокировке экрана и даже
 * после убийства процесса: остаток всегда вычисляется как endsAt − now.
 */
data class RestTimerState(val endsAtMillis: Long, val totalMillis: Long) {
    fun remainingMillis(now: Long): Long = (endsAtMillis - now).coerceAtLeast(0L)

    fun isExpired(now: Long): Boolean = now >= endsAtMillis

    /** Доля оставшегося времени 0..1 (для индикатора). */
    fun fractionLeft(now: Long): Float =
        if (totalMillis <= 0) 0f else (remainingMillis(now).toFloat() / totalMillis).coerceIn(0f, 1f)

    /** Остаток в секундах, округлённый вверх: 0:01 показывается до самого конца. */
    fun remainingSeconds(now: Long): Int = ((remainingMillis(now) + 999) / 1000).toInt()
}

object RestTimer {
    val PRESETS_SECONDS = listOf(60, 90, 120, 180)
    const val STEP_SECONDS = 15

    fun clampSeconds(sec: Int): Int = sec.coerceIn(InputRules.MIN_REST_SECONDS, InputRules.MAX_REST_SECONDS)

    fun start(now: Long, seconds: Int): RestTimerState {
        val total = clampSeconds(seconds) * 1000L
        return RestTimerState(endsAtMillis = now + total, totalMillis = total)
    }

    /**
     * Сдвигает окончание на [deltaSeconds]. Если остаток становится меньше секунды —
     * таймер считается остановленным (null).
     */
    fun adjust(state: RestTimerState, deltaSeconds: Int, now: Long): RestTimerState? {
        val newEnd = state.endsAtMillis + deltaSeconds * 1000L
        val remaining = newEnd - now
        if (remaining < 1000L) return null
        val cappedRemaining = remaining.coerceAtMost(InputRules.MAX_REST_SECONDS * 1000L)
        return RestTimerState(
            endsAtMillis = now + cappedRemaining,
            totalMillis = (state.totalMillis + deltaSeconds * 1000L).coerceAtLeast(cappedRemaining),
        )
    }

    fun fromPersisted(endsAt: Long?, totalSec: Int?): RestTimerState? {
        if (endsAt == null || totalSec == null || totalSec <= 0) return null
        return RestTimerState(endsAt, totalSec * 1000L)
    }
}
