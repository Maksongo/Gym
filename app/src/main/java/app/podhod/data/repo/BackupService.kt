package app.podhod.data.repo

import androidx.room.withTransaction
import app.podhod.data.db.AppDatabase
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.db.ProgramEntity
import app.podhod.data.db.ProgramItemEntity
import app.podhod.data.db.SessionEntity
import app.podhod.data.db.SetEntity
import app.podhod.data.db.WorkoutExerciseEntity
import app.podhod.data.settings.SettingsStore
import app.podhod.domain.AppSettings
import app.podhod.domain.Equipment
import app.podhod.domain.ExerciseType
import app.podhod.domain.LoadType
import app.podhod.domain.MuscleGroup
import app.podhod.domain.NameNormalizer
import app.podhod.domain.SessionStatus
import app.podhod.domain.backup.BackupCodec
import app.podhod.domain.backup.BackupData
import app.podhod.domain.backup.BackupExercise
import app.podhod.domain.backup.BackupFormatException
import app.podhod.domain.backup.BackupProgram
import app.podhod.domain.backup.BackupProgramItem
import app.podhod.domain.backup.BackupSession
import app.podhod.domain.backup.BackupSet
import app.podhod.domain.backup.BackupWorkoutExercise
import app.podhod.domain.backup.CsvExporter
import app.podhod.domain.enumOrDefault
import java.time.ZoneId

/** Что будет заменено при восстановлении: показывается пользователю до подтверждения. */
data class RestorePreview(
    val data: BackupData,
    val exercises: Int,
    val customExercises: Int,
    val programs: Int,
    val sessions: Int,
    val sets: Int,
    val exportedAt: Long,
)

/** Что сейчас лежит в базе: нужно для предупреждения о перезаписи. */
data class CurrentCounts(val programs: Int, val sessions: Int, val customExercises: Int)

/**
 * Экспорт / импорт / резервная копия. Работает со снимком [BackupData], который не зависит от Room:
 * тот же снимок можно будет отправлять в облако.
 */
