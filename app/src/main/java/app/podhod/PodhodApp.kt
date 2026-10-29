package app.podhod

import android.app.Application
import app.podhod.timer.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PodhodApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannels(this)
        // Начальная библиотека упражнений (идемпотентно: добавляет только недостающее).
        appScope.launch { container.repository.ensureSeeded() }
    }
}
