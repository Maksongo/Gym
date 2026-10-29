package app.podhod.domain

/** Безопасное чтение enum из строки (из БД или файла импорта). */
inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

enum class MuscleGroup(val title: String) {
    CHEST("Грудь"),
    BACK("Спина"),
    SHOULDERS("Плечи"),
    BICEPS("Бицепс"),
    TRICEPS("Трицепс"),
    FOREARMS("Предплечья"),
    TRAPS("Трапеции"),
    LOWER_BACK("Поясница"),
    QUADS("Квадрицепсы"),
    HAMSTRINGS("Бицепс бедра"),
    GLUTES("Ягодицы"),
    CALVES("Икры"),
    ABS("Пресс"),
    FULL_BODY("Всё тело"),
}

enum class Equipment(val title: String) {
    BARBELL("Штанга"),
    DUMBBELL("Гантели"),
    MACHINE("Тренажёр"),
    CABLE("Блок"),
    BODYWEIGHT("Свой вес"),
    KETTLEBELL("Гиря"),
    BAND("Резина"),
    OTHER("Другое"),
}

enum class ExerciseType(val title: String) {
    COMPOUND("Базовое"),
    ISOLATION("Изолирующее"),
}

/**
 * Способ учёта нагрузки. От него зависит, считается ли подход в тренировочный объём:
 * объём = вес × повторения считается только для WEIGHTED.
 */
enum class LoadType(val title: String, val hint: String) {
    WEIGHTED("Вес × повторения", "Штанга, гантели, тренажёры"),
    BODYWEIGHT("Свой вес + отягощение", "Подтягивания, отжимания; доп. вес необязателен"),
    REPS_ONLY("Только повторения", "Вес не используется"),
}

enum class SessionStatus { IN_PROGRESS, FINISHED }