class BackupService(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val repository: WorkoutRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val exerciseDao = db.exercises()
    private val programDao = db.programs()
    private val sessionDao = db.sessions()

    suspend fun snapshot(settings: AppSettings? = null): BackupData = db.withTransaction {
        val exercises = exerciseDao.all().map { e ->
            BackupExercise(
                id = e.id, name = e.name, primaryMuscle = e.primaryMuscle.name,
                secondaryMuscles = e.secondaryMuscles.map { it.name },
                exerciseType = e.exerciseType.name, equipment = e.equipment.name,
                loadType = e.loadType.name, technique = e.technique, notes = e.notes,
                isCustom = e.isCustom, isFavorite = e.isFavorite, isArchived = e.isArchived,
                builtInKey = e.builtInKey, createdAt = e.createdAt,
            )
        }
        val programs = programDao.all().map { p ->
            BackupProgram(
                id = p.program.id, name = p.program.name,
                createdAt = p.program.createdAt, updatedAt = p.program.updatedAt,
                items = p.items.sortedBy { it.position }.map {
                    BackupProgramItem(
                        exerciseId = it.exerciseId, position = it.position, targetSets = it.targetSets,
                        targetRepsMin = it.targetRepsMin, targetRepsMax = it.targetRepsMax,
                        targetWeight = it.targetWeight, restSeconds = it.restSeconds, notes = it.notes,
                    )
                },
            )
        }
        val sessions = sessionDao.all().map { s ->
            BackupSession(
                id = s.session.id, programId = s.session.programId, name = s.session.name,
                startedAt = s.session.startedAt, finishedAt = s.session.finishedAt,
                status = s.session.status.name, notes = s.session.notes,
                exercises = s.exercises.sortedBy { it.exercise.position }.map { we ->
                    BackupWorkoutExercise(
                        exerciseId = we.exercise.exerciseId, position = we.exercise.position,
                        notes = we.exercise.notes, loadType = we.exercise.loadType.name,
                        targetSets = we.exercise.targetSets, targetRepsMin = we.exercise.targetRepsMin,
                        targetRepsMax = we.exercise.targetRepsMax, restSeconds = we.exercise.restSeconds,
                        sets = we.sets.sortedBy { it.setNumber }.map {
                            BackupSet(
                                setNumber = it.setNumber, weight = it.weight, reps = it.reps,
                                isCompleted = it.isCompleted, completedAt = it.completedAt, notes = it.notes,
                            )
                        },
                    )
                },
            )
        }
        BackupData(
            exportedAt = clock(),
            settings = settings ?: settingsStore.current(),
            exercises = exercises, programs = programs, sessions = sessions,
        )
    }

    /** JSON со ВСЕМИ данными: упражнения, программы, история, настройки. */
    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    /** CSV: по строке на каждый выполненный подход завершённых тренировок. */
    suspend fun exportCsv(zone: ZoneId = ZoneId.systemDefault()): String = CsvExporter.build(snapshot(), zone)

    /** Разбирает и проверяет файл, ничего не меняя в базе. [BackupFormatException] — файл непригоден. */
    fun preview(text: String): RestorePreview {
        val data = BackupCodec.decode(text)
        return RestorePreview(
            data = data,
            exercises = data.exercises.size,
            customExercises = data.exercises.count { it.isCustom },
            programs = data.programs.size,
            sessions = data.sessions.size,
            sets = data.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } },
            exportedAt = data.exportedAt,
        )
    }

    suspend fun currentCounts(): CurrentCounts = db.withTransaction {
        CurrentCounts(
            programs = programDao.all().size,
            sessions = sessionDao.all().size,
            customExercises = exerciseDao.all().count { it.isCustom },
        )
    }

    /**
     * Полностью заменяет данные содержимым копии — ОДНОЙ транзакцией. Если что-то пойдёт не так,
     * текущие данные остаются нетронутыми. Идентификаторы сохраняются как в файле.
     */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            sessionDao.deleteAll()
            programDao.deleteAll()
            exerciseDao.deleteAll()

            val usedKeys = HashSet<String>()
            val usedBuiltIn = HashSet<String>()
            val exercises = data.exercises.map { e ->
                var name = NameNormalizer.display(e.name).take(WorkoutRepository.MAX_NAME).ifEmpty { "Упражнение ${e.id}" }
                var key = NameNormalizer.key(name).ifEmpty { "exercise ${e.id}" }
                var n = 2
                while (!usedKeys.add(key)) {
                    name = "${NameNormalizer.display(e.name)} ($n)"
                    key = NameNormalizer.key(name)
                    n++
                }
                val builtIn = e.builtInKey?.takeIf { usedBuiltIn.add(it) }
                ExerciseEntity(
                    id = e.id, name = name, nameKey = key,
                    primaryMuscle = enumOrDefault(e.primaryMuscle, MuscleGroup.FULL_BODY),
                    secondaryMuscles = e.secondaryMuscles.map { enumOrDefault(it, MuscleGroup.FULL_BODY) }.distinct(),
                    exerciseType = enumOrDefault(e.exerciseType, ExerciseType.COMPOUND),
                    equipment = enumOrDefault(e.equipment, Equipment.OTHER),
                    loadType = enumOrDefault(e.loadType, LoadType.WEIGHTED),
                    technique = e.technique, notes = e.notes,
                    isCustom = e.isCustom, isFavorite = e.isFavorite, isArchived = e.isArchived,
                    builtInKey = builtIn, createdAt = e.createdAt,
                )
            }
            exerciseDao.insertAll(exercises)

            programDao.insertAll(
                data.programs.map { ProgramEntity(it.id, it.name, it.createdAt, it.updatedAt) },
            )
            programDao.insertItems(
                data.programs.flatMap { p ->
                    p.items.sortedBy { it.position }.mapIndexed { index, it ->
                        ProgramItemEntity(
                            programId = p.id, exerciseId = it.exerciseId, position = index,
                            targetSets = it.targetSets, targetRepsMin = it.targetRepsMin,
                            targetRepsMax = it.targetRepsMax, targetWeight = it.targetWeight,
                            restSeconds = it.restSeconds, notes = it.notes,
                        )
                    }
                },
            )

            val programIds = data.programs.map { it.id }.toHashSet()
            sessionDao.insertSessions(
                data.sessions.map { s ->
                    SessionEntity(
                        id = s.id,
                        programId = s.programId?.takeIf { it in programIds },
                        name = s.name, startedAt = s.startedAt, finishedAt = s.finishedAt,
                        status = enumOrDefault(s.status, SessionStatus.FINISHED), notes = s.notes,
                    )
                },
            )
            for (s in data.sessions) {
                for (we in s.exercises.sortedBy { it.position }) {
                    val weId = sessionDao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            sessionId = s.id, exerciseId = we.exerciseId, position = we.position,
                            notes = we.notes, loadType = enumOrDefault(we.loadType, LoadType.WEIGHTED),
                            targetSets = we.targetSets, targetRepsMin = we.targetRepsMin,
                            targetRepsMax = we.targetRepsMax, restSeconds = we.restSeconds,
                        ),
                    )
                    sessionDao.insertSets(
                        we.sets.sortedBy { it.setNumber }.mapIndexed { i, st ->
                            SetEntity(
                                workoutExerciseId = weId, setNumber = i + 1, weight = st.weight, reps = st.reps,
                                isCompleted = st.isCompleted,
                                completedAt = st.completedAt?.takeIf { st.isCompleted },
                                notes = st.notes,
                            )
                        },
                    )
                }
            }
        }
        data.settings?.let { settingsStore.replace(it) }
        repository.ensureSeeded()
    }
}
