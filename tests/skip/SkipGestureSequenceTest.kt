package io.github.miclean.module.skip

import java.util.PriorityQueue
private var checks = 0
private fun verify(value: Boolean) { checks++; check(value) { "continuous gesture assertion $checks" } }
private class Harness(val duration: Long = 350, val distance: Float = 350f) {
    data class Stroke(val id: Int, val previous: Int?, val segment: SkipGestureSequence.Segment)
    private data class Job(val at: Long, val order: Int, val task: () -> Unit)
    var time = 0L; var valid = true; var reject = -1; var cancelAt = -1; var duplicate = false; var early = false
    val strokes = mutableListOf<Stroke>(); val results = mutableListOf<Pair<Boolean,String>>()
    private var order = 0
    private val jobs = PriorityQueue(compareBy<Job> { it.at }.thenBy { it.order })
    fun later(delay: Long, task: () -> Unit) { jobs.add(Job(time + delay, order++, task)) }
    val sequence = SkipGestureSequence<Stroke>(0f, 0f, distance, distance, duration,
        valid = { valid }, now = { time }, later = ::later,
        create = { previous, segment -> Stroke(strokes.size + 1, previous?.id, segment) },
        dispatch = { stroke, callback ->
            strokes.add(stroke)
            if (stroke.id == reject) false else {
                later(if (early) 0 else stroke.segment.duration) { callback(stroke.id != cancelAt); if (duplicate) callback(stroke.id != cancelAt) }
                true
            }
        }, finished = { ok, reason -> results.add(ok to reason) })
    fun drain() { var n = 0; while (jobs.isNotEmpty()) { check(n++ < 3000); val job = jobs.remove(); time = job.at; job.task() } }
}
fun main() {
    val normal = Harness(); verify(normal.sequence.start()); verify(!normal.sequence.start()); normal.drain()
    verify(normal.results == listOf(true to "")); verify(normal.time == 350L)
    verify(normal.strokes.map { it.segment.duration } == listOf(100L,100L,100L,50L))
    verify(normal.strokes.last().segment.end == SkipGestureSequence.Point(350,350))
    verify(!normal.strokes.last().segment.continues)
    normal.strokes.zipWithNext().forEach { (a,b) -> verify(b.previous == a.id); verify(a.segment.end == b.segment.start) }
    for (explicitCancel in listOf(false,true)) {
        val h = Harness(1000); h.later(150) { if (explicitCancel) h.sequence.cancel() else h.valid = false }
        verify(h.sequence.start()); h.drain(); verify(h.results.single() == (false to "gesture_stale"))
        verify(h.time <= 201); verify(h.strokes.size == 3)
        verify(h.strokes.last().segment.let { it.duration == 1L && !it.continues && it.start == it.end })
        verify(h.strokes.last().previous == h.strokes[1].id)
    }
    val initialReject = Harness().apply { reject = 1 }; verify(!initialReject.sequence.start()); initialReject.drain()
    verify(initialReject.results == listOf(false to "gesture_rejected")); verify(initialReject.strokes.size == 1)
    val laterReject = Harness().apply { reject = 2 }; verify(laterReject.sequence.start()); laterReject.drain()
    verify(laterReject.results == listOf(false to "gesture_rejected")); verify(laterReject.strokes.last().previous == 1)
    val failedRelease = Harness(1000).apply { reject = 3; later(150) { valid = false } }
    verify(failedRelease.sequence.start()); failedRelease.drain(); verify(failedRelease.results == listOf(false to "gesture_release_unconfirmed"))
    val cancelled = Harness().apply { cancelAt = 1 }; verify(cancelled.sequence.start()); cancelled.drain()
    verify(cancelled.results == listOf(false to "gesture_cancelled")); verify(cancelled.strokes.size == 1)
    val duplicated = Harness().apply { duplicate = true }; verify(duplicated.sequence.start()); duplicated.drain()
    verify(duplicated.results.size == 1); verify(duplicated.strokes.size == 4)
    val stationary = Harness(1000, 1f).apply { early = true }; verify(stationary.sequence.start()); stationary.drain()
    verify(stationary.time == 1000L); verify(stationary.strokes.size < 10); verify(stationary.results.single().first)
    val noScope = Harness().apply { valid = false }; verify(!noScope.sequence.start()); verify(noScope.strokes.isEmpty()); verify(noScope.results.single().second == "gesture_stale")
    val endChange = Harness(100).apply { later(99) { valid = false } }; verify(endChange.sequence.start()); endChange.drain(); verify(!endChange.results.single().first)
    println("PASS: $checks segmented swipe continuity, stale/cancel release, rejection, duplicate callbacks and stationary timing checks")
}
