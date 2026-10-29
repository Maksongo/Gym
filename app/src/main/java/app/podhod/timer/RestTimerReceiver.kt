package app.podhod.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.podhod.PodhodApp

/** Срабатывает по будильнику, когда время отдыха вышло (в том числе при свёрнутом приложении). */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_DONE) return
        val app = context.applicationContext as? PodhodApp
        val prefs = context.getSharedPreferences(RestTimerController.PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(RestTimerController.KEY_ENDS_AT)) return // таймер уже остановлен
        val endsAt = prefs.getLong(RestTimerController.KEY_ENDS_AT, 0L)
        // Будильник мог прийти для прежнего времени, если таймер потом продлили.
        if (System.currentTimeMillis() < endsAt - 1500L) return
        val vibrate = prefs.getBoolean(RestTimerController.KEY_VIBRATE, true)
        if (vibrate) Notifier.vibrate(context)
        if (app?.container?.restTimer?.isForeground != true) Notifier.showRestDone(context)
    }

    companion object {
        const val ACTION_REST_DONE = "app.podhod.action.REST_DONE"
    }
}
