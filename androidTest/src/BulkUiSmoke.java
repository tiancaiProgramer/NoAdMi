package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;

/** Exercises module controls only; official states are simulated in a detached panel. */
public final class BulkUiSmoke extends Instrumentation {
    volatile Activity activity; int checks;
    void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    Object field(Object target, String name) throws Exception { Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    void invoke(Object target, String name) throws Exception { Method m=target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target); }
    View tagged(View v, String tag) { if(tag.equals(v.getTag()))return v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tagged(((ViewGroup)v).getChildAt(i),tag);if(found!=null)return found;}return null; }
    TextView text(View v, String value) { if(v instanceof TextView&&value.equals(((TextView)v).getText().toString()))return(TextView)v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null; }
    void switches(View v,List<Switch> result) { if(v instanceof Switch&&v.getTag() instanceof String&&!"official_close".equals(v.getTag()))result.add((Switch)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)switches(((ViewGroup)v).getChildAt(i),result); }
    void open(View root,String id) { View body=tagged(root,"body:"+id),header=tagged(root,"fold:"+id);check(body!=null&&header!=null,"Missing fold "+id);if(body.getVisibility()!=View.VISIBLE)header.performClick(); }
    void refresh() throws Exception { for(Object action:(List<?>)field(activity,"refreshers"))((Runnable)action).run(); }
    void unchanged(Map<String,?> before,Map<String,?> after,Set<String> eligible) { for(String key:before.keySet())if(!eligible.contains(key))check(Objects.equals(before.get(key),after.get(key)),"Excluded setting changed "+key); }
    void restore(SharedPreferences prefs,Map<String,?> values) {
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(Map.Entry<String,?> item:values.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else throw new AssertionError("Unsupported preference type "+key);}
        check(edit.commit(),"Restore commit failed"); check(values.equals(prefs.getAll()),"Snapshot not restored");
    }
    void officialPanel(Switch appMaster) throws Exception {
        ClassLoader loader=getTargetContext().getClassLoader();
        Class<?> type=loader.loadClass("io.github.miclean.module.NativeAdsPanel"),notice=loader.loadClass("io.github.miclean.module.NativeAdsPanel$Notice");
        Constructor<?> constructor=type.getDeclaredConstructor(Activity.class,LinearLayout.class,notice);constructor.setAccessible(true);
        LinearLayout detached=new LinearLayout(activity);detached.setOrientation(LinearLayout.VERTICAL);
        Object panel=constructor.newInstance(activity,detached,Proxy.newProxyInstance(loader,new Class<?>[]{notice},(proxy,method,args)->null));
        Switch close=(Switch)field(panel,"close");TextView state=(TextView)field(panel,"closeState");
        check(close.getMinHeight()==appMaster.getMinHeight(),"Official master has different height");
        check(close.getPaddingTop()==appMaster.getPaddingTop()&&close.getTextSize()==appMaster.getTextSize(),"Official master has different row style");
        for(int i=0;i<detached.getChildCount();i++)check(!(detached.getChildAt(i) instanceof Button) || detached.getChildAt(i) instanceof CompoundButton,"Large button in official settings");
        check(!close.isChecked()&&state.getText().toString().contains("未确认 11 项"),"Unread states falsely complete");
        Map<?,?> rows=(Map<?,?>)field(panel,"rows");check(rows.size()==11,"Official setting count changed");
        Map<String,JSONObject> states=new LinkedHashMap<>();
        for(Object key:rows.keySet())states.put((String)key,new JSONObject().put("id",key).put("ok",true).put("enabled",false).put("writable",true));
        Method apply=type.getDeclaredMethod("apply",Map.class);apply.setAccessible(true);apply.invoke(panel,states);invoke(panel,"controls");
        check(close.isChecked()&&!close.isEnabled(),"Completed close must be checked and disabled");
        check(state.getText().toString().equals("已关闭 11/11 项 · 已完成"),"Completed count unclear");
        String first=states.keySet().iterator().next();states.get(first).put("enabled",true);apply.invoke(panel,states);invoke(panel,"controls");
        check(!close.isChecked()&&close.isEnabled(),"One official setting on must allow closing");
        check(state.getText().toString().contains("已关闭 10/11 项")&&state.getText().toString().contains("仍开启 1 项"),"Official enabled count unclear");
        states.put(first,new JSONObject().put("id",first).put("ok",false).put("error","simulated unread state"));apply.invoke(panel,states);invoke(panel,"controls");
        check(!close.isChecked()&&state.getText().toString().contains("未确认 1 项"),"Failed read falsely complete");
        Field busy=type.getDeclaredField("busy");busy.setAccessible(true);busy.setBoolean(panel,true);invoke(panel,"controls");
        check(!close.isEnabled()&&!((View)field(panel,"refresh")).isEnabled(),"Busy official controls enabled");
        check(!close.isChecked(),"Busy operation falsely complete");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity created,Bundle state){super.callActivityOnCreate(created,state);if(created.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=created;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Activity missing");
        boolean connected=false;for(int i=0;i<60;i++){final boolean[] ready={false};runOnMainSync(()->ready[0]=text(activity.getWindow().getDecorView(),"框架服务已连接")!=null);if(ready[0]){connected=true;break;}SystemClock.sleep(100);}check(connected,"Framework not connected");
        final Throwable[] error={null};runOnMainSync(()->{SharedPreferences prefs=null,ui=null;Map<String,?> original=null,originalUi=null;try{
            prefs=(SharedPreferences)field(activity,"prefs");ui=(SharedPreferences)field(activity,"uiPrefs");original=new HashMap<>(prefs.getAll());originalUi=new HashMap<>(ui.getAll());
            check(ui.edit().putBoolean("advertising",true).putBoolean("program",true).putBoolean("system_official",false).remove("app_ads").commit(),"UI seed failed");invoke(activity,"render");
            View root=activity.getWindow().getDecorView();
            check(tagged(root,"fold:advertising")!=null&&tagged(root,"fold:program")!=null,"Two main groups missing");
            ViewGroup ads=(ViewGroup)tagged(root,"body:app_ads"),program=(ViewGroup)tagged(root,"body:program");
            check(ads!=null&&ads.getVisibility()==View.GONE,"App ads must start collapsed");
            Switch master=(Switch)text(root,"广告隐藏总开关");check(master!=null&&!master.isShown(),"Collapsed ads leaks master");
            View official=tagged(root,"fold:system_official"),adsHeader=tagged(root,"fold:app_ads");
            check(official.getParent()==adsHeader.getParent()&&((ViewGroup)official.getParent()).indexOfChild(official)<((ViewGroup)adsHeader.getParent()).indexOfChild(adsHeader),"Official settings must precede App ads");
            open(root,"app_ads");check(master.isShown()&&master.isEnabled(),"Advertising master unavailable");
            List<Switch> advertising=new ArrayList<>(),functional=new ArrayList<>();switches(ads,advertising);switches(program,functional);
            check(advertising.size()==19&&functional.size()==29,"19/29 classification count changed");
            Set<String> eligible=new HashSet<>();for(Switch row:advertising){check(eligible.add((String)row.getTag()),"Duplicate advertising key");check(!row.getText().toString().contains("·"),"Repeated app prefix");}
            check(eligible.contains("system_network_traffic_recharge")&&eligible.contains("system_network_phone_recharge"),"Recharge entries not classified as ads");
            for(Switch row:functional)check(!eligible.contains(row.getTag()),"Functional item included in advertising");
            int headings=0;for(int i=0;i<ads.getChildCount();i++)if(String.valueOf(ads.getChildAt(i).getTag()).startsWith("ad_heading:"))headings++;check(headings==6,"App advertising headings missing");
            open(root,"app_home");open(root,"app_health");
            for(String key:new String[]{"home_play_mainland","health_play_mainland","health_sport_group"}){Switch row=(Switch)tagged(root,key);String parent=key.startsWith("home")?"body:app_home":"body:app_health";check(row!=null&&row.getParent()==tagged(root,parent),"Single option has extra category "+key);check(row.getMinHeight()==master.getMinHeight(),"Direct and ad row heights differ");check(row.getPaddingLeft()==Math.round(22*activity.getResources().getDisplayMetrics().density),"Direct row text not aligned");}
            for(String id:new String[]{"home_tabs","home_mine","health_mine"}){View header=tagged(root,"fold:"+id);check(header!=null&&header.getMinimumHeight()==master.getMinHeight(),"Fold and direct row heights differ");}
            Map<String,?> before=new HashMap<>(prefs.getAll());SharedPreferences.Editor seed=prefs.edit();int available=0;for(Switch row:advertising)if(row.isEnabled()){seed.putBoolean((String)row.getTag(),false);available++;}check(available>1,"Insufficient installed test controls");check(seed.commit(),"Seed failed");refresh();
            TextView summary=(TextView)tagged(root,"App 广告隐藏状态");check(summary!=null,"Advertising summary missing");
            check(!master.isChecked()&&summary.getText().toString().startsWith("全部关闭 · 已启用广告隐藏 0/"+available+" 项"),"All-off count unclear");
            master.performClick();check(master.isChecked(),"All-on master state missing");
            for(Switch row:advertising)if(row.isEnabled())check(row.isChecked()&&prefs.getBoolean((String)row.getTag(),false),"Master did not enable "+row.getTag());
            unchanged(before,prefs.getAll(),eligible);
            master.performClick();check(!master.isChecked(),"Master did not disable");
            for(Switch row:advertising)if(row.isEnabled())check(!row.isChecked(),"Master did not disable "+row.getTag());
            for(Switch row:advertising)if(row.isEnabled()){
                row.performClick();check(!master.isChecked(),"Partial shown as all-on "+row.getTag());
                check(summary.getText().toString().startsWith("部分开启 · 已启用广告隐藏 1/"+available+" 项"),"Partial count unclear "+row.getTag());
                if(Build.VERSION.SDK_INT>=30)check(master.getStateDescription().toString().equals(summary.getText().toString()),"Accessible state differs");
                master.performClick();check(master.isChecked(),"Partial did not fill remaining ads");
                for(Switch item:advertising)if(item.isEnabled())check(item.isChecked(),"Partial fill missing "+item.getTag());
                master.performClick();
            }
            unchanged(before,prefs.getAll(),eligible);
            officialPanel(master);
            // Collapsing either ancestor hides all descendants; expansion keeps nested state.
            tagged(root,"fold:advertising").performClick();check(!master.isShown(),"Parent fold leaks advertising controls");tagged(root,"fold:advertising").performClick();check(master.isShown(),"Nested fold state lost");
            TextView about=text(root,"▸  关于");if(about==null)about=text(root,"▾  关于");check(about!=null,"About missing");ViewGroup card=(ViewGroup)about.getParent(),list=(ViewGroup)card.getParent();check(list.indexOfChild(card)==list.getChildCount()-1,"About not last");
        }catch(Throwable failure){error[0]=failure;}finally{try{
            if(prefs!=null&&original!=null){restore(prefs,original);Class<?> settings=getTargetContext().getClassLoader().loadClass("io.github.miclean.module.Settings");Method backup=settings.getDeclaredMethod("backup",Context.class);backup.setAccessible(true);backup.invoke(null,getTargetContext());}
            if(ui!=null&&originalUi!=null)restore(ui,originalUi);invoke(activity,"render");
        }catch(Throwable failure){if(error[0]!=null)error[0].addSuppressed(failure);else error[0]=failure;}}});
        if(error[0]!=null)throw error[0];result.putString("stream","PASS: "+checks+" classification, 19 ad / 29 program controls, recharge inclusion, all-on/off/partial, isolation, aligned rows, folds and simulated official-state checks; original preferences restored; no official writes.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
