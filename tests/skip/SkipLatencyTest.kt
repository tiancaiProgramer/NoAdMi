package io.github.miclean.module.skip

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private var assertions = 0
private fun verify(ok: Boolean) { assertions++; check(ok) { "Latency regression $assertions" } }
fun main() {
    // Reproduce slow disk while producers continue; only the newest pending
    // snapshot is retained, including clear and records created after clear.
    val dir = Files.createTempDirectory("noadmi-write-test").toFile()
    val file = java.io.File(dir,"actions")
    val io = Executors.newSingleThreadExecutor()
    val started = CountDownLatch(1); val release = CountDownLatch(1); val saved = CountDownLatch(1)
    val writes = AtomicInteger()
    val writer = SkipLatestWriter<List<String>>(io, { value ->
        if (writes.incrementAndGet() == 1) { started.countDown(); check(release.await(3,TimeUnit.SECONDS)) }
        if (value.isEmpty()) file.delete() else file.writeText(value.joinToString(","))
        if (value == listOf("after-clear")) saved.countDown()
    })
    writer.submit(listOf("old")); verify(started.await(2,TimeUnit.SECONDS))
    repeat(1000) { writer.submit(listOf("pending-$it")) }
    writer.submit(emptyList());writer.submit(listOf("after-clear"))
    verify(writes.get()==1);release.countDown();verify(saved.await(3,TimeUnit.SECONDS))
    verify(file.readText()=="after-clear");verify(writes.get()==2)
    val cleared=CountDownLatch(1)
    io.execute { cleared.countDown() };verify(cleared.await(2,TimeUnit.SECONDS))
    writer.submit(emptyList());val drained=CountDownLatch(1);io.execute { drained.countDown() }
    verify(drained.await(2,TimeUnit.SECONDS));verify(!file.exists())
    io.shutdown();verify(io.awaitTermination(2,TimeUnit.SECONDS));dir.deleteRecursively()
    // A failed save does not strand the queue or retain an obsolete snapshot.
    val queue=java.util.ArrayDeque<Runnable>();val failures=AtomicInteger();val persisted=mutableListOf<Int>()
    val resilient=SkipLatestWriter<Int>({ queue.add(it) }, { if(it==1)throw java.io.IOException("disk");persisted.add(it) }, { failures.incrementAndGet() })
    resilient.submit(1);queue.removeFirst().run();resilient.submit(2);queue.removeFirst().run()
    verify(failures.get()==1);verify(persisted==listOf(2))
    // Scene/revision/expiry invalidates restrictions; content polling reuses
    // them without another package lookup. Failed loads never poison the key.
    var now=0L;var loads=0;val cache=SkipCandidateCache<List<String>>({now},30000)
    fun get(app:String="app",activity:String?="Main",rev:Long=1)=cache.get(app,activity,rev){ loads++;listOf("$app/$activity/$rev") }
    verify(get()==get());repeat(100){get()};verify(loads==1)
    get(activity="MiniApp");verify(loads==2);get(rev=2);verify(loads==3)
    get(app="other");verify(loads==4);now=30000;get(app="other");verify(loads==5)
    cache.invalidate();get(app="other");verify(loads==6)
    try { cache.get("bad",null,1){error("unavailable")};error("missing failure") }catch(_:IllegalStateException){}
    verify(cache.get("bad",null,1){listOf("recovered")}==listOf("recovered"))
    // A due rule is rechecked alone, without visiting an unrelated deep
    // selector first. Original delay, match expiry and cooldown still apply.
    val source=SkipSource(901,"""{id:901,name:'时序',version:1,apps:[{id:'app',groups:[{key:1,name:'开屏',actionCd:500,matchTime:10000,rules:[{key:0,matches:'[text="unrelated"]'},{key:1,actionDelay:800,matches:'[text="skip"]'}]}]}]}""",enabled=true)
    val group=source.subscription.apps[0].groups[0]
    val unrelated=SkipRule(source,"app",group,group.rules[0],0);val delayed=SkipRule(source,"app",group,group.rules[1],1)
    val rules=listOf(unrelated,delayed);now=0
    val session=SkipSession{now};session.transition("app","Main",rules,1)
    verify(!session.matched(delayed));now=799;verify(session.inspectionRules(rules)==rules)
    now=835;verify(session.inspectionRules(rules)==listOf(delayed));verify(session.delayLateness(delayed)==35L)
    // If the delayed target disappears, the next normal inspection must still
    // visit other eligible rules rather than selecting the expired timer forever.
    verify(session.inspectionRules(rules)==rules)
    session.success(delayed);verify(!session.ready(delayed));verify(session.inspectionRules(rules)==rules)
    session.transition("app","Main",rules,2);verify(session.delayRemaining(delayed)==null)
    // An unavailable descendant ends only that branch. A valid target in a
    // sibling branch remains matchable; null children are memoized per pass.
    var nullReads=0
    val broken=SkipNode(mapOf("name" to "FrameLayout"),childCountHint=2,childLookup={nullReads++;null})
    val root=SkipNode(mapOf("name" to "FrameLayout"));root.add(broken)
    val target=SkipNode(mapOf("name" to "TextView","text" to "skip"));root.add(target)
    verify(delayed.query(root)===target);verify(nullReads==1)
    verify(delayed.query(root)===target);verify(nullReads==1)
    println("PASS: $assertions slow-writer/clear recovery, coalescing, candidate invalidation, due-rule isolation and dynamic-child checks")
}
