package io.github.miclean.module.skip

import java.util.concurrent.Executor

/** One in-flight write and one latest snapshot. Producers never wait for disk I/O.
 * Submit an empty snapshot for clear: an older in-flight write finishes before
 * the clear, and cannot resurrect records after it. */
class SkipLatestWriter<T>(private val executor: Executor, private val write: (T) -> Unit,
    private val failed: (Exception) -> Unit = {}) {
    private class Snapshot<T>(val value: T)
    private val lock = Any()
    private var pending: Snapshot<T>? = null
    private var running = false
    fun submit(value: T) {
        synchronized(lock) {
            pending = Snapshot(value)
            if (running) return
            running = true
        }
        executor.execute {
            while (true) {
                val next = synchronized(lock) {
                    pending.also { pending = null; if (it == null) running = false }
                } ?: break
                try { write(next.value) } catch (error: Exception) { failed(error) }
            }
        }
    }
}
