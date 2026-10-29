package io.github.miclean.module.skip

/** Candidate restrictions depend on app/activity/source revision, not content
 * events. Expiry also refreshes package/home metadata; never cache node handles. */
class SkipCandidateCache<T>(private val now: () -> Long, private val ttlMs: Long = 30000) {
    private data class Key(val app: String, val activity: String?, val revision: Long)
    private var key: Key? = null
    private var value: T? = null
    private var loadedAt = 0L
    fun invalidate() { key = null; value = null }
    fun get(app: String, activity: String?, revision: Long, load: () -> T): T {
        val next = Key(app, activity, revision)
        if (key != next || now() - loadedAt >= ttlMs) {
            val fresh = load() // failed loads never replace a valid cache entry
            value = fresh; loadedAt = now(); key = next
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
