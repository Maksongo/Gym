package app.podhod.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podhod.AppContainer
import app.podhod.data.db.ProgramEntity
import app.podhod.data.db.ProgramWithItems
import app.podhod.data.db.SessionEntity
import app.podhod.data.db.SessionSummaryRow
import app.podhod.data.repo.FinishResult
import app.podhod.domain.LoadType
import app.podhod.domain.Period
import app.podhod.domain.ProgressCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class Highlight(
    val exerciseName: String,
    val loadType: LoadType,
    val weightChangeKg: Double?,
    val repsChange: Int?,
    val sessions: Int,
)

data class ActiveProgress(val done: Int, val total: Int)

class WorkoutsViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val eager = SharingStarted.WhileSubscribed(5_000)

    val period = MutableStateFlow(Period.MONTH)

    /** null — ещё загружается. */
    val summaries: StateFlow<List<SessionSummaryRow>?> = repo.observeSummaries().stateIn(viewModelScope, eager, null)
    val programs: StateFlow<List<ProgramWithItems>?> = repo.observePrograms().stateIn(viewModelScope, eager, null)
    val exerciseNames: StateFlow<Map<Long, String>> =
        repo.observeExercises().map { list -> list.associate { it.id to it.name } }.stateIn(viewModelScope, eager, emptyMap())
    val lastUsedProgram: StateFlow<ProgramEntity?> = repo.observeLastUsedProgram().stateIn(viewModelScope, eager, null)
    val activeSession: StateFlow<SessionEntity?> = repo.observeActiveSession().stateIn(viewModelScope, eager, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProgress: StateFlow<ActiveProgress?> = activeSession.flatMapLatest { s ->
        if (s == null) flowOf(null) else repo.observeSession(s.id).map { sw ->
            sw?.let {
                val sets = it.exercises.flatMap { e -> e.sets }
                ActiveProgress(done = sets.count { st -> st.isCompleted }, total = sets.size)
            }
        }
    }.stateIn(viewModelScope, eager, null)

    /** Краткая сводка прогресса за 30 дней: изменение рабочего веса (или повторений) по упражнениям. */
    val highlights: StateFlow<List<Highlight>> = run {
        val from = System.currentTimeMillis() - 30L * 86_400_000L
        combineHighlights(from)
    }.stateIn(viewModelScope, eager, emptyList())

    private fun combineHighlights(from: Long) = repo.observeStatSets(null, from).map { rows ->
        val names = exerciseNames.value
        rows.groupBy { it.exerciseId }.mapNotNull { (exerciseId, list) ->
            val s = ProgressCalculator.summary(list)
            if (s.sessionsCount < 2) return@mapNotNull null
            val loadType = list.first().loadType
            val w = s.weightChangeKg
            val r = s.repsChange
            if ((w == null || w == 0.0) && (r == null || r == 0)) return@mapNotNull null
            Highlight(names[exerciseId] ?: "Упражнение", loadType, w, r, s.sessionsCount)
        }.sortedByDescending { abs(it.weightChangeKg ?: 0.0) * 1000 + abs(it.repsChange ?: 0) }.take(3)
    }

    fun setPeriod(p: Period) {
        period.value = p
    }

    fun start(programId: Long?, onStarted: (sessionId: Long, alreadyActive: Boolean) -> Unit) {
        viewModelScope.launch {
            val r = repo.startWorkout(programId)
            onStarted(r.sessionId, r.alreadyActive)
        }
    }

    fun duplicate(programId: Long) {
        viewModelScope.launch { repo.duplicateProgram(programId) }
    }

    fun delete(programId: Long) {
        viewModelScope.launch { repo.deleteProgram(programId) }
    }

    /** Завершает незавершённую тренировку прямо с главного экрана. */
    fun finish(sessionId: Long, onResult: (FinishResult) -> Unit) {
        viewModelScope.launch {
            val r = repo.finishWorkout(sessionId)
            if (r is FinishResult.Finished) c.restTimer.stop()
            onResult(r)
        }
    }

    /** Отменяет (удаляет) незавершённую тренировку. Вызывается только после подтверждения. */
    fun cancel(sessionId: Long) {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
            c.restTimer.stop()
        }
    }
}
