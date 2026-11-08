package io.github.miclean.module.skip

import kotlin.math.roundToInt

/** Callback adaptation of GKD 1.13 ContinuousSwipe. One input lane until UP is confirmed. */
class SkipGestureSequence<S : Any>(
    private val startX: Float, private val startY: Float,
    private val endX: Float, private val endY: Float,
    private val duration: Long,
    private val valid: () -> Boolean,
    private val now: () -> Long,
    private val later: (Long, () -> Unit) -> Unit,
    private val create: (S?, Segment) -> S,
    private val dispatch: (S, (Boolean) -> Unit) -> Boolean,
    private val finished: (Boolean, String) -> Unit,
    private val segmentMillis: Long = 100,
) {
    data class Point(val x: Int, val y: Int)
    data class Segment(val start: Point, val end: Point, val duration: Long, val continues: Boolean)
    private var held: S? = null
    private var elapsed = 0L
    private var position = point(0)
    private var ending = false
    private var ended = false
    private var cancelled = false
    private var waiting = false
    private var started = false
    private fun point(time: Long) = Point(
        (startX + (endX - startX) * (time.toDouble() / duration)).roundToInt(),
        (startY + (endY - startY) * (time.toDouble() / duration)).roundToInt(),
    )
    init {
        require(duration in 1..60000 && segmentMillis in 1..100)
        require(listOf(startX, startY, endX, endY).all { it.isFinite() })
    }
    @Synchronized fun start(): Boolean {
        if (started) return false
        started = true
        return advance()
    }
    /** Let a submitted segment settle before releasing its continuing pointer. */
    @Synchronized fun cancel() {
        cancelled = true
        if (!waiting) end(false, "gesture_stale")
    }
    @Synchronized private fun end(success: Boolean, reason: String) {
        if (ending || ended) return
        ending = true
        val previous = held
        if (previous == null) { complete(success, reason); return }
        // A continuing stroke still owns a down pointer. Release that same pointer;
        // a new tap would interrupt it and may trigger an unrelated UI control.
        val release = create(previous, Segment(position, position, 1, false))
        if (!dispatch(release) { held = null; complete(success, reason) }) {
            complete(false, "gesture_release_unconfirmed")
        }
    }
    @Synchronized private fun complete(success: Boolean, reason: String) {
        if (ended) return
        ended = true
        finished(success, reason)
    }
    @Synchronized private fun advance(): Boolean {
        if (ending || ended) return false
        if (cancelled || !valid()) { end(false, "gesture_stale"); return false }
        if (elapsed >= duration) { end(true, ""); return true }
        val length = minOf(segmentMillis, duration - elapsed)
        val next = point(elapsed + length)
        val continues = elapsed + length < duration
        val started = now()
        fun nextSegment() {
            later((length - (now() - started)).coerceAtLeast(0)) {
                elapsed += length
                advance()
            }
        }
        // Android emits no MOVE for identical integer coordinates. Keep the
        // stationary wait cancellable rather than submitting an empty continuation.
        if (held != null && next == position && continues) { nextSegment(); return true }
        val stroke = create(held, Segment(position, next, length, continues))
        var delivered = false
        waiting = true
        val accepted = dispatch(stroke) { completed ->
            synchronized(this) {
                if (delivered || ending || ended) return@dispatch
                delivered = true
                waiting = false
                if (!completed) { held = null; end(false, "gesture_cancelled"); return@dispatch }
                held = stroke.takeIf { continues }
                position = next
                if (cancelled || !valid()) end(false, "gesture_stale") else nextSegment()
            }
        }
        if (!accepted) { delivered = true; waiting = false; end(false, "gesture_rejected") }
        return accepted
    }
}
