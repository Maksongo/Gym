package app.podhod.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podhod.AppContainer
import app.podhod.data.db.ExerciseEntity
import app.podhod.domain.ChartMetric
import app.podhod.domain.Period
import app.podhod.domain.StatSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    /** null — «все упражнения». */
    val exerciseId = MutableStateFlow<Long?>(null)
    val period = MutableStateFlow(Period.QUARTER)
    val metric = MutableStateFlow(ChartMetric.WEIGHT)

    /** Упражнения, по которым есть хотя бы один выполненный подход в завершённых тренировках. */
    val exercisesWithHistory: StateFlow<List<ExerciseEntity>> = combine(
        repo.observeExercises(),
        repo.observeExerciseIdsWithHistory(),
    ) { all, ids ->
        val set = ids.toHashSet()
        all.filter { it.id in set }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, whileSubscribed, emptyList())

    /** null — идёт загрузка. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<StatSet>?> = combine(exerciseId, period) { e, p -> e to p }
        .flatMapLatest { (e, p) -> repo.observeStatSets(e, p.fromMillis(System.currentTimeMillis())) }
        .stateIn(viewModelScope, whileSubscribed, null)

    fun selectExercise(id: Long?) {
        exerciseId.value = id
        // Для разных упражнений применимы разные метрики; «Вес» подходит большинству.
        metric.value = ChartMetric.WEIGHT
    }

    fun selectPeriod(p: Period) {
        period.value = p
    }

    fun selectMetric(m: ChartMetric) {
        metric.value = m
    }
}
