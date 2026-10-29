package app.podhod.domain

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
import app.podhod.domain.json.JsonException
import app.podhod.domain.json.JsonParser
import app.podhod.domain.json.JsonValue
import app.podhod.domain.json.JsonWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun expectBadBackup(text: String, contains: String? = null) {
    try {
        BackupCodec.decode(text)
        fail("Ожидалась BackupFormatException")
    } catch (e: BackupFormatException) {
        if (contains != null) assertTrue("Сообщение «${e.message}» не содержит «$contains»", e.message!!.contains(contains))
    }
}

class JsonTest {
    @Test
    fun `разбор и запись туда и обратно`() {
        val src = """{"a":1,"b":[true,false,null,"x\n\"y\" \u0041 ё"],"c":{"d":-2.5e2},"e":[]}"""
        val v = JsonParser.parse(src)
        val again = JsonParser.parse(JsonWriter.write(v))
        assertEquals(v, again)
        val pretty = JsonParser.parse(JsonWriter.write(v, pretty = true))
        assertEquals(v, pretty)
        val obj = v as JsonValue.Obj
        assertEquals(JsonValue.Num(1.0), obj["a"])
        assertEquals(JsonValue.Num(-250.0), (obj["c"] as JsonValue.Obj)["d"])
    }

    @Test
    fun `большие метки времени не теряют точность`() {
        val ms = 1_793_000_123_456L
        val text = JsonWriter.write(JsonValue.Num(ms.toDouble()))
        assertEquals("1793000123456", text)
    }

    @Test
    fun `BOM в начале файла допускается`() {
        assertEquals(JsonValue.Num(1.0), JsonParser.parse("\uFEFF 1 "))
    }

    @Test
    fun `повреждённый JSON даёт JsonException`() {
        val broken = listOf(
            "", "{", "[1,2", "{\"a\":}", "{\"a\" 1}", "{'a':1}", "[1,]x", "nul", "\"abc", "01", "1.", "{\"a\":1} лишнее",
            "\"\\x\"", "[1 2]", "\"\\u12\"",
        )
        for (b in broken) {
            try {
                JsonParser.parse(b)
                fail("Должна быть ошибка для: $b")
            } catch (e: JsonException) {
                // ожидаемо
            }
        }
    }

    @Test
    fun `слишком глубокая вложенность не роняет приложение`() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        try {
            JsonParser.parse(deep)
            fail("ожидалась ошибка")
        } catch (e: JsonException) {
            assertTrue(e.message!!.contains("вложенность"))
        }
    }
}

object BackupFixtures {
    fun sample(): BackupData {
        val exercises = listOf(
            BackupExercise(
                1, "Жим штанги лёжа", "CHEST", listOf("TRICEPS", "SHOULDERS"), "COMPOUND", "BARBELL", "WEIGHTED",
                "Техника \"с кавычками\"\nи переводом строки", null, false, true, false, "bench_press", 1000,
            ),
            BackupExercise(2, "Мой «особый» подтяг", "BACK", emptyList(), "COMPOUND", "BODYWEIGHT", "BODYWEIGHT", null, "заметка", true, false, false, null, 2000),
        )
        val programs = listOf(
            BackupProgram(
                10, "Тренировка А", 3000, 4000,
                listOf(
                    BackupProgramItem(1, 0, 3, 8, 12, 42.5, 90, "медленно"),
                    BackupProgramItem(2, 1, 3, null, null, null, null, null),
                ),
            ),
        )
        val sessions = listOf(
            BackupSession(
                100, 10, "Тренировка А", 1_793_000_000_000, 1_793_003_600_000, "FINISHED", "хорошо",
                listOf(
                    BackupWorkoutExercise(
                        1, 0, null, "WEIGHTED", 3, 8, 12, 90,
                        listOf(
                            BackupSet(1, 42.5, 8, true, 1_793_000_100_000, null),
                            BackupSet(2, 42.5, 8, true, 1_793_000_200_000, "тяжело"),
                            BackupSet(3, 42.5, 6, false, null, null),
                        ),
                    ),
                    BackupWorkoutExercise(2, 1, "n", "BODYWEIGHT", null, null, null, null, listOf(BackupSet(1, 0.0, 10, true, 1_793_000_300_000, null))),
                ),
            ),
        )
        return BackupData(
            exportedAt = 1_793_100_000_000,
            settings = AppSettings(theme = ThemeMode.DARK, unit = WeightUnit.LB, restSeconds = 120, progressionStep = 5.0),
            exercises = exercises, programs = programs, sessions = sessions,
        )
    }
}

/** Сценарий 10: экспорт и восстановление резервной копии; обработка повреждённых файлов. */
class BackupCodecTest {
    @Test
    fun `экспорт и импорт возвращают те же данные`() {
        val original = BackupFixtures.sample()
        val text = BackupCodec.encode(original)
        val restored = BackupCodec.decode(text)
        assertEquals(original, restored)
    }

