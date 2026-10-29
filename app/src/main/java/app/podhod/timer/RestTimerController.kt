package app.podhod.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.podhod.domain.RestTimer
import app.podhod.domain.RestTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Таймер отдыха. Состояние — абсолютное время окончания ([RestTimerState]), оно хранится в
 * SharedPreferences, поэтому переживает сворачивание, блокировку экрана и даже остановку процесса.
 * Об окончании отдыха сообщает будильник AlarmManager ([RestTimerReceiver]) — он срабатывает и
 * при свёрнутом приложении. Пока приложение на экране, остаток считает сам интерфейс по часам.
 */
class RestTimerController(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    /** Выставляется MainActivity: пока приложение на экране, уведомление не показывается. */
    @Volatile
    var isForeground: Boolean = false

    fun start(seconds: Int, vibrate: Boolean, now: Long = System.currentTimeMillis()) {
        val s = RestTimer.start(now, seconds)
        persist(s, vibrate)
        schedule(s)
        _state.value = s
    }

    fun adjust(deltaSeconds: Int, now: Long = System.currentTimeMillis()) {
        val cur = _state.value ?: return
        val next = RestTimer.adjust(cur, deltaSeconds, now)
        if (next == null) {
            stop()
        } else {
            persist(next, prefs.getBoolean(KEY_VIBRATE, true))
            schedule(next)
            _state.value = next
        }
    }

    fun stop() {
        cancelAlarm()
        prefs.edit().remove(KEY_ENDS_AT).remove(KEY_TOTAL_SEC).apply()
        Notifier.cancelRestDone(app)
        _state.value = null
    }

    /** Состояние могло измениться без нас (процесс был убит): перечитываем. */
    fun reload() {
        _state.value = load()
    }

    private fun load(): RestTimerState? {
        val endsAt = if (prefs.contains(KEY_ENDS_AT)) prefs.getLong(KEY_ENDS_AT, 0L) else null
        val total = if (prefs.contains(KEY_TOTAL_SEC)) prefs.getInt(KEY_TOTAL_SEC, 0) else null
        val s = RestTimer.fromPersisted(endsAt, total) ?: return null
        // Давно закончившийся отдых не показываем.
        return if (System.currentTimeMillis() - s.endsAtMillis > STALE_AFTER_MS) {
            prefs.edit().remove(KEY_ENDS_AT).remove(KEY_TOTAL_SEC).apply()
            null
        } else s
    }

    private fun persist(s: RestTimerState, vibrate: Boolean) {
        prefs.edit()
            .putLong(KEY_ENDS_AT, s.endsAtMillis)
            .putInt(KEY_TOTAL_SEC, (s.totalMillis / 1000L).toInt())
            .putBoolean(KEY_VIBRATE, vibrate)
            .apply()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_REST_DONE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(s: RestTimerState) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent()
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAtMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endsAtMillis, pi)
        }
    }

    private fun cancelAlarm() {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent())
    }

    companion object {
        const val PREFS = "rest_timer"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_TOTAL_SEC = "total_sec"
        const val KEY_VIBRATE = "vibrate"
        private const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
