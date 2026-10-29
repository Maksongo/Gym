package app.podhod.domain.backup

import app.podhod.domain.AppSettings
import app.podhod.domain.InputRules
import app.podhod.domain.ThemeMode
import app.podhod.domain.WeightUnit
import app.podhod.domain.enumOrDefault
import app.podhod.domain.json.JsonException
import app.podhod.domain.json.JsonParser
import app.podhod.domain.json.JsonValue
import app.podhod.domain.json.JsonValue.Arr
import app.podhod.domain.json.JsonValue.Bool
import app.podhod.domain.json.JsonValue.Num
import app.podhod.domain.json.JsonValue.Obj
import app.podhod.domain.json.JsonValue.Str
import app.podhod.domain.json.JsonWriter

/** Кодирование и строгая проверка файла резервной копии. */
object BackupCodec {

    // ---------------------------------------------------------------- encode

    fun encode(data: BackupData, pretty: Boolean = true): String =
        JsonWriter.write(toJson(data), pretty)

    fun toJson(d: BackupData): JsonValue = obj(
        "format" to Str(BackupData.FORMAT),
        "version" to num(d.version),
        "exportedAt" to num(d.exportedAt),
        "settings" to (d.settings?.let(::settingsToJson) ?: JsonValue.Null),
        "exercises" to Arr(d.exercises.map(::exerciseToJson)),
        "programs" to Arr(d.programs.map(::programToJson)),
        "sessions" to Arr(d.sessions.map(::sessionToJson)),
    )

    private fun settingsToJson(s: AppSettings) = obj(
        "theme" to Str(s.theme.name),
        "unit" to Str(s.unit.name),
        "restSeconds" to num(s.restSeconds),
        "restEnabled" to Bool(s.restEnabled),
        "autoStartRest" to Bool(s.autoStartRest),
        "keepScreenOn" to Bool(s.keepScreenOn),
        "vibrate" to Bool(s.vibrate),
        "progressionEnabled" to Bool(s.progressionEnabled),
        "progressionStep" to Num(s.progressionStep),
        "progressionSessions" to num(s.progressionSessions),
    )

    private fun exerciseToJson(e: BackupExercise) = obj(
        "id" to num(e.id),
        "name" to Str(e.name),
        "primaryMuscle" to Str(e.primaryMuscle),
        "secondaryMuscles" to Arr(e.secondaryMuscles.map { Str(it) }),
        "exerciseType" to Str(e.exerciseType),
        "equipment" to Str(e.equipment),
        "loadType" to Str(e.loadType),
        "technique" to optStr(e.technique),
        "notes" to optStr(e.notes),
        "isCustom" to Bool(e.isCustom),
        "isFavorite" to Bool(e.isFavorite),
        "isArchived" to Bool(e.isArchived),
        "builtInKey" to optStr(e.builtInKey),
        "createdAt" to num(e.createdAt),
    )

    private fun programToJson(p: BackupProgram) = obj(
        "id" to num(p.id),
        "name" to Str(p.name),
        "createdAt" to num(p.createdAt),
        "updatedAt" to num(p.updatedAt),
        "exercises" to Arr(
            p.items.map {
                obj(
                    "exerciseId" to num(it.exerciseId),
                    "position" to num(it.position),
                    "targetSets" to num(it.targetSets),
                    "targetRepsMin" to optNum(it.targetRepsMin),
                    "targetRepsMax" to optNum(it.targetRepsMax),
                    "targetWeight" to (it.targetWeight?.let { w -> Num(w) } ?: JsonValue.Null),
                    "restSeconds" to optNum(it.restSeconds),
                    "notes" to optStr(it.notes),
                )
            },
        ),
    )

    private fun sessionToJson(s: BackupSession) = obj(
        "id" to num(s.id),
        "programId" to (s.programId?.let { num(it) } ?: JsonValue.Null),
        "name" to Str(s.name),
        "startedAt" to num(s.startedAt),
        "finishedAt" to (s.finishedAt?.let { num(it) } ?: JsonValue.Null),
        "status" to Str(s.status),
        "notes" to optStr(s.notes),
        "exercises" to Arr(
            s.exercises.map { we ->
                obj(
                    "exerciseId" to num(we.exerciseId),
                    "position" to num(we.position),
                    "notes" to optStr(we.notes),
                    "loadType" to Str(we.loadType),
                    "targetSets" to optNum(we.targetSets),
                    "targetRepsMin" to optNum(we.targetRepsMin),
                    "targetRepsMax" to optNum(we.targetRepsMax),
                    "restSeconds" to optNum(we.restSeconds),
                    "sets" to Arr(
                        we.sets.map { st ->
                            obj(
                                "setNumber" to num(st.setNumber),
                                "weight" to Num(st.weight),
                                "reps" to num(st.reps),
                                "isCompleted" to Bool(st.isCompleted),
                                "completedAt" to (st.completedAt?.let { num(it) } ?: JsonValue.Null),
                                "notes" to optStr(st.notes),
                            )
                        },
                    ),
                )
            },
        ),
    )

