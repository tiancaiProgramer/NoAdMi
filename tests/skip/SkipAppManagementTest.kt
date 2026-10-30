package io.github.miclean.module.skip

import java.nio.file.Files
private var checks=0
private fun verify(value:Boolean){checks++;check(value){"app management assertion $checks"}}
fun main(){
    val directory=Files.createTempDirectory("noadmi-app-rules").toFile()
    fun document(id:Int)="""{id:$id,name:'订阅$id',version:1,apps:[{id:'test.chat',groups:[{key:1,name:'专属',rules:'[text="目标"]'}]}],globalGroups:[{key:2,name:'通用',matchAnyApp:true,rules:[{matches:'[text="目标"]'}]},{key:3,name:'系统',matchAnyApp:false,matchSystemApp:true,rules:[{matches:'[text="系统目标"]'}]}]}"""
    try{
        val file=directory.resolve("sources.json");var store=SkipSources(file)
        store.add(document(1),"",true);store.add(document(2),"",false)
        fun global(id:Long,app:String,key:Int=2):Boolean{val s=store.find(id);val g=s.subscription.globalGroups.first{it.key==key};return SkipScope.applicable(s,g,g.rules.first(),app,null,1,"1",false,false)}
        val all=SkipAppRules.groups(store.list(),"test.chat",1,"1",false,false)
        verify(all.size==4);verify(all.count{it.global}==2);verify(all.map{it.source.id}.toSet()==setOf(1L,2L));verify(all.first{it.source.id==2L}.preference)
        verify(SkipAppRules.groups(store.list(),"no.specific.rules",1,"1",false,false).size==2)
        verify(global(1,"test.chat"));verify(global(1,"other.app"));verify(!global(2,"test.chat"))
        store.enableGlobalForApp(1,"test.chat",2,false)
        verify(!global(1,"test.chat"));verify(global(1,"other.app"));verify(store.find(1).groupPreference("*",store.find(1).subscription.globalGroups.first()))
        verify(!SkipAppRules.groups(store.list(),"test.chat",1,"1",false,false).first{it.source.id==1L&&it.global}.preference)
        store=SkipSources(file);verify(!global(1,"test.chat"));verify(store.find(2).globalApps.isEmpty())
        store.replace(1,document(1).replace("version:1","version:2"),true);verify(!global(1,"test.chat"))
        store.enableGlobalForApp(1,"test.chat",2,true);verify(global(1,"test.chat"))
        store.enableApp(1,"test.chat",false);verify(!global(1,"test.chat"));store.enable(2,true);verify(global(2,"test.chat"))
        store.enableApp(1,"test.chat",true);store.enableGroup(1,"*",2,false);verify(!global(1,"test.chat"));verify(global(2,"test.chat"));verify(SkipAppRules.groups(store.list(),"test.chat",1,"1",false,false).size==4)
        val before=file.readText();try{store.enableGlobalForApp(1,"test.chat",999,false);error("unknown global group accepted")}catch(_:IllegalArgumentException){};verify(file.readText()==before)
        // Legacy source records without globalApps still use their original scopes.
        verify(SkipSource(1,document(1),enabled=true).globalApps.isEmpty())
        println("PASS: $checks cross-subscription app/global projections, per-app global isolation, disabled parent preservation, legacy defaults and disk/update persistence checks")
    }finally{directory.deleteRecursively()}
}
