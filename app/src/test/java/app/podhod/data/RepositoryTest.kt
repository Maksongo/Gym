package app.podhod.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.podhod.data.db.AppDatabase
import app.podhod.data.db.ExerciseEntity
import app.podhod.data.repo.BackupService
import app.podhod.data.repo.DeleteExerciseResult
import app.podhod.data.repo.ExerciseDraft
import app.podhod.data.repo.FinishResult
import app.podhod.data.repo.ProgramItemDraft
import app.podhod.data.repo.SaveExerciseResult
import app.podhod.data.repo.WorkoutRepository
import app.podhod.data.settings.SettingsStore
import app.podhod.domain.Equipment
import app.podhod.domain.ExerciseType
import app.podhod.domain.LoadType
import app.podhod.domain.MuscleGroup
import app.podhod.domain.SessionStatus
import app.podhod.domain.SetProblem
import app.podhod.domain.backup.BackupFormatException
import app.podhod.timer.RestTimerController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DAY = 86_400_000L

/** Сценарные тесты данных: настоящая Room-база в памяти (и в файле — для проверки «закрытия приложения»). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.inMemory(context)
        repo = WorkoutRepository(db) { now }
        runBlocking { repo.ensureSeeded() }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.FILE_NAME)
    }

    // ---------- вспомогательное ----------

    private fun ex(key: String): ExerciseEntity =
        runBlocking { db.exercises().all().first { it.builtInKey == key } }

    private fun draft(exerciseId: Long, sets: Int, reps: Int, weight: Double?) =
        ProgramItemDraft(exerciseId, sets, reps, reps, weight, null, null)

    private suspend fun completeAll(sessionId: Long) {
        val s = repo.getSession(sessionId)!!
        for (we in s.exercises) for (set in we.sets) assertNull(repo.setCompleted(set.id, true))
    }

    private suspend fun simpleProgram(): Long {
        val bench = ex("bench_press")
        val press = ex("overhead_press")
        return repo.saveProgram(null, "Тренировка А", listOf(draft(bench.id, 3, 8, 42.0), draft(press.id, 3, 8, 30.0)))
    }

    // ---------- 1. Программа ----------

    @Test
    fun createProgramAndAddExercises() = runBlocking<Unit> {
        val bench = ex("bench_press")
        val press = ex("overhead_press")
        val id = repo.saveProgram(null, "  Тренировка   А ", listOf(draft(bench.id, 3, 8, 42.0), draft(press.id, 3, 8, null)))

        val p = repo.getProgram(id)!!
        assertEquals("Тренировка А", p.program.name)
        val items = p.items.sortedBy { it.position }
        assertEquals(listOf(bench.id, press.id), items.map { it.exerciseId })
        assertEquals(42.0, items[0].targetWeight!!, 0.0)
        assertNull(items[1].targetWeight)

        // Редактирование: порядок меняется, упражнение удаляется.
        repo.saveProgram(id, "Тренировка А", listOf(draft(press.id, 4, 6, 30.0)))
        val edited = repo.getProgram(id)!!
        assertEquals(1, edited.items.size)
        assertEquals(4, edited.items[0].targetSets)
    }

    @Test
    fun duplicateProgramCopiesItems() = runBlocking<Unit> {
        val id = simpleProgram()
        val copy = repo.duplicateProgram(id)!!
        assertTrue(copy != id)
        assertEquals(2, repo.getProgram(copy)!!.items.size)
        assertEquals("Тренировка А (копия)", repo.getProgram(copy)!!.program.name)
    }

    // ---------- 2. Запуск тренировки ----------

    @Test
    fun startWorkoutFromProgramPrefillsWithoutCompleting() = runBlocking<Unit> {
        val programId = simpleProgram()
        val start = repo.startWorkout(programId)
        assertFalse(start.alreadyActive)

        val s = repo.getSession(start.sessionId)!!
        assertEquals(SessionStatus.IN_PROGRESS, s.session.status)
        assertEquals("Тренировка А", s.session.name)
        assertEquals(2, s.exercises.size)
        val bench = s.exercises.first { it.exercise.exerciseId == ex("bench_press").id }
        assertEquals(3, bench.sets.size)
        assertTrue(bench.sets.all { it.weight == 42.0 && it.reps == 8 })
        assertTrue("старые результаты не должны записываться как выполненные", bench.sets.none { it.isCompleted })
    }

    // ---------- 3. Подходы ----------

    @Test
    fun setsCanBeCompletedAndUndoneWithoutLosingValues() = runBlocking<Unit> {
        val start = repo.startWorkout(simpleProgram())
        val bench = repo.getSession(start.sessionId)!!.exercises.first { it.exercise.exerciseId == ex("bench_press").id }
        val set = bench.sets.first()

        repo.updateSetValues(set.id, 45.5, 7)
        assertNull(repo.setCompleted(set.id, true))
        var cur = db.sessions().getSet(set.id)!!
        assertTrue(cur.isCompleted)
        assertNotNull(cur.completedAt)

        assertNull(repo.setCompleted(set.id, false))
        cur = db.sessions().getSet(set.id)!!
        assertFalse(cur.isCompleted)
        assertNull(cur.completedAt)
        assertEquals(45.5, cur.weight, 0.0)
        assertEquals(7, cur.reps)

        // Добавление и удаление подхода сохраняет нумерацию.
        repo.addSet(bench.exercise.id)
        assertEquals(4, db.sessions().setsOf(bench.exercise.id).size)
        repo.deleteSet(set.id)
        assertEquals(listOf(1, 2, 3), db.sessions().setsOf(bench.exercise.id).map { it.setNumber })
    }

    @Test
    fun invalidSetCannotBeCompleted() = runBlocking<Unit> {
        val start = repo.startWorkout(null)
        val weId = repo.addExercise(start.sessionId, ex("bench_press").id)!!
        val set = db.sessions().setsOf(weId).first()
        assertEquals(SetProblem.NO_REPS, repo.setCompleted(set.id, true))

        repo.updateSetValues(set.id, 0.0, 8)
        assertEquals(SetProblem.NO_WEIGHT, repo.setCompleted(set.id, true))
        assertFalse(db.sessions().getSet(set.id)!!.isCompleted)
    }

    @Test
    fun negativeValuesAreClamped() = runBlocking<Unit> {
        val start = repo.startWorkout(simpleProgram())
        val set = repo.getSession(start.sessionId)!!.exercises.first().sets.first()
        repo.updateSetValues(set.id, -5.0, -3)
        val cur = db.sessions().getSet(set.id)!!
        assertEquals(0.0, cur.weight, 0.0)
        assertEquals(0, cur.reps)
    }

    // ---------- 4–5. Закрытие приложения и восстановление ----------

    @Test
    fun activeWorkoutSurvivesReopeningDatabase() = runBlocking<Unit> {
        context.deleteDatabase(AppDatabase.FILE_NAME)
        val db1 = AppDatabase.create(context)
        val repo1 = WorkoutRepository(db1) { now }
        repo1.ensureSeeded()
        val programId = repo1.saveProgram(
            null, "Тренировка А",
            listOf(draft(db1.exercises().all().first { it.builtInKey == "bench_press" }.id, 3, 8, 42.0)),
        )
        val sessionId = repo1.startWorkout(programId).sessionId
        val firstSet = repo1.getSession(sessionId)!!.exercises.first().sets.first()
        repo1.updateSetValues(firstSet.id, 44.0, 8)
        assertNull(repo1.setCompleted(firstSet.id, true))
        db1.close() // «приложение закрыли»

        val db2 = AppDatabase.create(context)
        try {
            val repo2 = WorkoutRepository(db2) { now }
            val active = repo2.observeActiveSession().first()
            assertNotNull("незавершённая тренировка должна восстановиться", active)
            assertEquals(sessionId, active!!.id)
            val restored = repo2.getSession(sessionId)!!.exercises.first().sets.first { it.id == firstSet.id }
            assertTrue(restored.isCompleted)
            assertEquals(44.0, restored.weight, 0.0)

            // Новая тренировка не затирает незавершённую.
            val again = repo2.startWorkout(null)
            assertTrue(again.alreadyActive)
            assertEquals(sessionId, again.sessionId)
        } finally {
            db2.close()
            context.deleteDatabase(AppDatabase.FILE_NAME)
        }
    }

    // ---------- 6. Предыдущий результат и автозаполнение ----------

    @Test
    fun nextWorkoutUsesPreviousResults() = runBlocking<Unit> {
        val programId = simpleProgram()
        val first = repo.startWorkout(programId).sessionId
        val bench1 = repo.getSession(first)!!.exercises.first { it.exercise.exerciseId == ex("bench_press").id }
        repo.updateSetValues(bench1.sets.sortedBy { it.setNumber }[2].id, 42.0, 6)
        now += 1_000
        completeAll(first)
        now += 3_600_000
        assertTrue(repo.finishWorkout(first) is FinishResult.Finished)

        now += DAY
        val second = repo.startWorkout(programId).sessionId
        val benchId = ex("bench_press").id
        val bench2 = repo.getSession(second)!!.exercises.first { it.exercise.exerciseId == benchId }
        assertEquals(listOf(8, 8, 6), bench2.sets.sortedBy { it.setNumber }.map { it.reps })
        assertTrue(bench2.sets.all { it.weight == 42.0 && !it.isCompleted })

        val session2 = repo.getSession(second)!!.session
        val prev = repo.recentResults(benchId, session2.startedAt, session2.id, 5)
        assertEquals(1, prev.size)
        assertEquals(listOf(8, 8, 6), prev[0].sets.map { it.reps })
        // Текущая (незавершённая) тренировка в «предыдущих» не участвует.
        assertTrue(repo.recentResults(benchId, session2.startedAt, session2.id, 5).none { it.startedAt == session2.startedAt })
    }

    @Test
    fun finishRemovesOnlyIncompleteSets() = runBlocking<Unit> {
        val start = repo.startWorkout(simpleProgram())
        val s = repo.getSession(start.sessionId)!!
        assertTrue(repo.finishWorkout(start.sessionId) is FinishResult.NothingCompleted)
        assertEquals(SessionStatus.IN_PROGRESS, db.sessions().session(start.sessionId)!!.status)

        val bench = s.exercises.first { it.exercise.exerciseId == ex("bench_press").id }
        assertNull(repo.setCompleted(bench.sets[0].id, true))
        assertNull(repo.setCompleted(bench.sets[1].id, true))
        now += 60_000
        val r = repo.finishWorkout(start.sessionId) as FinishResult.Finished
        assertEquals(2, r.completedSets)
        assertEquals(4, r.removedSets) // 1 подход жима лёжа + 3 подхода жима стоя
        assertEquals(1, r.removedExercises)
        val done = repo.getSession(start.sessionId)!!
        assertEquals(SessionStatus.FINISHED, done.session.status)
        assertEquals(1, done.exercises.size)
        assertEquals(2, done.exercises[0].sets.size)
    }

    // ---------- 7. Объём ----------

    @Test
    fun volumeCountsOnlyWeightedSets() = runBlocking<Unit> {
        val bench = ex("bench_press")
        val pullUp = ex("pull_up")
        val programId = repo.saveProgram(null, "Смешанная", listOf(draft(bench.id, 3, 8, 42.0), draft(pullUp.id, 3, 8, null)))
        val id = repo.startWorkout(programId).sessionId
        completeAll(id)
        now += 60_000
        repo.finishWorkout(id)

        val row = repo.observeSummaries().first().single()
        assertEquals(42.0 * 8 * 3, row.volume, 1e-9)
        assertEquals(3, row.skippedSets)
        assertEquals(6, row.setCount)
    }

    // ---------- 8. Правка завершённой тренировки ----------

    @Test
    fun editingFinishedWorkoutDoesNotTouchOthers() = runBlocking<Unit> {
        val programId = simpleProgram()
        val a = repo.startWorkout(programId).sessionId
        completeAll(a)
        now += 60_000
        repo.finishWorkout(a)
        now += DAY
        val b = repo.startWorkout(programId).sessionId
        completeAll(b)
        now += 60_000
        repo.finishWorkout(b)

        val before = repo.observeSummaries().first().associateBy { it.id }
        val benchA = repo.getSession(a)!!.exercises.first { it.exercise.exerciseId == ex("bench_press").id }
        repo.updateSetValues(benchA.sets[0].id, 50.0, 8)

        val after = repo.observeSummaries().first().associateBy { it.id }
        assertEquals(before[a]!!.volume + (50.0 - 42.0) * 8, after[a]!!.volume, 1e-9)
        assertEquals(before[b]!!.volume, after[b]!!.volume, 1e-9)
        // Выполненный подход после правки остался выполненным и в завершённой тренировке.
        assertTrue(db.sessions().getSet(benchA.sets[0].id)!!.isCompleted)
    }

    // ---------- 9. Удаление программы / упражнения ----------

    @Test
    fun deletingProgramKeepsHistory() = runBlocking<Unit> {
        val programId = simpleProgram()
        val id = repo.startWorkout(programId).sessionId
        completeAll(id)
        now += 60_000
        repo.finishWorkout(id)

        repo.deleteProgram(programId)

        assertNull(repo.getProgram(programId))
        val s = repo.getSession(id)!!
        assertNull(s.session.programId)
        assertEquals("Тренировка А", s.session.name)
        assertEquals(2, s.exercises.size)
        assertEquals(1, repo.observeSummaries().first().size)
        assertEquals(0, db.programs().all().size)
        assertEquals(0, db.query("SELECT COUNT(*) FROM program_items", null).use { c -> c.moveToFirst(); c.getInt(0) })
    }

    @Test
    fun deletingExerciseWithHistoryArchivesIt() = runBlocking<Unit> {
        val custom = (repo.saveExercise(
            ExerciseDraft(null, "Мой жим", MuscleGroup.CHEST, emptyList(), ExerciseType.COMPOUND, Equipment.MACHINE, LoadType.WEIGHTED, null, null),
        ) as SaveExerciseResult.Saved).id
        val unused = (repo.saveExercise(
            ExerciseDraft(null, "Неиспользуемое", MuscleGroup.ABS, emptyList(), ExerciseType.ISOLATION, Equipment.OTHER, LoadType.REPS_ONLY, null, null),
        ) as SaveExerciseResult.Saved).id

        val session = repo.startWorkout(null).sessionId
        val weId = repo.addExercise(session, custom)!!
        val set = db.sessions().setsOf(weId).first()
        repo.updateSetValues(set.id, 30.0, 10)
        repo.setCompleted(set.id, true)
        now += 60_000
        repo.finishWorkout(session)

        assertEquals(DeleteExerciseResult.ARCHIVED, repo.deleteExercise(custom))
        assertTrue(repo.getExercise(custom)!!.isArchived)
        assertEquals(DeleteExerciseResult.DELETED, repo.deleteExercise(unused))
        assertNull(repo.getExercise(unused))
        assertEquals(1, repo.observeStatSets(custom, 0L).first().size)
        // Встроенные упражнения только скрываются.
        assertEquals(DeleteExerciseResult.ARCHIVED, repo.deleteExercise(ex("bench_press").id))
    }

    // ---------- Библиотека упражнений ----------

    @Test
    fun exerciseNamesAreNotDuplicatedByTrivialDifferences() = runBlocking<Unit> {
        fun d(name: String) = ExerciseDraft(
            null, name, MuscleGroup.CHEST, emptyList(), ExerciseType.COMPOUND, Equipment.BARBELL, LoadType.WEIGHTED, null, null,
        )
        assertTrue(repo.saveExercise(d("жим  ШТАНГИ лежа.")) is SaveExerciseResult.Duplicate)
        assertTrue(repo.saveExercise(d("   ")) is SaveExerciseResult.EmptyName)
        assertTrue(repo.saveExercise(d("Жим Свенда")) is SaveExerciseResult.Saved)
        assertTrue(repo.saveExercise(d("жим свенда")) is SaveExerciseResult.Duplicate)
    }

    @Test
    fun renamingExerciseKeepsHistoryLinked() = runBlocking<Unit> {
        val bench = ex("bench_press")
        val session = repo.startWorkout(simpleProgram()).sessionId
        completeAll(session)
        now += 60_000
        repo.finishWorkout(session)

        val r = repo.saveExercise(
            ExerciseDraft(
                bench.id, "Жим лёжа (штанга)", bench.primaryMuscle, bench.secondaryMuscles, bench.exerciseType,
                bench.equipment, bench.loadType, bench.technique, bench.notes,
            ),
        )
        assertTrue(r is SaveExerciseResult.Saved)
        assertEquals("Жим лёжа (штанга)", repo.getExercise(bench.id)!!.name)
        assertEquals(3, repo.observeStatSets(bench.id, 0L).first().size)
    }

    @Test
    fun seededLibraryHasNoDuplicatesAndSeedingIsIdempotent() = runBlocking<Unit> {
        val before = db.exercises().all().size
        repo.ensureSeeded()
        repo.ensureSeeded()
        assertEquals(before, db.exercises().all().size)
        assertEquals(before, db.exercises().all().map { it.nameKey }.toSet().size)
    }

    // ---------- 10. Экспорт и восстановление ----------

    @Test
    fun backupRoundTripRestoresEverything() = runBlocking<Unit> {
        val programId = simpleProgram()
        val id = repo.startWorkout(programId).sessionId
        completeAll(id)
        now += 60_000
        repo.finishWorkout(id)
        repo.setFavorite(ex("pull_up").id, true)

        val backup = BackupService(db, SettingsStore(context), repo) { now }
        val json = backup.exportJson()
        val csv = backup.exportCsv()
        assertTrue(csv.contains("Жим штанги лёжа"))

        // «Потеряли» всё и восстановили из файла.
        db.sessions().deleteAll()
        db.programs().deleteAll()
        assertEquals(0, repo.observeSummaries().first().size)

        val preview = backup.preview(json)
        assertEquals(1, preview.sessions)
        assertEquals(1, preview.programs)
        backup.restore(preview.data)

        assertEquals(1, db.programs().all().size)
        val restored = repo.observeSummaries().first().single()
        assertEquals(42.0 * 8 * 3 + 30.0 * 8 * 3, restored.volume, 1e-9)
        assertTrue(repo.getExercise(ex("pull_up").id)!!.isFavorite)
        assertEquals("Тренировка А", repo.getSession(restored.id)!!.session.name)
    }

    @Test
    fun corruptedBackupIsRejectedAndNothingChanges() = runBlocking<Unit> {
        simpleProgram()
        val backup = BackupService(db, SettingsStore(context), repo) { now }
        for (bad in listOf("", "{не json", "[]", "{\"format\":\"other\"}")) {
            try {
                backup.preview(bad)
                fail("файл должен быть отвергнут: $bad")
            } catch (e: BackupFormatException) {
                // ожидаемо
            }
        }
        assertEquals(1, db.programs().all().size)
    }

    // ---------- 11. Таймер отдыха ----------

    @Test
    fun restTimerSurvivesRecreationAndCountsByAbsoluteTime() {
        val t0 = System.currentTimeMillis()
        val c1 = RestTimerController(context)
        c1.start(90, vibrate = false, now = t0)

        // «Приложение свернули/убили»: новый контроллер читает то же состояние.
        val c2 = RestTimerController(context)
        val state = c2.state.value
        assertNotNull(state)
        assertEquals(60_000L, state!!.remainingMillis(t0 + 30_000))
        assertFalse(state.isExpired(t0 + 89_000))
        assertTrue(state.isExpired(t0 + 90_000))

        c2.adjust(15, now = t0 + 30_000)
        assertEquals(75_000L, c2.state.value!!.remainingMillis(t0 + 30_000))

        c2.stop()
        assertNull(RestTimerController(context).state.value)
    }
}