    private fun obj(vararg pairs: Pair<String, JsonValue>): Obj = Obj(linkedMapOf(*pairs))
    private fun num(v: Int): JsonValue = Num(v.toDouble())
    private fun num(v: Long): JsonValue = Num(v.toDouble())
    private fun optNum(v: Int?): JsonValue = if (v == null) JsonValue.Null else num(v)
    private fun optStr(v: String?): JsonValue = if (v == null) JsonValue.Null else Str(v)

    // ---------------------------------------------------------------- decode

    /** Разбирает и проверяет файл. Любая проблема — [BackupFormatException] с понятным текстом. */
    fun decode(text: String): BackupData {
        val root = try {
            JsonParser.parse(text)
        } catch (e: JsonException) {
            throw BackupFormatException("Файл повреждён или не является JSON. ${e.message}")
        } catch (e: StackOverflowError) {
            throw BackupFormatException("Файл повреждён: слишком сложная структура.")
        }
        return fromJson(root)
    }

    fun fromJson(root: JsonValue): BackupData {
        val r = root as? Obj ?: bad("Корень файла должен быть объектом.")
        if ((r["format"] as? Str)?.value != BackupData.FORMAT) {
            bad("Это не файл резервной копии приложения «Подходы».")
        }
        val version = r.int("version", "$")
        if (version < 1) bad("Неверная версия формата: $version.")
        if (version > BackupData.CURRENT_VERSION) {
            bad("Файл создан более новой версией приложения (формат $version). Обновите приложение.")
        }
        val exportedAt = r.optLong("exportedAt", "$") ?: 0L

        val exercises = r.arr("exercises", "$").mapIndexed { i, v -> decodeExercise(v, "exercises[$i]") }
        val exerciseIds = HashSet<Long>()
        exercises.forEach { if (!exerciseIds.add(it.id)) bad("exercises: повторяющийся id ${it.id}.") }

        val programs = r.arr("programs", "$").mapIndexed { i, v -> decodeProgram(v, "programs[$i]", exerciseIds) }
        val programIds = HashSet<Long>()
        programs.forEach { if (!programIds.add(it.id)) bad("programs: повторяющийся id ${it.id}.") }

        val sessions = r.arr("sessions", "$").mapIndexed { i, v -> decodeSession(v, "sessions[$i]", exerciseIds) }
        val sessionIds = HashSet<Long>()
        sessions.forEach { if (!sessionIds.add(it.id)) bad("sessions: повторяющийся id ${it.id}.") }
        if (sessions.count { it.status == "IN_PROGRESS" } > 1) {
            bad("sessions: в файле больше одной незавершённой тренировки.")
        }

        val settings = (r["settings"] as? Obj)?.let(::decodeSettings)
        return BackupData(version, exportedAt, settings, exercises, programs, sessions)
    }

