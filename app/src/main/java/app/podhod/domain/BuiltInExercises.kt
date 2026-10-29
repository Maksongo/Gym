package app.podhod.domain

import app.podhod.domain.Equipment.*
import app.podhod.domain.ExerciseType.*
import app.podhod.domain.MuscleGroup.*

private val WEIGHTED = LoadType.WEIGHTED
private val LT_BODYWEIGHT = LoadType.BODYWEIGHT
private val REPS_ONLY = LoadType.REPS_ONLY

data class BuiltInExercise(
    val key: String,
    val name: String,
    val primary: MuscleGroup,
    val secondary: List<MuscleGroup>,
    val type: ExerciseType,
    val equipment: Equipment,
    val loadType: LoadType = WEIGHTED,
    val technique: String? = null,
)

/** Начальная библиотека. [key] стабилен: переименование или архивация не приводят к повторной вставке. */
object BuiltInExercises {
    val ALL: List<BuiltInExercise> = listOf(
        // Грудь
        BuiltInExercise(
            "bench_press", "Жим штанги лёжа", CHEST, listOf(TRICEPS, SHOULDERS), COMPOUND, BARBELL,
            technique = "Лопатки сведены и прижаты к скамье, стопы упираются в пол. Опускайте штангу к нижней части груди, предплечья вертикально, затем выжимайте вверх.",
        ),
        BuiltInExercise(
            "dumbbell_press", "Жим гантелей", CHEST, listOf(TRICEPS, SHOULDERS), COMPOUND, DUMBBELL,
            technique = "Лёжа на скамье, опускайте гантели по сторонам груди с контролем и выжимайте вверх по небольшой дуге, не сталкивая гантели вверху.",
        ),
        BuiltInExercise("incline_bench_press", "Жим штанги на наклонной скамье", CHEST, listOf(SHOULDERS, TRICEPS), COMPOUND, BARBELL),
        BuiltInExercise("incline_dumbbell_press", "Жим гантелей на наклонной скамье", CHEST, listOf(SHOULDERS, TRICEPS), COMPOUND, DUMBBELL),
        BuiltInExercise("dumbbell_fly", "Разведения гантелей лёжа", CHEST, emptyList(), ISOLATION, DUMBBELL),
        BuiltInExercise("cable_crossover", "Сведение рук в кроссовере", CHEST, listOf(SHOULDERS), ISOLATION, CABLE),
        BuiltInExercise("chest_press_machine", "Жим в тренажёре на грудь", CHEST, listOf(TRICEPS, SHOULDERS), COMPOUND, MACHINE),
        BuiltInExercise("push_up", "Отжимания от пола", CHEST, listOf(TRICEPS, SHOULDERS), COMPOUND, BODYWEIGHT, LT_BODYWEIGHT),
        BuiltInExercise("dips", "Отжимания на брусьях", CHEST, listOf(TRICEPS, SHOULDERS), COMPOUND, BODYWEIGHT, LT_BODYWEIGHT),

        // Плечи
        BuiltInExercise(
            "overhead_press", "Жим штанги стоя", SHOULDERS, listOf(TRICEPS, ABS), COMPOUND, BARBELL,
            technique = "Штанга на уровне ключиц, корпус и ягодицы напряжены. Выжимайте штангу вверх, слегка убирая голову назад, и фиксируйте над головой.",
        ),
        BuiltInExercise("dumbbell_shoulder_press", "Жим гантелей сидя", SHOULDERS, listOf(TRICEPS), COMPOUND, DUMBBELL),
        BuiltInExercise(
            "lateral_raise", "Разведения гантелей в стороны", SHOULDERS, emptyList(), ISOLATION, DUMBBELL,
            technique = "Лёгкий наклон корпуса вперёд, локти слегка согнуты. Поднимайте гантели до уровня плеч, ведя локтями, без рывка и раскачки.",
        ),
        BuiltInExercise("front_raise", "Подъёмы гантелей перед собой", SHOULDERS, emptyList(), ISOLATION, DUMBBELL),
        BuiltInExercise("rear_delt_fly", "Разведения гантелей в наклоне", SHOULDERS, listOf(BACK), ISOLATION, DUMBBELL),
        BuiltInExercise("face_pull", "Тяга к лицу на блоке", SHOULDERS, listOf(BACK, TRAPS), ISOLATION, CABLE),
        BuiltInExercise("barbell_shrug", "Шраги со штангой", TRAPS, listOf(FOREARMS), ISOLATION, BARBELL),

        // Спина
        BuiltInExercise(
            "pull_up", "Подтягивания", BACK, listOf(BICEPS, FOREARMS), COMPOUND, BODYWEIGHT, LT_BODYWEIGHT,
            technique = "Хват чуть шире плеч. Начинайте движение со сведения лопаток, подтягивайтесь до подбородка над перекладиной, опускайтесь до полного выпрямления рук.",
        ),
        BuiltInExercise("chin_up", "Подтягивания обратным хватом", BACK, listOf(BICEPS), COMPOUND, BODYWEIGHT, LT_BODYWEIGHT),
        BuiltInExercise(
            "barbell_row", "Тяга штанги в наклоне", BACK, listOf(BICEPS, LOWER_BACK, TRAPS), COMPOUND, BARBELL,
            technique = "Корпус наклонён почти параллельно полу, спина ровная. Тяните штангу к поясу, сводя лопатки, и опускайте с контролем.",
        ),
        BuiltInExercise(
            "one_arm_dumbbell_row", "Тяга гантели одной рукой", BACK, listOf(BICEPS), COMPOUND, DUMBBELL,
            technique = "Упор свободной рукой и коленом в скамью, спина ровная. Тяните гантель к поясу, не разворачивая корпус.",
        ),
        BuiltInExercise("lat_pulldown", "Тяга верхнего блока к груди", BACK, listOf(BICEPS), COMPOUND, CABLE),
        BuiltInExercise("seated_cable_row", "Тяга нижнего блока сидя", BACK, listOf(BICEPS), COMPOUND, CABLE),
        BuiltInExercise("tbar_row", "Тяга Т-грифа", BACK, listOf(BICEPS, TRAPS), COMPOUND, BARBELL),
        BuiltInExercise("hyperextension", "Гиперэкстензия", LOWER_BACK, listOf(GLUTES, HAMSTRINGS), ISOLATION, BODYWEIGHT, LT_BODYWEIGHT),

        // Ноги
        BuiltInExercise(
            "squat", "Приседания со штангой", QUADS, listOf(GLUTES, HAMSTRINGS, LOWER_BACK), COMPOUND, BARBELL,
            technique = "Штанга на задней части трапеций, стопы на ширине плеч. Садитесь, отводя таз назад и держа колени по линии носков, до параллели бёдер с полом и выше, затем вставайте.",
        ),
        BuiltInExercise("front_squat", "Фронтальные приседания", QUADS, listOf(GLUTES, ABS), COMPOUND, BARBELL),
        BuiltInExercise("goblet_squat", "Гоблет-приседания", QUADS, listOf(GLUTES), COMPOUND, DUMBBELL),
        BuiltInExercise("leg_press", "Жим ногами", QUADS, listOf(GLUTES, HAMSTRINGS), COMPOUND, MACHINE),
        BuiltInExercise("lunge", "Выпады с гантелями", QUADS, listOf(GLUTES, HAMSTRINGS), COMPOUND, DUMBBELL),
        BuiltInExercise("bulgarian_split_squat", "Болгарские сплит-приседания", QUADS, listOf(GLUTES), COMPOUND, DUMBBELL),
        BuiltInExercise("leg_extension", "Разгибания ног в тренажёре", QUADS, emptyList(), ISOLATION, MACHINE),
        BuiltInExercise("leg_curl", "Сгибания ног в тренажёре", HAMSTRINGS, emptyList(), ISOLATION, MACHINE),
        BuiltInExercise(
            "deadlift", "Становая тяга", BACK, listOf(GLUTES, HAMSTRINGS, LOWER_BACK, TRAPS, FOREARMS), COMPOUND, BARBELL,
            technique = "Штанга над серединой стопы, спина нейтральная. Отталкивайтесь ногами от пола, держа штангу близко к ногам, и выпрямляйтесь, не переразгибаясь в пояснице.",
        ),
        BuiltInExercise(
            "romanian_deadlift", "Румынская тяга", HAMSTRINGS, listOf(GLUTES, LOWER_BACK), COMPOUND, BARBELL,
            technique = "Колени слегка согнуты и почти не меняют угол. Отводите таз назад, скользя штангой вдоль ног, до растяжения задней поверхности бёдер, затем возвращайтесь.",
        ),
        BuiltInExercise("hip_thrust", "Ягодичный мост со штангой", GLUTES, listOf(HAMSTRINGS), COMPOUND, BARBELL),
        BuiltInExercise("standing_calf_raise", "Подъёмы на носки стоя", CALVES, emptyList(), ISOLATION, MACHINE),
        BuiltInExercise("seated_calf_raise", "Подъёмы на носки сидя", CALVES, emptyList(), ISOLATION, MACHINE),

        // Руки
        BuiltInExercise(
            "barbell_curl", "Сгибания рук со штангой", BICEPS, listOf(FOREARMS), ISOLATION, BARBELL,
            technique = "Локти прижаты к корпусу и неподвижны. Сгибайте руки без раскачки корпуса, в верхней точке напрягайте бицепс, опускайте медленно.",
        ),
        BuiltInExercise("dumbbell_curl", "Сгибания рук с гантелями", BICEPS, listOf(FOREARMS), ISOLATION, DUMBBELL),
        BuiltInExercise(
            "hammer_curl", "Молотковые сгибания", BICEPS, listOf(FOREARMS), ISOLATION, DUMBBELL,
            technique = "Нейтральный хват, ладони смотрят друг на друга. Сгибайте руки поочерёдно или вместе, не отводя локти вперёд.",
        ),
        BuiltInExercise("preacher_curl", "Сгибания рук на скамье Скотта", BICEPS, emptyList(), ISOLATION, BARBELL),
        BuiltInExercise(
            "skull_crusher", "Французский жим", TRICEPS, emptyList(), ISOLATION, BARBELL,
            technique = "Лёжа на скамье, локти направлены вверх и не разъезжаются. Опускайте штангу ко лбу или за голову, сгибая только локти, и разгибайте руки.",
        ),
        BuiltInExercise(
            "triceps_pushdown", "Разгибания рук на блоке", TRICEPS, emptyList(), ISOLATION, CABLE,
            technique = "Локти прижаты к корпусу. Разгибайте руки вниз до полного выпрямления, возвращайтесь до угла в локтях около 90°.",
        ),
        BuiltInExercise("overhead_triceps_extension", "Разгибания руки с гантелью из-за головы", TRICEPS, emptyList(), ISOLATION, DUMBBELL),
        BuiltInExercise("close_grip_bench", "Жим штанги узким хватом", TRICEPS, listOf(CHEST, SHOULDERS), COMPOUND, BARBELL),
        BuiltInExercise("wrist_curl", "Сгибания запястий со штангой", FOREARMS, emptyList(), ISOLATION, BARBELL),

        // Пресс и кор
        BuiltInExercise(
            "calf_raise", "Подъёмы на носки", CALVES, emptyList(), ISOLATION, BODYWEIGHT, LT_BODYWEIGHT,
            technique = "Поднимайтесь на носки максимально высоко, задержитесь на секунду и медленно опускайтесь до растяжения икр. Можно использовать гантели или ступеньку.",
        ),
        BuiltInExercise(
            "crunch", "Скручивания на пресс", ABS, emptyList(), ISOLATION, BODYWEIGHT, REPS_ONLY,
            technique = "Поясница прижата к полу. Отрывайте от пола только плечи и верх спины, скручивая корпус, без рывков руками за голову.",
        ),
        BuiltInExercise("hanging_leg_raise", "Подъём ног в висе", ABS, listOf(FOREARMS), ISOLATION, BODYWEIGHT, REPS_ONLY),
        BuiltInExercise("cable_crunch", "Скручивания на блоке", ABS, emptyList(), ISOLATION, CABLE),
    )
}
