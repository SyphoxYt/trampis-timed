package app.trampis.timed.ui

import android.app.Application
import android.media.MediaPlayer
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.trampis.timed.data.SessionEntity
import app.trampis.timed.data.ActiveTimerEntity
import app.trampis.timed.data.SolveEntity
import app.trampis.timed.data.SessionExchange
import app.trampis.timed.data.SessionWithSolves
import app.trampis.timed.data.TrampisDatabase
import app.trampis.timed.R
import app.trampis.timed.domain.Penalty
import app.trampis.timed.domain.CubeEvents
import app.trampis.timed.domain.ScrambleProvider
import app.trampis.timed.domain.TimerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.trampisPreferences by preferencesDataStore(name = "trampis-settings")
private val InspectionKey = booleanPreferencesKey("inspection_enabled")
private val InspectionCountdownKey = booleanPreferencesKey("inspection_countdown")
private val HoldTimeKey = floatPreferencesKey("hold_time_seconds")
private val HapticsKey = booleanPreferencesKey("haptics_enabled")
private val InspectionAlertsKey = booleanPreferencesKey("inspection_alerts_enabled")
private val TimerStatsKey = booleanPreferencesKey("timer_stats_enabled")
private val ScrambleDrawingKey = booleanPreferencesKey("scramble_drawing_enabled")
private val DisplayDpKey = androidx.datastore.preferences.core.intPreferencesKey("display_decimal_places")
private val SelectedSessionKey = androidx.datastore.preferences.core.longPreferencesKey("selected_session_id")
private val AppearanceOverrideKey = booleanPreferencesKey("appearance_override")
private val DarkModeKey = booleanPreferencesKey("appearance_dark")
private val StaticGradientKey = booleanPreferencesKey("static_gradient")
private val GraphGlowKey = booleanPreferencesKey("graph_glow")
private val GraphAnimationKey = booleanPreferencesKey("graph_animation")
private val ConfettiKey = booleanPreferencesKey("show_confetti")
private val BoldTimerKey = booleanPreferencesKey("bold_timer_digits")
private val AccentPresetKey = androidx.datastore.preferences.core.stringPreferencesKey("accent_preset")
private val StatsMotionKey = booleanPreferencesKey("stats_card_motion")
private val TimerSizeKey = floatPreferencesKey("timer_size_scale")
private val ScrambleSizeKey = androidx.datastore.preferences.core.intPreferencesKey("scramble_text_size")
private val TimerFontKey = androidx.datastore.preferences.core.stringPreferencesKey("timer_font_preset")

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = TrampisDatabase.get(application).timerDao()
    private val timer = TimerEngine(SystemClock::elapsedRealtimeNanos)
    private val scrambleProvider = ScrambleProvider()
    private val activeRunMutex = Mutex()

    val sessions = dao.observeSessions().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    private val currentSessionId = MutableStateFlow(0L)
    val selectedSession = combine(sessions, currentSessionId) { list, selectedId ->
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val solves = currentSessionId.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList<SolveEntity>()) else dao.observeSolves(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isRunning = MutableStateFlow(false)
    val isInspecting = MutableStateFlow(false)
    val isPreparing = MutableStateFlow(false)
    val inspectionEnabled = MutableStateFlow(false)
    val inspectionCountdownEnabled = MutableStateFlow(false)
    val holdTimeMillis = MutableStateFlow(500L)
    val hapticsEnabled = MutableStateFlow(true)
    val inspectionAlertsEnabled = MutableStateFlow(true)
    val timerStatsEnabled = MutableStateFlow(true)
    val scrambleDrawingEnabled = MutableStateFlow(true)
    val displayDecimalPlaces = MutableStateFlow(3)
    val appearanceOverride = MutableStateFlow(false)
    val appearanceDark = MutableStateFlow(false)
    val staticGradient = MutableStateFlow(true)
    val graphGlow = MutableStateFlow(true)
    val graphAnimation = MutableStateFlow(true)
    val showConfetti = MutableStateFlow(true)
    val boldTimerDigits = MutableStateFlow(true)
    val accentPreset = MutableStateFlow("blue")
    val statsCardMotion = MutableStateFlow(true)
    val timerSizeScale = MutableStateFlow(1f)
    val scrambleTextSize = MutableStateFlow(17)
    val timerFontPreset = MutableStateFlow("recursive")
    val elapsedNanos = MutableStateFlow(0L)
    val inspectionNanos = MutableStateFlow(0L)
    val phaseSplitsNanos = MutableStateFlow<List<Long>>(emptyList())
    val scramble = MutableStateFlow("Preparing official 3×3 scramble…")
    val scrambleSvg = MutableStateFlow<String?>(null)
    val isScrambleLocked = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var activeSessionId = 0L
    private var activeScramble = ""
    private var activePenalty = Penalty.NONE
    private var activeSessionType = "standard"
    private var activePhaseCount = 2
    private var lastInspectionAlertSecond = 0L
    private var recoveryWarning: String? = null

    init {
        viewModelScope.launch {
            application.trampisPreferences.data.collect { prefs ->
                inspectionEnabled.value = prefs[InspectionKey] ?: false
                inspectionCountdownEnabled.value = prefs[InspectionCountdownKey] ?: false
                holdTimeMillis.value = ((prefs[HoldTimeKey] ?: 0.5f) * 1000).toLong()
                hapticsEnabled.value = prefs[HapticsKey] ?: true
                inspectionAlertsEnabled.value = prefs[InspectionAlertsKey] ?: true
                timerStatsEnabled.value = prefs[TimerStatsKey] ?: true
                scrambleDrawingEnabled.value = prefs[ScrambleDrawingKey] ?: true
                displayDecimalPlaces.value = prefs[DisplayDpKey] ?: 3
                appearanceOverride.value = prefs[AppearanceOverrideKey] ?: false
                appearanceDark.value = prefs[DarkModeKey] ?: false
                staticGradient.value = prefs[StaticGradientKey] ?: true
                graphGlow.value = prefs[GraphGlowKey] ?: true
                graphAnimation.value = prefs[GraphAnimationKey] ?: true
                showConfetti.value = prefs[ConfettiKey] ?: true
                boldTimerDigits.value = prefs[BoldTimerKey] ?: true
                accentPreset.value = prefs[AccentPresetKey] ?: "blue"
                statsCardMotion.value = prefs[StatsMotionKey] ?: true
                timerSizeScale.value = (prefs[TimerSizeKey] ?: 1f).coerceIn(.85f, 1.2f)
                scrambleTextSize.value = (prefs[ScrambleSizeKey] ?: 17).coerceIn(14, 23)
                timerFontPreset.value = prefs[TimerFontKey]?.takeIf { it == "recursive" || it == "system" } ?: "recursive"
            }
        }
        viewModelScope.launch {
            var sessionList = dao.getSessions()
            if (sessionList.isEmpty()) {
                val initial = SessionEntity(name = "3×3×3")
                val id = dao.addSession(initial)
                sessionList = listOf(initial.copy(id = id))
            }
            val savedId = application.trampisPreferences.data.first()[SelectedSessionKey] ?: 0L
            var chosen = sessionList.firstOrNull { it.id == savedId } ?: sessionList.first()
            val savedRun = dao.getActiveTimer()
            var restoredTimer = false
            if (savedRun != null) {
                val runSession = sessionList.firstOrNull { it.id == savedRun.sessionId }
                val bootCount = Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, 0)
                val now = SystemClock.elapsedRealtimeNanos()
                if (runSession != null && savedRun.bootCount == bootCount && savedRun.startElapsedNanos in 0L..now) {
                    runCatching { timer.restoreRunning(savedRun.startElapsedNanos) }
                        .onSuccess {
                            chosen = runSession
                            currentSessionId.value = runSession.id
                            activeSessionId = runSession.id
                            activeSessionType = savedRun.sessionType
                            activePhaseCount = savedRun.phaseCount.coerceIn(2, 8)
                            activePenalty = Penalty.entries.getOrElse(savedRun.penalty) { Penalty.NONE }
                            activeScramble = savedRun.scramble
                            phaseSplitsNanos.value = parseLongArray(savedRun.phaseSplitsJson)
                            scramble.value = savedRun.scramble
                            isRunning.value = true
                            elapsedNanos.value = timer.elapsedNanos()
                            restoredTimer = true
                        }
                }
                if (!restoredTimer) {
                    dao.clearActiveTimer()
                    recoveryWarning = "An interrupted timer was discarded because its original device session is unavailable."
                    error.value = recoveryWarning
                }
            }
            currentSessionId.value = chosen.id
            persistSelectedSession(chosen.id)
            if (!restoredTimer) nextScramble(chosen)
            sessions.collect { list ->
                if (list.isNotEmpty() && list.none { it.id == currentSessionId.value }) {
                    chosen = list.first()
                    currentSessionId.value = chosen.id
                    persistSelectedSession(chosen.id)
                    nextScramble(chosen)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                if (timer.isRunning) elapsedNanos.value = timer.elapsedNanos()
                if (timer.isInspecting) {
                    val elapsed = timer.inspectionElapsedNanos()
                    inspectionNanos.value = elapsed
                    val second = elapsed / 1_000_000_000L
                    if (inspectionAlertsEnabled.value) {
                        when {
                            second >= 12 && lastInspectionAlertSecond < 12 -> playInspectionAlert(R.raw.inspection_12)
                            second >= 8 && lastInspectionAlertSecond < 8 -> playInspectionAlert(R.raw.inspection_8)
                        }
                    }
                    lastInspectionAlertSecond = second
                } else lastInspectionAlertSecond = 0L
                delay(16)
            }
        }
    }

    fun selectSession(sessionId: Long) {
        currentSessionId.value = sessionId
        persistSelectedSession(sessionId)
        nextScramble()
    }

    fun setSessionPinned(sessionId: Long, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { dao.setPinned(sessionId, pinned) }
    }

    fun updateSession(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = session.copy(eventId = CubeEvents.fromId(session.eventId).id)
            dao.updateSession(updated)
            if (currentSessionId.value == session.id) nextScramble(updated)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRunning.value && activeSessionId == sessionId) {
                error.value = "Stop the timer before deleting its session."
                return@launch
            }
            dao.deleteSession(sessionId)
            val remaining = dao.getSessions()
            val nextId = remaining.firstOrNull()?.id ?: dao.addSession(SessionEntity(name = "3×3×3"))
            if (currentSessionId.value == sessionId) {
                currentSessionId.value = nextId
                persistSelectedSession(nextId)
                nextScramble(remaining.firstOrNull() ?: SessionEntity(id = nextId, name = "3×3×3"))
            }
        }
    }

    fun addSession(name: String, eventId: String, sessionType: String = "standard", phaseCount: Int = 2, targetMillis: Long? = null) {
        val cleanName = name.trim().ifEmpty { "3×3×3" }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                dao.addSession(SessionEntity(name = cleanName, eventId = CubeEvents.fromId(eventId).id, sessionType = sessionType, phaseCount = phaseCount, targetMillis = targetMillis))
            }
            currentSessionId.value = id
            persistSelectedSession(id)
            nextScramble(SessionEntity(id = id, name = cleanName, eventId = CubeEvents.fromId(eventId).id, sessionType = sessionType, phaseCount = phaseCount, targetMillis = targetMillis))
        }
    }

    suspend fun exportSessionCsv(sessionId: Long): String = withContext(Dispatchers.IO) {
        val session = dao.getSessions().firstOrNull { it.id == sessionId }
            ?: error("The selected session could not be found.")
        SessionExchange.csv(SessionWithSolves(session, dao.getSolves(sessionId)))
    }

    suspend fun exportCsTimerJson(): String = withContext(Dispatchers.IO) {
        val all = dao.getSessions().map { session ->
            SessionWithSolves(session, dao.getSolves(session.id))
        }
        SessionExchange.csTimer(all)
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val all = dao.getSessions().map { session -> SessionWithSolves(session, dao.getSolves(session.id)) }
        SessionExchange.backup(all)
    }

    suspend fun exportOds(): ByteArray = withContext(Dispatchers.IO) {
        val all = dao.getSessions().map { session -> SessionWithSolves(session, dao.getSolves(session.id)) }
        SessionExchange.ods(all)
    }

    suspend fun importSessions(text: String, filename: String): Int = withContext(Dispatchers.IO) {
        val backup = SessionExchange.parseBackup(text)
        if (backup != null) {
            val ids = dao.addBackupSessions(backup.map { session -> session.copy(eventId = CubeEvents.fromId(session.eventId).id) })
            val first = backup.first()
            currentSessionId.value = ids.first()
            persistSelectedSession(ids.first())
            nextScramble(SessionEntity(id = ids.first(), name = first.name, eventId = CubeEvents.fromId(first.eventId).id, sessionType = first.sessionType, phaseCount = first.phaseCount, targetMillis = first.targetMillis))
            return@withContext ids.size
        }
        val imported = SessionExchange.parse(text, filename)
        require(imported.isNotEmpty()) { "The file contains no sessions." }
        val ids = dao.addImportedSessions(imported.map { session ->
            session.copy(eventId = CubeEvents.fromId(session.eventId).id)
        })
        val firstId = ids.first()
        currentSessionId.value = firstId
        persistSelectedSession(firstId)
        nextScramble(SessionEntity(id = firstId, name = imported.first().name, eventId = CubeEvents.fromId(imported.first().eventId).id))
        imported.size
    }

    fun resetTimer() {
        timer.reset()
        isRunning.value = false
        isInspecting.value = false
        isPreparing.value = false
        elapsedNanos.value = 0L
        inspectionNanos.value = 0L
        phaseSplitsNanos.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            activeRunMutex.withLock { dao.clearActiveTimer() }
        }
    }

    fun nextScramble(sessionOverride: SessionEntity? = null) {
        val session = sessionOverride ?: sessions.value.firstOrNull { it.id == currentSessionId.value }
        if (session?.sessionType == "timerOnly") {
            scramble.value = ""
            scrambleSvg.value = null
            error.value = recoveryWarning
            return
        }
        val eventId = session?.eventId ?: "333"
        viewModelScope.launch {
            try {
                scramble.value = "Generating scramble…"
                val output = withContext(Dispatchers.IO) { scrambleProvider.nextWithDrawing(eventId) }
                scramble.value = output.notation
                scrambleSvg.value = output.drawingSvg
                error.value = recoveryWarning
            } catch (e: Exception) {
                scrambleSvg.value = null
                error.value = "TNoodle could not generate a scramble: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun toggleScrambleLock() {
        val wasLocked = isScrambleLocked.value
        isScrambleLocked.value = !wasLocked
        if (wasLocked) nextScramble()
    }

    fun setPlaygroundEvent(eventId: String) {
        val session = selectedSession.value ?: return
        val puzzleId = CubeEvents.fromId(eventId).id
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.setEvent(session.id, puzzleId) }
            try {
                val output = withContext(Dispatchers.IO) { scrambleProvider.nextWithDrawing(puzzleId) }
                scramble.value = output.notation
                scrambleSvg.value = output.drawingSvg
                error.value = null
            } catch (e: Exception) {
                scrambleSvg.value = null
                error.value = "TNoodle could not generate a scramble: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun applyPenalty(solveId: Long, penalty: Penalty) {
        viewModelScope.launch(Dispatchers.IO) { dao.setPenalty(solveId, penalty.ordinal) }
    }

    fun setComment(solveId: Long, comment: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.setComment(solveId, comment) }
    }

    fun deleteSolve(solveId: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteSolve(solveId) }
    }

    fun applyPenalty(solveIds: List<Long>, penalty: Penalty) {
        if (solveIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { dao.setPenalties(solveIds, penalty.ordinal) }
    }

    fun deleteSolves(solveIds: List<Long>) {
        if (solveIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { dao.deleteSolves(solveIds) }
    }

    fun moveSolves(solveIds: List<Long>, destination: SessionEntity) {
        if (solveIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.moveSolves(solveIds, destination.id, if (destination.sessionType == "multiphase") null else "[]")
        }
    }

    fun copySolves(solveIds: List<Long>, destination: SessionEntity) {
        if (solveIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.copySolves(solveIds, destination.id, destination.sessionType == "multiphase")
        }
    }

    fun setInspectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().trampisPreferences.edit { it[InspectionKey] = enabled }
            inspectionEnabled.value = enabled
            if (!enabled && timer.isInspecting) cancelInspection()
        }
    }

    fun setInspectionCountdownEnabled(enabled: Boolean) = updatePreference(InspectionCountdownKey, enabled)
    fun setHapticsEnabled(enabled: Boolean) = updatePreference(HapticsKey, enabled)
    fun setInspectionAlertsEnabled(enabled: Boolean) = updatePreference(InspectionAlertsKey, enabled)
    fun setTimerStatsEnabled(enabled: Boolean) = updatePreference(TimerStatsKey, enabled)
    fun setScrambleDrawingEnabled(enabled: Boolean) = updatePreference(ScrambleDrawingKey, enabled)
    fun setHoldTimeSeconds(seconds: Float) {
        val normalized = seconds.coerceIn(0f, 1f)
        holdTimeMillis.value = (normalized * 1000).toLong()
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[HoldTimeKey] = normalized } }
    }
    fun setDisplayDecimalPlaces(value: Int) {
        val normalized = value.coerceIn(2, 3)
        displayDecimalPlaces.value = normalized
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[DisplayDpKey] = normalized } }
    }
    fun setAppearanceOverride(value: Boolean) = updatePreference(AppearanceOverrideKey, value)
    fun setAppearanceDark(value: Boolean) = updatePreference(DarkModeKey, value)
    fun setStaticGradient(value: Boolean) = updatePreference(StaticGradientKey, value)
    fun setGraphGlow(value: Boolean) = updatePreference(GraphGlowKey, value)
    fun setGraphAnimation(value: Boolean) = updatePreference(GraphAnimationKey, value)
    fun setShowConfetti(value: Boolean) = updatePreference(ConfettiKey, value)
    fun setBoldTimerDigits(value: Boolean) = updatePreference(BoldTimerKey, value)
    fun setAccentPreset(value: String) {
        val normalized = value.takeIf { it in setOf("blue", "violet", "mint", "amber") } ?: "blue"
        accentPreset.value = normalized
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[AccentPresetKey] = normalized } }
    }
    fun setStatsCardMotion(value: Boolean) = updatePreference(StatsMotionKey, value)
    fun setTimerSizeScale(value: Float) {
        val normalized = value.coerceIn(.85f, 1.2f)
        timerSizeScale.value = normalized
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[TimerSizeKey] = normalized } }
    }
    fun setScrambleTextSize(value: Int) {
        val normalized = value.coerceIn(14, 23)
        scrambleTextSize.value = normalized
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[ScrambleSizeKey] = normalized } }
    }
    fun setTimerFontPreset(value: String) {
        val normalized = value.takeIf { it == "recursive" || it == "system" } ?: "recursive"
        timerFontPreset.value = normalized
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[TimerFontKey] = normalized } }
    }
    fun resetAppearance() {
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit {
            it.remove(AppearanceOverrideKey); it.remove(DarkModeKey); it.remove(StaticGradientKey)
            it.remove(GraphGlowKey); it.remove(GraphAnimationKey); it.remove(ConfettiKey)
            it.remove(BoldTimerKey); it.remove(AccentPresetKey)
            it.remove(StatsMotionKey)
            it.remove(TimerSizeKey); it.remove(ScrambleSizeKey)
            it.remove(TimerFontKey)
        } }
    }
    private fun persistSelectedSession(sessionId: Long) {
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[SelectedSessionKey] = sessionId } }
    }
    private fun <T> updatePreference(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        viewModelScope.launch { getApplication<Application>().trampisPreferences.edit { it[key] = value } }
    }

    fun shortPress() {
        if (timer.isRunning) return
        if (!inspectionEnabled.value) {
            startFromHold()
            return
        }
        timer.startInspection()
        lastInspectionAlertSecond = 0L
        isInspecting.value = true
        inspectionNanos.value = 0L
        error.value = null
    }

    private fun playInspectionAlert(resourceId: Int) {
        runCatching {
            MediaPlayer.create(getApplication<Application>(), resourceId)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    fun setPreparing(value: Boolean) { isPreparing.value = value }

    fun startFromHold() {
        if (timer.isRunning) return
        val sessionEntity = sessions.value.firstOrNull { it.id == currentSessionId.value }
        val timerOnly = sessionEntity?.sessionType == "timerOnly"
        if (!timerOnly && (scramble.value.isBlank() || scramble.value.startsWith("Preparing") || scramble.value.startsWith("Generating"))) {
            error.value = "Wait for the next scramble before starting."
            return
        }
        val session = currentSessionId.value
        if (session == 0L) {
            error.value = "Your session is still loading. Try again in a moment."
            return
        }
        if (activeSessionType == "timerOnly") {
            viewModelScope.launch(Dispatchers.IO) { activeRunMutex.withLock { dao.clearActiveTimer() } }
            activePenalty = Penalty.NONE
            phaseSplitsNanos.value = emptyList()
            return
        }
        val inspectedNanos = timer.inspectionElapsedNanos()
        activePenalty = when {
            inspectedNanos >= 17_000_000_000L -> Penalty.DNF
            inspectedNanos >= 15_000_000_000L -> Penalty.PLUS_TWO
            else -> Penalty.NONE
        }
        timer.start()
        activeSessionId = session
        activeSessionType = sessionEntity?.sessionType ?: "standard"
        activePhaseCount = sessionEntity?.phaseCount?.coerceIn(2, 8) ?: 2
        phaseSplitsNanos.value = emptyList()
        activeScramble = if (timerOnly) "" else scramble.value
        isRunning.value = true
        isInspecting.value = false
        isPreparing.value = false
        elapsedNanos.value = 0L
        error.value = null
        recoveryWarning = null
        queueActiveRunSave()
    }

    fun stopAtTouch() {
        if (timer.isRunning && activeSessionType == "multiphase") {
            val cumulative = timer.elapsedNanos()
            val currentSplits = phaseSplitsNanos.value
            if (currentSplits.size < activePhaseCount - 1) {
                phaseSplitsNanos.value = currentSplits + cumulative
                queueActiveRunSave()
                return
            }
        }
        val duration = timer.stop() ?: return
        isRunning.value = false
        elapsedNanos.value = duration
        val session = activeSessionId
        val phaseTimes = if (activeSessionType == "multiphase") phaseSplitsNanos.value + duration else emptyList()
        if (session == 0L) {
            error.value = "Your session could not be loaded. The solve was not saved."
            viewModelScope.launch(Dispatchers.IO) { activeRunMutex.withLock { dao.clearActiveTimer() } }
            return
        }
        val completedSolve = SolveEntity(
            sessionId = session,
            timeMillis = duration / 1_000_000L,
            penalty = activePenalty.ordinal,
            scramble = activeScramble,
            phaseTimesJson = if (phaseTimes.isEmpty()) "[]" else phaseTimes.joinToString(prefix = "[", postfix = "]") { (it / 1_000_000L).toString() },
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                activeRunMutex.withLock { dao.finishActiveTimer(completedSolve) }
            }
            activePenalty = Penalty.NONE
            phaseSplitsNanos.value = emptyList()
            if (!isScrambleLocked.value) nextScramble()
        }
    }

    private fun queueActiveRunSave() {
        val start = timer.startedAtNanos ?: return
        val activeRun = ActiveTimerEntity(
            sessionId = activeSessionId,
            startElapsedNanos = start,
            bootCount = Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.BOOT_COUNT, 0),
            sessionType = activeSessionType,
            phaseCount = activePhaseCount,
            penalty = activePenalty.ordinal,
            scramble = activeScramble,
            phaseSplitsJson = phaseSplitsNanos.value.joinToString(prefix = "[", postfix = "]"),
        )
        viewModelScope.launch(Dispatchers.IO) {
            activeRunMutex.withLock { dao.saveActiveTimer(activeRun) }
        }
    }

    private fun parseLongArray(json: String): List<Long> = json.removePrefix("[").removeSuffix("]")
        .split(',').mapNotNull { it.trim().toLongOrNull() }

    fun cancelInspection() {
        timer.reset()
        isInspecting.value = false
        inspectionNanos.value = 0L
        isPreparing.value = false
    }
}
