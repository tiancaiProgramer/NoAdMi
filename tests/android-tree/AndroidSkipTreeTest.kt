package io.github.miclean.module.skip

import android.view.accessibility.AccessibilityNodeInfo as Native
import li.gkd.selector.FastQuery
import li.gkd.selector.property.CompareOperator

private var checks=0
private fun verify(b:Boolean){checks++;check(b){"Android tree assertion $checks"}}
private fun text(value:String)=FastQuery.Text(value, CompareOperator.Equal)
private fun query(tree:AndroidSkipTree,root:SkipNode,q:FastQuery)=tree.adapter.getFastQueryDescendants(root,listOf(q)).toList()
fun main(args:Array<String>){
 val baseline=args.firstOrNull()=="baseline"
 val reader=SkipReadExecutor()
 try {
  val root=Native.Data(0,null,null);val children=(1..40).map{Native.Data(it,"跳过", "test.app:id/skip$it")}
  children.forEach{root.add(it)}
  Native.resetCounts()
  AndroidSkipTree(Native(root),reader).use { tree ->
   val start=tree.root()
   repeat(40){verify(query(tree,start,text("跳过")).size==40)}
   println("METRIC repeated_query_rpc=${Native.textQueries}")
   verify(Native.textQueries==if(baseline)40 else 1)
   val matches=query(tree,start,text("跳过"))
   matches.forEachIndexed{i,n -> verify(tree.adapter.getAttr(n,"index")==i)}
   println("METRIC sibling_child_rpc=${Native.childrenRead} sibling_parent_rpc=${Native.parentsRead}")
   verify(Native.childrenRead==if(baseline)820 else 40)
   // Do not eagerly fetch unvisited siblings just to avoid parent reads.
   verify(Native.parentsRead==40)
   verify(matches.all{tree.adapter.getAttr(it,"depth")==1})
   // Native substring lookup is shared, but selector predicates still differ.
   val source=SkipSource(7,"""{id:7,name:'test',version:1,apps:[{id:'test.app',groups:[{key:1,name:'test',rules:[{matches:'[text="跳过" && index=4]'},{matches:'[text="跳过" && index=7]'}]}]}]}""")
   val group=source.subscription.apps[0].groups[0]
   for(i in 0..1){val n=SkipRule(source,"test.app",group,group.rules[i],i).query(start,adapter=tree.adapter);verify(n===matches[if(i==0)4 else 7])}
  }
  verify(Native.liveHandles==0)
  // A new inspection must observe changed positive AND formerly empty results.
  val dynamic=Native.Data(100,null,null);val child=Native.Data(101,"广告",null);dynamic.add(child)
  AndroidSkipTree(Native(dynamic),reader).use{t ->
   verify(query(t,t.root(),text("跳过")).isEmpty())
   verify(query(t,t.root(),text("广告")).size==1)
  }
  child.text="跳过"
  AndroidSkipTree(Native(dynamic),reader).use{t ->
   verify(query(t,t.root(),text("跳过")).size==1)
   verify(query(t,t.root(),text("广告")).isEmpty())
   verify(query(t,t.root(),FastQuery.Id("跳过")).isEmpty())
   Native.failQuery=true
   var failed=false;try{query(t,t.root(),text("临时失败"))}catch(_:IllegalStateException){failed=true};verify(failed)
   verify(query(t,t.root(),text("临时失败")).isEmpty())
  }
  verify(Native.liveHandles==0)
  if(!baseline) AndroidSkipTree(Native(dynamic),reader).use{t ->
   val start=t.root()
   repeat(300){verify(query(t,start,text("absent-$it")).isEmpty())}
   val before=Native.textQueries
   query(t,start,text("absent-0"));verify(Native.textQueries==before)
   query(t,start,text("absent-299"));verify(Native.textQueries==before+1)
  }
  verify(Native.liveHandles==0)
  // An event subtree need not read its ancestors unless a selector asks depth.
  val above=Native.Data(200,null,null);val event=Native.Data(201,null,null);val leaf=Native.Data(202,"跳过",null);above.add(event);event.add(leaf)
  Native.resetCounts()
  AndroidSkipTree(Native(event),reader,false).use{t ->
   val descendants=t.adapter.getDescendants(t.root()).toList();verify(descendants.size==1)
   println("METRIC unused_ancestor_rpc=${Native.parentsRead}")
   if(!baseline)verify(Native.parentsRead==0)
   verify(t.adapter.getAttr(descendants[0],"depth")==2)
   verify(Native.parentsRead>0)
   // Identical text on separate subtree roots must not share a native query.
   val outer=t.windowRoot{Native(above)}
   verify(query(t,outer,text("跳过")).size==1)
   verify(query(t,t.root(),text("跳过")).size==1)
  }
  verify(Native.liveHandles==0)
  // Cached hits must still stop when the inspection becomes outdated.
  var outdated=false
  AndroidSkipTree(Native(dynamic),reader,true){outdated}.use{t ->
   query(t,t.root(),text("跳过"));outdated=true
   var rejected=false;try{query(t,t.root(),text("跳过"))}catch(e:SkipReadFailure){rejected=e.reason=="event_changed"};verify(rejected)
  }
  verify(Native.liveHandles==0)
  // Empty fast-query values use normal traversal; null branches do not discard
  // other already queued branches and native failures are never cached.
  AndroidSkipTree(Native(dynamic),reader).use{t -> query(t,t.root(),text(""))}
  verify(Native.liveHandles==0)
 } finally {reader.close()}
 println("PASS: $checks native-tree cache/sibling/depth/invalidation/ownership checks (${if(baseline)"baseline" else "optimized"})")
}
