package app.trampis.timed.domain

/** The timer reads a monotonic clock; display refreshes never determine recorded time. */
enum class TimerMode { IDLE, INSPECTING, RUNNING, STOPPED }

class TimerEngine(private val nowNanos: () -> Long) {
    var mode: TimerMode = TimerMode.IDLE
        private set
    var startedAtNanos: Long? = null
        private set
    var inspectionStartedAtNanos: Long? = null
        private set
    var lastDurationNanos: Long = 0L
        private set

    val isRunning: Boolean get() = mode == TimerMode.RUNNING
    val isInspecting: Boolean get() = mode == TimerMode.INSPECTING

    fun startInspection() {
        if (isRunning) return
        startedAtNanos = null
        inspectionStartedAtNanos = nowNanos()
        lastDurationNanos = 0L
        mode = TimerMode.INSPECTING
    }

    fun inspectionElapsedNanos(): Long = if (isInspecting) {
        (nowNanos() - checkNotNull(inspectionStartedAtNanos)).coerceAtLeast(0L)
    } else 0L

    fun start() {
        if (isRunning) return
        startedAtNanos = nowNanos()
        inspectionStartedAtNanos = null
        lastDurationNanos = 0L
        mode = TimerMode.RUNNING
    }

    /** Restores a running timer from a monotonic timestamp saved in the same device boot. */
    fun restoreRunning(startedAtNanos: Long) {
        val now = nowNanos()
        require(startedAtNanos >= 0L && startedAtNanos <= now) { "Saved timer timestamp is not valid for this device boot." }
        this.startedAtNanos = startedAtNanos
        inspectionStartedAtNanos = null
        lastDurationNanos = 0L
        mode = TimerMode.RUNNING
    }

    fun elapsedNanos(): Long = if (isRunning) {
        (nowNanos() - checkNotNull(startedAtNanos)).coerceAtLeast(0L)
    } else lastDurationNanos

    fun stop(): Long? {
        if (!isRunning) return null
        lastDurationNanos = elapsedNanos()
        mode = TimerMode.STOPPED
        startedAtNanos = null
        inspectionStartedAtNanos = null
        return lastDurationNanos
    }

    fun reset() {
        mode = TimerMode.IDLE
        startedAtNanos = null
        inspectionStartedAtNanos = null
        lastDurationNanos = 0L
    }
}
