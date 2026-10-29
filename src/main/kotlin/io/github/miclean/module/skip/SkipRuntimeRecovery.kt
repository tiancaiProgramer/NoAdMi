package io.github.miclean.module.skip

/** Bounded retries per outage. A briefly revived worker cannot cause a hot loop. */
class SkipRuntimeRecovery(private val now: () -> Long) {
    private var used = 0
    private var healthySince: Long? = null
    fun reset() { used = 0; healthySince = null }
    fun connected() { if (healthySince == null) healthySince = now() }
    fun failed(): Long? {
        if (healthySince?.let { now() - it >= 30000 } == true) used = 0
        healthySince = null
        return DELAYS.getOrNull(used)?.also { used++ }
    }
    companion object { private val DELAYS = listOf(1000L, 3000L, 8000L) }
}
