package io.github.miclean.module.skip

private var checks = 0
private fun verify(value: Boolean) { checks++; check(value) { "Delayed/runtime assertion $checks" } }
private fun rule(extra: String = "", raw: String = "{key:0,matches:'[text=\"跳过\"]'}"): SkipRule {
    val source = SkipSource(301,"""{id:301,name:'时序测试',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏广告',actionDelay:800,actionCd:300,actionMaximum:1,matchTime:10000,$extra rules:[$raw]}]}]}""",enabled=true)
    val group=source.subscription.apps[0].groups[0]; return SkipRule(source,"test.app",group,group.rules[0],0)
}
fun main() {
    var now=0L; val rule=rule(); val session=SkipSession{now};session.transition("test.app","Main",listOf(rule),1)
    verify(session.ready(rule));verify(!session.matched(rule));verify(session.delayRemaining(rule)==800L);verify(!session.ready(rule))
    // A stream of unrelated event subtrees cannot restart the 800 ms timer.
    for(t in 100L..700L step 100L) { now=t;session.missed(rule);verify(!session.ready(rule));verify(session.needsPoll(rule));verify(session.delayRemaining(rule)==800-t) }
    now=800;verify(session.ready(rule));verify(session.delayedDue(rule));verify(session.matched(rule))
    val absent=SkipNode(mapOf("name" to "android.widget.FrameLayout"));verify(rule.query(absent)==null);session.missed(rule)
    verify(session.delayedDue(rule));now=900
    val root=SkipNode(mapOf("name" to "android.widget.FrameLayout"));val skip=SkipNode(mapOf("name" to "android.widget.TextView","text" to "跳过"));root.add(skip)
    verify(rule.query(root)===skip);verify(session.matched(rule));session.success(rule);verify(!session.ready(rule));verify(!session.delayedDue(rule));verify(!session.needsPoll(rule))
    // Same-class reopen clears delay/maximum; generic events don't.
    session.transition("test.app","Main",listOf(rule),2);verify(session.ready(rule));verify(!session.matched(rule));now+=300
    session.transition("test.app","Main",listOf(rule),3);verify(session.delayRemaining(rule)==null);verify(!session.matched(rule));now+=799;verify(!session.ready(rule));now++;verify(session.ready(rule))
    // A delay that extends beyond matchTime must never click after expiry.
    val short=rule("matchDelay:0,", "{key:0,matchTime:500,matches:'[text=\"跳过\"]'}");val bounded=SkipSession{now};bounded.transition("test.app","Main",listOf(short));verify(!bounded.matched(short));now+=800;verify(!bounded.ready(short));verify(!bounded.delayedDue(short));verify(!bounded.needsPoll(short))
    val idleRule=rule();val idle=SkipSession{now};idle.transition("test.app","Idle",listOf(idleRule));verify(!idle.matched(idleRule));now+=3500;idle.missed(idleRule);verify(!idle.needsPoll(idleRule));verify(idle.delayedDue(idleRule))
    // Predecessor identity survives Activity transitions, as in GKD lastTriggerRule.
    val first=rule(raw="{key:0,actionDelay:0,matches:'[text=\"跳过\"]'},{key:1,preKeys:0,matches:'[text=\"跳过\"]'}")
    val second=SkipRule(first.source,first.app,first.group,first.group.rules[1],1);val chain=SkipSession{now};chain.transition("test.app","Main",listOf(first,second));chain.success(first);chain.transition("test.app","Next",listOf(first,second));verify(chain.ready(second));verify(!chain.matched(second));now+=400;verify(chain.needsPoll(second));verify(!chain.ready(second));now+=400;verify(chain.ready(second))
    // Task callbacks advance repeated same-class scenes, then deduplicate the matching A11y event.
    val tracker=SkipActivityTracker{now};val a=tracker.task("test.app","Main");verify(tracker.activity("test.app","Main")==a);now+=50;val b=tracker.task("test.app","Main");verify(b.generation>a.generation);verify(tracker.activity("test.app","Main")==b)
    verify(SkipRuntimePolicy.protected("android"));verify(SkipRuntimePolicy.protected("com.android.systemui"));verify(SkipRuntimePolicy.protected("io.github.miclean.module"));verify(!SkipRuntimePolicy.protected("test.app"))
    verify(SkipRuntimePolicy.coordinates(1f,2f,999f,1999f,1000,2000));verify(!SkipRuntimePolicy.coordinates(Float.NaN,2f,2f,3f,1000,2000));verify(!SkipRuntimePolicy.coordinates(1f,2f,1000f,3f,1000,2000));verify(!SkipRuntimePolicy.coordinates(1f,-1f,2f,3f,1000,2000))
    val follow=SkipFollowupSchedule();verify(follow.request(0,true)==300L);verify(!follow.consume(100));verify(follow.request(100,true)==200L);verify(!follow.consume(200));verify(follow.request(200,true)==100L);verify(follow.consume(300));verify(follow.request(300,true)==300L);verify(follow.request(400,false)==null);verify(!follow.consume(600))
    println("PASS: $checks retained actionDelay, deadline re-query, expiry, scene reset, cross-Activity prerequisites and runtime guards")
}
