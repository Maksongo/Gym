package app.podhod.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сценарий 6: подстановка результатов предыдущей тренировки. */
class WorkoutPlannerTest {
    private val prev = listOf(SetValues(42.0, 8), SetValues(42.0, 8), SetValues(42.0, 7))

    @Test
    fun `подставляет вес и повторения прошлой тренировки по подходам`() {
        val sets = WorkoutPlanner.initialSets(LoadType.WEIGHTED, null, 8, 12, 40.0, prev)
        assertEquals(prev, sets)
    }

    @Test
    fun `количество подходов берётся из цели программы, значения из прошлого раза`() {
        val sets = WorkoutPlanner.initialSets(LoadType.WEIGHTED, 4, 8, 8, 40.0, prev)
        assertEquals(4, sets.size)
        assertEquals(SetValues(42.0, 7), sets[3]) // дополнительный подход повторяет последний
    }

    @Test
    fun `без истории используются цели программы`() {
        val sets = WorkoutPlanner.initialSets(LoadType.WEIGHTED, 3, 8, 12, 42.0, emptyList())
        assertEquals(listOf(SetValues(42.0, 8), SetValues(42.0, 8), SetValues(42.0, 8)), sets)
    }

    @Test
    fun `без истории и целей — три пустых подхода`() {
        val sets = WorkoutPlanner.initialSets(LoadType.WEIGHTED, null, null, null, null, emptyList())
        assertEquals(WorkoutPlanner.DEFAULT_SETS, sets.size)
        assertTrue(sets.all { it.weight == 0.0 && it.reps == 0 })
    }

    @Test
    fun `для упражнений без веса вес всегда ноль`() {
        val sets = WorkoutPlanner.initialSets(LoadType.REPS_ONLY, 3, 15, 20, 10.0, listOf(SetValues(5.0, 18)))
        assertTrue(sets.all { it.weight == 0.0 })
        assertEquals(18, sets[0].reps)
    }

    @Test
    fun `число подходов ограничено`() {
        val sets = WorkoutPlanner.initialSets(LoadType.WEIGHTED, 500, null, null, null, emptyList())
        assertEquals(InputRules.MAX_SETS, sets.size)
    }
}

class ProgressionAdvisorTest {
    private fun session(t: Long, weight: Double, reps: List<Int>, sets: Int? = 3, max: Int? = 8) =
        SessionResult(t, sets, 6, max, reps.map { SetValues(weight, it) })

    @Test
    fun `рекомендует повысить вес после нескольких успешных тренировок подряд`() {
        val history = listOf(session(2, 42.0, listOf(8, 8, 8)), session(1, 42.0, listOf(8, 8, 9)))
        val s = ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, requiredSessions = 2, stepKg = 2.5)
        assertNotNull(s)
        assertEquals(42.0, s!!.currentWeightKg, 0.0)
        assertEquals(44.5, s.suggestedWeightKg, 0.0)
    }

    @Test
    fun `не рекомендует, если истории недостаточно`() {
        val history = listOf(session(1, 42.0, listOf(8, 8, 8)))
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, 2, 2.5))
    }

    @Test
    fun `не рекомендует, если хотя бы в одной тренировке цель не выполнена`() {
        val history = listOf(session(2, 42.0, listOf(8, 8, 7)), session(1, 42.0, listOf(8, 8, 8)))
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, 2, 2.5))
        val fewSets = listOf(session(2, 42.0, listOf(8, 8)), session(1, 42.0, listOf(8, 8, 8)))
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, fewSets, 2, 2.5))
    }

    @Test
    fun `смена веса прерывает серию`() {
        val history = listOf(session(2, 45.0, listOf(8, 8, 8)), session(1, 42.0, listOf(8, 8, 8)))
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, 2, 2.5))
    }

    @Test
    fun `без целевого диапазона или для упражнений без веса рекомендаций нет`() {
        val noTarget = listOf(session(2, 42.0, listOf(8, 8, 8), max = null), session(1, 42.0, listOf(8, 8, 8)))
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, noTarget, 2, 2.5))
        val ok = listOf(session(2, 0.0, listOf(20, 20, 20), max = 20), session(1, 0.0, listOf(20, 20, 20), max = 20))
        assertNull(ProgressionAdvisor.suggest(LoadType.REPS_ONLY, ok, 2, 2.5))
        // для WEIGHTED нулевой вес недопустим
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, ok, 2, 2.5))
    }

    @Test
    fun `шаг настраивается и должен быть положительным`() {
        val history = listOf(session(2, 20.0, listOf(8, 8, 8)), session(1, 20.0, listOf(8, 8, 8)))
        assertEquals(21.0, ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, 2, 1.0)!!.suggestedWeightKg, 0.0)
        assertNull(ProgressionAdvisor.suggest(LoadType.WEIGHTED, history, 2, 0.0))
    }

    @Test
    fun `подтягивания с отягощением тоже поддерживаются`() {
        val history = listOf(session(2, 10.0, listOf(8, 8, 8)), session(1, 10.0, listOf(8, 8, 8)))
        assertEquals(12.5, ProgressionAdvisor.suggest(LoadType.BODYWEIGHT, history, 2, 2.5)!!.suggestedWeightKg, 0.0)
    }

    @Test
    fun `учитываются только рабочие подходы с максимальным весом`() {
        // разминочный подход 20 кг не мешает
        val withWarmup = SessionResult(
            2, 3, 6, 8,
            listOf(SetValues(20.0, 12), SetValues(42.0, 8), SetValues(42.0, 8), SetValues(42.0, 8)),
        )
        val prev = session(1, 42.0, listOf(8, 8, 8))
        assertFalse(ProgressionAdvisor.suggest(LoadType.WEIGHTED, listOf(withWarmup, prev), 2, 2.5) == null)
    }
}
