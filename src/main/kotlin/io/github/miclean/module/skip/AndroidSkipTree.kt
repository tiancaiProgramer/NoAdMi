package io.github.miclean.module.skip

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.IdentityHashMap
import li.gkd.selector.FastQuery

/** Per-inspection memoized nodes/relationships. Never acts from a failed read. */
class AndroidSkipTree(private val nativeRoot: AccessibilityNodeInfo, private val reader: SkipReadExecutor,
    private val rootIsWindow: Boolean = true,
    private val outdated: () -> Boolean = { false }) : AutoCloseable {
    private var actualWindowRoot: AccessibilityNodeInfo? = nativeRoot.takeIf { rootIsWindow }
    private val owned = IdentityHashMap<AccessibilityNodeInfo, Boolean>()
    private val cache = mutableMapOf<AccessibilityNodeInfo, SkipNode>()
    private val positions = mutableMapOf<AccessibilityNodeInfo, Int>()
    private data class QueryKey(val node: AccessibilityNodeInfo, val kind: String, val value: String)
    // Only native result lists are memoized. Every selector still validates its
    // own predicates/options. The cache dies with this inspection, also after
    // an action; it never retains a node across events or delay deadlines.
    private val fastResults = mutableMapOf<QueryKey, List<AccessibilityNodeInfo>>()
    var nativeReads = 0; private set
    var parentReads = 0; private set
    var fastQueryHits = 0; private set
    val count: Int get() = cache.size
    val queryCounts = mutableListOf<Int>()
    init { owned[nativeRoot] = true }
    private fun budget(depth: Int = 0) {
        if (outdated()) throw SkipReadFailure("event_changed")
        // GKD A11yExt: MAX_DESCENDANTS_SIZE=4096, MAX_CHILD_SIZE=512.
        if (cache.size >= 4096 || depth > 4096) throw SkipReadFailure("tree_limit")
    }
    private fun own(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        owned[node] = true; budget(); return node
    }
    private fun <T> read(node: AccessibilityNodeInfo, discard: (T) -> Unit, operation: (AccessibilityNodeInfo) -> T): T {
        budget()
        nativeReads++
        // The background task owns its input copy even after the inspection closes.
        val copy = AccessibilityNodeInfo.obtain(node)
        var started = false
        try {
            return reader.read(discard = discard) {
                started = true
                try { operation(copy) } finally { copy.recycle() }
            }
        } catch (e: SkipReadFailure) {
            if (e.reason == "native_busy" && !started) copy.recycle()
            throw e
        }
    }
    private fun child(node: AccessibilityNodeInfo, index: Int) = read(node, { it: AccessibilityNodeInfo? -> it?.recycle() }) { it.getChild(index) }
    private fun parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        parentReads++
        return read(node, { it: AccessibilityNodeInfo? -> it?.recycle() }) { it.parent }
    }
    private fun wrap(node: AccessibilityNodeInfo, parent: SkipNode? = null, position: Int? = null): SkipNode {
        own(node)
        if (position != null) positions[node] = position
        cache[node]?.let { existing -> if (parent != null) existing.parent = parent; return existing }
        if (node.windowId != nativeRoot.windowId) throw SkipReadFailure("window_changed")
        val isRoot = node == actualWindowRoot
        lateinit var result: SkipNode
        val depth by lazy {
            var current = result.parent; var value = 0
            val visited = mutableSetOf(result)
            while (current != null) {
                value++; budget(value)
                if (!visited.add(current)) throw SkipReadFailure("tree_changed")
                current = current.parent
            }
            value
        }
        val index by lazy { if (isRoot) 0 else positions[node] ?: run {
            val parentNode = result.parent ?: return@lazy 0
            var found = -1
            // Reuse loaded siblings and establish their parent/index together.
            // Native fast-query results often lack these relationships.
            for (i in 0 until parentNode.count()) {
                val sibling = parentNode.child(i) ?: break
                if (sibling === result) { found = i; break }
            }
            if (found < 0) 0 else found
        } }
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        val id = node.viewIdResourceName
        val baseAttrs = mapOf(
            "id" to id, "vid" to id?.substringAfter(":id/", ""), "name" to node.className?.toString(),
            "text" to node.text?.toString(), "desc" to node.contentDescription?.toString(),
            "clickable" to node.isClickable, "focusable" to node.isFocusable, "checkable" to node.isCheckable,
            "checked" to node.isChecked, "editable" to node.isEditable, "longClickable" to node.isLongClickable,
            "visibleToUser" to node.isVisibleToUser, "left" to bounds.left, "top" to bounds.top,
            "right" to bounds.right, "bottom" to bounds.bottom, "width" to bounds.width(), "height" to bounds.height(),
            "depth" to null, "index" to null)
        // Native fast queries return a target directly. Do not walk its entire
        // parent chain and every sibling merely to compute unused index/depth.
        val attrs = object : Map<String, Any?> by baseAttrs {
            override fun get(key: String): Any? = when (key) {
                "depth" -> depth
                "index" -> index
                else -> baseAttrs[key]
            }
        }
        val childCount = node.childCount
        result = SkipNode(attrs, node, childCount, parentLookup = {
            if (isRoot) null else parent ?: parent(node)?.let { wrap(own(it)) }
        }) { childIndex ->
            // Traversal already has a visited set and the 4096-node budget.
            // Do not fetch the entire ancestor chain merely to visit a child;
            // the real depth remains lazy for selectors which request it.
            budget()
            child(node, childIndex)?.let { wrap(own(it), result, childIndex) }
        }
        cache[node] = result; budget()
        return result
    }
    fun root(): SkipNode = wrap(nativeRoot)
    fun queryRoot(node: AccessibilityNodeInfo): SkipNode = wrap(node)
    fun windowRoot(provider: () -> AccessibilityNodeInfo?): SkipNode {
        val root = actualWindowRoot ?: (provider() ?: throw SkipReadFailure("root_unavailable")).also {
            own(it)
            if (it.windowId != nativeRoot.windowId || it.packageName != nativeRoot.packageName) throw SkipReadFailure("window_changed")
            actualWindowRoot = it
        }
        return wrap(root)
    }
    val adapter = object : SnapshotNodeAdapter() {
        override fun traversalChildCount(node: SkipNode): Int = node.count().coerceAtMost(512)
        override fun getFastQueryDescendants(node: SkipNode, fastQueryList: List<FastQuery>): Sequence<SkipNode> = sequence {
            val native = node.handle as AccessibilityNodeInfo
            val seen = mutableSetOf<SkipNode>()
            for (query in fastQueryList) {
                budget()
                if (query.value.isEmpty()) {
                    // Empty-text lookup has platform-dependent behavior; traverse normally.
                    for (child in super.getFastQueryDescendants(node, listOf(query))) if (seen.add(child)) yield(child)
                    continue
                }
                val packageId = nativeRoot.packageName
                val key = when (query) {
                    is FastQuery.Id -> QueryKey(native, "id", query.value)
                    is FastQuery.Vid -> QueryKey(native, "id", "$packageId:id/${query.value}")
                    is FastQuery.Text -> QueryKey(native, "text", query.value)
                }
                val cached = fastResults[key]
                if (cached != null) {
                    fastQueryHits++
                    for (handle in cached) {
                        if (handle == native) continue
                        val child = wrap(handle)
                        if (seen.add(child)) yield(child)
                    }
                    continue
                }
                val found = read(native, { list: List<AccessibilityNodeInfo> -> list.forEach { it.recycle() } }) { input -> when (query) {
                    is FastQuery.Id -> input.findAccessibilityNodeInfosByViewId(query.value)
                    is FastQuery.Vid -> input.findAccessibilityNodeInfosByViewId("$packageId:id/${query.value}")
                    is FastQuery.Text -> input.findAccessibilityNodeInfosByText(query.value)
                }
                }
                if (queryCounts.size < 24) queryCounts.add(found.size)
                found.forEach { owned[it] = true }; budget()
                // Bound even negative lookups with many distinct selectors.
                if (fastResults.size < 256) fastResults[key] = found
                for (handle in found) {
                    if (handle == native) continue
                    val child = wrap(handle)
                    if (seen.add(child)) yield(child)
                }
            }
        }
    }
    override fun close() { owned.keys.forEach { it.recycle() }; owned.clear(); cache.clear(); positions.clear(); fastResults.clear() }
}
