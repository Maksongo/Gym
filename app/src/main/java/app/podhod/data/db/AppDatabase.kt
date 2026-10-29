package app.podhod.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import app.podhod.domain.MuscleGroup

class Converters {
    @TypeConverter
    fun musclesToString(list: List<MuscleGroup>): String = list.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String): List<MuscleGroup> =
        value.split(',').mapNotNull { n -> MuscleGroup.values().firstOrNull { it.name == n } }
}

/**
 * Версия схемы 1. Схема экспортируется в app/schemas; для каждого следующего изменения добавляется
 * Migration в [MIGRATIONS] (destructive-миграции намеренно не используются — данные пользователя
 * нельзя терять).
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ProgramEntity::class,
        ProgramItemEntity::class,
        SessionEntity::class,
        WorkoutExerciseEntity::class,
        SetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun programs(): ProgramDao
    abstract fun sessions(): SessionDao

    companion object {
        const val FILE_NAME = "podhody.db"

        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /** Для тестов. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
