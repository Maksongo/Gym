package app.podhod.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE nameKey = :key LIMIT 1")
    suspend fun findByKey(key: String): ExerciseEntity?

    @Query("SELECT builtInKey FROM exercises WHERE builtInKey IS NOT NULL")
    suspend fun builtInKeys(): List<String>

    @Insert
    suspend fun insert(e: ExerciseEntity): Long

    @Insert
    suspend fun insertAll(list: List<ExerciseEntity>)

    /** Игнорирует конфликт по уникальному ключу (название или builtInKey уже занято). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(e: ExerciseEntity): Long

    @Update
    suspend fun update(e: ExerciseEntity)

    @Query("UPDATE exercises SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE exercises SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE exerciseId = :id")
    suspend fun usageInHistory(id: Long): Int
}

@Dao
interface ProgramDao {
    @Transaction
    @Query("SELECT * FROM programs ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ProgramWithItems>>

    @Transaction
    @Query("SELECT * FROM programs ORDER BY id")
    suspend fun all(): List<ProgramWithItems>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun get(id: Long): ProgramWithItems?

    /** Программа последней тренировки, которая была запущена по программе и программа ещё существует. */
    @Query(
        "SELECT p.* FROM programs p JOIN sessions s ON s.programId = p.id " +
            "ORDER BY s.startedAt DESC, s.id DESC LIMIT 1",
    )
    fun observeLastUsed(): Flow<ProgramEntity?>

    @Insert
    suspend fun insert(p: ProgramEntity): Long

    @Insert
    suspend fun insertAll(list: List<ProgramEntity>)

    @Update
    suspend fun update(p: ProgramEntity)

    @Insert
    suspend fun insertItems(items: List<ProgramItemEntity>)

    @Query("DELETE FROM program_items WHERE programId = :programId")
    suspend fun deleteItems(programId: Long)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    // ----- чтение -----

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun all(): List<SessionWithExercises>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC, id DESC LIMIT 1")
    suspend fun active(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query(
        "SELECT s.id AS id, s.name AS name, s.startedAt AS startedAt, s.finishedAt AS finishedAt, " +
            "COUNT(DISTINCT we.id) AS exerciseCount, " +
            "COALESCE(SUM(CASE WHEN ws.isCompleted = 1 THEN 1 ELSE 0 END), 0) AS setCount, " +
            "COALESCE(SUM(CASE WHEN ws.isCompleted = 1 AND we.loadType = 'WEIGHTED' AND ws.weight > 0 AND ws.reps > 0 " +
            "THEN ws.weight * ws.reps ELSE 0 END), 0) AS volume, " +
            "COALESCE(SUM(CASE WHEN ws.isCompleted = 1 AND we.loadType != 'WEIGHTED' AND ws.reps > 0 " +
            "THEN 1 ELSE 0 END), 0) AS skippedSets " +
            "FROM sessions s " +
            "LEFT JOIN workout_exercises we ON we.sessionId = s.id " +
            "LEFT JOIN workout_sets ws ON ws.workoutExerciseId = we.id " +
            "WHERE s.status = 'FINISHED' " +
            "GROUP BY s.id ORDER BY s.startedAt DESC, s.id DESC",
    )
    fun observeSummaries(): Flow<List<SessionSummaryRow>>

    @Query(
        "SELECT s.id AS sessionId, s.startedAt AS startedAt, we.exerciseId AS exerciseId, " +
            "we.loadType AS loadType, ws.weight AS weight, ws.reps AS reps " +
            "FROM workout_sets ws " +
            "JOIN workout_exercises we ON we.id = ws.workoutExerciseId " +
            "JOIN sessions s ON s.id = we.sessionId " +
            "WHERE s.status = 'FINISHED' AND ws.isCompleted = 1 AND ws.reps > 0 " +
            "AND s.startedAt >= :from AND (:exerciseId IS NULL OR we.exerciseId = :exerciseId) " +
            "ORDER BY s.startedAt, s.id, we.position, ws.setNumber",
    )
    fun observeStatRows(exerciseId: Long?, from: Long): Flow<List<StatRow>>

    @Query(
        "SELECT DISTINCT we.exerciseId FROM workout_exercises we " +
            "JOIN sessions s ON s.id = we.sessionId " +
            "JOIN workout_sets ws ON ws.workoutExerciseId = we.id " +
            "WHERE s.status = 'FINISHED' AND ws.isCompleted = 1 AND ws.reps > 0",
    )
    fun observeExerciseIdsWithHistory(): Flow<List<Long>>

    /**
     * Прошлые тренировки с этим упражнением (только завершённые, с выполненными подходами),
     * строго ДО указанной тренировки. Свежие первыми.
     */
    @Query(
        "SELECT we.*, s.startedAt AS sessionStartedAt FROM workout_exercises we " +
            "JOIN sessions s ON s.id = we.sessionId " +
            "WHERE we.exerciseId = :exerciseId AND s.status = 'FINISHED' " +
            "AND (s.startedAt < :beforeStartedAt OR (s.startedAt = :beforeStartedAt AND s.id < :beforeId)) " +
            "AND EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.workoutExerciseId = we.id " +
            "AND ws.isCompleted = 1 AND ws.reps > 0) " +
            "ORDER BY s.startedAt DESC, s.id DESC LIMIT :limit",
    )
    suspend fun recentExercises(
        exerciseId: Long,
        beforeStartedAt: Long,
        beforeId: Long,
        limit: Int,
    ): List<RecentExerciseRow>

    @Query(
        "SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId " +
            "AND isCompleted = 1 AND reps > 0 ORDER BY setNumber",
    )
    suspend fun completedSets(workoutExerciseId: Long): List<SetEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber")
    suspend fun setsOf(workoutExerciseId: Long): List<SetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: Long): SetEntity?

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getWorkoutExercise(id: Long): WorkoutExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE sessionId = :sessionId")
    suspend fun maxPosition(sessionId: Long): Int

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun maxSetNumber(workoutExerciseId: Long): Int

    // ----- запись -----

    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Insert
    suspend fun insertSessions(list: List<SessionEntity>)

    @Update
    suspend fun updateSession(s: SessionEntity)

    @Insert
    suspend fun insertWorkoutExercise(we: WorkoutExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntity>)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateSet(set: SetEntity)

    @Query("UPDATE workout_exercises SET notes = :notes WHERE id = :id")
    suspend fun updateExerciseNotes(id: Long, notes: String?)

    @Query("UPDATE workout_sets SET weight = :weight WHERE workoutExerciseId = :workoutExerciseId AND isCompleted = 0")
    suspend fun setWeightOfPendingSets(workoutExerciseId: Long, weight: Double)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE workout_sets SET setNumber = setNumber - 1 WHERE workoutExerciseId = :workoutExerciseId AND setNumber > :number")
    suspend fun shiftSetNumbersDown(workoutExerciseId: Long, number: Int)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query(
        "DELETE FROM workout_sets WHERE isCompleted = 0 AND workoutExerciseId IN " +
            "(SELECT id FROM workout_exercises WHERE sessionId = :sessionId)",
    )
    suspend fun deleteIncompleteSets(sessionId: Long): Int

    @Query(
        "DELETE FROM workout_exercises WHERE sessionId = :sessionId AND " +
            "NOT EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.workoutExerciseId = workout_exercises.id)",
    )
    suspend fun deleteExercisesWithoutSets(sessionId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM workout_sets ws JOIN workout_exercises we ON we.id = ws.workoutExerciseId " +
            "WHERE we.sessionId = :sessionId AND ws.isCompleted = 1",
    )
    suspend fun completedSetCount(sessionId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM workout_sets ws JOIN workout_exercises we ON we.id = ws.workoutExerciseId " +
            "WHERE we.sessionId = :sessionId AND ws.isCompleted = 0",
    )
    suspend fun incompleteSetCount(sessionId: Long): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
