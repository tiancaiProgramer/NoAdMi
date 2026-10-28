package io.github.miclean.module.skip
import java.nio.file.Files
private fun ensure(value: Boolean) { check(value) }
fun main() {
    val folder=Files.createTempDirectory("noadmi-management").toFile()
    val document="""{id: 123,name:'分类测试',version:1,categories:[{key:1,name:'开屏',enable:true},{key:2,name:'功能',enable:false}],apps:[{id:'test.app',groups:[{key:1,name:'开屏广告',rules:[{matches:'[text="跳过"]'}]},{key:2,name:'功能测试',rules:[{matches:'[text="测试"]'}]},{key:3,name:'开屏默认关闭',enable:false,rules:[{matches:'[text="跳过"]'}]}]}],globalGroups:[{key:1,name:'功能全局',rules:[{matches:'[text="全局"]'}]}]}"""
    try {
        val file=folder.resolve("sources.json");var store=SkipSources(file);store.add(document,"https://example.com/old",true)
        fun group(key:Int)=store.find(123).subscription.getAppGroups("test.app").first{it.key==key}
        ensure(store.find(123).groupEnabled("test.app",group(1)));ensure(!store.find(123).groupEnabled("test.app",group(2)))
        ensure(!store.find(123).groupEnabled("test.app",group(3)))
        store.enableCategory(123,1,false);ensure(!store.find(123).groupEnabled("test.app",group(1)))
        store.enableCategory(123,1,true);ensure(store.find(123).groupEnabled("test.app",group(3)))
        store.enableGroup(123,"test.app",1,false);store.enableCategory(123,1,true);ensure(!store.find(123).groupEnabled("test.app",group(1)))
        store.enableCategory(123,2,false);val source=store.find(123);ensure(source.groupEnabled("*",source.subscription.globalGroups.first()))
        store.enableApp(123,"test.app",false);ensure(!store.find(123).groupEnabled("test.app",group(3)))
        val before=file.readText();try{store.changeUrl(123,"http://invalid.test/");error("accepted insecure URL")}catch(_:IllegalArgumentException){};ensure(file.readText()==before)
        store.changeUrl(123,"  https://example.net/new.json5  ");store=SkipSources(file);ensure(store.find(123).url=="https://example.net/new.json5");ensure(store.find(123).document==document);ensure(store.find(123).enabled);ensure(store.find(123).apps["test.app"]==false);ensure(store.find(123).categories[2]==false);ensure(store.find(123).groups["test.app/1"]==false)
        store.replace(123,document.replace("version:1","version:2"),true);ensure(store.find(123).categories[1]==true);ensure(store.find(123).url=="https://example.net/new.json5")
        val saved=file.readText();try{store.replace(123,document.replace("id: 123","id: 999"),true);error("accepted changed ID")}catch(_:IllegalArgumentException){};ensure(file.readText()==saved)
        val unchanged=file.readText()
        try{store.replaceFromUrl(123,document.replace("id: 123","id: 999"),"https://example.org/wrong");error("accepted changed ID")}catch(_:IllegalArgumentException){};ensure(file.readText()==unchanged)
        try{store.replaceFromUrl(123,document,"https://example.org/older");error("accepted downgrade")}catch(_:IllegalArgumentException){};ensure(file.readText()==unchanged)
        ensure(store.replaceFromUrl(123,document.replace("version:1","version:3"),"https://example.org/new"));ensure(store.find(123).url=="https://example.org/new");ensure(store.find(123).categories[1]==true);ensure(store.find(123).apps["test.app"]==false)
        ensure(!store.replaceFromUrl(123,document.replace("version:1","version:3"),"https://example.org/same"));ensure(store.find(123).url=="https://example.org/same");ensure(store.find(123).version==3)
        // Old serialized records have no category field and still load with unchanged defaults.
        file.writeText("""[{"id":123,"document":${kotlinx.serialization.json.Json.encodeToString(document)},"url":"https://example.com/old","enabled":true}]""");store=SkipSources(file);ensure(store.find(123).categories.isEmpty());ensure(store.find(123).groupEnabled("test.app",group(1)))
        println("PASS subscription management: backward-compatible categories, precedence, globals, atomic HTTPS link edit, ID protection and update preservation")
    } finally { folder.deleteRecursively() }
}
