package app.podhod.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCalculatorTest {
    private fun w(weight: Double, reps: Int) = SetData(weight, reps, LoadType.WEIGHTED)

    @Test
    fun `объём равен сумме вес на повторения`() {
        val r = VolumeCalculator.volume(listOf(w(42.0, 8), w(42.0, 8), w(42.0, 8)))
        assertEquals(1008.0, r.totalKg, 0.0001)
        assertEquals(3L, r.countedSets.toLong())
        assertFalse(r.isPartial)
    }

    @Test
    fun `дробные веса считаются точно`() {
        val r = VolumeCalculator.volume(listOf(w(4.5, 10), w(4.5, 9)))
        assertEquals(85.5, r.totalKg, 0.0001)
    }

    @Test
    fun `упражнения со своим весом не выдаются за полный объём`() {
        val r = VolumeCalculator.volume(
            listOf(
                w(50.0, 10),
                SetData(0.0, 12, LoadType.BODYWEIGHT),
                SetData(5.0, 8, LoadType.BODYWEIGHT),
                SetData(0.0, 20, LoadType.REPS_ONLY),
            ),
        )
        assertEquals(500.0, r.totalKg, 0.0001)
        assertEquals(1L, r.countedSets.toLong())
        assertEquals(3L, r.skippedSets.toLong())
        assertTrue("объём должен быть помечен как неполный", r.isPartial)
    }

    @Test
    fun `пустые и нулевые подходы игнорируются`() {
        val r = VolumeCalculator.volume(listOf(w(0.0, 10), w(40.0, 0)))
        assertEquals(0.0, r.totalKg, 0.0)
        assertFalse(r.hasData)
        assertFalse(r.isPartial)
    }

    @Test
    fun `результаты суммируются`() {
        val a = VolumeResult(100.0, 1, 0)
        val b = VolumeResult(50.5, 2, 1)
        val c = a + b
        assertEquals(150.5, c.totalKg, 0.0001)
        assertEquals(3L, c.countedSets.toLong())
        assertTrue(c.isPartial)
    }
}

class OneRepMaxTest {
    @Test
    fun `формула Эпли`() {
        // 100 × (1 + 5/30) = 116.67
        assertEquals(116.67, OneRepMax.estimate(100.0, 5)!!, 0.0001)
        // 42 × (1 + 8/30) = 53.2
        assertEquals(53.2, OneRepMax.estimate(42.0, 8)!!, 0.0001)
    }

    @Test
    fun `для одного повторения оценка равна весу`() {
        assertEquals(140.0, OneRepMax.estimate(140.0, 1)!!, 0.0)
    }

    @Test
    fun `неприменимо при нуле и при большом числе повторений`() {
        assertNull(OneRepMax.estimate(0.0, 5))
        assertNull(OneRepMax.estimate(50.0, 0))
        assertNull(OneRepMax.estimate(50.0, 13))
        assertNotNull(OneRepMax.estimate(50.0, 12))
    }
}

class WeightUnitTest {
    @Test
    fun `фунты пересчитываются туда и обратно`() {
        val kg = WeightUnit.LB.toKg(45.0)
        assertEquals(20.41, kg, 0.01)
        assertEquals(45.0, WeightUnit.LB.fromKg(kg), 0.0)
    }

    @Test
    fun `шаг не уводит вес в минус`() {
        assertEquals(0.0, WeightUnit.KG.stepKg(1.0, -2.5), 0.0)
        assertEquals(44.5, WeightUnit.KG.stepKg(42.0, 2.5), 0.0)
    }

    @Test
    fun `шаг прогрессии переводится в кг`() {
        val s = AppSettings(unit = WeightUnit.LB, progressionStep = 5.0)
        assertEquals(2.27, s.progressionStepKg, 0.01)
    }
}
