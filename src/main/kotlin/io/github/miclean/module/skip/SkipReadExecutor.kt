package io.github.miclean.module.skip

import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class SkipReadFailure(val reason: String) : RuntimeException()

/** A slow platform callback cannot hold the matcher or create an unbounded queue. */
class SkipReadExecutor : AutoCloseable {
    private val pool = ThreadPoolExecutor(0, 2, 10, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "NoAdMi-node-read").apply { isDaemon = true } })
    private class Ticket<T> {
        val done = CountDownLatch(1)
        var abandoned = false
        var result: Result<T>? = null
    }
    fun <T> read(timeoutMs: Long = 200, discard: (T) -> Unit = {}, operation: () -> T): T {
        val ticket = Ticket<T>()
        try {
            pool.execute {
                val result = runCatching(operation)
                synchronized(ticket) {
                    if (ticket.abandoned) result.onSuccess { runCatching { discard(it) } }
                    else ticket.result = result
                    ticket.done.countDown()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { throw SkipReadFailure("native_busy") }
        try { ticket.done.await(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        synchronized(ticket) {
            ticket.result?.let { return it.getOrThrow() }
            ticket.abandoned = true
        }
        throw SkipReadFailure("native_timeout")
    }
    override fun close() { pool.shutdown() }
}
