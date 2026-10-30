package app.trampis.timed.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.worldcubeassociation.tnoodle.scrambles.Puzzle

data class ScrambleOutput(val notation: String, val drawingSvg: String)

/** TNoodle supplies actual WCA puzzle scrambles; generation stays off the UI thread. */
class ScrambleProvider {
    suspend fun next(eventId: String): String = nextWithDrawing(eventId).notation

    suspend fun nextWithDrawing(eventId: String): ScrambleOutput = withContext(Dispatchers.IO) {
        val puzzle = CubeEvents.fromId(eventId).puzzle.getScrambler()
        val notation = puzzle.generateWcaScramble(Puzzle.getSecureRandom())
        ScrambleOutput(notation, puzzle.drawScramble(notation, puzzle.defaultColorScheme).toString())
    }
}
