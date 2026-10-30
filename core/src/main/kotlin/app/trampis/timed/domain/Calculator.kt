package app.trampis.timed.domain

enum class CalculatorMode { AVERAGE, MEAN }

/** Input and calculation rules shared by the standalone solve calculator. */
object SolveCalculator {
    fun parseTime(input: String): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val parts = text.split(':')
        if (parts.size !in 1..3 || parts.any(String::isBlank)) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        if (!seconds.isFinite() || seconds < 0 || seconds >= 60 && parts.size > 1) return null
        val wholeUnits = parts.dropLast(1).map { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        val totalSeconds = when (parts.size) {
            1 -> seconds
            2 -> wholeUnits[0] * 60.0 + seconds
            else -> wholeUnits[0] * 3600.0 + wholeUnits[1] * 60.0 + seconds
        }
        return (totalSeconds * 1000.0).toLong().takeIf { it >= 0 }
    }

    fun result(results: List<TimedResult>, mode: CalculatorMode, width: Int): Average? {
        if (results.isEmpty() || width < 1 || results.size != width) return null
        if (mode == CalculatorMode.AVERAGE) {
            if (width >= 5) return Statistics.average(results)
            if (width < 3) return simpleMean(results)
            val sorted = results.mapIndexed { index, result -> index to result }
                .sortedWith(compareBy<Pair<Int, TimedResult>> { it.second.penalty == Penalty.DNF }
                    .thenBy { it.second.timeMillis + if (it.second.penalty == Penalty.PLUS_TWO) 2_000L else 0L }
                    .thenBy { it.first })
            val counted = sorted.subList(1, width - 1)
            val dnf = counted.any { it.second.penalty == Penalty.DNF }
            val sum = counted.sumOf { (_, result) -> (result.timeMillis + if (result.penalty == Penalty.PLUS_TWO) 2_000L else 0L).toDouble() }
            return Average(sum / counted.size, dnf, (sorted.take(1) + sorted.takeLast(1)).map { it.first })
        }
        return simpleMean(results)
    }

    private fun simpleMean(results: List<TimedResult>): Average {
        val penaltyDnf = results.any { it.penalty == Penalty.DNF }
        val sum = results.sumOf { (it.timeMillis + if (it.penalty == Penalty.PLUS_TWO) 2_000L else 0L).toDouble() }
        return Average(sum / results.size, penaltyDnf, emptyList())
    }
}
