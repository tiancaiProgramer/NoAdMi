package io.github.miclean.module.skip

import li.gkd.app.rule.RuleExecutionParameters
import li.gkd.app.subscription.RawSubscription
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector

/** Stable snapshot: selector traversal never mixes nodes from different accessibility events. */
class SkipNode(val attrs: Map<String, Any?>, val handle: Any? = null,
    private val childCountHint: Int? = null, private val parentLookup: (() -> SkipNode?)? = null,
    private val childLookup: ((Int) -> SkipNode?)? = null) {
    val children = mutableListOf<SkipNode>()
    private val loaded = mutableMapOf<Int, SkipNode?>()
    private var parentRead = parentLookup == null
    private var parentValue: SkipNode? = null
    var parent: SkipNode?
        get() {
            if (!parentRead) { parentValue = parentLookup?.invoke(); parentRead = true }
            return parentValue
        }
        set(value) { parentValue = value; parentRead = true }
    fun add(child: SkipNode) { child.parent = this; children.add(child) }
    fun count(): Int = childCountHint ?: children.size
    fun child(index: Int): SkipNode? = if (childCountHint == null) children.getOrNull(index)
        else if (index !in 0 until childCountHint) null else {
            if (!loaded.containsKey(index)) loaded[index] = childLookup!!(index)
            loaded[index]
        }
}

open class SnapshotNodeAdapter : NodeAdapter<SkipNode>() {
    protected open fun traversalChildCount(node: SkipNode): Int = node.count()
    override fun getAttr(target: Any, name: String): Any? {
        val node = target as? SkipNode ?: return null
        return when (name) {
            "parent" -> node.parent
            "childCount" -> node.count()
            "index" -> node.attrs["index"] ?: 0
            else -> node.attrs[name]
        }
    }
    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
        if (target is SkipNode && name == "getChild" && args.size == 1) target.child(args[0] as Int) else null
    override fun getName(node: SkipNode): String? = node.attrs["name"] as? String
    override fun getChildCount(node: SkipNode): Int = node.count()
    override fun getChild(node: SkipNode, index: Int): SkipNode? = node.child(index)
    override fun getParent(node: SkipNode): SkipNode? = node.parent
    override fun getNodeKey(node: SkipNode): Any = node
    override fun getDescendants(node: SkipNode): Sequence<SkipNode> = sequence {
        // Preserve depth-first order without eagerly loading every sibling first.
        class Frame(val node: SkipNode, var next: Int = 0)
        val stack = mutableListOf(Frame(node)); val visited = mutableSetOf(node)
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (frame.next >= traversalChildCount(frame.node)) { stack.removeAt(stack.lastIndex); continue }
            val child = frame.node.child(frame.next++)
            if (child == null) { stack.removeAt(stack.lastIndex); continue }
            if (visited.add(child)) { yield(child); stack.add(Frame(child)) }
        }
    }
}
object SkipAdapter : SnapshotNodeAdapter()

class SkipRule(val source: SkipSource, val app: String, val group: RawSubscription.RawGroupProps,
    val raw: RawSubscription.RawRuleProps, val index: Int) {
    val params = RuleExecutionParameters(raw, group)
    val identity = "${source.id}/$app/${group.key}/$index"
    fun query(root: SkipNode, excluded: () -> Unit = {}, adapter: NodeAdapter<SkipNode> = SkipAdapter,
        stage: (String) -> Unit = {}, windowRoot: SkipNode = root,
        windowRootProvider: (() -> SkipNode)? = null): SkipNode? {
        val actualWindow by lazy { windowRootProvider?.invoke() ?: windowRoot }
        val queryRoot by lazy { if (params.matchRoot) actualWindow else root }
        fun query(selector: Selector): SkipNode? =
            if (selector.isMatchRoot) selector.match(actualWindow, adapter, params.matchOptions)
            else selector.match(queryRoot, adapter, params.matchOptions) ?: adapter.querySelector(queryRoot, selector, params.matchOptions)
        var target: SkipNode? = null
        if (params.anyMatches.isNotEmpty()) target = params.anyMatches.withIndex().firstNotNullOfOrNull {
            stage("any/${it.index}"); query(it.value)
        } ?: return null
        for ((i, selector) in params.matches.withIndex()) { stage("match/$i"); target = query(selector) ?: return null }
        // Match the positive selectors first, as GKD does. An absent target
        // must not consume the inspection budget scanning unrelated exclusions.
        // Every exclusion is still validated before returning an action target.
        if (params.excludeMatches.withIndex().any { stage("exclude/${it.index}"); query(it.value) != null }) { excluded(); return null }
        if (params.excludeAllMatches.isNotEmpty() && params.excludeAllMatches.withIndex().all { stage("excludeAll/${it.index}"); query(it.value) != null }) { excluded(); return null }
        return target
    }
}

