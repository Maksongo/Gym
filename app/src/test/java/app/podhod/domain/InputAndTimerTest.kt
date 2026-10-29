package app.podhod.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сценарий 12: обработка ошибок ввода. */
class InputRulesTest {
    @Test
    fun `отрицательные значения не проходят`() {
        assertNull(InputRules.parseDecimal("-5"))
        assertNull(InputRules.parseInt("-3"))
        assertEquals("5", InputRules.sanitizeDecimal("-5"))
        assertEquals("12", InputRules.sanitizeInt("-12"))
    }

    @Test
    fun `дробные веса принимаются с запятой и точкой`() {
        assertEquals(4.5, InputRules.parseDecimal("4,5")!!, 0.0)
        assertEquals(4.5, InputRules.parseDecimal("4.5")!!, 0.0)
        assertEquals(4.0, InputRules.parseDecimal("4,")!!, 0.0)
        assertEquals(0.5, InputRules.parseDecimal(",5")!!, 0.0)
    }

    @Test
    fun `пустые и мусорные значения дают null`() {
        assertNull(InputRules.parseDecimal(""))
        assertNull(InputRules.parseDecimal("   "))
        assertNull(InputRules.parseDecimal("abc"))
        assertNull(InputRules.parseDecimal("1.2.3"))
        assertNull(InputRules.parseDecimal("."))
        assertNull(InputRules.parseDecimal("NaN"))
        assertNull(InputRules.parseInt(""))
        assertNull(InputRules.parseInt("8,5"))
        assertNull(InputRules.parseInt("99999999999"))
    }

    @Test
    fun `фильтр поля оставляет одну запятую и два знака`() {
        assertEquals("42,5", InputRules.sanitizeDecimal("42.5"))
        assertEquals("4,25", InputRules.sanitizeDecimal("4,2,5,9"))
        assertEquals("1234", InputRules.sanitizeDecimal("12345"))
        assertEquals("", InputRules.sanitizeDecimal("abc"))
    }

    @Test
    fun `повторения ограничены`() {
        assertEquals(0, InputRules.stepReps(0, -1))
        assertEquals(9, InputRules.stepReps(8, 1))
        assertEquals(InputRules.MAX_REPS, InputRules.clampReps(100000))
        assertEquals(0, InputRules.clampReps(-4))
    }

    @Test
    fun `подход нельзя отметить без повторений и без веса`() {
        assertEquals(SetProblem.NO_REPS, SetValidator.problem(LoadType.WEIGHTED, 40.0, 0))
        assertEquals(SetProblem.NO_WEIGHT, SetValidator.problem(LoadType.WEIGHTED, 0.0, 8))
        assertNull(SetValidator.problem(LoadType.WEIGHTED, 40.0, 8))
        assertNull(SetValidator.problem(LoadType.BODYWEIGHT, 0.0, 8))
        assertNull(SetValidator.problem(LoadType.REPS_ONLY, 0.0, 20))
        assertEquals(SetProblem.NO_REPS, SetValidator.problem(LoadType.REPS_ONLY, 0.0, -1))
    }

    @Test
    fun `нечисловые значения клампятся`() {
        assertEquals(0.0, InputRules.clampKg(Double.NaN), 0.0)
        assertEquals(0.0, InputRules.clampKg(-5.0), 0.0)
    }
}

class NameNormalizerTest {
    @Test
    fun `незначительные различия не создают дубликаты`() {
        val base = NameNormalizer.key("Жим лёжа")
        assertEquals(base, NameNormalizer.key("жим лежа"))
        assertEquals(base, NameNormalizer.key("  Жим   ЛЁЖА "))
        assertEquals(base, NameNormalizer.key("Жим-лёжа."))
        assertNotEquals(base, NameNormalizer.key("Жим стоя"))
    }

    @Test
    fun `отображаемое имя чистится`() {
        assertEquals("Жим лёжа", NameNormalizer.display("  Жим    лёжа  "))
    }

    @Test
    fun `встроенная библиотека без дубликатов и с обязательными упражнениями`() {
        val keys = BuiltInExercises.ALL.map { it.key }
        assertEquals(keys.size.toLong(), keys.toSet().size.toLong())
        val nameKeys = BuiltInExercises.ALL.map { NameNormalizer.key(it.name) }
        assertEquals(nameKeys.size.toLong(), nameKeys.toSet().size.toLong())
        val required = listOf(
            "Жим штанги лёжа", "Жим гантелей", "Жим штанги стоя", "Приседания со штангой", "Становая тяга",
            "Румынская тяга", "Подтягивания", "Тяга штанги в наклоне", "Тяга гантели одной рукой",
            "Разведения гантелей в стороны", "Сгибания рук со штангой", "Молотковые сгибания",
            "Французский жим", "Разгибания рук на блоке", "Подъёмы на носки", "Скручивания на пресс",
        )
        for (name in required) {
            assertTrue("нет упражнения $name", nameKeys.contains(NameNormalizer.key(name)))
        }
    }
}

/** Сценарий 11: таймер отдыха при сворачивании приложения и блокировке экрана. */
class RestTimerTest {
    @Test
    fun `остаток считается от абсолютного времени окончания`() {
        val t = RestTimer.start(now = 1_000_000L, seconds = 90)
        assertEquals(90, t.remainingSeconds(1_000_000L))
        // «приложение свёрнуто» 40 секунд — ничто не тикало, но остаток верный
        assertEquals(50, t.remainingSeconds(1_040_000L))
        assertFalse(t.isExpired(1_040_000L))
    }

    @Test
    fun `после долгого сна таймер истёк и остаток не уходит в минус`() {
        val t = RestTimer.start(0L, 60)
        assertTrue(t.isExpired(61_000L))
        assertEquals(0L, t.remainingMillis(10 * 60_000L))
        assertEquals(0, t.remainingSeconds(10 * 60_000L))
    }

    @Test
    fun `состояние восстанавливается из сохранённых значений`() {
        val original = RestTimer.start(5_000L, 120)
        val restored = RestTimer.fromPersisted(original.endsAtMillis, 120)!!
        assertEquals(original, restored)
        assertNull(RestTimer.fromPersisted(null, 90))
        assertNull(RestTimer.fromPersisted(1L, null))
    }

    @Test
    fun `время можно увеличить и уменьшить`() {
        val now = 10_000L
        val t = RestTimer.start(now, 90)
        val plus = RestTimer.adjust(t, 15, now)!!
        assertEquals(105, plus.remainingSeconds(now))
        val minus = RestTimer.adjust(t, -15, now)!!
        assertEquals(75, minus.remainingSeconds(now))
        // уменьшение до нуля останавливает таймер
        assertNull(RestTimer.adjust(t, -90, now))
        assertNull(RestTimer.adjust(t, -300, now))
    }

    @Test
    fun `стандартные интервалы`() {
        assertEquals(listOf(60, 90, 120, 180), RestTimer.PRESETS_SECONDS)
        assertEquals(InputRules.MIN_REST_SECONDS, RestTimer.clampSeconds(0))
        assertEquals(InputRules.MAX_REST_SECONDS, RestTimer.clampSeconds(99999))
    }

    @Test
    fun `доля оставшегося времени`() {
        val t = RestTimer.start(0L, 100)
        assertEquals(1f, t.fractionLeft(0L), 0.0001f)
        assertEquals(0.5f, t.fractionLeft(50_000L), 0.0001f)
        assertEquals(0f, t.fractionLeft(200_000L), 0.0001f)
    }
}
