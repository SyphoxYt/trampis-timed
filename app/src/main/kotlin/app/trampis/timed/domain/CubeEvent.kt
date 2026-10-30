package app.trampis.timed.domain

import org.worldcubeassociation.tnoodle.scrambles.PuzzleRegistry

data class CubeEvent(val id: String, val name: String, val puzzle: PuzzleRegistry)

/** CubeTime event ordering and TNoodle puzzle mapping, including event aliases. */
object CubeEvents {
    val all = listOf(
        CubeEvent("222", "2×2×2", PuzzleRegistry.TWO),
        CubeEvent("333", "3×3×3", PuzzleRegistry.THREE),
        CubeEvent("444", "4×4×4", PuzzleRegistry.FOUR),
        CubeEvent("555", "5×5×5", PuzzleRegistry.FIVE),
        CubeEvent("666", "6×6×6", PuzzleRegistry.SIX),
        CubeEvent("777", "7×7×7", PuzzleRegistry.SEVEN),
        CubeEvent("sq1", "Square-1", PuzzleRegistry.SQ1),
        CubeEvent("minx", "Megaminx", PuzzleRegistry.MEGA),
        CubeEvent("pyram", "Pyraminx", PuzzleRegistry.PYRA),
        CubeEvent("clock", "Clock", PuzzleRegistry.CLOCK),
        CubeEvent("skewb", "Skewb", PuzzleRegistry.SKEWB),
        CubeEvent("333oh", "3×3×3 One-Handed", PuzzleRegistry.THREE),
        CubeEvent("333bld", "3×3×3 Blindfolded", PuzzleRegistry.THREE_NI),
        CubeEvent("444bld", "4×4×4 Blindfolded", PuzzleRegistry.FOUR_NI),
        CubeEvent("555bld", "5×5×5 Blindfolded", PuzzleRegistry.FIVE_NI),
    )

    fun fromId(id: String): CubeEvent = all.firstOrNull { it.id == id } ?: all[1]
}