/** Version/activity restrictions are resolved before matching, including inherited group fields. */
object SkipScope {
    fun activity(app: String, current: String?, included: List<String>?, excluded: List<String>?): Boolean {
        fun full(value: String) = if (value.startsWith('.')) app + value else value
        // GKD AppRule.matchActivity: unknown Activity still enters node matching.
        if (current == null) return true
        return excluded.orEmpty().none { current.startsWith(full(it)) } &&
            (included.isNullOrEmpty() || included.any { current.startsWith(full(it)) })
    }
    fun version(code: Int?, name: String?, codes: RawSubscription.IntegerMatcher?, names: RawSubscription.StringMatcher?): Boolean =
        (codes == null || codes.match(code)) && (names == null || names.match(name))
    fun applicable(source: SkipSource, group: RawSubscription.RawGroupProps, raw: RawSubscription.RawRuleProps,
        app: String, current: String?, code: Int?, name: String?, system: Boolean, launcher: Boolean): Boolean {
        if (!source.groupEnabled(if (group is RawSubscription.RawAppGroup) app else "*", group)) return false
        if (source.apps[app] == false) return false
        if (group is RawSubscription.RawGlobalGroup && source.globalApps[source.groupKey(app, group.key)] == false) return false
        if (!matchesApp(group, raw, app, current, code, name, system, launcher)) return false
        if (group is RawSubscription.RawGlobalGroup) {
            val prefix = group.disableIfAppGroupMatch?.ifEmpty { group.name }
            if (prefix != null && source.subscription.getAppGroups(app).any { it.ignoreGlobalGroupMatch != true &&
                    it.name.startsWith(prefix) && source.groupEnabled(app, it) }) return false
        }
        return true
    }
    /** The management page lists scope-compatible rules even when their saved switch is off. */
    fun matchesApp(group: RawSubscription.RawGroupProps, raw: RawSubscription.RawRuleProps,
        app: String, current: String?, code: Int?, name: String?, system: Boolean, launcher: Boolean): Boolean {
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
        return activity(app, current, child?.activityIds ?: parent?.activityIds, child?.excludeActivityIds ?: parent?.excludeActivityIds) &&
            version(code, name, child?.versionCode ?: parent?.versionCode, child?.versionName ?: parent?.versionName)
    }
}

