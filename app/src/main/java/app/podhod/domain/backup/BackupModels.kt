package app.podhod.domain.backup

import app.podhod.domain.AppSettings

/**
 * Снимок всех пользовательских данных. Это граница сериализации: JSON-файл, а в будущем и облачная
 * синхронизация, работают именно с этой моделью и не знают о Room.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val settings: AppSettings?,
    val exercises: List<BackupExercise>,
    val programs: List<BackupProgram>,
    val sessions: List<BackupSession>,
) {
    companion object {
        const val FORMAT = "podhody-backup"
        const val CURRENT_VERSION = 1
    }
}

data class BackupExercise(
    val id: Long,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val exerciseType: String,
    val equipment: String,
    val loadType: String,
    val technique: String?,
    val notes: String?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val builtInKey: String?,
    val createdAt: Long,
)

data class BackupProgramItem(
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeight: Double?,
    val restSeconds: Int?,
    val notes: String?,
)

data class BackupProgram(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<BackupProgramItem>,
)

data class BackupSet(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val notes: String?,
)

data class BackupWorkoutExercise(
    val exerciseId: Long,
    val position: Int,
    val notes: String?,
    val loadType: String,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val restSeconds: Int?,
    val sets: List<BackupSet>,
)

data class BackupSession(
    val id: Long,
    val programId: Long?,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val notes: String?,
    val exercises: List<BackupWorkoutExercise>,
)

class BackupFormatException(message: String) : Exception(message)
