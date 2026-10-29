package app.podhod.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podhod.AppContainer
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.db.SessionWithExercises
import app.podhod.data.db.SetEntity
import app.podhod.data.repo.FinishResult
import app.podhod.data.repo.StartResult
import app.podhod.domain.LoadType
import app.podhod.domain.PreviousResultFormatter
import app.podhod.domain.ProgressionAdvisor
import app.podhod.domain.ProgressionSuggestion
import app.podhod.domain.RestTimerState
import app.podhod.domain.SessionResult
import app.podhod.domain.SessionStatus
import app.podhod.domain.SetProblem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class SessionUi {
    object Loading : SessionUi()

    /** Тренировки нет (например, её отменили в другом месте). */
    object Missing : SessionUi()

    data class Ready(val data: SessionWithExercises) : SessionUi()
}

/** Что показать в карточке упражнения над таблицей подходов. */
data class PreviousInfo(
    /** «42 кг × 8, 8, 8» или null, если раньше упражнение не выполнялось. */
    val text: String?,
    val date: Long?,
    val suggestion: ProgressionSuggestion?,
)

private data class PrevItem(val workoutExerciseId: Long, val exerciseId: Long, val loadType: LoadType)

private data class PrevKey(val startedAt: Long, val items: List<PrevItem>)

/**
 * Модель одной тренировки: и активной, и уже завершённой (экран деталей истории).
 * Все изменения сразу записываются в базу, поэтому при закрытии приложения ничего не теряется.
 */
class SessionViewModel(private val c: AppContainer, val sessionId: Long) : ViewModel() {
    private val repo = c.repository
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    val state: StateFlow<SessionUi> = repo.observeSession(sessionId)
        .map { if (it == null) SessionUi.Missing else SessionUi.Ready(it) }
        .stateIn(viewModelScope, whileSubscribed, SessionUi.Loading)