/** Per-screen counters, delays and prerequisite chains survive repeated accessibility events. */
class SkipSession(private val now: () -> Long) {
    private class Counter(var time: Long = Long.MIN_VALUE / 2, var count: Int = 0)
    private class State(val entered: Long) { var delayed: Long? = null; var deadlineInspected = false; var cooldown = Counter(); var maximum = Counter() }
    private val states = mutableMapOf<String, State>()
    private var last: SkipRule? = null
    private var app = ""; private var activity: String? = null
    private var previous = emptySet<String>()
    private var scene = 0L
    @JvmOverloads fun transition(nextApp: String, nextActivity: String?, rules: List<SkipRule>, generation: Long = 0L) {
        val appChanged = app != nextApp; val activityChanged = appChanged || activity != nextActivity || scene != generation
        rules.forEach { rule ->
                val reset = rule.params.resetMatch ?: "activity"
                if (appChanged || (activityChanged && reset == "activity") || (reset == "match" && rule.identity !in previous)) states.remove(rule.identity)
        }
        if (activityChanged) {
            app = nextApp; activity = nextActivity; scene = generation
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
    private fun prerequisite(rule: SkipRule): Boolean {
        val p = rule.params
        return p.preKeys.isEmpty() || (last?.let {
            it.source.id == rule.source.id && it.app == rule.app &&
                (it.group.key == rule.group.key || rule.group.scopeKeys.orEmpty().contains(it.group.key)) && p.preKeys.contains(it.raw.key)
        } == true)
    }
    fun ready(rule: SkipRule): Boolean {
        val s = state(rule); val p = rule.params; val t = now()
        if (p.actionMaximum != null && s.maximum.count >= p.actionMaximum!!) return false
        if (!prerequisite(rule)) return false
        if (delayRemaining(rule)?.let { it > 0 } == true) return false
        return t - s.entered >= p.matchDelay && (p.matchTime == null || t - s.entered <= p.matchDelay + p.matchTime!!) &&
            t - s.cooldown.time >= p.actionCd
    }
    fun matched(rule: SkipRule): Boolean {
        val state = state(rule); val time = now()
        if (rule.params.actionDelay <= 0) return true
        if (state.delayed == null) { state.delayed = time; state.deadlineInspected = false }
        return time - state.delayed!! >= rule.params.actionDelay
    }
    // GKD keeps actionTriggerTime across temporary nonmatches. A delayed job
    // must re-query a fresh window at its deadline; it never retains a node.
    fun missed(rule: SkipRule) { /* only success or the reset policy clears delay */ }
    fun delayRemaining(rule: SkipRule): Long? = state(rule).delayed?.let {
        (rule.params.actionDelay - (now() - it)).coerceAtLeast(0)
    }
    fun delayLateness(rule: SkipRule): Long = state(rule).delayed?.let {
        (now() - it - rule.params.actionDelay).coerceAtLeast(0)
    } ?: 0
    /** One targeted inspection per timer, like GKD byDelayRule. A disappeared
     * target must not starve unrelated rules on subsequent event/follow-up passes. */
    fun inspectionRules(rules: List<SkipRule>): List<SkipRule> {
        val due = rules.filter { delayedDue(it) && !state(it).deadlineInspected }
        due.forEach { state(it).deadlineInspected = true }
        return due.ifEmpty { rules }
    }
    fun delayedDue(rule: SkipRule): Boolean = delayRemaining(rule) == 0L && ready(rule)
    fun success(rule: SkipRule) {
        val s = state(rule); s.cooldown.time = now(); s.maximum.count++; s.delayed = null; s.deadlineInspected = false; last = rule
    }
    fun needsPoll(rule: SkipRule): Boolean {
        val s = state(rule); val p = rule.params; val t = now() - s.entered
        if (p.actionMaximum != null && s.maximum.count >= p.actionMaximum!!) return false
        if (!prerequisite(rule) && t >= p.matchDelay) return false
        // App startup can render its ad after the first event even when forcedTime=0.
        // GKD's application-change follow-up lasts three seconds.
        val startup = s.maximum.count == 0 && t < 3000L
        return (startup || t < p.matchDelay || (delayRemaining(rule)?.let { it > 0 } == true) || t < p.matchDelay + p.forcedTime) &&
            (p.matchTime == null || t <= p.matchDelay + p.matchTime!!)
    }
    fun priority(rule: SkipRule): Boolean = ready(rule) && rule.params.priorityTime > 0 &&
        now() - state(rule).entered < rule.params.matchDelay + rule.params.priorityTime &&
        state(rule).maximum.count < rule.params.priorityActionMaximum
}
