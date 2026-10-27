package io.github.miclean.module.skip

import li.gkd.app.rule.RuleExecutionParameters
import li.gkd.app.subscription.RawSubscription
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector

/** Stable snapshot: selector traversal never mixes nodes from different accessibility events. */
class SkipNode(val attrs: Map<String, Any?>, val handle: Any? = null) {
    val children = mutableListOf<SkipNode>()
    var parent: SkipNode? = null
    fun add(child: SkipNode) { child.parent = this; children.add(child) }
}

object SkipAdapter : NodeAdapter<SkipNode>() {
    override fun getAttr(target: Any, name: String): Any? {
        val node = target as? SkipNode ?: return null
        return when (name) {
            "parent" -> node.parent
            "childCount" -> node.children.size
            "index" -> node.attrs["index"] ?: 0
            else -> node.attrs[name]
        }
    }
    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
        if (target is SkipNode && name == "getChild" && args.size == 1) target.children.getOrNull(args[0] as Int) else null
    override fun getName(node: SkipNode): String? = node.attrs["name"] as? String
    override fun getChildCount(node: SkipNode): Int = node.children.size
    override fun getChild(node: SkipNode, index: Int): SkipNode? = node.children.getOrNull(index)
    override fun getParent(node: SkipNode): SkipNode? = node.parent
    override fun getNodeKey(node: SkipNode): Any = node
}

class SkipRule(val source: SkipSource, val app: String, val group: RawSubscription.RawGroupProps,
    val raw: RawSubscription.RawRuleProps, val index: Int) {
    val params = RuleExecutionParameters(raw, group)
    val identity = "${source.id}/$app/${group.key}/$index"
    fun query(root: SkipNode): SkipNode? {
        fun query(selector: Selector): SkipNode? =
            if (selector.isMatchRoot) selector.match(root, SkipAdapter, params.matchOptions)
            else selector.match(root, SkipAdapter, params.matchOptions) ?: SkipAdapter.querySelector(root, selector, params.matchOptions)
        if (params.excludeMatches.any { query(it) != null }) return null
        if (params.excludeAllMatches.isNotEmpty() && params.excludeAllMatches.all { query(it) != null }) return null
        var target: SkipNode? = null
        if (params.anyMatches.isNotEmpty()) target = params.anyMatches.firstNotNullOfOrNull { query(it) } ?: return null
        for (selector in params.matches) target = query(selector) ?: return null
        return target
    }
}

/** Version/activity restrictions are resolved before matching, including inherited group fields. */
object SkipScope {
    fun activity(app: String, current: String?, included: List<String>?, excluded: List<String>?): Boolean {
        fun full(value: String) = if (value.startsWith('.')) app + value else value
        if (current == null) return included.isNullOrEmpty() && excluded.isNullOrEmpty()
        return excluded.orEmpty().none { current.startsWith(full(it)) } &&
            (included.isNullOrEmpty() || included.any { current.startsWith(full(it)) })
    }
    fun version(code: Int?, name: String?, codes: RawSubscription.IntegerMatcher?, names: RawSubscription.StringMatcher?): Boolean =
        (codes == null || codes.match(code)) && (names == null || names.match(name))
    fun applicable(source: SkipSource, group: RawSubscription.RawGroupProps, raw: RawSubscription.RawRuleProps,
        app: String, current: String?, code: Int?, name: String?, system: Boolean, launcher: Boolean): Boolean {
        if (!source.groupEnabled(if (group is RawSubscription.RawAppGroup) app else "*", group)) return false
        if (source.apps[app] == false) return false
        if (group is RawSubscription.RawAppGroup && raw is RawSubscription.RawAppRule) {
            return activity(app, current, raw.activityIds ?: group.activityIds, raw.excludeActivityIds ?: group.excludeActivityIds) &&
                version(code, name, raw.versionCode ?: group.versionCode, raw.versionName ?: group.versionName)
        }
        group as RawSubscription.RawGlobalGroup; raw as RawSubscription.RawGlobalRule
        val parent = group.apps.orEmpty().lastOrNull { it.id == app }
        val child = raw.apps.orEmpty().lastOrNull { it.id == app }
        if (parent?.enable == false) return false
        if (parent == null && child == null) {
            if (launcher && group.matchLauncher != true) return false
            if (system && group.matchSystemApp != true) return false
            if (group.matchAnyApp == false) return false
        }
        val prefix = group.disableIfAppGroupMatch?.ifEmpty { group.name }
        if (prefix != null && source.subscription.getAppGroups(app).any { it.ignoreGlobalGroupMatch != true &&
                it.name.startsWith(prefix) && source.groupEnabled(app, it) }) return false
        return activity(app, current, child?.activityIds ?: parent?.activityIds, child?.excludeActivityIds ?: parent?.excludeActivityIds) &&
            version(code, name, child?.versionCode ?: parent?.versionCode, child?.versionName ?: parent?.versionName)
    }
}

