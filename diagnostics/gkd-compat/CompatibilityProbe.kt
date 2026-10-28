package audit

import io.github.miclean.module.skip.*
import java.io.File
import li.gkd.app.subscription.RawSubscription

fun main(args: Array<String>) {
    val doc=File(args[0]).readText();val raw=RawSubscription.parse(doc)
    val source=SkipSource(raw.id,doc,enabled=true)
    println("Public reference subscription: id=${raw.id} version=${raw.version} apps=${raw.apps.size}")
    for(id in listOf("com.taobao.taobao","com.taobao.idlefish")) {
        val app=raw.apps.first{it.id==id};val groups=app.groups.filter{it.valid&&source.groupEnabled(id,it)}
        val rules=groups.flatMap{g->g.rules.mapIndexed{i,r->SkipRule(source,id,g,r,i)}}
        val unknown=rules.count{SkipScope.applicable(source,it.group,it.raw,id,null,Int.MAX_VALUE,"",false,false)}
        val globals=raw.globalGroups.filter{it.valid&&source.groupEnabled("*",it)}.flatMap{g->g.rules.mapIndexed{i,r->SkipRule(source,"*",g,r,i)}}.count{SkipScope.applicable(source,it.group,it.raw,id,null,Int.MAX_VALUE,"",false,false)}
        println("$id enabled app rules=${rules.size}; allowed with unknown activity=$unknown; eligible global rules=$globals")
        if(id=="com.taobao.idlefish") raw.globalGroups.filter{it.valid&&source.groupEnabled("*",it)}.forEach{g->g.rules.forEachIndexed{i,r->
            if(SkipScope.applicable(source,g,r,id,null,Int.MAX_VALUE,"",false,false)){
                val x=SkipRule(source,"*",g,r,i);var tick=0L;val runtime=SkipSession{tick};runtime.transition(id,null,listOf(x));tick=100L
                println("Idlefish global group=${g.name} key=${r.key} matchTime=${x.params.matchTime} forcedTime=${x.params.forcedTime} nextPollAfterMiss=${runtime.needsPoll(x)}")
            }
        }}
    }
    val group=raw.apps.first{it.id=="com.taobao.taobao"}.groups.first{it.name=="开屏广告"}
    val rule=SkipRule(source,"com.taobao.taobao",group,group.rules.first(),0)
    val root=SkipNode(mapOf("name" to "android.widget.FrameLayout","depth" to 0))
    val button=SkipNode(mapOf("name" to "android.widget.FrameLayout","vid" to "ad_close","clickable" to true,"visibleToUser" to true,"width" to 180,"height" to 70,"depth" to 1,"index" to 0))
    val text=SkipNode(mapOf("name" to "android.widget.TextView","text" to "跳过 5","visibleToUser" to true,"width" to 160,"height" to 60,"depth" to 2,"index" to 0))
    root.add(button);button.add(text)
    check(rule.query(root)===button)
    println("Taobao real splash selector matched its synthetic close node: true")
    var now=0L;val session=SkipSession{now};session.transition("com.taobao.taobao","com.taobao.tao.welcome.Welcome",listOf(rule));now=100L
    println("Taobao after initial miss: ready=${session.ready(rule)}, matchTime=${rule.params.matchTime}, forcedTime=${rule.params.forcedTime}, scheduledPoll=${session.needsPoll(rule)}")
    check(session.ready(rule)&&rule.params.matchTime==10000L&&session.needsPoll(rule))
    check(SkipScope.activity("com.taobao.idlefish",null,listOf(".maincontainer.activity.MainActivity"),null))
    println("Unknown Activity follows GKD AppRule fallback and still requires node matching: true")
    println("PASS: real Taobao selector syntax, GKD startup follow-up and Activity fallback")
}
