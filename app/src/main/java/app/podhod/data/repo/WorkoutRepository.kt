package app.podhod.data.repo

import androidx.room.withTransaction
import app.podhod.data.db.AppDatabase
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.db.ProgramEntity
import app.podhod.data.db.ProgramItemEntity
import app.podhod.data.db.ProgramWithItems
import app.podhod.data.db.SessionEntity
import app.podhod.data.db.SessionSummaryRow
import app.podhod.data.db.SessionWithExercises
import app.podhod.data.db.SetEntity
import app.podhod.data.db.StatRow
import app.podhod.data.db.WorkoutExerciseEntity
import app.podhod.domain.BuiltInExercises
import app.podhod.domain.Equipment
import app.podhod.domain.ExerciseType
import app.podhod.domain.InputRules
import app.podhod.domain.LoadType
import app.podhod.domain.MuscleGroup
import app.podhod.domain.NameNormalizer
import app.podhod.domain.SessionResult
import app.podhod.domain.SessionStatus
import app.podhod.domain.SetProblem
import app.podhod.domain.SetValidator
import app.podhod.domain.SetValues
import app.podhod.domain.StatSet
import app.podhod.domain.WorkoutPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ExerciseDraft(
    val id: Long?,
    val name: String,
    val primary: MuscleGroup,
    val secondary: List<MuscleGroup>,
    val type: ExerciseType,
    val equipment: Equipment,
    val loadType: LoadType,
    val technique: String?,
    val notes: String?,
)

sealed class SaveExerciseResult {
    data class Saved(val id: Long) : SaveExerciseResult()
    data class Duplicate(val existingName: String) : SaveExerciseResult()
    object EmptyName : SaveExerciseResult()
    object NotFound : SaveExerciseResult()
}

enum class DeleteExerciseResult { DELETED, ARCHIVED }

data class ProgramItemDraft(
    val exerciseId: Long,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    /** кг; null — не задан. */
    val targetWeight: Double?,
    val restSeconds: Int?,
    val notes: String?,
)

data class StartResult(val sessionId: Long, val alreadyActive: Boolean)

sealed class FinishResult {
    data class Finished(val completedSets: Int, val removedSets: Int, val removedExercises: Int) : FinishResult()

    /** Нет ни одного выполненного подхода: тренировка не завершается, ничего не меняется. */
    object NothingCompleted : FinishResult()
}

/**
 * Единая точка доступа к данным. Все составные изменения выполняются в транзакциях,
 * поэтому приложение не может оказаться в «полусохранённом» состоянии.
 */
class WorkoutRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val exerciseDao = db.exercises()
    private val programDao = db.programs()
    private val sessionDao = db.sessions()

    // =====================================================================
    // Упражнения
    // =====================================================================

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    suspend fun getExercise(id: Long): ExerciseEntity? = exerciseDao.get(id)

    /** Добавляет недостающие упражнения начальной библиотеки. Безопасно вызывать при каждом запуске. */
    suspend fun ensureSeeded() {
        db.withTransaction {
            val have = exerciseDao.builtInKeys().toHashSet()
            val now = clock()
            for (b in BuiltInExercises.ALL) {
                if (b.key in have) continue
                exerciseDao.insertIgnore(
                    ExerciseEntity(
                        name = b.name,
                        nameKey = NameNormalizer.key(b.name),
                        primaryMuscle = b.primary,
                        secondaryMuscles = b.secondary,
                        exerciseType = b.type,
                        equipment = b.equipment,
                        loadType = b.loadType,
                        technique = b.technique,
                        notes = null,
                        isCustom = false,
                        isFavorite = false,
                        isArchived = false,
                        builtInKey = b.key,
                        createdAt = now,
                    ),
                )
            }
        }
    }

    suspend fun saveExercise(d: ExerciseDraft): SaveExerciseResult {
        val display = NameNormalizer.display(d.name).take(MAX_NAME)
        val key = NameNormalizer.key(display)
        if (display.isEmpty() || key.isEmpty()) return SaveExerciseResult.EmptyName
        val secondary = d.secondary.filter { it != d.primary }.distinct()
        val technique = d.technique?.trim()?.takeIf { it.isNotEmpty() }
        val notes = d.notes?.trim()?.takeIf { it.isNotEmpty() }

        return db.withTransaction {
            val same = exerciseDao.findByKey(key)
            if (d.id == null) {
                if (same != null) {
                    if (same.isArchived) {
                        // Ранее удалённое (архивное) упражнение с таким названием возвращается в библиотеку.
                        exerciseDao.update(
                            same.copy(
                                name = display, primaryMuscle = d.primary, secondaryMuscles = secondary,
                                exerciseType = d.type, equipment = d.equipment, loadType = d.loadType,
                                technique = technique, notes = notes, isArchived = false,
                            ),
                        )
                        SaveExerciseResult.Saved(same.id)
                    } else {
                        SaveExerciseResult.Duplicate(same.name)
                    }
                } else {
                    val id = exerciseDao.insert(
                        ExerciseEntity(
                            name = display, nameKey = key, primaryMuscle = d.primary,
                            secondaryMuscles = secondary, exerciseType = d.type, equipment = d.equipment,
                            loadType = d.loadType, technique = technique, notes = notes,
                            isCustom = true, isFavorite = false, isArchived = false,
                            builtInKey = null, createdAt = clock(),
                        ),
                    )
                    SaveExerciseResult.Saved(id)
                }
            } else {
                val current = exerciseDao.get(d.id) ?: return@withTransaction SaveExerciseResult.NotFound
                if (same != null && same.id != current.id) {
                    SaveExerciseResult.Duplicate(same.name)
                } else {
                    exerciseDao.update(
                        current.copy(
                            name = display, nameKey = key, primaryMuscle = d.primary,
                            secondaryMuscles = secondary, exerciseType = d.type, equipment = d.equipment,
                            loadType = d.loadType, technique = technique, notes = notes,
                        ),
                    )
                    SaveExerciseResult.Saved(current.id)
                }
            }
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = exerciseDao.setFavorite(id, favorite)

    /**
     * Удаление упражнения. Встроенные и те, что есть в истории, только архивируются (история
     * остаётся полной), остальные пользовательские удаляются совсем вместе со строками программ.
     */
    suspend fun deleteExercise(id: Long): DeleteExerciseResult = db.withTransaction {
        val e = exerciseDao.get(id) ?: return@withTransaction DeleteExerciseResult.DELETED
        if (!e.isCustom || exerciseDao.usageInHistory(id) > 0) {
            exerciseDao.setArchived(id, true)
            DeleteExerciseResult.ARCHIVED
        } else {
            exerciseDao.delete(id)
            DeleteExerciseResult.DELETED
        }
    }

    // =====================================================================
    // Программы
    // =====================================================================

    fun observePrograms(): Flow<List<ProgramWithItems>> = programDao.observeAll()

    fun observeLastUsedProgram(): Flow<ProgramEntity?> = programDao.observeLastUsed()

    suspend fun getProgram(id: Long): ProgramWithItems? = programDao.get(id)

    suspend fun saveProgram(id: Long?, name: String, items: List<ProgramItemDraft>): Long {
        val title = NameNormalizer.display(name).take(MAX_NAME).ifEmpty { DEFAULT_PROGRAM_NAME }
        return db.withTransaction {
            val now = clock()
            val programId: Long
            if (id == null || programDao.get(id) == null) {
                programId = programDao.insert(ProgramEntity(name = title, createdAt = now, updatedAt = now))
            } else {
                val cur = programDao.get(id)!!.program
                programDao.update(cur.copy(name = title, updatedAt = now))
                programDao.deleteItems(id)
                programId = id
            }
            programDao.insertItems(
                items.mapIndexed { index, it ->
                    ProgramItemEntity(
                        programId = programId,
                        exerciseId = it.exerciseId,
                        position = index,
                        targetSets = it.targetSets.coerceIn(1, InputRules.MAX_SETS),
                        targetRepsMin = it.targetRepsMin?.takeIf { v -> v > 0 }?.let(InputRules::clampReps),
                        targetRepsMax = it.targetRepsMax?.takeIf { v -> v > 0 }?.let(InputRules::clampReps),
                        targetWeight = it.targetWeight?.takeIf { v -> v > 0.0 }?.let(InputRules::clampKg),
                        restSeconds = it.restSeconds?.let { s -> s.coerceIn(InputRules.MIN_REST_SECONDS, InputRules.MAX_REST_SECONDS) },
                        notes = it.notes?.trim()?.takeIf { s -> s.isNotEmpty() },
                    )
                },
            )
            programId
        }
    }

    suspend fun duplicateProgram(id: Long): Long? = db.withTransaction {
        val src = programDao.get(id) ?: return@withTransaction null
        val now = clock()
        val newId = programDao.insert(
            ProgramEntity(name = "${src.program.name} (копия)".take(MAX_NAME + 8), createdAt = now, updatedAt = now),
        )
        programDao.insertItems(src.items.sortedBy { it.position }.map { it.copy(id = 0, programId = newId) })
        newId
    }

    /** Удаляет программу. История тренировок остаётся: у тренировок просто обнуляется ссылка на программу. */
    suspend fun deleteProgram(id: Long) = programDao.delete(id)

    /** «Копировать в новую программу»: цели берутся из фактически выполненных подходов тренировки. */
    suspend fun createProgramFromSession(sessionId: Long, name: String): Long? = db.withTransaction {
        val s = sessionDao.get(sessionId) ?: return@withTransaction null
        val now = clock()
        val title = NameNormalizer.display(name).take(MAX_NAME).ifEmpty { DEFAULT_PROGRAM_NAME }
        val programId = programDao.insert(ProgramEntity(name = title, createdAt = now, updatedAt = now))
        val items = s.exercises.sortedBy { it.exercise.position }.mapNotNull { we ->
            val done = we.sets.filter { it.isCompleted && it.reps > 0 }.sortedBy { it.setNumber }
            val basis = done.ifEmpty { we.sets.filter { it.reps > 0 } }
            if (basis.isEmpty() && we.sets.isEmpty()) return@mapNotNull null
            val topWeight = basis.maxOfOrNull { it.weight }?.takeIf { it > 0.0 }
            val reps = basis.map { it.reps }
            ProgramItemEntity(
                programId = programId,
                exerciseId = we.exercise.exerciseId,
                position = 0,
                targetSets = (done.size.takeIf { it > 0 } ?: we.sets.size).coerceIn(1, InputRules.MAX_SETS),
                targetRepsMin = we.exercise.targetRepsMin ?: reps.minOrNull(),
                targetRepsMax = we.exercise.targetRepsMax ?: reps.maxOrNull(),
                targetWeight = if (we.exercise.loadType == LoadType.REPS_ONLY) null else topWeight,
                restSeconds = we.exercise.restSeconds,
                notes = we.exercise.notes?.takeIf { it.isNotBlank() },
            )
        }.mapIndexed { i, it -> it.copy(position = i) }
        programDao.insertItems(items)
        programId
    }

    // =====================================================================
    // Тренировка: старт / ход / завершение
    // =====================================================================

    fun observeActiveSession(): Flow<SessionEntity?> = sessionDao.observeActive()

    fun observeSession(id: Long): Flow<SessionWithExercises?> = sessionDao.observe(id)

    suspend fun getSession(id: Long): SessionWithExercises? = sessionDao.get(id)

    /**
     * Запускает тренировку по программе (или пустую, если [programId] == null).
     * Одновременно может идти только одна тренировка: если она уже есть, возвращается она.
     */
    suspend fun startWorkout(programId: Long?): StartResult = db.withTransaction {
        sessionDao.active()?.let { return@withTransaction StartResult(it.id, alreadyActive = true) }
        val program = programId?.let { programDao.get(it) }
        val now = clock()
        val sessionId = sessionDao.insertSession(
            SessionEntity(
                programId = program?.program?.id,
                name = program?.program?.name ?: FREE_WORKOUT_NAME,
                startedAt = now,
                finishedAt = null,
                status = SessionStatus.IN_PROGRESS,
                notes = null,
            ),
        )
        program?.items?.sortedBy { it.position }?.forEach { item ->
            val ex = exerciseDao.get(item.exerciseId) ?: return@forEach
            appendExercise(
                sessionId = sessionId, exercise = ex,
                targetSets = item.targetSets, repsMin = item.targetRepsMin, repsMax = item.targetRepsMax,
                targetWeight = item.targetWeight, restSeconds = item.restSeconds, notes = item.notes,
            )
        }
        StartResult(sessionId, alreadyActive = false)
    }

    /**
     * «Повторить тренировку»: новая тренировка с теми же упражнениями; значения подхода берутся из
     * этой тренировки, подходы НЕ отмечены выполненными.
     */
    suspend fun repeatWorkout(sourceSessionId: Long): StartResult? = db.withTransaction {
        sessionDao.active()?.let { return@withTransaction StartResult(it.id, alreadyActive = true) }
        val src = sessionDao.get(sourceSessionId) ?: return@withTransaction null
        val now = clock()
        val sessionId = sessionDao.insertSession(
            SessionEntity(
                programId = src.session.programId,
                name = src.session.name,
                startedAt = now,
                finishedAt = null,
                status = SessionStatus.IN_PROGRESS,
                notes = null,
            ),
        )
        src.exercises.sortedBy { it.exercise.position }.forEachIndexed { index, we ->
            val newWe = sessionDao.insertWorkoutExercise(
                we.exercise.copy(id = 0, sessionId = sessionId, position = index),
            )
            val source = we.sets.sortedBy { it.setNumber }
            val values = source.ifEmpty {
                listOf(SetEntity(workoutExerciseId = 0, setNumber = 1, weight = 0.0, reps = 0, isCompleted = false, completedAt = null, notes = null))
            }
            sessionDao.insertSets(
                values.mapIndexed { i, s ->
                    SetEntity(
                        workoutExerciseId = newWe, setNumber = i + 1, weight = s.weight, reps = s.reps,
                        isCompleted = false, completedAt = null, notes = null,
                    )
                },
            )
        }
        StartResult(sessionId, alreadyActive = false)
    }

    suspend fun addExercise(sessionId: Long, exerciseId: Long): Long? = db.withTransaction {
        val ex = exerciseDao.get(exerciseId) ?: return@withTransaction null
        sessionDao.session(sessionId) ?: return@withTransaction null
        appendExercise(sessionId, ex, null, null, null, null, null, null)
    }

    private suspend fun appendExercise(
        sessionId: Long,
        exercise: ExerciseEntity,
        targetSets: Int?,
        repsMin: Int?,
        repsMax: Int?,
        targetWeight: Double?,
        restSeconds: Int?,
        notes: String?,
    ): Long {
        val session = sessionDao.session(sessionId)
        val beforeStarted = session?.startedAt ?: Long.MAX_VALUE
        val beforeId = session?.id ?: Long.MAX_VALUE
        val previous = lastCompletedSets(exercise.id, beforeStarted, beforeId)
        val sets = WorkoutPlanner.initialSets(
            loadType = exercise.loadType,
            targetSets = targetSets,
            targetRepsMin = repsMin,
            targetRepsMax = repsMax,
            targetWeight = targetWeight,
            previous = previous,
        )
        val weId = sessionDao.insertWorkoutExercise(
            WorkoutExerciseEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                position = sessionDao.maxPosition(sessionId) + 1,
                notes = notes?.takeIf { it.isNotBlank() },
                loadType = exercise.loadType,
                targetSets = targetSets,
                targetRepsMin = repsMin,
                targetRepsMax = repsMax,
                restSeconds = restSeconds,
            ),
        )
        sessionDao.insertSets(
            sets.mapIndexed { i, v ->
                SetEntity(
                    workoutExerciseId = weId, setNumber = i + 1, weight = v.weight, reps = v.reps,
                    isCompleted = false, completedAt = null, notes = null,
                )
            },
        )
        return weId
    }

    private suspend fun lastCompletedSets(exerciseId: Long, beforeStarted: Long, beforeId: Long): List<SetValues> {
        val last = sessionDao.recentExercises(exerciseId, beforeStarted, beforeId, 1).firstOrNull()
            ?: return emptyList()
        return sessionDao.completedSets(last.we.id).map { SetValues(it.weight, it.reps) }
    }

    /** Результаты прошлых тренировок по упражнению строго до указанной (свежие первыми). */
    suspend fun recentResults(
        exerciseId: Long,
        beforeStartedAt: Long,
        beforeSessionId: Long,
        limit: Int,
    ): List<SessionResult> =
        sessionDao.recentExercises(exerciseId, beforeStartedAt, beforeSessionId, limit).map { r ->
            SessionResult(
                startedAt = r.sessionStartedAt,
                targetSets = r.we.targetSets,
                repsMin = r.we.targetRepsMin,
                repsMax = r.we.targetRepsMax,
                sets = sessionDao.completedSets(r.we.id).map { SetValues(it.weight, it.reps) },
            )
        }

    suspend fun addSet(workoutExerciseId: Long): Long? = db.withTransaction {
        val sets = sessionDao.setsOf(workoutExerciseId)
        if (sets.size >= InputRules.MAX_SETS) return@withTransaction null
        val last = sets.lastOrNull()
        sessionDao.insertSet(
            SetEntity(
                workoutExerciseId = workoutExerciseId,
                setNumber = sessionDao.maxSetNumber(workoutExerciseId) + 1,
                weight = last?.weight ?: 0.0,
                reps = last?.reps ?: 0,
                isCompleted = false, completedAt = null, notes = null,
            ),
        )
    }

    /**
     * Меняет вес и повторения подхода (в том числе уже выполненного — это и есть исправление результата).
     * Если значения выполненного подхода стали некорректными (например, повторений 0), отметка
     * выполнения снимается, чтобы в истории не оказалось «выполненного» подхода без результата.
     */
    suspend fun updateSetValues(setId: Long, weightKg: Double, reps: Int) = db.withTransaction {
        val set = sessionDao.getSet(setId) ?: return@withTransaction
        val w = InputRules.clampKg(weightKg)
        val r = InputRules.clampReps(reps)
        if (w == set.weight && r == set.reps) return@withTransaction
        var updated = set.copy(weight = w, reps = r)
        if (updated.isCompleted) {
            val we = sessionDao.getWorkoutExercise(set.workoutExerciseId)
            if (we != null && SetValidator.problem(we.loadType, w, r) != null) {
                updated = updated.copy(isCompleted = false, completedAt = null)
            }
        }
        sessionDao.updateSet(updated)
    }

    /** Отмечает подход выполненным / снимает отметку. null — успех, иначе причина отказа. Значения не сбрасываются. */
    suspend fun setCompleted(setId: Long, completed: Boolean): SetProblem? = db.withTransaction {
        val set = sessionDao.getSet(setId) ?: return@withTransaction null
        if (set.isCompleted == completed) return@withTransaction null
        if (completed) {
            val we = sessionDao.getWorkoutExercise(set.workoutExerciseId) ?: return@withTransaction null
            SetValidator.problem(we.loadType, set.weight, set.reps)?.let { return@withTransaction it }
        }
        val at: Long? = if (completed) {
            val we = sessionDao.getWorkoutExercise(set.workoutExerciseId)
            val session = we?.let { sessionDao.session(it.sessionId) }
            // Для исправления прошлой тренировки время выполнения не «переезжает» в сегодня.
            if (session?.status == SessionStatus.FINISHED) session.finishedAt ?: clock() else clock()
        } else null
        sessionDao.updateSet(set.copy(isCompleted = completed, completedAt = at))
        null
    }

    suspend fun deleteSet(setId: Long) = db.withTransaction {
        val set = sessionDao.getSet(setId) ?: return@withTransaction
        sessionDao.deleteSet(setId)
        sessionDao.shiftSetNumbersDown(set.workoutExerciseId, set.setNumber)
    }

    suspend fun removeWorkoutExercise(workoutExerciseId: Long) = sessionDao.deleteWorkoutExercise(workoutExerciseId)

    suspend fun updateExerciseNotes(workoutExerciseId: Long, notes: String) =
        sessionDao.updateExerciseNotes(workoutExerciseId, notes.takeIf { it.isNotBlank() })

    suspend fun updateSessionNotes(sessionId: Long, notes: String) = db.withTransaction {
        val s = sessionDao.session(sessionId) ?: return@withTransaction
        sessionDao.updateSession(s.copy(notes = notes.takeIf { it.isNotBlank() }))
    }

    suspend fun renameSession(sessionId: Long, name: String) = db.withTransaction {
        val s = sessionDao.session(sessionId) ?: return@withTransaction
        val title = NameNormalizer.display(name).take(MAX_NAME)
        if (title.isNotEmpty()) sessionDao.updateSession(s.copy(name = title))
    }

    /** Применяет принятую рекомендацию: меняет вес только НЕвыполненных подходов упражнения. */
    suspend fun applyWeightToPendingSets(workoutExerciseId: Long, weightKg: Double) =
        sessionDao.setWeightOfPendingSets(workoutExerciseId, InputRules.clampKg(weightKg))

    /**
     * Завершает тренировку. Невыполненные подходы (и упражнения, где не осталось подходов) удаляются:
     * в журнале остаётся только то, что реально сделано.
     */
    suspend fun finishWorkout(sessionId: Long): FinishResult = db.withTransaction {
        val s = sessionDao.session(sessionId) ?: return@withTransaction FinishResult.NothingCompleted
        if (s.status == SessionStatus.FINISHED) {
            return@withTransaction FinishResult.Finished(sessionDao.completedSetCount(sessionId), 0, 0)
        }
        val completed = sessionDao.completedSetCount(sessionId)
        if (completed == 0) return@withTransaction FinishResult.NothingCompleted
        val removedSets = sessionDao.deleteIncompleteSets(sessionId)
        val removedExercises = sessionDao.deleteExercisesWithoutSets(sessionId)
        sessionDao.updateSession(s.copy(status = SessionStatus.FINISHED, finishedAt = clock()))
        FinishResult.Finished(completed, removedSets, removedExercises)
    }

    /** Полностью удаляет тренировку (активную — «отменить», завершённую — из истории). */
    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteSession(sessionId)

    // =====================================================================
    // История и статистика
    // =====================================================================

    fun observeSummaries(): Flow<List<SessionSummaryRow>> = sessionDao.observeSummaries()

    fun observeStatSets(exerciseId: Long?, fromMillis: Long): Flow<List<StatSet>> =
        sessionDao.observeStatRows(exerciseId, fromMillis).map { rows -> rows.map(StatRow::toStatSet) }

    fun observeExerciseIdsWithHistory(): Flow<List<Long>> = sessionDao.observeExerciseIdsWithHistory()

    companion object {
        const val MAX_NAME = 80
        const val DEFAULT_PROGRAM_NAME = "Новая программа"
        const val FREE_WORKOUT_NAME = "Свободная тренировка"
    }
}

fun StatRow.toStatSet() = StatSet(
    sessionId = sessionId, startedAt = startedAt, exerciseId = exerciseId,
    loadType = loadType, weight = weight, reps = reps,
)