/** Per-screen counters, delays and prerequisite chains survive repeated accessibility events. */
class SkipSession(private val now: () -> Long) {
    private class Counter(var time: Long = Long.MIN_VALUE / 2, var count: Int = 0)
    private class State(val entered: Long) { var delayed: Long? = null; var cooldown = Counter(); var maximum = Counter() }
    private val states = mutableMapOf<String, State>()
    private var last: SkipRule? = null
    private var app = ""; private var activity: String? = null
    private var previous = emptySet<String>()
    fun transition(nextApp: String, nextActivity: String?, rules: List<SkipRule>) {
        val appChanged = app != nextApp; val activityChanged = appChanged || activity != nextActivity
        rules.forEach { rule ->
                val reset = rule.params.resetMatch ?: "activity"
                if (appChanged || (activityChanged && reset == "activity") || (reset == "match" && rule.identity !in previous)) states.remove(rule.identity)
        }
        if (activityChanged) {
            last = null; app = nextApp; activity = nextActivity
        }
        previous = rules.map { it.identity }.toSet()
        rules.forEach { state(it) }
        rules.forEach { rule ->
            fun other(key: Int?): SkipRule? = if (key == null) null else rules.firstOrNull { it.source.id == rule.source.id && it.app == rule.app &&
                (it.group.key == rule.group.key || rule.group.scopeKeys.orEmpty().contains(it.group.key)) && it.raw.key == key }
            other(rule.params.actionCdKey)?.let { state(rule).cooldown = state(it).cooldown }
            other(rule.params.actionMaximumKey)?.let { state(rule).maximum = state(it).maximum }
        }
    }
    private fun state(rule: SkipRule) = states.getOrPut(rule.identity) { State(now()) }
    fun ready(rule: SkipRule): Boolean {
        val s = state(rule); val p = rule.params; val t = now()
        if (p.actionMaximum != null && s.maximum.count >= p.actionMaximum!!) return false
        if (p.preKeys.isNotEmpty() && (last == null || last!!.source.id != rule.source.id ||
                last!!.app != rule.app || (last!!.group.key != rule.group.key && !rule.group.scopeKeys.orEmpty().contains(last!!.group.key)) ||
                !p.preKeys.contains(last!!.raw.key))) return false
        return t - s.entered >= p.matchDelay && (p.matchTime == null || t - s.entered <= p.matchDelay + p.matchTime!!) &&
            t - s.cooldown.time >= p.actionCd
    }
    fun matched(rule: SkipRule): Boolean {
        val state = state(rule); val time = now()
        if (rule.params.actionDelay <= 0) return true
        if (state.delayed == null) state.delayed = time
        return time - state.delayed!! >= rule.params.actionDelay
    }
    fun missed(rule: SkipRule) { state(rule).delayed = null }
    fun success(rule: SkipRule) {
        val s = state(rule); s.cooldown.time = now(); s.maximum.count++; s.delayed = null; last = rule
    }
    fun needsPoll(rule: SkipRule): Boolean {
        val s = state(rule); val p = rule.params; val t = now() - s.entered
        if (p.actionMaximum != null && s.maximum.count >= p.actionMaximum!!) return false
        if (p.preKeys.isNotEmpty() && !ready(rule) && t >= p.matchDelay) return false
        return (t < p.matchDelay || s.delayed != null || t < p.matchDelay + p.forcedTime) &&
            (p.matchTime == null || t <= p.matchDelay + p.matchTime!!)
    }
    fun priority(rule: SkipRule): Boolean = ready(rule) && rule.params.priorityTime > 0 &&
        now() - state(rule).entered < rule.params.matchDelay + rule.params.priorityTime &&
        state(rule).maximum.count < rule.params.priorityActionMaximum
}
