package app.trampis.timed.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val eventId: String = "333",
    val sessionType: String = "standard",
    val phaseCount: Int = 2,
    val targetMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

@Entity(
    tableName = "solves",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("createdAt")],
)
data class SolveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timeMillis: Long,
    val penalty: Int = 0,
    val scramble: String,
    val createdAt: Long = System.currentTimeMillis(),
    val comment: String = "",
    val phaseTimesJson: String = "[]",
)

@Entity(tableName = "active_timer")
data class ActiveTimerEntity(
    @PrimaryKey val id: Int = 1,
    val sessionId: Long,
    val startElapsedNanos: Long,
    val bootCount: Int,
    val sessionType: String,
    val phaseCount: Int,
    val penalty: Int,
    val scramble: String,
    val phaseSplitsJson: String = "[]",
)

@Dao
interface TimerDao {
    @Query("SELECT * FROM sessions ORDER BY pinned DESC, createdAt ASC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM solves WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC")
    fun observeSolves(sessionId: Long): Flow<List<SolveEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, createdAt ASC")
    suspend fun getSessions(): List<SessionEntity>

    @Query("SELECT * FROM solves WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getSolves(sessionId: Long): List<SolveEntity>

    @Query("SELECT * FROM solves WHERE id = :solveId LIMIT 1")
    suspend fun getSolve(solveId: Long): SolveEntity?

    @Query("SELECT * FROM active_timer WHERE id = 1 LIMIT 1")
    suspend fun getActiveTimer(): ActiveTimerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addSolve(solve: SolveEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveTimer(activeTimer: ActiveTimerEntity)

    @Query("DELETE FROM active_timer WHERE id = 1")
    suspend fun clearActiveTimer()

    @androidx.room.Transaction
    suspend fun finishActiveTimer(solve: SolveEntity): Long {
        val solveId = addSolve(solve)
        clearActiveTimer()
        return solveId
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addSolves(solves: List<SolveEntity>): List<Long>

    @androidx.room.Transaction
    suspend fun addImportedSessions(imported: List<ImportedSession>): List<Long> {
        return imported.map { item ->
            val sessionId = addSession(SessionEntity(name = item.name, eventId = item.eventId))
            if (item.solves.isNotEmpty()) addSolves(item.solves.map { solve ->
                SolveEntity(sessionId = sessionId, timeMillis = solve.timeMillis, penalty = solve.penalty,
                    scramble = solve.scramble, createdAt = solve.createdAt, comment = solve.comment)
            })
            sessionId
        }
    }

    @androidx.room.Transaction
    suspend fun addBackupSessions(backups: List<BackupSession>): List<Long> = backups.map { item ->
        val sessionId = addSession(SessionEntity(
            name = item.name, eventId = item.eventId, sessionType = item.sessionType,
            phaseCount = item.phaseCount, targetMillis = item.targetMillis, createdAt = item.createdAt, pinned = item.pinned,
        ))
        if (item.solves.isNotEmpty()) addSolves(item.solves.map { solve ->
            SolveEntity(sessionId = sessionId, timeMillis = solve.timeMillis, penalty = solve.penalty,
                scramble = solve.scramble, createdAt = solve.createdAt, comment = solve.comment, phaseTimesJson = solve.phaseTimesJson)
        })
        sessionId
    }

    @Query("UPDATE solves SET penalty = :penalty WHERE id = :solveId")
    suspend fun setPenalty(solveId: Long, penalty: Int)

    @Query("UPDATE solves SET comment = :comment WHERE id = :solveId")
    suspend fun setComment(solveId: Long, comment: String)

    @Query("UPDATE solves SET penalty = :penalty WHERE id IN (:solveIds)")
    suspend fun setPenalties(solveIds: List<Long>, penalty: Int)

    @Query("UPDATE sessions SET eventId = :eventId WHERE id = :sessionId")
    suspend fun setEvent(sessionId: Long, eventId: String)

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :sessionId")
    suspend fun setPinned(sessionId: Long, pinned: Boolean)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM solves WHERE id = :solveId")
    suspend fun deleteSolve(solveId: Long)

    @Query("DELETE FROM solves WHERE id IN (:solveIds)")
    suspend fun deleteSolves(solveIds: List<Long>)

    @Query("UPDATE solves SET sessionId = :sessionId, phaseTimesJson = COALESCE(:phaseTimesJson, phaseTimesJson) WHERE id IN (:solveIds)")
    suspend fun moveSolves(solveIds: List<Long>, sessionId: Long, phaseTimesJson: String?)

    @androidx.room.Transaction
    suspend fun copySolves(solveIds: List<Long>, sessionId: Long, preservePhases: Boolean) {
        solveIds.forEach { solveId ->
            val solve = getSolve(solveId) ?: return@forEach
            addSolve(solve.copy(id = 0, sessionId = sessionId, phaseTimesJson = if (preservePhases) solve.phaseTimesJson else "[]"))
        }
    }
}

@Database(entities = [SessionEntity::class, SolveEntity::class, ActiveTimerEntity::class], version = 3, exportSchema = true)
abstract class TrampisDatabase : RoomDatabase() {
    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile private var instance: TrampisDatabase? = null

        fun get(context: Context): TrampisDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrampisDatabase::class.java,
                "trampis-timed.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        internal val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN phaseCount INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE sessions ADD COLUMN targetMillis INTEGER")
            }
        }

        internal val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS active_timer (id INTEGER NOT NULL, sessionId INTEGER NOT NULL, startElapsedNanos INTEGER NOT NULL, bootCount INTEGER NOT NULL, sessionType TEXT NOT NULL, phaseCount INTEGER NOT NULL, penalty INTEGER NOT NULL, scramble TEXT NOT NULL, phaseSplitsJson TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}
