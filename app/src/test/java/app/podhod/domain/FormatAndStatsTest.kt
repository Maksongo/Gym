package app.podhod.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun `числа форматируются с запятой`() {
        assertEquals("42", Fmt.number(42.0))
        assertEquals("4,5", Fmt.number(4.5))
        assertEquals("0,25", Fmt.number(0.25))
        assertEquals("100", Fmt.number(100.0))
        assertEquals("", Fmt.field(0.0))
        assertEquals("42,5", Fmt.field(42.5))
    }

    @Test
    fun `объём с разделением разрядов`() {
        assertEquals("5${NBSP}120", Fmt.grouped(5120.0))
        assertEquals("1${NBSP}234${NBSP}567", Fmt.grouped(1234567.0))
        assertEquals("85,5", Fmt.grouped(85.5))
        assertEquals("5${NBSP}120${NBSP}кг", Fmt.volume(5120.0, WeightUnit.KG))
    }

    @Test
    fun `предыдущий результат`() {
        val sets = listOf(SetValues(42.0, 8), SetValues(42.0, 8), SetValues(42.0, 8))
        assertEquals("42${NBSP}кг × 8, 8, 8", PreviousResultFormatter.format(sets, LoadType.WEIGHTED, WeightUnit.KG))
        val mixed = listOf(SetValues(42.0, 8), SetValues(42.0, 8), SetValues(45.0, 6))
        assertEquals(
            "42${NBSP}кг × 8, 8; 45${NBSP}кг × 6",
            PreviousResultFormatter.format(mixed, LoadType.WEIGHTED, WeightUnit.KG),
        )
        assertNull(PreviousResultFormatter.format(emptyList(), LoadType.WEIGHTED, WeightUnit.KG))
        assertEquals(
            "10, 9 повт.",
            PreviousResultFormatter.format(listOf(SetValues(0.0, 10), SetValues(0.0, 9)), LoadType.REPS_ONLY, WeightUnit.KG),
        )
        assertEquals(
            "свой вес × 8, 7",
            PreviousResultFormatter.format(listOf(SetValues(0.0, 8), SetValues(0.0, 7)), LoadType.BODYWEIGHT, WeightUnit.KG),
        )
    }

    @Test
    fun `длительность и таймер`() {
        assertEquals("47 мин", Fmt.duration(47 * 60_000L))
        assertEquals("1 ч 05 мин", Fmt.duration(65 * 60_000L))
        assertEquals("01:30", Fmt.clock(90))
        assertEquals("00:00", Fmt.clock(-5))
    }

    @Test
    fun `цель упражнения`() {
        assertEquals("3 × 8", Fmt.target(3, 8, 8, null, LoadType.WEIGHTED, WeightUnit.KG))
        assertEquals("3 × 8–12, 42${NBSP}кг", Fmt.target(3, 8, 12, 42.0, LoadType.WEIGHTED, WeightUnit.KG))
        assertEquals("3 подх.", Fmt.target(3, null, null, null, LoadType.BODYWEIGHT, WeightUnit.KG))
        assertNull(Fmt.target(null, null, null, null, LoadType.WEIGHTED, WeightUnit.KG))
    }

    @Test
    fun `даты по-русски и границы недели`() {
        val zone = ZoneId.of("UTC")
        val tue = ZonedDateTime.of(2026, 10, 27, 14, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("27 октября", DateFmt.dayMonth(tue, zone))
        assertEquals("вт, 27 октября", DateFmt.weekdayDayMonth(tue, zone))
        assertEquals("14:05", DateFmt.time(tue, zone))
        assertEquals("Октябрь 2026", DateFmt.monthYear(tue, zone))
        val monday = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(monday, DateFmt.startOfWeek(tue, zone))
        assertEquals(monday, DateFmt.startOfWeek(monday, zone))
        val sunday = ZonedDateTime.of(2026, 11, 1, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(monday, DateFmt.startOfWeek(sunday, zone))
    }
}

/** Сценарий 7 и раздел «Прогресс». */
class ProgressCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private fun day(d: Int) = ZonedDateTime.of(2026, 10, d, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun s(session: Long, d: Int, w: Double, r: Int, lt: LoadType = LoadType.WEIGHTED) =
        StatSet(session, day(d), 1L, lt, w, r)

    @Test
    fun `агрегирование по тренировкам`() {
        val rows = listOf(s(1, 1, 40.0, 8), s(1, 1, 40.0, 8), s(2, 8, 42.5, 8), s(2, 8, 42.5, 6))
        val pts = ProgressCalculator.perSession(rows)
        assertEquals(2, pts.size)
        assertEquals(40.0, pts[0].maxWeightKg, 0.0)
        assertEquals(640.0, pts[0].volume.totalKg, 0.0)
        assertEquals(42.5, pts[1].maxWeightKg, 0.0)
        assertEquals(8, pts[1].maxReps)
        assertEquals(14, pts[1].totalReps)
        assertEquals(42.5 * 14, pts[1].volume.totalKg, 0.0001)
        assertTrue(pts[0].startedAt < pts[1].startedAt)
    }

    @Test
    fun `оценка 1ПМ берётся по лучшему подходу`() {
        val pts = ProgressCalculator.perSession(listOf(s(1, 1, 100.0, 5), s(1, 1, 90.0, 10)))
        // 100×(1+5/30)=116.67; 90×(1+10/30)=120
        assertEquals(120.0, pts[0].bestOneRmKg!!, 0.0001)
    }

    @Test
    fun `для упражнений без веса нет серии веса, объёма и 1ПМ`() {
        val rows = listOf(s(1, 1, 0.0, 12, LoadType.REPS_ONLY), s(2, 8, 0.0, 15, LoadType.REPS_ONLY))
        val pts = ProgressCalculator.perSession(rows)
        assertTrue(ProgressCalculator.series(pts, ChartMetric.WEIGHT).isEmpty())
        assertTrue(ProgressCalculator.series(pts, ChartMetric.VOLUME).isEmpty())
        assertTrue(ProgressCalculator.series(pts, ChartMetric.ONE_RM).isEmpty())
        assertEquals(2, ProgressCalculator.series(pts, ChartMetric.REPS).size)
    }

    @Test
    fun `лучший подход - самый тяжёлый, при равенстве больше повторений`() {
        val best = ProgressCalculator.bestSet(listOf(s(1, 1, 60.0, 5), s(2, 8, 60.0, 7), s(3, 9, 55.0, 12)))!!
        assertEquals(60.0, best.weight, 0.0)
        assertEquals(7, best.reps)
        assertNull(ProgressCalculator.bestSet(emptyList()))
    }

    @Test
    fun `сводка с изменениями веса и повторений`() {
        val rows = listOf(s(1, 1, 40.0, 8), s(2, 8, 45.0, 10))
        val sum = ProgressCalculator.summary(rows)
        assertEquals(2, sum.sessionsCount)
        assertEquals(45.0, sum.maxWeightKg!!, 0.0)
        assertEquals(5.0, sum.weightChangeKg!!, 0.0)
        assertEquals(2, sum.repsChange!!)
        assertNotNull(sum.bestOneRmKg)
    }

    @Test
    fun `при одной тренировке изменения не выдумываются`() {
        val sum = ProgressCalculator.summary(listOf(s(1, 1, 40.0, 8)))
        assertNull(sum.weightChangeKg)
        assertNull(sum.repsChange)
        val empty = ProgressCalculator.summary(emptyList())
        assertEquals(0, empty.sessionsCount)
        assertNull(empty.maxWeightKg)
        assertNull(empty.bestSet)
    }

    @Test
    fun `недельный объём заполняет пропущенные недели нулями`() {
        val rows = listOf(s(1, 5, 50.0, 10), s(2, 26, 50.0, 10))
        val weeks = ProgressCalculator.weeklyVolume(rows, zone)
        assertEquals(4, weeks.size) // 5 окт (нед. с 5.10), 12, 19, 26
        assertEquals(500.0, weeks.first().volume.totalKg, 0.0)
        assertEquals(0.0, weeks[1].volume.totalKg, 0.0)
        assertEquals(500.0, weeks.last().volume.totalKg, 0.0)
        assertTrue(ProgressCalculator.weeklyVolume(emptyList(), zone).isEmpty())
    }

    @Test
    fun `период ограничивает выборку`() {
        val now = 1_000L * 86_400_000L
        assertEquals(now - 30 * 86_400_000L, Period.MONTH.fromMillis(now))
        assertEquals(0L, Period.ALL.fromMillis(now))
    }
}
