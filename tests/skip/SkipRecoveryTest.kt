package io.github.miclean.module.skip

private var assertions = 0
private fun verify(value: Boolean) { assertions++; check(value) { "Recovery assertion $assertions" } }
private fun rule(extra: String = ""): SkipRule {
    val s = SkipSource(8,"""{id:8,name:'Recovery',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏广告',$extra rules:[{matches:'[text="跳过"]'}]}]}]}""",enabled=true)
    val group = s.subscription.apps[0].groups[0]
    return SkipRule(s,"test.app",group,group.rules[0],0)
}
fun main() {
    var now = 0L
    val splash = rule("matchTime:10000,forcedTime:0,")
    val session = SkipSession { now }
    session.transition("test.app",null,listOf(splash))
    session.missed(splash);now=100;verify(session.needsPoll(splash))
    now=2999;verify(session.needsPoll(splash))
    now=3000;verify(!session.needsPoll(splash))
    // Successful startup, explicit maximum and matchTime still stop extra polling.
    now=0;val done=SkipSession{now};done.transition("test.app",null,listOf(splash));done.success(splash);verify(!done.needsPoll(splash))
    val short=rule("matchTime:300,");val limited=SkipSession{now};limited.transition("test.app",null,listOf(short));now=301;verify(!limited.needsPoll(short))
    now=0;val max=rule("actionMaximum:0,");val capped=SkipSession{now};capped.transition("test.app",null,listOf(max));verify(!capped.needsPoll(max))
    val forced=rule("matchTime:10000,forcedTime:10000,");val retry=SkipSession{now};retry.transition("test.app",null,listOf(forced))
    // Simulate failed node reads: no matched/success call; retries remain bounded.
    for(t in listOf(100L,600L,1200L,5000L,9999L)){now=t;verify(retry.needsPoll(forced))}
    now=10001;verify(!retry.needsPoll(forced))
    // A delayed ad appearing without a new accessibility event can still match.
    now=0;val delayed=SkipSession{now};delayed.transition("test.app",null,listOf(splash));delayed.missed(splash)
    now=1600;verify(delayed.needsPoll(splash));verify(delayed.ready(splash))
    val root=SkipNode(mapOf("name" to "android.widget.FrameLayout"));val button=SkipNode(mapOf("name" to "android.widget.TextView","text" to "跳过"));root.add(button)
    verify(splash.query(root)===button)
    delayed.success(splash);verify(!delayed.needsPoll(splash))
    // Repeated content events must not push an existing scheduled inspection back.
    val queue=SkipCheckSchedule();verify(queue.request(0,120))
    for(t in 1L..120L){verify(!queue.request(t,120));verify(queue.nextAt==120L)}
    verify(queue.request(120,0).not());queue.consume();verify(queue.request(120,200));verify(queue.request(125,0));verify(queue.nextAt==125L)
    queue.consume();verify(queue.nextAt==null)
    // Local diagnostics cannot create an event-by-event disk/logging loop.
    now=0;val gate=SkipDiagnosticGate{now};verify(gate.claim());for(t in 1L..999L){now=t;verify(!gate.claim())};now=1000;verify(gate.claim())
    // An early match must not read unrelated, expensive or changing sibling subtrees.
    var reads=0;lateinit var lazyRoot: SkipNode
    lazyRoot=SkipNode(mapOf("name" to "android.widget.FrameLayout"),childCountHint=2,childLookup={index->
        reads++
        if(index==1)error("unrelated subtree should remain unread")
        SkipNode(mapOf("name" to "android.widget.TextView","text" to "跳过","index" to 0,"depth" to 1)).also{it.parent=lazyRoot}
    })
    val target=splash.query(lazyRoot);verify(target!=null);verify(reads==1)
    verify(lazyRoot.child(0)===target);verify(reads==1)
    // Exclusion failure cannot be treated as 'no exclusion' and proceed to a click.
    val excludedSource=SkipSource(9,"""{id:9,name:'Exclusion',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏',rules:[{matches:'[text="跳过"]',excludeMatches:'[text="禁止"]'}]}]}]}""",enabled=true)
    val excludedGroup=excludedSource.subscription.apps[0].groups[0]
    val exclusion=SkipRule(excludedSource,"test.app",excludedGroup,excludedGroup.rules[0],0)
    var failed=false;try{exclusion.query(lazyRoot)}catch(_:IllegalStateException){failed=true};verify(failed)
    // Missing positive targets must not trigger expensive exclusion traversal.
    val orderedSource=SkipSource(10,"""{id:10,name:'Ordered',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏',fastQuery:true,rules:[{anyMatches:'[text="跳过"]',excludeMatches:'[text="禁止"]'}]}]}]}""",enabled=true)
    val orderedGroup=orderedSource.subscription.apps[0].groups[0]
    val orderedRule=SkipRule(orderedSource,"test.app",orderedGroup,orderedGroup.rules[0],0)
    val lookupValues=mutableListOf<String>()
    val lookup=object:SnapshotNodeAdapter(){
        override fun getFastQueryDescendants(node:SkipNode,fastQueryList:List<li.gkd.selector.FastQuery>):Sequence<SkipNode>{
            lookupValues.addAll(fastQueryList.map{it.value})
            check(fastQueryList.none{it.value=="禁止"}){"Exclusion queried without a positive target"}
            return emptySequence()
        }
    }
    verify(orderedRule.query(SkipNode(mapOf("name" to "Root")),adapter=lookup)==null)
    verify(lookupValues==listOf("跳过"))
    // Reordering never permits an excluded positive target to execute.
    val guardedRoot=SkipNode(mapOf("name" to "Root"));val guardedTarget=SkipNode(mapOf("text" to "跳过"));guardedRoot.add(guardedTarget)
    verify(orderedRule.query(guardedRoot)===guardedTarget)
    guardedRoot.add(SkipNode(mapOf("text" to "禁止")))
    var excluded=false;verify(orderedRule.query(guardedRoot,excluded={excluded=true})==null);verify(excluded)
    // Direct native-query candidates need no ancestors unless the selector asks.
    var parentReads=0
    val deferred=SkipNode(mapOf("text" to "跳过"),parentLookup={parentReads++;guardedRoot})
    verify(deferred.attrs["text"]=="跳过");verify(parentReads==0)
    verify(deferred.parent===guardedRoot);verify(deferred.parent===guardedRoot);verify(parentReads==1)
    deferred.parent=null;verify(deferred.parent==null);verify(parentReads==1)
    // GKD queries an event subtree unless matchRoot explicitly requests the window.
    val fullWindow=SkipNode(mapOf("name" to "Root"));val eventButton=SkipNode(mapOf("text" to "跳过"))
    fullWindow.add(eventButton);fullWindow.add(SkipNode(mapOf("text" to "禁止")))
    verify(orderedRule.query(eventButton,windowRoot=fullWindow)===eventButton)
    verify(orderedRule.query(eventButton,windowRootProvider={error("Event query must not fetch a window root")})===eventButton)
    val rootedSource=SkipSource(11,"""{id:11,name:'Rooted',version:1,apps:[{id:'test.app',groups:[{key:1,name:'开屏',rules:[{matchRoot:true,matches:'[text="跳过"]',excludeMatches:'[text="禁止"]'}]}]}]}""",enabled=true)
    val rootedGroup=rootedSource.subscription.apps[0].groups[0]
    verify(SkipRule(rootedSource,"test.app",rootedGroup,rootedGroup.rules[0],0).query(eventButton,windowRoot=fullWindow)==null)
    println("PASS: $assertions startup follow-up, bounded failure recovery, event coalescing and diagnostic throttling checks")
}
