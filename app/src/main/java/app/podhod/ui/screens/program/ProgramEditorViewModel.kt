package app.podhod.ui.screens.program

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podhod.AppContainer
import app.podhod.data.repo.ProgramItemDraft
import app.podhod.domain.InputRules
import app.podhod.domain.LoadType
import kotlinx.coroutines.launch

/** Упражнение в редакторе. Нули в числовых полях означают «не задано». */
data class EditItem(
    val key: Long,
    val exerciseId: Long,
    val name: String,
    val loadType: LoadType,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double,
    val restSeconds: Int,
    val notes: String,
    val expanded: Boolean,
)

sealed class SaveOutcome {
    data class Saved(val programId: Long) : SaveOutcome()
    object NoExercises : SaveOutcome()
}

class ProgramEditorViewModel(private val c: AppContainer, private val programId: Long?) : ViewModel() {
    private val repo = c.repository

    var loaded by mutableStateOf(programId == null)
        private set

    /** Программа, которую открыли, оказалась удалена. */
    var missing by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set

    val items = mutableStateListOf<EditItem>()

    var dirty by mutableStateOf(false)
        private set

    private var nextKey = 1L

    init {
        if (programId != null) {
            viewModelScope.launch {
                val p = repo.getProgram(programId)
                if (p == null) {
                    missing = true
                } else {
                    name = p.program.name
                    for (it in p.items.sortedBy { i -> i.position }) {
                        val ex = repo.getExercise(it.exerciseId) ?: continue
                        items += EditItem(
                            key = nextKey++,
                            exerciseId = ex.id,
                            name = ex.name,
                            loadType = ex.loadType,
                            sets = it.targetSets,
                            repsMin = it.targetRepsMin ?: 0,
                            repsMax = it.targetRepsMax ?: 0,
                            weightKg = it.targetWeight ?: 0.0,
                            restSeconds = it.restSeconds ?: 0,
                            notes = it.notes.orEmpty(),
                            expanded = false,
                        )
                    }
                }
                loaded = true
            }
        }
    }

    fun updateName(value: String) {
        name = value.take(80)
        dirty = true
    }

    fun addExercises(ids: List<Long>) {
        viewModelScope.launch {
            for (id in ids) {
                val ex = repo.getExercise(id) ?: continue
                items += EditItem(
                    key = nextKey++,
                    exerciseId = ex.id,
                    name = ex.name,
                    loadType = ex.loadType,
                    sets = 3,
                    repsMin = 8,
                    repsMax = 12,
                    weightKg = 0.0,
                    restSeconds = 0,
                    notes = "",
                    expanded = true,
                )
            }
            dirty = true
        }
    }

    fun update(key: Long, transform: (EditItem) -> EditItem) {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0) return
        val old = items[i]
        val next = transform(old)
        if (next == old) return
        items[i] = next
        // Раскрытие/сворачивание карточки не считается правкой программы.
        if (next.copy(expanded = old.expanded) != old) dirty = true
    }

    fun remove(key: Long) {
        if (items.removeAll { it.key == key }) dirty = true
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        dirty = true
    }

    fun moveByKey(key: Long, delta: Int) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) move(i, i + delta)
    }

    suspend fun save(): SaveOutcome {
        if (items.isEmpty()) return SaveOutcome.NoExercises
        val drafts = items.map { it.toDraft() }
        val id = repo.saveProgram(programId, name, drafts)
        dirty = false
        return SaveOutcome.Saved(id)
    }

    private fun EditItem.toDraft(): ProgramItemDraft {
        var lo = repsMin.takeIf { it > 0 }
        var hi = repsMax.takeIf { it > 0 }
        if (lo != null && hi != null && lo > hi) {
            val t = lo; lo = hi; hi = t
        }
        return ProgramItemDraft(
            exerciseId = exerciseId,
            targetSets = sets.coerceIn(1, InputRules.MAX_SETS),
            targetRepsMin = lo,
            targetRepsMax = hi,
            targetWeight = if (loadType == LoadType.REPS_ONLY) null else weightKg.takeIf { it > 0.0 },
            restSeconds = restSeconds.takeIf { it > 0 },
            notes = notes.takeIf { it.isNotBlank() },
        )
    }
}