    private fun decodeSettings(o: Obj): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOrDefault<ThemeMode>((o["theme"] as? Str)?.value, d.theme),
            unit = enumOrDefault<WeightUnit>((o["unit"] as? Str)?.value, d.unit),
            restSeconds = (o["restSeconds"] as? Num)?.value?.toInt()?.coerceIn(
                InputRules.MIN_REST_SECONDS, InputRules.MAX_REST_SECONDS,
            ) ?: d.restSeconds,
            restEnabled = (o["restEnabled"] as? Bool)?.value ?: d.restEnabled,
            autoStartRest = (o["autoStartRest"] as? Bool)?.value ?: d.autoStartRest,
            keepScreenOn = (o["keepScreenOn"] as? Bool)?.value ?: d.keepScreenOn,
            vibrate = (o["vibrate"] as? Bool)?.value ?: d.vibrate,
            progressionEnabled = (o["progressionEnabled"] as? Bool)?.value ?: d.progressionEnabled,
            progressionStep = (o["progressionStep"] as? Num)?.value?.takeIf { it > 0 && it <= 100 } ?: d.progressionStep,
            progressionSessions = (o["progressionSessions"] as? Num)?.value?.toInt()?.coerceIn(1, 10)
                ?: d.progressionSessions,
        )
    }

    private fun decodeExercise(v: JsonValue, path: String): BackupExercise {
        val o = v.asObj(path)
        val name = o.str("name", path)
        if (name.isBlank()) bad("$path: пустое название упражнения.")
        return BackupExercise(
            id = o.long("id", path),
            name = name,
            primaryMuscle = o.optStr("primaryMuscle", path) ?: "FULL_BODY",
            secondaryMuscles = (o["secondaryMuscles"] as? Arr)?.items?.mapNotNull { (it as? Str)?.value } ?: emptyList(),
            exerciseType = o.optStr("exerciseType", path) ?: "COMPOUND",
            equipment = o.optStr("equipment", path) ?: "OTHER",
            loadType = o.optStr("loadType", path) ?: "WEIGHTED",
            technique = o.optStr("technique", path),
            notes = o.optStr("notes", path),
            isCustom = o.optBool("isCustom", path) ?: true,
            isFavorite = o.optBool("isFavorite", path) ?: false,
            isArchived = o.optBool("isArchived", path) ?: false,
            builtInKey = o.optStr("builtInKey", path),
            createdAt = o.optLong("createdAt", path) ?: 0L,
        )
    }

    private fun decodeProgram(v: JsonValue, path: String, exerciseIds: Set<Long>): BackupProgram {
        val o = v.asObj(path)
        val name = o.str("name", path)
        if (name.isBlank()) bad("$path: пустое название программы.")
        val items = o.arr("exercises", path).mapIndexed { i, iv ->
            val ip = "$path.exercises[$i]"
            val io = iv.asObj(ip)
            val exId = io.long("exerciseId", ip)
            if (exId !in exerciseIds) bad("$ip: упражнение $exId отсутствует в файле.")
            val sets = io.int("targetSets", ip)
            if (sets !in 1..InputRules.MAX_SETS) bad("$ip: неверное число подходов ($sets).")
            val min = io.optInt("targetRepsMin", ip)
            val max = io.optInt("targetRepsMax", ip)
            checkReps(min, ip)
            checkReps(max, ip)
            if (min != null && max != null && min > max) bad("$ip: минимум повторений больше максимума.")
            val weight = io.optDouble("targetWeight", ip)
            if (weight != null) checkWeight(weight, ip)
            BackupProgramItem(
                exerciseId = exId,
                position = io.optInt("position", ip) ?: i,
                targetSets = sets,
                targetRepsMin = min,
                targetRepsMax = max,
                targetWeight = weight,
                restSeconds = io.optInt("restSeconds", ip)?.also {
                    if (it < 0 || it > InputRules.MAX_REST_SECONDS) bad("$ip: неверное время отдыха.")
                },
                notes = io.optStr("notes", ip),
            )
        }
        return BackupProgram(
            id = o.long("id", path),
            name = name,
            createdAt = o.optLong("createdAt", path) ?: 0L,
            updatedAt = o.optLong("updatedAt", path) ?: 0L,
            items = items,
        )
    }

    private fun decodeSession(v: JsonValue, path: String, exerciseIds: Set<Long>): BackupSession {
        val o = v.asObj(path)
        val startedAt = o.long("startedAt", path)
        if (startedAt <= 0) bad("$path: неверное время начала тренировки.")
        val finishedAt = o.optLong("finishedAt", path)
        val status = o.str("status", path)
        when (status) {
            "FINISHED" -> if (finishedAt == null) bad("$path: у завершённой тренировки нет времени окончания.")
            "IN_PROGRESS" -> if (finishedAt != null) bad("$path: у незавершённой тренировки задано время окончания.")
            else -> bad("$path: неизвестный статус «$status».")
        }
        if (finishedAt != null && finishedAt < startedAt) bad("$path: окончание раньше начала.")

        val exercises = o.arr("exercises", path).mapIndexed { i, ev ->
            val ep = "$path.exercises[$i]"
            val eo = ev.asObj(ep)
            val exId = eo.long("exerciseId", ep)
            if (exId !in exerciseIds) bad("$ep: упражнение $exId отсутствует в файле.")
            val min = eo.optInt("targetRepsMin", ep)
            val max = eo.optInt("targetRepsMax", ep)
            checkReps(min, ep)
            checkReps(max, ep)
            val sets = eo.arr("sets", ep).mapIndexed { si, sv ->
                val sp = "$ep.sets[$si]"
                val so = sv.asObj(sp)
                val weight = so.double("weight", sp)
                checkWeight(weight, sp)
                val reps = so.int("reps", sp)
                if (reps < 0 || reps > 10_000) bad("$sp: неверное число повторений ($reps).")
                val number = so.optInt("setNumber", sp) ?: (si + 1)
                if (number < 1) bad("$sp: номер подхода должен быть положительным.")
                BackupSet(
                    setNumber = number,
                    weight = weight,
                    reps = reps,
                    isCompleted = so.optBool("isCompleted", sp) ?: false,
                    completedAt = so.optLong("completedAt", sp),
                    notes = so.optStr("notes", sp),
                )
            }
            BackupWorkoutExercise(
                exerciseId = exId,
                position = eo.optInt("position", ep) ?: i,
                notes = eo.optStr("notes", ep),
                loadType = eo.optStr("loadType", ep) ?: "WEIGHTED",
                targetSets = eo.optInt("targetSets", ep),
                targetRepsMin = min,
                targetRepsMax = max,
                restSeconds = eo.optInt("restSeconds", ep),
                sets = sets,
            )
        }
        return BackupSession(
            id = o.long("id", path),
            programId = o.optLong("programId", path),
            name = o.optStr("name", path) ?: "Тренировка",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status,
            notes = o.optStr("notes", path),
            exercises = exercises,
        )
    }

    private fun checkReps(v: Int?, path: String) {
        if (v != null && (v < 0 || v > 10_000)) bad("$path: неверное число повторений ($v).")
    }

    private fun checkWeight(v: Double, path: String) {
        if (!v.isFinite() || v < 0.0 || v > 100_000.0) bad("$path: неверный вес ($v).")
    }

    // ------------------------------------------------------- typed accessors

    private fun bad(msg: String): Nothing = throw BackupFormatException(msg)

    private fun JsonValue.asObj(path: String): Obj = this as? Obj ?: bad("$path: ожидался объект.")

    private fun Obj.arr(key: String, path: String): List<JsonValue> {
        val v = this[key] ?: bad("$path: отсутствует поле «$key».")
        return (v as? Arr)?.items ?: bad("$path.$key: ожидался массив.")
    }

    private fun Obj.str(key: String, path: String): String {
        val v = this[key] ?: bad("$path: отсутствует поле «$key».")
        return (v as? Str)?.value ?: bad("$path.$key: ожидалась строка.")
    }

    private fun Obj.optStr(key: String, path: String): String? = when (val v = this[key]) {
        null, JsonValue.Null -> null
        is Str -> v.value
        else -> bad("$path.$key: ожидалась строка.")
    }

    private fun Obj.optBool(key: String, path: String): Boolean? = when (val v = this[key]) {
        null, JsonValue.Null -> null
        is Bool -> v.value
        else -> bad("$path.$key: ожидалось true/false.")
    }

    private fun Obj.double(key: String, path: String): Double {
        val v = this[key] ?: bad("$path: отсутствует поле «$key».")
        return (v as? Num)?.value ?: bad("$path.$key: ожидалось число.")
    }

    private fun Obj.optDouble(key: String, path: String): Double? = when (val v = this[key]) {
        null, JsonValue.Null -> null
        is Num -> v.value
        else -> bad("$path.$key: ожидалось число.")
    }

    private fun integral(d: Double, key: String, path: String): Long {
        if (d != Math.rint(d) || Math.abs(d) > 9.0e15) bad("$path.$key: ожидалось целое число.")
        return d.toLong()
    }

    private fun Obj.long(key: String, path: String): Long = integral(double(key, path), key, path)

    private fun Obj.optLong(key: String, path: String): Long? = optDouble(key, path)?.let { integral(it, key, path) }

    private fun Obj.int(key: String, path: String): Int {
        val l = long(key, path)
        if (l < Int.MIN_VALUE || l > Int.MAX_VALUE) bad("$path.$key: число вне диапазона.")
        return l.toInt()
    }

    private fun Obj.optInt(key: String, path: String): Int? {
        val l = optLong(key, path) ?: return null
        if (l < Int.MIN_VALUE || l > Int.MAX_VALUE) bad("$path.$key: число вне диапазона.")
        return l.toInt()
    }
}
