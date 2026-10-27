package io.github.miclean.module.skip

import java.nio.file.Files
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleBounds

private var assertions = 0
private fun verify(value: Boolean) { assertions++; check(value) { "Smart skip assertion $assertions" } }
private fun rejects(action: () -> Unit) { var rejected = false; try { action() } catch (e: IllegalArgumentException) { rejected = true }; verify(rejected) }
private fun source(version: Int = 1, id: Long = 7, extra: String = "", rules: String = "{matches: '[text=\"跳过\"]'}"): String = """
    {id: $id, name: '测试订阅', version: $version, // JSON5 comments + single quotes + trailing comma
     apps: [{id: 'test.app', name: '测试应用', groups: [{key: 1, name: '开屏广告', $extra rules: [$rules]}]}],}
""".trimIndent()
private fun rule(document: String, index: Int = 0): SkipRule {
    val s = SkipSource(7, document, enabled = true); val group = s.subscription.apps[0].groups[0]
    verify(group.valid); return SkipRule(s, "test.app", group, group.rules[index], index)
}
fun main() {
    // Transaction/update regressions: real disk reload, invalid updates and false overrides retained.
    val folder = Files.createTempDirectory("noadmi-subscription-tests").toFile()
    try {
        val file = folder.resolve("sources.json"); var store = SkipSources(file)
        store.add(source(), "https://example.com/rules.json5", true)
        store.enableApp(7, "test.app", false); store.enableGroup(7, "test.app", 1, false)
        store = SkipSources(file); verify(!store.find(7).apps.getValue("test.app")); verify(!store.find(7).groups.getValue("test.app/1"))
        verify(store.replace(7, source(2), true)); verify(store.find(7).version == 2)
        verify(!store.find(7).apps.getValue("test.app")); verify(!store.find(7).groups.getValue("test.app/1"))
        val before = file.readText(); rejects { store.replace(7, source(3, 9), true) }; verify(file.readText() == before)
        rejects { store.replace(7, source(1), true) }; verify(file.readText() == before)
        rejects { store.replace(7, "{broken", true) }; verify(file.readText() == before)
        rejects { store.add(source(3), "https://example.com/other", true) }
        rejects { SkipSources.requireHttps("file:///private/data.json") }; rejects { SkipSources.requireHttps("http://example.com/a") }
        rejects { SkipSources.requireHttps("https://user:pass@example.com/a") }
        verify(!store.replace(7, source(2), true)); verify(SkipSources(file).find(7).checkedAt > 0)
        store.add(source(id = -2), "", false); verify(store.find(-2).local); store.replace(-2, source(3, -2), false)
        verify(SkipSources(file).find(-2).version == 3); store.remove(-2); verify(SkipSources(file).list().size == 1)
        val workers = (0..3).map { index -> Thread { repeat(15) { store.enableGroup(7, "test.app", index + 2, it % 2 == 0) } }.apply { start() } }
        workers.forEach { it.join() }; verify(SkipSources(file).find(7).groups.size == 5)
    } finally { folder.deleteRecursively() }
    // Real GKD selectors: target marker, ancestry, sibling and logical/regex conditions.
    val root = SkipNode(mapOf("name" to "android.widget.FrameLayout", "text" to null, "depth" to 0))
    val title = SkipNode(mapOf("name" to "android.widget.TextView", "text" to "广告", "depth" to 1, "index" to 0))
    val skip = SkipNode(mapOf("name" to "android.widget.Button", "text" to "跳过", "clickable" to true, "visibleToUser" to true, "index" to 1, "depth" to 1))
    root.add(title); root.add(skip)
    verify(rule(source()).query(root) === skip)
    verify(rule(source(rules = "{matches: ['[text=\"广告\"]', '[text=\"跳过\"]']}" )).query(root) === skip)
    verify(rule(source(rules = "{matches: '[text=\"跳过\"]', excludeMatches: '[text=\"广告\"]'}")).query(root) == null)
    verify(rule(source(rules = "{anyMatches: ['[text=\"未知\"]', '[text=\"跳过\"]']}" )).query(root) === skip)
    verify(rule(source(rules = "{matches: '[text=\"跳过\"]', excludeAllMatches: ['[text=\"广告\"]', '[text=\"不存在\"]']}" )).query(root) === skip)
    verify(rule(source(rules = "{matchRoot: true, matches: '@Button[clickable=true] <2 FrameLayout'}")).query(root) === skip)
    verify(rule(source(rules = "{matches: '[text~=\"^跳.*\" && visibleToUser=true]'}")).query(root) === skip)
    verify(!RawSubscription.parse(source(rules = "{matches: '[unknownAttr=true]'}")).apps[0].groups[0].valid)
    // Match timing, prerequisite chain, per-screen count and action-delay cancellation.
    var time = 10000L
    val first = rule(source(extra = "actionCd: 500, actionMaximum: 1,", rules = "{key: 0, matches: '[text=\"跳过\"]'}, {key: 1, preKeys: 0, actionDelay: 200, matches: '[text=\"跳过\"]'}"))
    val second = SkipRule(first.source, first.app, first.group, first.group.rules[1], 1)
    val session = SkipSession { time }; session.transition("test.app", "test.app.Main", listOf(first, second))
    verify(session.ready(first)); verify(!session.ready(second)); session.success(first)
    verify(!session.ready(first)); verify(session.ready(second)); verify(!session.matched(second)); time += 199; verify(!session.matched(second))
    session.missed(second); time += 1; verify(!session.matched(second)); time += 200; verify(session.matched(second)); session.success(second)
    verify(!session.ready(second)); time += 1000; session.transition("test.app", "test.app.Main", listOf(first, second)); verify(!session.ready(first))
    session.transition("test.app", "test.app.Other", listOf(first, second)); verify(session.ready(first)); verify(!session.ready(second))
    val delayed = rule(source(extra = "matchDelay: 300, matchTime: 500, forcedTime: 1000,"))
    val wait = SkipSession { time }; wait.transition("test.app", "Main", listOf(delayed)); verify(!wait.ready(delayed)); verify(wait.needsPoll(delayed))
    time += 300; verify(wait.ready(delayed)); time += 501; verify(!wait.ready(delayed)); verify(!wait.needsPoll(delayed))
    // Group restrictions are inherited; unknown foreground activity/version cannot bypass them.
    verify(!SkipScope.activity("test.app", null, listOf(".Main"), null))
    verify(SkipScope.activity("test.app", "test.app.Main", listOf(".Main"), null))
    verify(!SkipScope.activity("test.app", "test.app.MainDialog", null, listOf(".Main")))
    val versioned = rule(source(extra = "versionCodes: [42], activityIds: '.Main',"))
    verify(!SkipScope.applicable(versioned.source, versioned.group, versioned.raw, "test.app", "test.app.Main", 41, "1", false, false))
    verify(SkipScope.applicable(versioned.source, versioned.group, versioned.raw, "test.app", "test.app.Main", 42, "1", false, false))
    verify(!SkipScope.applicable(versioned.source, versioned.group, versioned.raw, "test.app", null, 42, "1", false, false))
    val position = RawSubscription.parse(source(rules = "{position: {right: 'width / 4', top: 'height / 2'}, matches: '[text=\"跳过\"]'}")).apps[0].groups[0].rules[0].position!!
    verify(position.calc(RuleBounds(100, 100, 300, 200), 1080, 2400) == (250f to 150f))
    val limited = rule(source(extra = "actionMaximum: 1,"))
    val roundTrip = SkipSession { time }
    roundTrip.transition("test.app", "test.app.Main", listOf(limited)); roundTrip.success(limited)
    verify(!roundTrip.ready(limited))
    roundTrip.transition("io.github.miclean.module", null, emptyList())
    roundTrip.transition("test.app", "test.app.Main", listOf(limited)); verify(roundTrip.ready(limited))
    println("PASS: $assertions smart skip JSON5, atomic persistence, override retention, selector, timing, prerequisite, version/activity and position checks")
}
