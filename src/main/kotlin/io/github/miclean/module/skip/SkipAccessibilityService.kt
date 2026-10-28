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
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class SkipAccessibilityService : AccessibilityService() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var repository: SkipRepository
    private lateinit var diagnostics: SkipDiagnostics
    @Volatile private var activity: String? = null
    @Volatile private var eventApp = ""
    @Volatile private var appChangeTime = 0L
    private var lastTriggerTime = 0L
    private var lastEventTime = 0L
    private var lastEventDiagnostic = 0L
    private val stateEpoch = java.util.concurrent.atomic.AtomicLong()
    private lateinit var eventThread: HandlerThread
    private lateinit var eventWorker: Handler
    @Volatile private var screenInteractive = true
    private var screenReceiverRegistered = false
    private var aliveView: android.view.View? = null
    private fun removeAliveView() {
        aliveView?.let { view -> runCatching { getSystemService(android.view.WindowManager::class.java).removeView(view) } }
        aliveView = null
    }
    private fun addAliveView() {
        // GKD A11yService.useAliveOverlayView: visible process priority without
        // a touch target, focus, screenshot or extra overlay permission.
        removeAliveView()
        val view = android.view.View(this)
        val params = android.view.WindowManager.LayoutParams().apply {
            type = android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = android.graphics.PixelFormat.TRANSLUCENT
            flags = flags or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
            width = 1; height = 1; packageName = this@SkipAccessibilityService.packageName
        }
        try { getSystemService(android.view.WindowManager::class.java).addView(view, params); aliveView = view }
        catch (_: Exception) { android.util.Log.w("NoAdMiSkip", "accessibility keep-alive window unavailable") }
    }
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            screenInteractive = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> false
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> true
                else -> screenInteractive
            }
            if (screenInteractive && ::worker.isInitialized) worker.post {
                appChangeTime = SystemClock.elapsedRealtime(); requestCheck(0)
            }
        }
    }
    private var lastAttempt = 0L; private var revision = -1L
    private var rules = emptyList<SkipRule>()
    private var session = SkipSession { SystemClock.elapsedRealtime() }
    private var gesturePending = false
    private val schedule = SkipCheckSchedule()
    private var retryRules = emptyList<SkipRule>()
    private data class NodeEvent(val app: String, val name: String, val type: Int, val copy: AccessibilityEvent) {
        fun same(other: NodeEvent) = app == other.app && name == other.name && type == other.type
    }
    private val eventLock = Any()
    private val incomingEvents = mutableListOf<NodeEvent>()
    private val queryEvents = mutableListOf<NodeEvent>()
    private val eventCheck = Runnable {
        val events = synchronized(eventLock) { incomingEvents.toList().also { incomingEvents.clear() } }
        if (events.isNotEmpty()) {
            val latest = events.last()
            val root = if (latest.app == eventApp) null else try { currentRoot(100) } catch (_: Exception) { null }
            val rootApp = root?.packageName?.toString()
            val activeEvent = if (rootApp == latest.app || latest.app == eventApp) false else try {
                nativeReader.read(100) { windows.any { it.isActive && it.id == latest.copy.windowId } }
            } catch (_: Exception) { false }
            val rightApp = if (latest.app == eventApp || activeEvent) latest.app else rootApp
            root?.recycle()
            if (rightApp == latest.app) {
                if (eventApp != rightApp) {
                    activity = null; appChangeTime = SystemClock.elapsedRealtime()
                    discardQueryEvents()
                }
                eventApp = rightApp
                if (latest.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && try {
                    packageManager.getActivityInfo(ComponentName(latest.app, latest.name), 0); true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }) activity = latest.name
                synchronized(eventLock) {
                    queryEvents.addAll(events)
                    while (queryEvents.size > 32) queryEvents.removeAt(0).copy.recycle()
                }
                worker.post { requestCheck(0) }
            } else events.forEach { it.copy.recycle() }
        }
    }
    private val check = Runnable {
        schedule.consume()
        try { inspect() }
        catch (e: Exception) { diagnostics.record(eventApp, (e as? SkipReadFailure)?.reason ?: "scan_error") }
        finally { retry() }
    }
    private val nativeReader = SkipReadExecutor()
    private fun requestCheck(delay: Long) {
        if (schedule.request(SystemClock.elapsedRealtime(), delay)) {
            worker.removeCallbacks(check); worker.postDelayed(check, delay.coerceAtLeast(0))
        }
    }
    private fun retry() {
        val time = SystemClock.elapsedRealtime()
        // GKD checkFutureStartJob: 300 ms during the first 3 s after an app
        // change/action; thereafter only a rule's forced/delay window continues.
        if (repository.enabled && !gesturePending && (time - appChangeTime < 3000 ||
            time - lastTriggerTime < 3000 || retryRules.any { session.needsPoll(it) })) requestCheck(300)
    }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (::worker.isInitialized) worker.post { appChangeTime = SystemClock.elapsedRealtime(); requestCheck(0) }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        repository = SkipRepository.get(this)
        diagnostics = SkipDiagnostics(File(filesDir, "smart_skip/diagnostics.json"))
        schedule.consume(); retryRules = emptyList(); clearEvents()
        thread = HandlerThread("NoAdMi-matcher"); thread.start(); worker = Handler(thread.looper)
        if (::eventWorker.isInitialized) { eventWorker.removeCallbacksAndMessages(null); eventThread.quitSafely() }
        eventThread = HandlerThread("NoAdMi-events"); eventThread.start(); eventWorker = Handler(eventThread.looper)
        repository.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (!screenReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }
        connectionChanged(this, true)
        addAliveView()
        worker.post { appChangeTime = SystemClock.elapsedRealtime(); requestCheck(0) }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::worker.isInitialized || event == null || !repository.enabled) return
        val diagnosticTime = SystemClock.elapsedRealtime()
        if (diagnosticTime - lastEventDiagnostic >= 1000) {
            lastEventDiagnostic = diagnosticTime
            android.util.Log.i("NoAdMiSkip", "event app=${event.packageName} type=${event.eventType} display=${if (android.os.Build.VERSION.SDK_INT >= 33) event.displayId else 0} time=${event.eventTime} previous=$lastEventTime interactive=$screenInteractive")
        }
        // GKD accepts only state/content events on the default display.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !screenInteractive) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && event.displayId != android.view.Display.DEFAULT_DISPLAY) return
        val pkg = event.packageName?.toString() ?: return
        val name = event.className?.toString() ?: return
        if (event.eventTime < lastEventTime) return
        lastEventTime = event.eventTime
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) stateEpoch.incrementAndGet()
        synchronized(eventLock) {
            incomingEvents.add(NodeEvent(pkg, name, event.eventType, AccessibilityEvent.obtain(event)))
            while (incomingEvents.size > 32) incomingEvents.removeAt(0).copy.recycle()
        }
        eventWorker.removeCallbacks(eventCheck); eventWorker.post(eventCheck)
    }
    private fun clearEvents() = synchronized(eventLock) {
        incomingEvents.forEach { it.copy.recycle() }; incomingEvents.clear()
        queryEvents.forEach { it.copy.recycle() }; queryEvents.clear()
    }
    private fun discardQueryEvents() = synchronized(eventLock) {
        queryEvents.forEach { it.copy.recycle() }; queryEvents.clear()
    }
    private fun eventSource(): AccessibilityNodeInfo? {
        val events = synchronized(eventLock) { queryEvents.toList().also { queryEvents.clear() } }
        if (events.isEmpty()) return null
        try {
            // GKD uses the latest event node only when events describe the same
            // source; mixed events fall back to the active-window root.
            if (events.any { !it.same(events.last()) }) return null
            val latest = events.last().copy.source ?: return null
            if (events.size > 1) {
                val previous = events[events.size - 2].copy.source
                val same = previous == null || previous == latest
                previous?.recycle()
                if (!same) { latest.recycle(); return null }
            }
            return latest
        } finally { events.forEach { it.copy.recycle() } }
    }
    private fun inspect() {
        if (!repository.enabled || gesturePending) return
        if (!screenInteractive) { diagnostics.record(eventApp, "screen_paused"); return }
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            diagnostics.record(eventApp, "screen_locked"); return
        }
        val epoch = stateEpoch.get()
        var app = eventApp
        if (app.isEmpty()) {
            val first = try { currentRoot() } catch (_: SkipReadFailure) { null } ?: return
            app = first.packageName?.toString().orEmpty(); first.recycle()
            eventApp = app; activity = null; appChangeTime = SystemClock.elapsedRealtime()
        }
        if (app == packageName || app in setOf("android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.lbe.security.miui", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller")) {
            session.transition(app, null, emptyList()); retryRules = emptyList(); discardQueryEvents(); return
        }
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
        retryRules = available
        // GKD checks eligible rules before resolving event nodes. Never fetch an
        // entire active window before trying a valid current event source.
        if (available.isEmpty()) { diagnostics.record(app, "no_candidates"); discardQueryEvents(); return }
        if (available.none { session.ready(it) }) { discardQueryEvents(); return }
        val source = eventSource()
        val fromEvent = source != null && source.packageName?.toString() == app
        val start = if (fromEvent) source!! else {
            source?.recycle()
            try { currentRoot() } catch (e: SkipReadFailure) { diagnostics.record(app, e.reason); return }
                ?: run { diagnostics.record(app, "root_unavailable"); return }
        }
        if (start.packageName?.toString() != app || stateEpoch.get() != epoch) {
            val rightApp = start.packageName?.toString().orEmpty(); start.recycle()
            if (rightApp.isNotEmpty() && rightApp != app) {
                eventApp = rightApp; activity = null; appChangeTime = SystemClock.elapsedRealtime()
            }
            return
        }
        val nodes = AndroidSkipTree(start, nativeReader, rootIsWindow = !fromEvent) { stateEpoch.get() != epoch }
        val started = SystemClock.elapsedRealtime()
        var stage = "root"
        fun detail() = "window=${start.windowId} display=0 origin=${if (fromEvent) "event" else "root"} stage=$stage fastCounts=${nodes.queryCounts}"
        try {
            val tree = nodes.root()
            var outcome = "unmatched"
            for (rule in available.sortedWith(compareBy<SkipRule> { !session.priority(it) }.thenBy { it.params.order }.thenBy { it.index })) {
                if (stateEpoch.get() != epoch || eventApp != app) { outcome = "event_changed"; break }
                if (!session.ready(rule)) continue
                val target = rule.query(tree, excluded = { outcome = "excluded" }, adapter = nodes.adapter,
                    stage = { stage = "${rule.identity}/$it" }, windowRootProvider = { nodes.windowRoot { currentRoot() } })
                if (target == null) { session.missed(rule); continue }
                if (!session.matched(rule)) continue
                // GKD checks current event/app/rule state before action. A new
                // root RPC here would discard the benefit of event-node queries.
                if (!repository.enabled || repository.sources.revision != revision ||
                    stateEpoch.get() != epoch || eventApp != app || !session.ready(rule)) break
                if (perform(rule, target, app)) { outcome = "action_accepted"; break }
                outcome = "action_rejected"
            }
            diagnostics.record(app, outcome, available.size, nodes.count, SystemClock.elapsedRealtime() - started, detail())
        } catch (e: Exception) {
            diagnostics.record(app, (e as? SkipReadFailure)?.reason ?: "scan_error", available.size, nodes.count, SystemClock.elapsedRealtime() - started, detail())
        } finally { nodes.close() }
    }
    /** GKD A11yService.windowNodeInfo is rootInActiveWindow (default display). */
    private fun currentRoot(timeoutMs: Long = 500): AccessibilityNodeInfo? =
        nativeReader.read(timeoutMs, discard = { it?.recycle() }) { rootInActiveWindow }
    private fun perform(rule: SkipRule, target: SkipNode, app: String): Boolean {
        val node = target.handle as? AccessibilityNodeInfo ?: return false
        if (!node.isVisibleToUser) return false
        if (node.packageName?.toString() != app) return false
        val rect = Rect(); node.getBoundsInScreen(rect)
        val raw = rule.raw
        val action = raw.action ?: if (raw.position != null) "clickCenter" else if (raw.swipeArg != null) "swipe" else "click"
        fun success() { session.success(rule); lastTriggerTime = SystemClock.elapsedRealtime(); repository.record(rule, app, "已执行 $action") }
        if (action == "none") { success(); return true }
        if (action == "back") return performGlobalAction(GLOBAL_ACTION_BACK).also { if (it) success() }
        if (action in listOf("click", "clickNode", "longClick", "longClickNode")) {
            val long = action.startsWith("long")
            if ((action.endsWith("Node") || (if (long) node.isLongClickable else node.isClickable)) && node.performAction(
                    if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)) { success(); return true }
            if (action.endsWith("Node")) return false
        }
        if (action !in listOf("click", "clickCenter", "longClick", "longClickCenter", "swipe")) return false
        val displayId = android.view.Display.DEFAULT_DISPLAY
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
        val duration = if (action == "swipe") raw.swipeArg!!.duration else if (action.startsWith("long")) 500L else android.view.ViewConfiguration.getTapTimeout().toLong()
        if (duration !in 1..60000L) return false
        val builder = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        if (android.os.Build.VERSION.SDK_INT >= 30) builder.setDisplayId(displayId)
        val gesture = builder.build()
        gesturePending = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) { worker.post { gesturePending = false; success(); requestCheck(300) } }
            override fun onCancelled(gesture: GestureDescription?) { worker.post { gesturePending = false; repository.record(rule, app, "手势已取消"); retry() } }
        }, Handler(mainLooper))
        if (!accepted) gesturePending = false
        return accepted
    }
    override fun onInterrupt() { if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); schedule.consume(); clearEvents() } }
    override fun onDestroy() {
        removeAliveView()
        connectionChanged(this, false)
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        if (::eventWorker.isInitialized) { eventWorker.removeCallbacksAndMessages(null); eventThread.quitSafely() }
        if (screenReceiverRegistered) { unregisterReceiver(screenReceiver); screenReceiverRegistered = false }
        clearEvents(); nativeReader.close()
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
