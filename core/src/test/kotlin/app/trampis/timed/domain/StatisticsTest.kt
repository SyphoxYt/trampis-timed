package app.trampis.timed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsTest {
    @Test fun ao5TrimsOneAtEachEnd() {
        val values = listOf(10_000L, 11_000L, 12_000L, 13_000L, 14_000L)
            .map { TimedResult(it) }
        val average = Statistics.average(values)!!
        assertEquals(12_000.0, average.millis!!, 0.001)
        assertEquals(listOf(0, 4), average.trimmed.sorted())
        assertFalse(average.isDnf)
    }

    @Test fun plusTwoIsIncludedBeforeTrimmingAndAveraging() {
        val values = listOf(
            TimedResult(10_000), TimedResult(11_000, Penalty.PLUS_TWO),
            TimedResult(12_000), TimedResult(13_000), TimedResult(14_000),
        )
        assertEquals(12_666.666, Statistics.average(values)!!.millis!!, 0.01)
    }

    @Test fun oneDnfCanBeTrimmedButTwoDnfsLeaveTheAverageAsDnf() {
        val oneDnf = listOf(10_000L, 11_000L, 12_000L, 13_000L)
            .map { TimedResult(it) } + TimedResult(1_000, Penalty.DNF)
        val trimmed = Statistics.average(oneDnf)!!
        assertFalse(trimmed.isDnf)
        assertEquals(12_000.0, trimmed.millis!!, 0.001)

        val twoDnfs = listOf(
            TimedResult(10_000), TimedResult(11_000), TimedResult(1_000, Penalty.DNF),
            TimedResult(2_000, Penalty.DNF), TimedResult(12_000),
        )
        assertTrue(Statistics.average(twoDnfs)!!.isDnf)
    }

    @Test fun fewerThanFiveSolvesHasNoAverage() {
        assertNull(Statistics.average(List(4) { TimedResult(1_000) }))
    }

    @Test fun bestAverageChoosesLowestValidRollingWindow() {
        val values = listOf(20_000L, 21_000L, 22_000L, 23_000L, 24_000L,
            10_000L, 11_000L, 12_000L, 13_000L, 14_000L).map(::TimedResult)
        assertEquals(12_000.0, Statistics.bestAverage(values, 5)!!.millis!!, 0.001)
    }

    @Test fun distributionTrimsTenPercentAtBothEndsAndCapsAtEightBins() {
        val bins = Statistics.distribution((1L..20L).map { it * 1_000 })
        assertEquals(8, bins.size)
        assertEquals(16, bins.sumOf { it.count })
    }

    @Test fun competitionSimulationUsesFixedGroupsAndDoesNotRoll() {
        val times = (10L..14L).map { TimedResult(it * 1_000) } +
            (20L..24L).map { TimedResult(it * 1_000) }
        val groups = Statistics.compSimGroups(times)
        assertEquals(2, groups.size)
        assertEquals(12_000.0, groups[0].average!!.millis!!, 0.001)
        assertEquals(22_000.0, groups[1].average!!.millis!!, 0.001)
        assertEquals(12_000.0, Statistics.compSimBestAverage(times)!!.millis!!, 0.001)
        assertNull(Statistics.compSimMeanOfTen(times))
    }

    @Test fun competitionProgressComputesBpaWpaAndRequiredLastSolve() {
        val results = listOf(10L, 11L, 12L, 13L).map { TimedResult(it * 1_000) }
        val progress = Statistics.compSimProgress(results, targetMillis = 12_000)
        assertEquals(11_000.0, progress.currentBpaMillis!!, 0.001)
        assertEquals(12_000.0, progress.currentWpaMillis!!, 0.001)
        assertEquals(13_000.0, progress.neededForTargetMillis!!, 0.001)
        assertFalse(progress.targetGuaranteed)
        assertFalse(progress.targetImpossible)
    }

    @Test fun competitionProgressMarksDnfInWpaButStillUsesItsRawTimeForTarget() {
        val results = listOf(TimedResult(10_000), TimedResult(11_000), TimedResult(12_000), TimedResult(1_000, Penalty.DNF))
        val progress = Statistics.compSimProgress(results, targetMillis = 12_000)
        assertTrue(progress.currentWpaIsDnf)
        assertFalse(progress.targetGuaranteed)
        assertEquals(13_000.0, progress.neededForTargetMillis!!, 0.001)
    }

    @Test fun calculatorParsesMinuteTimesAndRejectsInvalidInput() {
        assertEquals(83_450L, SolveCalculator.parseTime("1:23.450"))
        assertEquals(3_723_000L, SolveCalculator.parseTime("1:02:03"))
        assertNull(SolveCalculator.parseTime("1:60"))
        assertNull(SolveCalculator.parseTime("not a time"))
    }

    @Test fun calculatorAverageUsesWcaTrimAndMeanKeepsDnfRawTime() {
        val values = listOf(10L, 11L, 12L, 13L, 40L).map { TimedResult(it * 1000) }
        assertEquals(12_000.0, SolveCalculator.result(values, CalculatorMode.AVERAGE, 5)!!.millis!!, 0.001)
        val withDnf = listOf(TimedResult(10_000), TimedResult(20_000, Penalty.DNF))
        val mean = SolveCalculator.result(withDnf, CalculatorMode.MEAN, 2)!!
        assertTrue(mean.isDnf)
        assertEquals(15_000.0, mean.millis!!, 0.001)
    }
}
