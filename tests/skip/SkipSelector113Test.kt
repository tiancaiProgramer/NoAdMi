package io.github.miclean.module.skip

import li.gkd.selector.*
private var checks = 0
private fun verify(value: Boolean) { checks++; check(value) { "selector 1.13 assertion $checks" } }
fun main() {
    val child = SkipNode(mapOf("name" to "Button", "text" to "Close", "clickable" to true))
    val root = SkipNode(mapOf("name" to "Root")); root.add(child)
    val reads = mutableListOf<String>()
    val adapter = object : SnapshotNodeAdapter() {
        override fun getAttr(target: Any, name: String): Any? { reads.add(name); return super.getAttr(target,name) }
    }
    val source = "Button[text='Skip' && clickable=true]"
    val selector = Selector.compile(source).value!!
    verify(selector.match(child,adapter) == null)
    val without = reads.toList(); reads.clear()
    val metrics = SkipSelectorMetrics()
    verify(selector.match(child,adapter,observer=metrics) == null)
    verify(reads == without); verify("clickable" !in reads)
    verify(metrics.summary().contains("skipped:")); verify(!metrics.summary().contains("Close")); verify(!metrics.summary().contains("Skip"))
    val bounded = SkipSelectorMetrics(2)
    repeat(30) { verify(selector.match(child,adapter,observer=bounded) == null) }
    verify(bounded.events == 2 && !bounded.enabled)
    for (expression in listOf("[text='Close']", "[!(text='Skip')]", "!([text='Skip'])", "[text~='C.*']", "[text!~='S.*']")) {
        val s = Selector.compile(expression).value!!
        verify(s.match(child,adapter) === child)
        val trace = s.matchWithTrace(child,adapter)
        verify(trace?.target === child)
        verify(s.match(child,adapter,observer=SkipSelectorMetrics()) === child)
    }
    // Native fast-query overrides may include the source itself. Queries must
    // still return descendants only and evaluate every remaining predicate.
    val fast = object : SnapshotNodeAdapter() {
        override fun getFastQueryDescendants(node: SkipNode, fastQueryList: List<FastQuery>) = sequenceOf(root,child,child)
    }
    val any = Selector.compile("[text='Close']").value!!
    verify(fast.querySelectorAll(root,any,MatchOptions(fastQuery=true)) == listOf(child))
    val falsePredicate = Selector.compile("[text='Close' && clickable=false]").value!!
    verify(fast.querySelector(root,falsePredicate,MatchOptions(fastQuery=true)) == null)
    // Sparse child traversal must preserve actual offsets in the success trace.
    val sparseChild = SkipNode(mapOf("name" to "Target"))
    val sparseRoot = SkipNode(mapOf("name" to "Root"), childCountHint=4, childLookup={ if(it==2) sparseChild else null })
    val sparse = Selector.compile("Target <n Root").value!!
    val trace = sparse.matchWithTrace(sparseRoot,SkipAdapter)
    verify(trace?.target === sparseRoot)
    verify(trace?.units?.single()?.steps?.single()?.offset == 2)
    println("PASS: $checks upstream selector matching/trace agreement, bounded private-free diagnostics, sparse offsets and fast-query predicate checks")
}
