package io.github.miclean.module.skip

import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorEvaluationKind
import li.gkd.selector.SelectorObserver

/** Count upstream explanation events without retaining nodes, text, selector strings or values. */
class SkipSelectorMetrics(private val limit: Int = 64) : SelectorObserver<SkipNode> {
    private val counts = linkedMapOf<SelectorEvaluationKind, Int>()
    var events = 0; private set
    override val enabled: Boolean get() = events < limit
    override fun record(event: SelectorEvaluation<SkipNode>) {
        if (!enabled) return
        events++
        counts[event.kind] = (counts[event.kind] ?: 0) + 1
    }
    fun summary(): String = counts.entries.joinToString(",") { "${it.key.key}:${it.value}" }
}
