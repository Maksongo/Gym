package app.podhod.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.podhod.domain.Equipment
import app.podhod.domain.ExerciseType
import app.podhod.domain.LoadType
import app.podhod.domain.MuscleGroup
import app.podhod.domain.SessionStatus

/**
 * Упражнение библиотеки. [nameKey] — нормализованное название (без регистра, ё = е, без пунктуации):
 * уникальный индекс не даёт создать дубликат из-за незначительных различий в написании.
 * Упражнения с историей не удаляются, а архивируются ([isArchived]).
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["nameKey"], unique = true),
        Index(value = ["builtInKey"], unique = true),
    ],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val loadType: LoadType,
    val technique: String?,
    val notes: String?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val builtInKey: String?,
    val createdAt: Long,
)

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "program_items",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programId"), Index("exerciseId")],
)
data class ProgramItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    /** Всегда в кг; null — вес не задан. */
    val targetWeight: Double?,
    val restSeconds: Int?,
    val notes: String?,
)

/**
 * Тренировка. При удалении программы [programId] обнуляется (SET_NULL): история остаётся,
 * название программы на момент тренировки сохранено в [name].
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("programId"), Index("status"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long?,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: SessionStatus,
    val notes: String?,
)

/**
 * Упражнение в тренировке. Цели ([targetSets], [targetRepsMin], [targetRepsMax]), способ учёта
 * нагрузки и пауза копируются сюда на момент старта: изменение программы историю не меняет.
 * Удалить упражнение, на которое ссылается тренировка, нельзя (RESTRICT) — только архивировать.
 */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String?,
    val loadType: LoadType,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val restSeconds: Int?,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    /** Всегда в кг. */
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val notes: String?,
)

// ---------- Модели чтения (не таблицы) ----------

data class ProgramWithItems(
    @Embedded val program: ProgramEntity,
    @Relation(parentColumn = "id", entityColumn = "programId")
    val items: List<ProgramItemEntity>,
)

data class WorkoutExerciseWithSets(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntity>,
)

data class SessionWithExercises(
    @Embedded val session: SessionEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<WorkoutExerciseWithSets>,
)

/** Строка журнала: итоги тренировки одним запросом. */
data class SessionSummaryRow(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volume: Double,
    /** Выполненные подходы упражнений со своим весом / без веса: в объём не входят. */
    val skippedSets: Int,
)

/** Один выполненный подход завершённой тренировки (для статистики). */
data class StatRow(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: Long,
    val loadType: LoadType,
    val weight: Double,
    val reps: Int,
)

data class RecentExerciseRow(
    @Embedded val we: WorkoutExerciseEntity,
    @ColumnInfo(name = "sessionStartedAt") val sessionStartedAt: Long,
)
