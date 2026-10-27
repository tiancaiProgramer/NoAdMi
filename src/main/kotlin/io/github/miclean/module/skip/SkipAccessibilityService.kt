package io.github.miclean.module.skip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleBounds

class SkipAccessibilityService : AccessibilityService() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var repository: SkipRepository
    private var activity: String? = null; private var eventApp = ""
    private var lastAttempt = 0L; private var revision = -1L
    private var rules = emptyList<SkipRule>()
    private var session = SkipSession { SystemClock.elapsedRealtime() }
    private var gesturePending = false
    private val check = Runnable { inspect() }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (::worker.isInitialized) { worker.removeCallbacks(check); worker.post(check) }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        repository = SkipRepository.get(this)
        thread = HandlerThread("NoAdMi-matcher"); thread.start(); worker = Handler(thread.looper)
        repository.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        connectionChanged(this, true)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::worker.isInitialized || event == null) return
        val pkg = event.packageName?.toString() ?: return
        val candidate = event.className?.toString()
        val transition = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val windowId = event.windowId
        worker.post {
            if (transition) {
                val foreground = currentRoot()
                val same = foreground?.windowId == windowId && foreground.packageName?.toString() == pkg
                foreground?.recycle()
                if (same) {
                    if (eventApp != pkg) activity = null
                    eventApp = pkg
                    if (candidate != null && try {
                        packageManager.getActivityInfo(ComponentName(pkg, candidate), 0); true
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) { false }) activity = candidate
                }
            }
            worker.removeCallbacks(check)
            worker.postDelayed(check, (lastAttempt + 120L - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        }
    }
    private fun inspect() {
        if (!repository.enabled || gesturePending) return
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(android.os.PowerManager::class.java).isInteractive) return
        lastAttempt = SystemClock.elapsedRealtime()
        val root = currentRoot() ?: return
        val app = root.packageName?.toString().orEmpty()
        // The module and permission/lock/system surfaces are never automated by subscriptions.
        if (app == packageName || app in setOf("android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.lbe.security.miui", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller")) {
            session.transition(app, null, emptyList())
            root.recycle(); return
        }
        if (app != eventApp) { eventApp = app; activity = null }
        val info = try { packageManager.getPackageInfo(app, 0) } catch (e: android.content.pm.PackageManager.NameNotFoundException) { null }
        val system = info?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
        val launcher = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == app
        val available = mutableListOf<SkipRule>()
        if (revision != repository.sources.revision) {
            val sourceSnapshot = repository.sources.snapshot()
            val compiled = mutableListOf<SkipRule>()
            sourceSnapshot.sources.filter { it.enabled }.forEach { source ->
                fun add(appId: String, group: RawSubscription.RawGroupProps) {
                    if (!group.valid || !source.groupEnabled(appId, group)) return
                    group.rules.forEachIndexed { index, raw -> compiled.add(SkipRule(source, appId, group, raw, index)) }
                }
                source.subscription.apps.forEach { a -> a.groups.forEach { add(a.id, it) } }
                source.subscription.globalGroups.forEach { add("*", it) }
            }
            rules = compiled; revision = sourceSnapshot.revision; session = SkipSession { SystemClock.elapsedRealtime() }
        }
        for (rule in rules) if ((rule.app == "*" || rule.app == app) && SkipScope.applicable(rule.source, rule.group, rule.raw,
                app, activity, info?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode }, info?.versionName, system, launcher)) available.add(rule)
        session.transition(app, activity, available)
        if (available.isEmpty()) { root.recycle(); return }
        val handles = mutableListOf<AccessibilityNodeInfo>()
        try {
            val started = SystemClock.elapsedRealtime()
            fun snapshot(node: AccessibilityNodeInfo, depth: Int, index: Int): SkipNode {
                handles.add(node)
                check(handles.size <= 2500 && depth <= 80 && SystemClock.elapsedRealtime() - started < 300) { "节点树过大，本轮跳过" }
                val rect = Rect(); node.getBoundsInScreen(rect)
                val id = node.viewIdResourceName
                val result = SkipNode(mapOf(
                    "id" to id, "vid" to id?.substringAfter(":id/", ""), "name" to node.className?.toString(),
                    "text" to node.text?.toString(), "desc" to node.contentDescription?.toString(),
                    "clickable" to node.isClickable, "focusable" to node.isFocusable, "checkable" to node.isCheckable,
                    "checked" to node.isChecked, "editable" to node.isEditable, "longClickable" to node.isLongClickable,
                    "visibleToUser" to node.isVisibleToUser, "left" to rect.left, "top" to rect.top,
                    "right" to rect.right, "bottom" to rect.bottom, "width" to rect.width(), "height" to rect.height(),
                    "depth" to depth, "index" to index), node)
                repeat(node.childCount) { childIndex ->
                    // Missing children make exclusion selectors ambiguous: don't click from a partial tree.
                    val child = node.getChild(childIndex) ?: error("节点树已变化，本轮跳过")
                    result.add(snapshot(child, depth + 1, childIndex))
                }
                return result
            }
            val tree = snapshot(root, 0, 0)
            for (rule in available.sortedWith(compareBy<SkipRule> { !session.priority(it) }.thenBy { it.params.order }.thenBy { it.index })) {
                if (!session.ready(rule)) continue
                val target = rule.query(tree)
                if (target == null) { session.missed(rule); continue }
                if (!session.matched(rule)) continue
                // Recheck master switch and foreground window immediately before any action.
                if (!repository.enabled || repository.sources.revision != revision) break
                val fresh = currentRoot()
                val sameWindow = fresh != null && fresh.windowId == root.windowId && fresh.packageName?.toString() == app
                fresh?.recycle(); if (!sameWindow) break
                if (perform(rule, target, app)) break
            }
            if (!gesturePending && available.any { session.needsPoll(it) }) worker.postDelayed(check, 200)
        } catch (e: Exception) {
            android.util.Log.d("NoAdMiSkip", e.message ?: "本轮节点读取失败")
        } finally { if (handles.isEmpty()) root.recycle() else handles.forEach { it.recycle() } }
    }
    /** Foldables can keep an old focused window on the powered-off logical display. */
    private fun currentRoot(): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT < 30) return rootInActiveWindow
        val displays = windowsOnAllDisplays
        val manager = getSystemService(android.hardware.display.DisplayManager::class.java)
        val candidates = mutableListOf<android.view.accessibility.AccessibilityWindowInfo>()
        for (index in 0 until displays.size()) {
            val display = manager.getDisplay(displays.keyAt(index)) ?: continue
            if (display.state != android.view.Display.STATE_ON) continue
            candidates.addAll(displays.valueAt(index).filter { it.isActive || it.isFocused })
        }
        val ordered = candidates.sortedWith(compareByDescending<android.view.accessibility.AccessibilityWindowInfo> { it.isFocused }.thenByDescending { it.layer })
        return ordered.firstNotNullOfOrNull { it.root }
    }
    private fun perform(rule: SkipRule, target: SkipNode, app: String): Boolean {
        val node = target.handle as? AccessibilityNodeInfo ?: return false
        if (!node.refresh() || !node.isVisibleToUser) return false
        if (node.packageName?.toString() != app || node.text?.toString() != target.attrs["text"] ||
            node.contentDescription?.toString() != target.attrs["desc"] || node.viewIdResourceName != target.attrs["id"] ||
            node.className?.toString() != target.attrs["name"]) return false
        val rect = Rect(); node.getBoundsInScreen(rect)
        val raw = rule.raw
        val action = raw.action ?: if (raw.swipeArg != null) "swipe" else if (raw.position != null) "clickCenter" else "click"
        fun success() { session.success(rule); repository.record(rule, app, "已执行 $action") }
        if (action == "none") { success(); return true }
        if (action == "back") return performGlobalAction(GLOBAL_ACTION_BACK).also { if (it) success() }
        if (action in listOf("click", "clickNode", "longClick", "longClickNode")) {
            val long = action.startsWith("long")
            if ((action.endsWith("Node") || (if (long) node.isLongClickable else node.isClickable)) && node.performAction(
                    if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)) { success(); return true }
            if (action.endsWith("Node")) return false
        }
        if (action !in listOf("click", "clickCenter", "longClick", "longClickCenter", "swipe")) return false
        val displayId = if (android.os.Build.VERSION.SDK_INT >= 30) node.window?.displayId ?: android.view.Display.DEFAULT_DISPLAY else android.view.Display.DEFAULT_DISPLAY
        val display = getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId) ?: return false
        val metrics = android.util.DisplayMetrics(); display.getRealMetrics(metrics)
        val bounds = RuleBounds(rect.left, rect.top, rect.right, rect.bottom)
        fun point(position: RawSubscription.Position?): Pair<Float, Float>? =
            if (position == null) rect.exactCenterX() to rect.exactCenterY()
            else position.calc(bounds, metrics.widthPixels, metrics.heightPixels)
        val start = point(if (action == "swipe") raw.swipeArg?.start else raw.position) ?: return false
        val end = if (action == "swipe") point(raw.swipeArg?.end) ?: return false else start
        fun inside(p: Pair<Float, Float>) = p.first.isFinite() && p.second.isFinite() && p.first >= 0 && p.second >= 0 &&
            p.first < metrics.widthPixels && p.second < metrics.heightPixels
        if (!inside(start) || !inside(end)) return false
        val path = Path().apply { moveTo(start.first, start.second); if (action == "swipe") lineTo(end.first, end.second) }
        val duration = if (action == "swipe") raw.swipeArg!!.duration else if (action.startsWith("long")) 400L else 40L
        if (duration !in 1..60000L) return false
        val builder = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        if (android.os.Build.VERSION.SDK_INT >= 30) builder.setDisplayId(displayId)
        val gesture = builder.build()
        gesturePending = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) { worker.post { gesturePending = false; success(); worker.postDelayed(check, 160) } }
            override fun onCancelled(gesture: GestureDescription?) { worker.post { gesturePending = false; repository.record(rule, app, "手势已取消") } }
        }, Handler(mainLooper))
        if (!accepted) gesturePending = false
        return accepted
    }
    override fun onInterrupt() { if (::worker.isInitialized) worker.removeCallbacksAndMessages(null) }
    override fun onDestroy() {
        connectionChanged(this, false)
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        super.onDestroy()
    }
    override fun onUnbind(intent: Intent?): Boolean { connectionChanged(this, false); return super.onUnbind(intent) }
    companion object {
        @JvmField @Volatile var connected: Boolean = false
        private val listeners = java.util.concurrent.CopyOnWriteArrayList<Runnable>()
        private val main by lazy { Handler(android.os.Looper.getMainLooper()) }
        @JvmStatic fun observeConnection(listener: Runnable) { listeners.addIfAbsent(listener) }
        @JvmStatic fun removeConnectionObserver(listener: Runnable) { listeners.remove(listener) }
        private var owner: SkipAccessibilityService? = null
        private fun connectionChanged(service: SkipAccessibilityService, value: Boolean) {
            if (value) owner = service else if (owner !== service) return else owner = null
            connected = value
            android.util.Log.i("NoAdMiAccess", if (value) "accessibility service connected" else "accessibility service disconnected")
            listeners.forEach { listener -> main.post { if (listeners.contains(listener)) listener.run() } }
        }
    }
}
