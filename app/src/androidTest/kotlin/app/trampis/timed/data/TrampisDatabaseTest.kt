package app.trampis.timed.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrampisDatabaseTest {
    private lateinit var database: TrampisDatabase
    private lateinit var dao: TimerDao

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrampisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.timerDao()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun finishingSolveAndClearingRecoveryRecordIsAtomic() = runBlocking {
        val sessionId = dao.addSession(SessionEntity(name = "Recovery test"))
        dao.saveActiveTimer(ActiveTimerEntity(
            sessionId = sessionId,
            startElapsedNanos = 100L,
            bootCount = 1,
            sessionType = "standard",
            phaseCount = 2,
            penalty = 0,
            scramble = "R U R'",
        ))
        dao.finishActiveTimer(SolveEntity(
            sessionId = sessionId,
            timeMillis = 2_000L,
            scramble = "R U R'",
        ))

        assertNull(dao.getActiveTimer())
        assertEquals(1, dao.getSolves(sessionId).size)
    }

    @Test fun importingSeveralSessionsKeepsEverySolveAttachedToItsSession() = runBlocking {
        val imported = listOf(
            ImportedSession("A", "333", listOf(ImportedSolve(1_000, 0, "R", "", 100L))),
            ImportedSession("B", "clock", listOf(ImportedSolve(2_000, 1, "U", "+2", 200L))),
        )
        val sessionIds = dao.addImportedSessions(imported)

        assertEquals(2, sessionIds.size)
        assertEquals(1, dao.getSolves(sessionIds[0]).size)
        assertEquals(1, dao.getSolves(sessionIds[1]).size)
        assertEquals(1, dao.getSolves(sessionIds[1]).single().penalty)
    }
}
