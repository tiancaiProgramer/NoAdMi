package io.github.miclean.module.skip

/** An event may bring a check forward, but cannot postpone an already due check. */
class SkipCheckSchedule {
    var nextAt: Long? = null
        private set
    fun request(now: Long, delay: Long): Boolean {
        val due = now + delay.coerceAtLeast(0)
        if (nextAt?.let { it <= due } == true) return false
        nextAt = due
        return true
    }
    fun consume() { nextAt = null }
}

/** Keep local diagnostics bounded even when a page changes on every frame. */
class SkipDiagnosticGate(private val now: () -> Long) {
    private var last: Long? = null
    fun claim(): Boolean {
        val time = now()
        if (last?.let { time - it < 1000 } == true) return false
        last = time
        return true
    }
}

/** Event inspections do not postpone GKD's independent 300 ms follow-up job. */
class SkipFollowupSchedule {
    var dueAt: Long? = null
        private set
    fun request(now: Long, active: Boolean): Long? {
        if (!active) { dueAt = null; return null }
        if (dueAt == null) dueAt = now + 300
        return (dueAt!! - now).coerceAtLeast(0)
    }
    fun consume(now: Long): Boolean {
        if (dueAt?.let { now >= it } != true) return false
        dueAt = null; return true
    }
    fun clear() { dueAt = null }
}
