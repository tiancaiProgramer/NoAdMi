package io.github.miclean.module.skip

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val reader = SkipReadExecutor()
    val release = CountDownLatch(1)
    val discarded = CountDownLatch(1)
    var timedOut = false
    try {
        reader.read(25, discard = { value: Int -> check(value == 7); discarded.countDown() }) {
            release.await(); 7
        }
    } catch (e: SkipReadFailure) { timedOut = e.reason == "native_timeout" }
    check(timedOut)
    // A stale window callback must not prevent a fresh window query succeeding.
    check(reader.read { 42 } == 42)
    release.countDown(); check(discarded.await(2, TimeUnit.SECONDS))
    reader.close()

    val bounded = SkipReadExecutor()
    val unblock = CountDownLatch(1)
    val cleaned = CountDownLatch(2)
    val running = AtomicInteger()
    repeat(2) {
        try {
            bounded.read(25, discard = { _: Int -> cleaned.countDown() }) {
                running.incrementAndGet(); unblock.await(); 1
            }
            error("Blocked query returned")
        } catch (e: SkipReadFailure) { check(e.reason == "native_timeout") }
    }
    check(running.get() == 2)
    try { bounded.read { error("A third read must never run") }; error("Unbounded queue") }
    catch (e: SkipReadFailure) { check(e.reason == "native_busy") }
    unblock.countDown(); check(cleaned.await(2, TimeUnit.SECONDS)); bounded.close()
    try { bounded.read { 1 }; error("Closed reader accepted a query") }
    catch (e: SkipReadFailure) { check(e.reason == "native_busy") }
    println("PASS: bounded native callback timeouts, independent retry, late-result disposal and shutdown")
}