    @Test
    fun `компактная запись тоже читается`() {
        val original = BackupFixtures.sample()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original, pretty = false)))
    }

    @Test
    fun `настройки входят в копию`() {
        val s = BackupCodec.decode(BackupCodec.encode(BackupFixtures.sample())).settings!!
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(WeightUnit.LB, s.unit)
        assertEquals(120, s.restSeconds)
        assertEquals(5.0, s.progressionStep, 0.0)
    }

    @Test
    fun `файл без настроек допускается`() {
        val data = BackupFixtures.sample().copy(settings = null)
        assertNull(BackupCodec.decode(BackupCodec.encode(data)).settings)
    }

    @Test
    fun `мусор вместо файла`() {
        expectBadBackup("", "повреждён")
        expectBadBackup("это не json", "повреждён")
        expectBadBackup("[1,2,3]", "объектом")
        expectBadBackup("{\"a\":1}", "резервной копии")
    }

    @Test
    fun `обрезанный файл`() {
        val text = BackupCodec.encode(BackupFixtures.sample())
        expectBadBackup(text.substring(0, text.length / 2), "повреждён")
    }

    @Test
    fun `файл более новой версии`() {
        val text = BackupCodec.encode(BackupFixtures.sample()).replace("\"version\": 1", "\"version\": 99")
        expectBadBackup(text, "новой версией")
    }

    @Test
    fun `неверная структура`() {
        val base = BackupCodec.encode(BackupFixtures.sample())
        expectBadBackup(base.replace("\"exercises\": [", "\"exercisez\": ["), "exercises")
        expectBadBackup(base.replace("\"weight\": 42.5", "\"weight\": -42.5"), "вес")
        expectBadBackup(base.replace("\"reps\": 8", "\"reps\": -8"), "повторений")
        expectBadBackup(base.replace("\"status\": \"FINISHED\"", "\"status\": \"WEIRD\""), "статус")
        expectBadBackup(base.replace("\"weight\": 42.5", "\"weight\": \"тяжёлый\""), "число")
    }

    @Test
    fun `ссылка на несуществующее упражнение`() {
        val data = BackupFixtures.sample()
        val orphan = data.copy(
            sessions = data.sessions.map { s ->
                s.copy(exercises = s.exercises.map { it.copy(exerciseId = 777) })
            },
        )
        expectBadBackup(BackupCodec.encode(orphan), "отсутствует")
        val orphanProgram = data.copy(
            programs = data.programs.map { p -> p.copy(items = p.items.map { it.copy(exerciseId = 888) }) },
        )
        expectBadBackup(BackupCodec.encode(orphanProgram), "отсутствует")
    }

    @Test
    fun `повторяющиеся идентификаторы и нелогичные даты`() {
        val data = BackupFixtures.sample()
        val dup = data.copy(exercises = data.exercises + data.exercises.first())
        expectBadBackup(BackupCodec.encode(dup), "повторяющийся")
        val badTime = data.copy(sessions = data.sessions.map { it.copy(finishedAt = it.startedAt - 1) })
        expectBadBackup(BackupCodec.encode(badTime), "раньше")
        val noFinish = data.copy(sessions = data.sessions.map { it.copy(finishedAt = null) })
        expectBadBackup(BackupCodec.encode(noFinish), "времени окончания")
    }

    @Test
    fun `не больше одной незавершённой тренировки`() {
        val data = BackupFixtures.sample()
        val a = data.sessions.first().copy(id = 1, status = "IN_PROGRESS", finishedAt = null)
        val b = a.copy(id = 2)
        expectBadBackup(BackupCodec.encode(data.copy(sessions = listOf(a, b))), "больше одной")
    }

    @Test
    fun `неизвестные поля игнорируются, неизвестные значения перечислений не ломают чтение`() {
        val text = BackupCodec.encode(BackupFixtures.sample())
            .replace("\"exportedAt\"", "\"futureField\": {\"x\": [1,2]}, \"exportedAt\"")
            .replace("\"theme\": \"DARK\"", "\"theme\": \"NEON\"")
        val data = BackupCodec.decode(text)
        assertEquals(ThemeMode.DARK, data.settings!!.theme)
    }
}

class CsvExporterTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `одна строка на выполненный подход`() {
        val start = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val data = BackupFixtures.sample().let { d ->
            d.copy(sessions = d.sessions.map { it.copy(startedAt = start, finishedAt = start + 3_600_000L) })
        }
        val csv = CsvExporter.build(data, zone)
        assertTrue(csv.startsWith("\uFEFF"))
        val lines = csv.removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals(1 + 3, lines.size) // заголовок + 2 выполненных + 1 с собственным весом (невыполненный пропущен)
        assertEquals(CsvExporter.HEADER.joinToString(";"), lines[0])
        assertTrue(lines[1].startsWith("27.10.2026;10:00;11:00;Тренировка А;Жим штанги лёжа;1;42,5;8;340;"))
        // объём для упражнения со своим весом пустой, а не выдуманный
        assertTrue(lines[3].contains("свой вес + отягощение"))
        assertTrue(lines[3].split(";")[8].isEmpty())
    }

    @Test
    fun `экранирование и защита от формул`() {
        assertEquals("\"a;b\"", CsvExporter.escape("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"'=SUM(A1)\"", CsvExporter.escape("=SUM(A1)"))
        assertEquals("обычный", CsvExporter.escape("обычный"))
    }
}
