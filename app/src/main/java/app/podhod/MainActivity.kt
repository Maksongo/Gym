package app.podhod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podhod.domain.AppSettings
import app.podhod.timer.Notifier
import app.podhod.ui.components.LocalAppSettings
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.nav.AppRoot
import app.podhod.ui.theme.PodhodTheme
import app.podhod.ui.theme.isDarkTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PodhodApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = container
        // Настройки читаются до первого кадра, чтобы тема не «мигала» при запуске.
        val initial: AppSettings = runBlocking { container.settings.current() }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = initial)
            val dark = isDarkTheme(settings.theme)
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CompositionLocalProvider(
                LocalContainer provides container,
                LocalAppSettings provides settings,
            ) {
                PodhodTheme(settings.theme) { AppRoot() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.restTimer.isForeground = true
        container.restTimer.reload()
        Notifier.cancelRestDone(this)
    }

    override fun onStop() {
        container.restTimer.isForeground = false
        super.onStop()
    }
}
