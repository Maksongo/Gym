package app.podhod

import android.content.Context
import app.podhod.data.db.AppDatabase
import app.podhod.data.repo.BackupService
import app.podhod.data.repo.WorkoutRepository
import app.podhod.data.settings.SettingsStore
import app.podhod.timer.RestTimerController

/**
 * Ручной DI-контейнер. Все зависимости создаются один раз на процесс.
 * Для облачной синхронизации достаточно добавить сюда сервис, работающий со снимком
 * [BackupService.snapshot] (модель BackupData не зависит от Room).
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.create(app) }
    val repository: WorkoutRepository by lazy { WorkoutRepository(database) }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val backup: BackupService by lazy { BackupService(database, settings, repository) }
    val restTimer: RestTimerController by lazy { RestTimerController(app) }
}
