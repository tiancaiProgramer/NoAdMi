package io.github.miclean.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
import io.github.miclean.module.skip.*;

/** Disabled isolated subscription; checks real native recycling, edits and display controls. */
public final class AppManagementSmoke extends Instrumentation {
    private volatile Activity current;private SkipRepository repo;private int checks;private final long id=202610280127L;
    private long navigationMillis;private final String url="https://example.com/noadmi-ui-test.json5";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);current=activity;}
    private void check(boolean value,String error){checks++;if(!value)throw new AssertionError(error);}
    private void main(Runnable action){Throwable[] issue={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){issue[0]=e;}});if(issue[0]!=null)throw new AssertionError(issue[0]);}
    private View tag(View root,String value){if(value.equals(root.getTag()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=tag(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
    private TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
    private View root(){return current.getWindow().getDecorView();}
    private void click(String value){main(()->{TextView v=text(root(),value);check(v!=null,"Control absent: "+value);v.performClick();});waitForIdleSync();}
    private ListView list(){return (ListView)tag(root(),"managed_apps_list");}
    private void awaitCount(int count){for(int i=0;i<180;i++){boolean[] done={false};main(()->{ListView v=list();done[0]=v!=null&&v.getAdapter().getCount()==count;});if(done[0]){checks++;waitForIdleSync();SystemClock.sleep(50);return;}SystemClock.sleep(50);}int[] actual={-1};main(()->{if(list()!=null)actual[0]=list().getAdapter().getCount();});throw new AssertionError("List did not reach "+count+", actual="+actual[0]+", groups="+getTargetContext().getSharedPreferences("smart_skip_list",0).getInt("groups",-1));}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String label,boolean editable){if(editable?node.isEditable():label.equals(node.getText()==null?null:node.getText().toString()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;AccessibilityNodeInfo match=find(child,label,editable);if(match!=null)return match;child.recycle();}return null;}
    private void dialog(String label,String value){UiAutomation ua=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);AccessibilityNodeInfo node=null;for(int i=0;i<30&&node==null;i++){AccessibilityNodeInfo r=ua.getRootInActiveWindow();if(r!=null){node=find(r,label,value!=null);if(r!=node)r.recycle();}if(node==null)SystemClock.sleep(100);}check(node!=null,"Dialog control absent: "+label);Bundle args=new Bundle();if(value!=null)args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(node.performAction(value==null?AccessibilityNodeInfo.ACTION_CLICK:AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Dialog rejected: "+label);node.recycle();waitForIdleSync();SystemClock.sleep(200);}
    private void capture(String name){android.graphics.Bitmap bitmap=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(bitmap==null)return;try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();}}
    private void search(String query){main(()->((EditText)tag(root(),"managed_apps_search")).setText(query));waitForIdleSync();}
    private void awaitSaved(java.util.function.BooleanSupplier check){for(int i=0;i<100&&!check.getAsBoolean();i++)SystemClock.sleep(50);check(check.getAsBoolean(),"Saved state differs");}
    private void back(){main(()->current.onBackPressed());waitForIdleSync();}
    @Override public void onStart(){
        Bundle result=new Bundle();SharedPreferences filters=getTargetContext().getSharedPreferences("smart_skip_apps",0),oldFilters=getTargetContext().getSharedPreferences("smart_skip_list",0);
        Map<String,?> savedFilters=new HashMap<>(filters.getAll()),savedOldFilters=new HashMap<>(oldFilters.getAll());Set<String> savedWhite=null;boolean hadWhite=false,prepared=false;long a=202610300201L,b=202610300202L;
        try{
            for(int i=0;i<80&&current==null;i++)SystemClock.sleep(100);check(current!=null,"Main Activity missing");repo=SkipRepository.get(getTargetContext());
            savedWhite=new HashSet<>(repo.whitelist());hadWhite=repo.getPreferences().contains(SkipRepository.GLOBAL_WHITELIST);
            check(repo.getSources().list().stream().noneMatch(s->s.getId()==a||s.getId()==b),"Test ID exists");prepared=true;
            String pkg="io.github.miclean.tests";try{getTargetContext().getPackageManager().getApplicationInfo("com.privoce.vocechatclient.legacyui",0);pkg="com.privoce.vocechatclient.legacyui";}catch(Exception ignored){}final String target=pkg;
            String zero="io.github.miclean.module";
            for(long source:new long[]{a,b})repo.getSources().add("{id:"+source+",name:'r2隔离订阅"+(source==a?"A":"B")+"',version:1,apps:[{id:'"+target+"',name:'r2应用',groups:[{key:1,name:'验证专属',rules:'[text=\"NoAdMi isolated target\"]'}]}],globalGroups:[{key:2,name:'验证全局',matchAnyApp:true,rules:[{matches:'[text=\"NoAdMi isolated target\"]'}]}]}","",false);
            repo.getSources().enableApp(a,target,false);repo.setWhitelisted(target,false);filters.edit().clear().commit();oldFilters.edit().clear().commit();
            main(()->{try{current.startActivity(new Intent(current,Class.forName("io.github.miclean.module.SmartSkipActivity")).putExtra("source_list",true));}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();SystemClock.sleep(300);
            main(()->{check(tag(root(),"app_management_card")!=null,"Independent management card absent");check(tag(root(),"skip_source_card:"+a)!=null&&tag(root(),"skip_source_card:"+b)!=null,"Original subscription cards absent");tag(root(),"skip_source_card:"+a).performClick();});
            click("应用规则  ›");for(int i=0;i<100;i++){boolean[] done={false};main(()->{ListView v=(ListView)tag(root(),"app_rules_list");done[0]=v!=null&&v.getAdapter().getCount()==1;});if(done[0])break;SystemClock.sleep(100);}main(()->{ListView v=(ListView)tag(root(),"app_rules_list");check(v!=null&&v.getAdapter().getCount()==1,"Subscription list contains undeclared apps");((EditText)tag(root(),"app_rules_search")).setText(zero);});SystemClock.sleep(400);main(()->check(((ListView)tag(root(),"app_rules_list")).getAdapter().getCount()==0,"No-rule app leaked into subscription list"));back();
            main(()->{long started=SystemClock.elapsedRealtime();tag(root(),"app_management_card").performClick();navigationMillis=SystemClock.elapsedRealtime()-started;check(navigationMillis<750,"App navigation blocked main thread");});
            for(int i=0;i<150;i++){boolean[] ready={false};main(()->ready[0]=list()!=null&&list().getAdapter().getCount()>0);if(ready[0])break;SystemClock.sleep(100);}main(()->{check(list()!=null&&list().getAdapter().getCount()>0,"Installed app list empty");check(list().getChildCount()<30,"All rows eagerly inflated");});
            search("com.android.internal.systemui.navbar.threebutton");awaitCount(0);
            search(zero);awaitCount(1);check(text(root(),"管理此应用规则  ›")!=null,"No-specific-rule app not manageable");
            search(target);awaitCount(1);for(int i=0;i<100;i++){boolean[] visible={false};main(()->visible[0]=tag(root(),"managed_app_switch:"+target)!=null);if(visible[0])break;SystemClock.sleep(50);}main(()->{Switch row=(Switch)tag(root(),"managed_app_switch:"+target);check(row!=null&&row.isChecked(),"Global whitelist inherited a subscription's local exclusion");row.performClick();});awaitSaved(()->repo.isWhitelisted(target));
            check(Boolean.FALSE.equals(repo.getSources().find(a).getApps().get(target))&&repo.getSources().find(b).getApps().get(target)==null,"Global whitelist rewrote source-specific settings");
            click("筛选 ▾");dialog("只看白名单",null);dialog("确定",null);awaitCount(1);capture("app-management-r2-whitelist.png");
            click("管理此应用规则  ›");for(int i=0;i<100;i++){boolean[] done={false};main(()->done[0]=tag(root(),"managed_rule:"+b+":app:1")!=null);if(done[0])break;SystemClock.sleep(50);}
            main(()->{Switch white=(Switch)tag(root(),"global_whitelist:"+target);check(white!=null&&white.isChecked(),"Global whitelist detail not synchronized");check(tag(root(),"managed_rule:"+a+":global:2")!=null&&tag(root(),"managed_rule:"+b+":global:2")!=null,"Cross-subscription global projection missing");((Switch)tag(root(),"managed_rule:"+a+":global:2")).performClick();((Switch)tag(root(),"managed_rule:"+b+":app:1")).performClick();});
            awaitSaved(()->Boolean.FALSE.equals(repo.getSources().find(a).getGlobalApps().get(target+"/2"))&&Boolean.FALSE.equals(repo.getSources().find(b).getGroups().get(target+"/1")));
            check(repo.getSources().find(b).getGlobalApps().isEmpty(),"Per-app global edit affected another source");check(repo.getSources().find(a).getGroups().get("*/2")==null,"Per-app edit changed shared global group");capture("app-management-r2-rules.png");
            main(()->((Switch)tag(root(),"global_whitelist:"+target)).performClick());awaitSaved(()->!repo.isWhitelisted(target));back();awaitCount(0);
            click("只看白名单 ▾");dialog("只看白名单",null);dialog("确定",null);awaitCount(1);
            main(()->{Switch row=(Switch)tag(root(),"managed_app_switch:"+target);for(int i=0;i<12;i++)row.performClick();check(row.isChecked(),"Rapid global switches lost immediate intent");});awaitSaved(()->!repo.isWhitelisted(target));
            for(String order:new String[]{"最近使用","最近触发","按名称"}){main(()->{for(String label:new String[]{"按名称 ▾","最近使用 ▾","最近触发 ▾"}){TextView button=text(root(),label);if(button!=null){button.performClick();return;}}throw new AssertionError("Sort button absent");});dialog(order,null);}
            back();main(()->{tag(root(),"skip_source_card:"+b).performClick();text(tag(root(),"skip_source_content:"+b),"应用规则  ›").performClick();});for(int i=0;i<100;i++){boolean[] done={false};main(()->{View v=tag(root(),"app_rule_switch:"+target);done[0]=v!=null;});if(done[0])break;SystemClock.sleep(50);}click("规则组  ›");main(()->{TextView group=text(root(),"验证专属");check(group instanceof Switch&&!((Switch)group).isChecked(),"App edit did not synchronize subscription group");});
            result.putString("stream","PASS: "+checks+" independent app management checks; navigation main thread="+navigationMillis+"ms; target="+target+"; original declared-only subscriptions, system component filtering, zero-specific-rule installed app, cross-subscription groups, local/global whitelist independence, only-whitelist filtering, per-app global isolation, shared rule controls, 3 sorts, rapid toggles and recycled rows.\n");
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");}
        finally{if(repo!=null&&prepared){for(long source:new long[]{a,b})if(repo.getSources().list().stream().anyMatch(s->s.getId()==source))repo.getSources().remove(source);if(savedWhite!=null){Set<String> changed=new HashSet<>(repo.whitelist());changed.addAll(savedWhite);for(String pkg:changed)repo.setWhitelisted(pkg,savedWhite.contains(pkg));if(!hadWhite)repo.getPreferences().edit().remove(SkipRepository.GLOBAL_WHITELIST).commit();}}restore(filters,savedFilters);restore(oldFilters,savedOldFilters);}
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){Object v=e.getValue();if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);}edit.commit();}
}
