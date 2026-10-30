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

open class SkipAccessibilityService : AccessibilityService() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var repository: SkipRepository
    private lateinit var diagnostics: SkipDiagnostics
    private var runtime: SkipRuntimeClient? = null
    private val activityTracker = SkipActivityTracker { SystemClock.elapsedRealtime() }
    private val activity: String? get() = activityTracker.current.takeIf { it.app == eventApp }?.activity
    private val noticeHandler = Handler(android.os.Looper.getMainLooper())
    private var noticeOverlay: SkipNoticeOverlay? = null
    private val activityCache = object : android.util.LruCache<ComponentName, Boolean>(256) {
        override fun create(key: ComponentName): Boolean = try { packageManager.getActivityInfo(key, 0); true }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
    }
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
            if (!screenInteractive) noticeHandler.post { noticeOverlay?.dismiss() }
            if (screenInteractive && ::worker.isInitialized) worker.post {
                appChangeTime = SystemClock.elapsedRealtime(); requestCheck(0)
            }
        }
    }
    private var lastAttempt = 0L; private var revision = -1L
    private var indexedRules = emptyMap<String, List<IndexedValue<SkipRule>>>()
    private val candidateCache = SkipCandidateCache<List<SkipRule>>({ SystemClock.elapsedRealtime() })
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (::worker.isInitialized) worker.post { candidateCache.invalidate(); requestCheck(0) }
        }
    }
    private var packageReceiverRegistered = false
    private var session = SkipSession { SystemClock.elapsedRealtime() }
    private var gesturePending = false
    private val schedule = SkipCheckSchedule()
    private val followup = SkipFollowupSchedule()
    private var actionFailure = ""
    private var retryRules = emptyList<SkipRule>()
    private data class NodeEvent(val app: String, val name: String, val type: Int, val copy: AccessibilityEvent) {
        fun same(other: NodeEvent) = app == other.app && name == other.name && type == other.type
    }
    private val eventLock = Any()
    private val incomingEvents = mutableListOf<NodeEvent>()
    private val queryEvents = mutableListOf<NodeEvent>()
    private val eventCheck = Runnable {
        val pending = synchronized(eventLock) { incomingEvents.toList().also { incomingEvents.clear() } }
        var start = 0
        // Consume event identities in order; a later foreign/content event cannot erase a state event.
        while (start < pending.size) {
            var end = start + 1
            while (end < pending.size && pending[end].same(pending[start])) end++
            consumeEvents(pending.subList(start, end)); start = end
        }
    }
    private fun consumeEvents(events: List<NodeEvent>) {
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
                    repository.noteAppVisit(rightApp)
                    activityTracker.app(rightApp); appChangeTime = SystemClock.elapsedRealtime()
                    discardQueryEvents()
                }
                eventApp = rightApp
                // A later content event must not erase a real Activity event in this batch.
                for (entry in events) if (entry.app == rightApp && entry.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    activityCache.get(ComponentName(entry.app, entry.name)) == true) {
                    val before = activityTracker.current.generation
                    val scene = activityTracker.activity(entry.app, entry.name)
                    if (scene.generation != before) {
                        stateEpoch.incrementAndGet()
                        android.util.Log.i("NoAdMiSkip", "scene app=${scene.app} activity=${scene.activity} generation=${scene.generation}")
                    }
                }
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
        try { inspect(followup.consume(SystemClock.elapsedRealtime())) }
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
        if (repository.enabled && !gesturePending) {
            // Bring the next inspection to the actual actionDelay deadline. An
            // intervening event cannot postpone it (SkipCheckSchedule).
            retryRules.filter { session.needsPoll(it) }.mapNotNull { session.delayRemaining(it) }
                .filter { it > 0 }.minOrNull()?.let { requestCheck(it) }
        }
        followup.request(time, repository.enabled && !gesturePending && (time - appChangeTime < 3000 ||
            time - lastTriggerTime < 3000 || retryRules.any { session.needsPoll(it) }))?.let { requestCheck(it) }
    }
    private fun updateRuntime() {
        runtime?.enabled(repository.enabled && repository.preferences.getString("access_method", "root") == "shizuku")
    }
    private fun taskScene(next: SkipRuntimeScene) {
        if (!::eventWorker.isInitialized) return
        eventWorker.post {
            if (!repository.enabled || repository.preferences.getString("access_method", "root") != "shizuku") return@post
            if (eventApp != next.app) repository.noteAppVisit(next.app)
            val scene = activityTracker.task(next.app, next.activity)
            eventApp = next.app; appChangeTime = SystemClock.elapsedRealtime(); stateEpoch.incrementAndGet()
            discardQueryEvents()
            android.util.Log.i("NoAdMiSkip", "task_scene app=${scene.app} activity=${scene.activity} generation=${scene.generation} listening=${next.listening}")
            worker.post { requestCheck(0) }
        }
    }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SkipRepository.GLOBAL_WHITELIST) stateEpoch.incrementAndGet()
        if (key == "access_method" || key == "enabled") updateRuntime()
        if (key == SkipNotice.ENABLED && !repository.preferences.getBoolean(SkipNotice.ENABLED,false)) noticeHandler.post { noticeOverlay?.dismiss() }
        if (::worker.isInitialized) worker.post { appChangeTime = SystemClock.elapsedRealtime(); requestCheck(0) }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        repository = SkipRepository.get(this)
        diagnostics = SkipDiagnostics(File(filesDir, "smart_skip/diagnostics.json"))
        schedule.consume(); followup.clear(); retryRules = emptyList(); clearEvents()
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
        if (!packageReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            else registerReceiver(packageReceiver, filter)
            packageReceiverRegistered = true
        }
        candidateCache.invalidate()
        runtime?.close(); runtime = SkipRuntimeClient(this, ::taskScene); updateRuntime()
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
        // Our feedback/content windows are not foreground Activity changes (GKD self-event filter).
        if (pkg == packageName && (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || !name.startsWith(packageName))) return
        if (event.eventTime < lastEventTime) return
        lastEventTime = event.eventTime
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) stateEpoch.incrementAndGet()
        synchronized(eventLock) {
            incomingEvents.add(NodeEvent(pkg, name, event.eventType, AccessibilityEvent.obtain(event)))
            while (incomingEvents.size > 32) {
                val content = incomingEvents.indexOfFirst { it.type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED }
                incomingEvents.removeAt(if (content >= 0) content else 0).copy.recycle()
            }
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
    private fun inspect(wholeWindow: Boolean = false) {
        if (!repository.enabled || gesturePending) return
        if (!screenInteractive) { diagnostics.record(eventApp, "screen_paused"); return }
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            diagnostics.record(eventApp, "screen_locked"); return
        }
        val inspectionStarted = SystemClock.elapsedRealtime()
        val epoch = stateEpoch.get()
        var app = eventApp
        if (app.isEmpty()) {
            val first = try { currentRoot() } catch (_: SkipReadFailure) { null } ?: return
            app = first.packageName?.toString().orEmpty(); first.recycle()
            eventApp = app; activityTracker.app(app); appChangeTime = SystemClock.elapsedRealtime()
        }
        if (repository.isWhitelisted(app) || SkipRuntimePolicy.protected(app)) {
            session.transition(app, null, emptyList(), activityTracker.current.generation); retryRules = emptyList(); discardQueryEvents(); return
        }
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
            indexedRules = compiled.withIndex().groupBy { it.value.app }
            revision = sourceSnapshot.revision; session = SkipSession { SystemClock.elapsedRealtime() }
            candidateCache.invalidate()
        }
        val available = candidateCache.get(app, activity, revision) {
            val info = try { packageManager.getPackageInfo(app, 0) } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
            val system = info?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
            val launcher = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == app
            (indexedRules[app].orEmpty() + indexedRules["*"].orEmpty()).sortedBy { it.index }.map { it.value }.filter { rule ->
                SkipScope.applicable(rule.source, rule.group, rule.raw, app, activity,
                    info?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode }, info?.versionName, system, launcher)
            }
        }
        session.transition(app, activity, available, activityTracker.current.generation)
        retryRules = available
        // GKD checks eligible rules before resolving event nodes. Never fetch an
        // entire active window before trying a valid current event source.
        if (available.isEmpty()) { diagnostics.record(app, "no_candidates"); discardQueryEvents(); return }
        if (available.none { session.ready(it) }) { discardQueryEvents(); return }
        // GKD byDelayRule queries the current whole window, never the noisy
        // event subtree that happened to arrive near the timer deadline.
        val inspectionRules = session.inspectionRules(available)
        val delayedInspection = inspectionRules.any { session.delayedDue(it) }
        if (delayedInspection || wholeWindow) discardQueryEvents()
        val source = if (delayedInspection || wholeWindow) null else eventSource()
        val fromEvent = source != null && source.packageName?.toString() == app
        val start = if (fromEvent) source!! else {
            source?.recycle()
            try { currentRoot() } catch (e: SkipReadFailure) { diagnostics.record(app, e.reason); return }
                ?: run { diagnostics.record(app, "root_unavailable"); return }
        }
        if (start.packageName?.toString() != app || stateEpoch.get() != epoch) {
            val rightApp = start.packageName?.toString().orEmpty(); start.recycle()
            if (rightApp.isNotEmpty() && rightApp != app) {
                eventApp = rightApp; activityTracker.app(rightApp); appChangeTime = SystemClock.elapsedRealtime()
            }
            return
        }
        var nodes = AndroidSkipTree(start, nativeReader, rootIsWindow = !fromEvent) { stateEpoch.get() != epoch }
        val started = SystemClock.elapsedRealtime()
        var stage = "root"
        var inspectedWindow = start.windowId
        var origin = if (fromEvent) "event" else "root"
        var previousNodeCount = 0
        fun detail() = "window=$inspectedWindow display=0 origin=$origin scene=${activityTracker.current.generation} activity=$activity stage=$stage runtime=${SkipRuntimeClient.status} fastCounts=${nodes.queryCounts} reads=${nodes.nativeReads} parents=${nodes.parentReads} queryHits=${nodes.fastQueryHits}"
        try {
            var tree = nodes.root()
            var refreshAfterAction = false
            var outcome = "unmatched"
            for (rule in inspectionRules.sortedWith(compareBy<SkipRule> { !session.priority(it) }.thenBy { it.params.order }.thenBy { it.index })) {
                if (stateEpoch.get() != epoch || eventApp != app || !repository.enabled || repository.isWhitelisted(app) || repository.sources.revision != revision) { outcome = "event_changed"; break }
                if (!session.ready(rule)) continue
                if (refreshAfterAction) {
                    // GKD continues to other eligible rules after a synchronous
                    // accepted action. Re-read before querying the next rule:
                    // never reuse a target/tree from before a possibly effective click.
                    val fresh = currentRoot(refresh = true) ?: break
                    if (fresh.packageName?.toString() != app || stateEpoch.get() != epoch) { fresh.recycle(); break }
                    previousNodeCount += nodes.count
                    nodes.close()
                    nodes = AndroidSkipTree(fresh, nativeReader, rootIsWindow = true) { stateEpoch.get() != epoch }
                    inspectedWindow = fresh.windowId; origin = "post_action_root"
                    tree = nodes.root(); refreshAfterAction = false
                }
                val target = rule.query(tree, excluded = { outcome = "excluded" }, adapter = nodes.adapter,
                    stage = { stage = "${rule.identity}/$it" }, windowRootProvider = { nodes.windowRoot { currentRoot() } })
                if (target == null) { session.missed(rule); continue }
                if (!session.matched(rule)) continue
                // GKD checks current event/app/rule state before action. A new
                // root RPC here would discard the benefit of event-node queries.
                if (!repository.enabled || repository.isWhitelisted(app) || repository.sources.revision != revision ||
                    stateEpoch.get() != epoch || eventApp != app || !session.ready(rule)) break
                if (perform(rule, target, app, SystemClock.elapsedRealtime() - inspectionStarted, session.delayLateness(rule))) {
                    outcome = "action_accepted"
                    // Gesture callbacks are asynchronous; wait for completion.
                    // Synchronous node / InputManager actions keep the ordered
                    // pass going, once per rule, exactly like GKD's rule loop.
                    if (gesturePending) break
                    refreshAfterAction = true
                    continue
                }
                outcome = "action_rejected_$actionFailure"
            }
            diagnostics.record(app, outcome, available.size, previousNodeCount + nodes.count, SystemClock.elapsedRealtime() - started, detail())
        } catch (e: Exception) {
            diagnostics.record(app, (e as? SkipReadFailure)?.reason ?: "scan_error", available.size, previousNodeCount + nodes.count, SystemClock.elapsedRealtime() - started, detail())
        } finally { nodes.close() }
    }
    /** GKD A11yService.windowNodeInfo is rootInActiveWindow (default display). */
    private fun currentRoot(timeoutMs: Long = 500, refresh: Boolean = false): AccessibilityNodeInfo? =
        nativeReader.read(timeoutMs, discard = { it?.recycle() }) {
            val node = rootInActiveWindow
            try {
                if (!refresh || node == null || node.refresh()) node else { node.recycle(); null }
            } catch (error: Exception) { node?.recycle(); throw error }
        }
    private fun perform(rule: SkipRule, target: SkipNode, app: String, scanMs: Long, delayLateMs: Long): Boolean {
        val actionSession = session
        val actionRevision = revision
        val actionGeneration = activityTracker.current.generation
        var inputMs = 0L
        var gestureStarted: Long? = null
        fun stillCurrent() = !repository.isWhitelisted(app) && session === actionSession && eventApp == app && activityTracker.current.generation == actionGeneration && repository.sources.revision == actionRevision
        actionFailure = "unsupported"
        fun rejected(reason: String): Boolean { actionFailure = reason; return false }
        if (repository.isWhitelisted(app)) return rejected("whitelisted")
        val node = target.handle as? AccessibilityNodeInfo ?: return rejected("missing_node")
        if (!node.isVisibleToUser) return rejected("not_visible")
        if (node.packageName?.toString() != app) return rejected("foreign_node")
        val rect = Rect(); node.getBoundsInScreen(rect)
        val raw = rule.raw
        val action = raw.action ?: if (raw.position != null) "clickCenter" else if (raw.swipeArg != null) "swipe" else "click"
        fun success(actual: String = action, route: String = "") {
            if (stillCurrent()) { session.success(rule); lastTriggerTime = SystemClock.elapsedRealtime() }
            val timing = SkipActionTiming(scanMs, delayLateMs, inputMs,
                gestureStarted?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0)
            repository.record(rule, app, "已执行 $actual" + (if (route.isEmpty()) "" else "（$route）"), timing)
            android.util.Log.i("NoAdMiSkip", "timing rule=${rule.identity} scanMs=${timing.scanMs} delayLateMs=${timing.delayLateMs} inputMs=${timing.inputMs} callbackMs=${timing.gestureMs}")
            android.util.Log.i("NoAdMiSkip", "action app=$app requested=$action actual=$actual route=$route accepted=true")
            if (actual != "none") noticeHandler.post {
                if (connected && stillCurrent() && repository.preferences.getBoolean(SkipNotice.ENABLED, false)) {
                    val presenter = noticeOverlay ?: SkipNoticeOverlay(this).also { noticeOverlay = it }
                    presenter.show(SkipNotice.text(repository.preferences.getString(SkipNotice.TEXT, null)))
                }
            }
        }
        if (action == "none") { success(); return true }
        if (action == "back") return performGlobalAction(GLOBAL_ACTION_BACK).also { if (it) success("back", "系统返回请求") }
        if (action in listOf("click", "clickNode", "longClick", "longClickNode")) {
            val long = action.startsWith("long")
            if ((action.endsWith("Node") || (if (long) node.isLongClickable else node.isClickable)) && node.performAction(
                    if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)) { success(if (long) "longClickNode" else "clickNode", "节点接口返回成功"); return true }
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
        if (!inside(start) || !inside(end)) return rejected("offscreen")
        val duration = if (action == "swipe") raw.swipeArg!!.duration else if (action.startsWith("long")) 500L else android.view.ViewConfiguration.getTapTimeout().toLong()
        if (duration !in 1..60000L) return false
        // GKD prefers its Shizuku input worker for coordinate actions. Native
        // node-click success still returns earlier; never send a second click.
        val inputMode = runCatching { SkipInputMode.normalize(repository.preferences.getInt(SkipInputMode.KEY, SkipInputMode.COMPAT)) }.getOrDefault(SkipInputMode.COMPAT)
        val enhanced = runtime?.scene?.takeIf { inputMode != SkipInputMode.ACCESSIBILITY && it.app == app && it.activity == activity }
        if (enhanced != null) {
            val inputStarted = SystemClock.elapsedRealtime()
            val result = runtime!!.touch(enhanced, start.first, start.second, end.first, end.second,
                if (action == "swipe" || action.startsWith("long")) duration else 0L)
            inputMs = SystemClock.elapsedRealtime() - inputStarted
            if (result in listOf(1,3,4,5)) {
                val route = when(result) { 1 -> "InputManager"; 4 -> "GKD命令输入"; 5 -> "GKD虚拟触屏"; else -> "input命令后备" }
                success(if (action == "swipe") "swipe" else if (action.startsWith("long")) "longClickCenter" else "clickCenter",
                    "Shizuku 输入返回成功 / $route / 调用${SystemClock.elapsedRealtime()-inputStarted}ms")
                return true
            }
            if (result != 0) {
                diagnostics.record(app, if (result == 2) "privileged_input_uncertain" else "privileged_input_stale")
                return rejected(if (result == 2) "privileged_uncertain" else "privileged_stale")
            }
        }
        val path = Path().apply { moveTo(start.first, start.second); if (action == "swipe") lineTo(end.first, end.second) }
        val builder = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        if (android.os.Build.VERSION.SDK_INT >= 30) builder.setDisplayId(displayId)
        val gesture = builder.build()
        gesturePending = true
        gestureStarted = SystemClock.elapsedRealtime()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) { worker.post { gesturePending = false; success(if (action == "swipe") "swipe" else if (action.startsWith("long")) "longClickCenter" else "clickCenter", if (inputMode == SkipInputMode.ACCESSIBILITY) "无障碍手势完成 / 手势对照" else "无障碍手势完成"); requestCheck(0) } }
            override fun onCancelled(gesture: GestureDescription?) { worker.post { gesturePending = false; repository.record(rule, app, "手势已取消"); retry() } }
        }, Handler(mainLooper))
        inputMs = SystemClock.elapsedRealtime() - gestureStarted!!
        if (!accepted) { gesturePending = false; actionFailure = "gesture_rejected" }
        return accepted
    }
    override fun onInterrupt() { if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); schedule.consume(); followup.clear(); clearEvents() } }
    override fun onDestroy() {
        runtime?.close(); runtime = null
        noticeHandler.removeCallbacksAndMessages(null); noticeOverlay?.dismiss(); noticeOverlay = null
        removeAliveView()
        connectionChanged(this, false)
        if (::repository.isInitialized) repository.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); thread.quitSafely() }
        if (::eventWorker.isInitialized) { eventWorker.removeCallbacksAndMessages(null); eventThread.quitSafely() }
        if (screenReceiverRegistered) { unregisterReceiver(screenReceiver); screenReceiverRegistered = false }
        if (packageReceiverRegistered) { unregisterReceiver(packageReceiver); packageReceiverRegistered = false }
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
