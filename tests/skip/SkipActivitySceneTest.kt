package io.github.miclean.module.skip

private var assertions = 0
private fun verify(value: Boolean) { assertions++; check(value) { "Activity scene assertion $assertions" } }
private fun rule(reset: String = "activity", delay: Int = 0): SkipRule {
    val source=SkipSource(91,"""{id:91,name:'场景',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏广告',resetMatch:'$reset',matchTime:10000,forcedTime:10000,actionMaximum:1,rules:[{matches:'[text="跳过"]',actionDelay:$delay}]}]}]}""",enabled=true)
    val group=source.subscription.apps[0].groups[0];return SkipRule(source,"test.app",group,group.rules[0],0)
}
fun main(){
    var now=0L;val tracker=SkipActivityTracker{now};val session=SkipSession{now};val activity="test.app.SameActivity";val splash=rule()
    val first=tracker.activity("test.app",activity);session.transition(first.app,first.activity,listOf(splash),first.generation);verify(session.ready(splash));session.success(splash);verify(!session.ready(splash))
    now=999;val duplicate=tracker.activity("test.app",activity);verify(duplicate==first);session.transition(duplicate.app,duplicate.activity,listOf(splash),duplicate.generation);verify(!session.ready(splash))
    now=11000;session.transition(first.app,first.activity,listOf(splash),first.generation);verify(!session.ready(splash));verify(!session.needsPoll(splash))
    val reopened=tracker.activity("test.app",activity);verify(reopened.generation>first.generation);session.transition(reopened.app,reopened.activity,listOf(splash),reopened.generation);verify(session.ready(splash));verify(session.needsPoll(splash))
    // Reopening the same named Activity repeatedly restores time/maximum only once per scene.
    repeat(15){session.success(splash);verify(!session.ready(splash));now+=11000;val scene=tracker.activity("test.app",activity);session.transition(scene.app,scene.activity,listOf(splash),scene.generation);verify(session.ready(splash))}
    // App-lifetime rules must not be reset by Activity generations or content events.
    val appRule=rule("app");val appSession=SkipSession{now};var scene=tracker.current;appSession.transition(scene.app,scene.activity,listOf(appRule),scene.generation);appSession.success(appRule);now+=11000;scene=tracker.activity("test.app",activity);appSession.transition(scene.app,scene.activity,listOf(appRule),scene.generation);verify(!appSession.ready(appRule))
    val other=tracker.app("test.other");appSession.transition(other.app,other.activity,emptyList(),other.generation);val back=tracker.activity("test.app",activity);appSession.transition(back.app,back.activity,listOf(appRule),back.generation);verify(appSession.ready(appRule))
    // A later generic content event cannot replace the already accepted scene.
    val accepted=tracker.current;repeat(30){now+=100;verify(tracker.app("test.app")==accepted)}
    // Genuine Activity switches must advance even inside the one-second deduplication window.
    val next=tracker.activity("test.app","test.app.OtherActivity");verify(next.generation>accepted.generation);verify(next.activity=="test.app.OtherActivity")
    // An old actionDelay cannot carry into a newly reopened scene.
    val delayed=rule(delay=800);val ds=SkipSession{now};ds.transition(next.app,next.activity,listOf(delayed),next.generation);verify(!ds.matched(delayed));now+=2000;val newer=tracker.activity("test.app",next.activity!!);ds.transition(newer.app,newer.activity,listOf(delayed),newer.generation);verify(!ds.matched(delayed));now+=800;verify(ds.matched(delayed))
    verify(SkipNotice.text(null)==SkipNotice.DEFAULT_TEXT);verify(SkipNotice.text("   ")==SkipNotice.DEFAULT_TEXT);verify(SkipNotice.text("  自定义提示  ")=="自定义提示");val long="🙂".repeat(100);val truncated=SkipNotice.text(long);verify(truncated.codePointCount(0,truncated.length)==80);verify(!Character.isHighSurrogate(truncated.last()))
    println("PASS: $assertions same-Activity reopen, expiry, maximum, burst deduplication, app reset policy, delayed action and custom notice checks")
}