    val exercises: StateFlow<Map<Long, ExerciseEntity>> = repo.observeExercises()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, whileSubscribed, emptyMap())

    val restTimer: StateFlow<RestTimerState?> get() = c.restTimer.state

    private val dismissedSuggestions = MutableStateFlow<Set<Long>>(emptySet())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Короткие сообщения для Snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Результаты прошлых тренировок по каждому упражнению (строго ДО этой тренировки) и необязательная
     * рекомендация повысить вес. Пересчитывается только при смене набора упражнений или настроек.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previous: StateFlow<Map<Long, PreviousInfo>> = combine(
        state.filterIsInstance<SessionUi.Ready>()
            .map { ready ->
                PrevKey(
                    ready.data.session.startedAt,
                    ready.data.exercises.map { PrevItem(it.exercise.id, it.exercise.exerciseId, it.exercise.loadType) },
                )
            }
            .distinctUntilChanged()
            .mapLatest { key ->
                key.items.associate { item ->
                    item.workoutExerciseId to Pair(
                        item,
                        repo.recentResults(item.exerciseId, key.startedAt, sessionId, MAX_HISTORY),
                    )
                }
            },
        c.settings.settings,
        dismissedSuggestions,
    ) { loaded, settings, dismissed ->
        loaded.mapValues { (weId, pair) ->
            val (item, results) = pair
            val last: SessionResult? = results.firstOrNull()
            val suggestion = if (settings.progressionEnabled && weId !in dismissed) {
                ProgressionAdvisor.suggest(
                    loadType = item.loadType,
                    sessionsNewestFirst = results,
                    requiredSessions = settings.progressionSessions,
                    stepKg = settings.progressionStepKg,
                )
            } else null
            PreviousInfo(
                text = last?.let { PreviousResultFormatter.format(it.sets, item.loadType, settings.unit) },
                date = last?.startedAt,
                suggestion = suggestion,
            )
        }
    }.stateIn(viewModelScope, whileSubscribed, emptyMap())

    private fun isActive(): Boolean =
        (state.value as? SessionUi.Ready)?.data?.session?.status == SessionStatus.IN_PROGRESS

    // ---------- подходы ----------

    fun toggleSet(set: SetEntity, completed: Boolean, exerciseRestSeconds: Int?) {
        viewModelScope.launch {
            val problem = repo.setCompleted(set.id, completed)
            if (problem != null) {
                _messages.emit(
                    when (problem) {
                        SetProblem.NO_WEIGHT -> "Укажите вес, чтобы отметить подход выполненным"
                        SetProblem.NO_REPS -> "Укажите число повторений, чтобы отметить подход выполненным"
                    },
                )
                return@launch
            }
            if (completed && isActive()) {
                val s = c.settings.current()
                if (s.restEnabled && s.autoStartRest) {
                    c.restTimer.start(exerciseRestSeconds ?: s.restSeconds, s.vibrate)
                }
            }
        }
    }

    // Изменения веса и повторений идут через мьютекс и читают актуальное значение из базы:
    // два быстрых действия подряд не затирают друг друга устаревшими данными с экрана.
    private val editMutex = Mutex()

    fun setWeight(setId: Long, weightKg: Double) {
        viewModelScope.launch {
            editMutex.withLock {
                val cur = c.database.sessions().getSet(setId) ?: return@launch
                repo.updateSetValues(setId, weightKg, cur.reps)
            }
        }
    }

    fun setReps(setId: Long, reps: Int) {
        viewModelScope.launch {
            editMutex.withLock {
                val cur = c.database.sessions().getSet(setId) ?: return@launch
                repo.updateSetValues(setId, cur.weight, reps)
            }
        }
    }

    fun addSet(workoutExerciseId: Long) {
        viewModelScope.launch {
            if (repo.addSet(workoutExerciseId) == null) _messages.emit("Достигнут предел числа подходов")
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch { repo.deleteSet(setId) }
    }

    // ---------- упражнения ----------

    fun addExercises(ids: List<Long>) {
        viewModelScope.launch { for (id in ids) repo.addExercise(sessionId, id) }
    }

    fun removeExercise(workoutExerciseId: Long) {
        viewModelScope.launch { repo.removeWorkoutExercise(workoutExerciseId) }
    }

    fun updateExerciseNotes(workoutExerciseId: Long, notes: String) {
        viewModelScope.launch { repo.updateExerciseNotes(workoutExerciseId, notes) }
    }

    fun updateSessionNotes(notes: String) {
        viewModelScope.launch { repo.updateSessionNotes(sessionId, notes) }
    }

    fun renameSession(name: String) {
        viewModelScope.launch { repo.renameSession(sessionId, name) }
    }

    // ---------- рекомендации ----------

    /** Принимает рекомендацию: вес меняется только у ещё не выполненных подходов. */
    fun applySuggestion(workoutExerciseId: Long, suggestion: ProgressionSuggestion) {
        viewModelScope.launch {
            repo.applyWeightToPendingSets(workoutExerciseId, suggestion.suggestedWeightKg)
            dismissedSuggestions.value = dismissedSuggestions.value + workoutExerciseId
        }
    }

    fun dismissSuggestion(workoutExerciseId: Long) {
        dismissedSuggestions.value = dismissedSuggestions.value + workoutExerciseId
    }

    // ---------- таймер отдыха ----------

    fun startRest(seconds: Int) {
        viewModelScope.launch {
            val s = c.settings.current()
            c.restTimer.start(seconds, s.vibrate)
        }
    }

    fun adjustRest(deltaSeconds: Int) = c.restTimer.adjust(deltaSeconds)

    fun stopRest() = c.restTimer.stop()

    // ---------- завершение ----------

    suspend fun finish(): FinishResult {
        val r = repo.finishWorkout(sessionId)
        if (r is FinishResult.Finished) c.restTimer.stop()
        return r
    }

    suspend fun delete() {
        repo.deleteSession(sessionId)
        c.restTimer.stop()
    }

    suspend fun repeat(): StartResult? = repo.repeatWorkout(sessionId)

    suspend fun copyToProgram(name: String): Long? = repo.createProgramFromSession(sessionId, name)

    private companion object {
        const val MAX_HISTORY = 10
    }
}
