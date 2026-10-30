package app.trampis.timed.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrambleProviderTest {
    @Test fun threeByThreeScrambleComesFromTnoodle() = runBlocking {
        val scramble = ScrambleProvider().next("333")
        val moves = scramble.trim().split(Regex("\\s+"))
        assertTrue("TNoodle returned too few moves: $scramble", moves.size >= 17)
        assertTrue("Unexpectedly long scramble: $scramble", moves.size <= 20)
        assertTrue(moves.all { it.matches(Regex("[URFDLB](2|')?")) })
        assertTrue(moves.zipWithNext().all { (a, b) -> a.first() != b.first() })
    }
}
