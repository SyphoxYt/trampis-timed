package app.trampis.timed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class SessionExchangeTest {
    @Test fun csvEscapesAndRestoresTextFieldsAndPenalties() {
        val original = SessionWithSolves(
            SessionEntity(id = 4, name = "My, Session", eventId = "333"),
            listOf(SolveEntity(
                id = 9, sessionId = 4, timeMillis = 12_345, penalty = 1,
                scramble = "R U, F", comment = "said \"hello\"\nnext line", createdAt = 1_700_000_000_000,
            )),
        )
        val csv = SessionExchange.csv(original)
        assertTrue(csv.startsWith("Time,Comment,Scramble,Date"))
        val restored = SessionExchange.parse(csv, "round trip.csv").single()
        assertEquals(12_345L, restored.solves.single().timeMillis)
        assertEquals("R U, F", restored.solves.single().scramble)
        assertEquals("said \"hello\"\nnext line", restored.solves.single().comment)
        assertEquals(1, restored.solves.single().penalty)
        assertEquals(1_700_000_000_000L, restored.solves.single().createdAt)
    }

    @Test fun csTimerRoundTripsSessionsEventsPenaltiesAndDates() {
        val source = listOf(
            SessionWithSolves(SessionEntity(id = 1, name = "3x3", eventId = "333"), listOf(
                SolveEntity(sessionId = 1, timeMillis = 10_234, penalty = 2, scramble = "R U", comment = "DNF solve", createdAt = 1_700_000_000_000),
            )),
            SessionWithSolves(SessionEntity(id = 2, name = "Clock", eventId = "clock"), emptyList()),
        )
        val restored = SessionExchange.parse(SessionExchange.csTimer(source), "backup.json")
        assertEquals(2, restored.size)
        assertEquals("333", restored[0].eventId)
        assertEquals("clock", restored[1].eventId)
        assertEquals(2, restored[0].solves.single().penalty)
        assertEquals(10_234L, restored[0].solves.single().timeMillis)
        assertEquals(1_700_000_000_000L, restored[0].solves.single().createdAt)
    }

    @Test fun odsExportsOneValidSheetPerSessionAndKeepsTextEscaped() {
        val sessions = listOf(
            SessionWithSolves(SessionEntity(id = 3, name = "Main & Practice"), listOf(
                SolveEntity(sessionId = 3, timeMillis = 12_345, penalty = 1, scramble = "R < U", comment = "note & <check>", createdAt = 1_700_000_000_000),
            )),
            SessionWithSolves(SessionEntity(id = 4, name = "Other"), emptyList()),
        )
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(SessionExchange.ods(sessions))).use { zip ->
            var entry = zip.nextEntry
            assertEquals("mimetype", entry.name)
            assertEquals("application/vnd.oasis.opendocument.spreadsheet", zip.readBytes().toString(Charsets.UTF_8))
            while (true) {
                entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertTrue(entries.keys.containsAll(listOf("content.xml", "styles.xml", "META-INF/manifest.xml")))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.forEach { (name, bytes) -> if (name.endsWith(".xml")) factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)) }
        val xml = entries.getValue("content.xml").toString(Charsets.UTF_8)
        assertTrue(xml.contains("table:name=\"Main &amp; Practice\""))
        assertTrue(xml.contains("note &amp; &lt;check&gt;"))
        assertTrue(xml.contains("R &lt; U"))
        assertTrue(xml.contains("office:value=\"12.345\""))
    }
}
