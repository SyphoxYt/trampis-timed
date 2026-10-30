package app.trampis.timed.domain

enum class Penalty { NONE, PLUS_TWO, DNF }

data class TimedResult(val timeMillis: Long, val penalty: Penalty = Penalty.NONE) {
    val adjustedMillis: Long
        get() = when (penalty) {
            Penalty.NONE -> timeMillis
            Penalty.PLUS_TWO -> timeMillis + 2_000L
            Penalty.DNF -> Long.MAX_VALUE
        }
}

data class Average(val millis: Double?, val isDnf: Boolean, val trimmed: List<Int>)
data class DistributionBin(val centerMillis: Long, val count: Int)
data class CompSimGroup(val results: List<TimedResult>, val average: Average?)
data class CompSimProgress(val groups: List<CompSimGroup>, val currentBpaMillis: Double?, val currentBpaIsDnf: Boolean, val currentWpaMillis: Double?, val currentWpaIsDnf: Boolean, val neededForTargetMillis: Double?, val targetGuaranteed: Boolean, val targetImpossible: Boolean)

/** Mirrors CubeTime's trim rule: one at each end through Ao12, then floor(n * 5%). */
object Statistics {
    /** CubeTime competition simulation uses fixed, non-overlapping groups of five. */
    fun compSimGroups(results: List<TimedResult>): List<CompSimGroup> = results.chunked(5).map { group ->
        CompSimGroup(group, if (group.size == 5) average(group) else null)
    }

    fun compSimProgress(results: List<TimedResult>, targetMillis: Long): CompSimProgress {
        val groups = compSimGroups(results)
        val current = groups.lastOrNull()?.takeIf { it.results.size == 4 }?.results.orEmpty()
        val sorted = current.sortedBy { it.adjustedMillis }
        val bpa = sorted.take(3).takeIf { current.size == 4 }?.let(::meanOrDnf)
        val wpa = sorted.takeLast(3).takeIf { current.size == 4 }?.let(::meanOrDnf)
        val needed = if (bpa != null && wpa != null && current.size == 4 && !bpa.second) {
            when {
                wpa.first <= targetMillis && !wpa.second -> null
                bpa.first > targetMillis -> Double.POSITIVE_INFINITY
                else -> targetMillis * 3.0 - sorted.drop(1).dropLast(1).sumOf { it.adjustedMillis.toDouble() }
            }
        } else null
        return CompSimProgress(groups, bpa?.first, bpa?.second == true, wpa?.first, wpa?.second == true, needed,
            targetGuaranteed = wpa?.let { it.first < targetMillis && !it.second } == true,
            targetImpossible = bpa?.let { it.second || it.first > targetMillis } == true)
    }

    private fun meanOrDnf(results: List<TimedResult>): Pair<Double, Boolean> {
        val dnf = results.any { it.penalty == Penalty.DNF }
        val sum = results.sumOf { result -> (result.timeMillis + if (result.penalty == Penalty.PLUS_TWO) 2_000L else 0L).toDouble() }
        return (sum / results.size) to dnf
    }

    fun compSimBestAverage(results: List<TimedResult>): Average? = compSimGroups(results)
        .mapNotNull { it.average }.filterNot { it.isDnf }.minByOrNull { it.millis ?: Double.POSITIVE_INFINITY }

    fun compSimMeanOfTen(results: List<TimedResult>): Double? {
        val averages = compSimGroups(results).mapNotNull { it.average?.takeUnless(Average::isDnf)?.millis }
        return averages.takeIf { it.size >= 10 }?.takeLast(10)?.average()
    }

    fun trimEachEnd(width: Int): Int = when {
        width < 5 -> 0
        width <= 12 -> 1
        else -> maxOf(1, width / 20)
    }

    fun average(results: List<TimedResult>): Average? {
        val n = results.size
        if (n < 5) return null
        val trim = trimEachEnd(n)
        val sorted = results.mapIndexed { index, result -> index to result.adjustedMillis }
            .sortedWith(compareBy<Pair<Int, Long>> { it.second }.thenBy { it.first })
        val counted = sorted.subList(trim, n - trim)
        val isDnf = counted.any { it.second == Long.MAX_VALUE }
        val mean = if (isDnf) null else counted.sumOf { it.second.toDouble() } / counted.size
        val trimmedIndices = (sorted.take(trim) + sorted.takeLast(trim)).map { it.first }
        return Average(mean, isDnf, trimmedIndices)
    }

    /** Best WCA-trimmed rolling average in the session, preserving solve order. */
    fun bestAverage(results: List<TimedResult>, width: Int): Average? {
        if (width < 5 || results.size < width) return null
        return results.windowed(width).mapNotNull(::average).minWithOrNull(
            compareBy<Average> { if (it.isDnf) Double.POSITIVE_INFINITY else it.millis ?: Double.POSITIVE_INFINITY },
        )
    }

    fun bestSingle(results: List<TimedResult>): Long? = results
        .map { it.adjustedMillis }
        .filter { it != Long.MAX_VALUE }
        .minOrNull()

    fun median(results: List<TimedResult>): Double? {
        val valid = results.map { it.adjustedMillis }.filter { it != Long.MAX_VALUE }.sorted()
        if (valid.isEmpty()) return null
        val middle = valid.size / 2
        return if (valid.size % 2 == 1) valid[middle].toDouble()
        else (valid[middle - 1].toDouble() + valid[middle].toDouble()) / 2.0
    }

    /** CubeTime's time distribution trims the slowest and fastest 10% before making up to eight bins. */
    fun distribution(values: List<Long>, maxBins: Int = 8): List<DistributionBin> {
        if (values.size < 4 || maxBins < 1) return emptyList()
        val sorted = values.sorted()
        val trim = kotlin.math.ceil(sorted.size * 0.1).toInt()
        val inliers = sorted.drop(trim).dropLast(trim)
        if (inliers.isEmpty()) return emptyList()
        val binCount = minOf(maxBins, inliers.size)
        val min = inliers.first().toDouble()
        val max = inliers.last().toDouble()
        val counts = IntArray(binCount)
        inliers.forEach { value ->
            val index = if (max <= min) 0 else (((value - min) / (max - min) * binCount).toInt()).coerceIn(0, binCount - 1)
            counts[index]++
        }
        return counts.mapIndexed { index, count ->
            val center = if (max <= min) min else min + (max - min) * (index + 0.5) / binCount
            DistributionBin(center.toLong(), count)
        }
    }
}
