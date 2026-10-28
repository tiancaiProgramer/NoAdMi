package io.github.miclean.module.skip

/** GKD A11yState: repeated verified Activity state events create a new scene after 1 s. */
data class SkipActivityScene(val app: String = "", val activity: String? = null, val generation: Long = 0)
class SkipActivityTracker(private val clock: () -> Long) {
    @Volatile var current = SkipActivityScene()
        private set
    private var acceptedAt: Long? = null
    @Synchronized fun app(packageName: String): SkipActivityScene {
        if (current.app != packageName) {
            current = SkipActivityScene(packageName, generation = current.generation + 1)
            acceptedAt = null
        }
        return current
    }
    /** A verified default-display TaskStack transition is authoritative, including same-class reopen. */
    @Synchronized fun task(packageName: String, name: String): SkipActivityScene {
        app(packageName)
        current = SkipActivityScene(packageName, name, current.generation + 1)
        acceptedAt = clock()
        return current
    }
    /** Call only for real Activity names, never generic window or content-change events. */
    @Synchronized fun activity(packageName: String, name: String): SkipActivityScene {
        app(packageName)
        val now = clock()
        if (current.activity == name && acceptedAt?.let { now - it < 1000 } == true) return current
        current = SkipActivityScene(packageName, name, current.generation + 1)
        acceptedAt = now
        return current
    }
}
