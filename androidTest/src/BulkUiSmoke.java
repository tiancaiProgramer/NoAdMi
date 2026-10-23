package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Uses only NoAdMi's UI; restores every tested module preference after the test. */
public final class BulkUiSmoke extends Instrumentation {
    volatile Activity activity;int checks;
    void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    TextView find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView r=find(((ViewGroup)v).getChildAt(i),text);if(r!=null)return r;}return null;}
    TextView named(View v,String desc){if(v instanceof TextView&&desc.equals(v.getTag()))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView r=named(((ViewGroup)v).getChildAt(i),desc);if(r!=null)return r;}return null;}
    TextView descriptionStarting(View v,String prefix){if(v instanceof TextView&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(prefix))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView r=descriptionStarting(((ViewGroup)v).getChildAt(i),prefix);if(r!=null)return r;}return null;}
    Switch button(View v,String desc){if(v instanceof Switch&&desc.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return(Switch)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Switch r=button(((ViewGroup)v).getChildAt(i),desc);if(r!=null)return r;}return null;}
    void switches(View v,List<Switch> list){if(v instanceof Switch&&v.getTag() instanceof String)list.add((Switch)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)switches(((ViewGroup)v).getChildAt(i),list);}
    boolean listed(TextView scope,Switch child){String text=child.getText().toString();for(String line:scope.getText().toString().split("\n"))if(line.equals("• "+text)||line.endsWith(" · "+text))return true;return false;}
    boolean allowed(String key){return !key.contains("mainland")&&!key.startsWith("home_hide_")&&!key.startsWith("health_mine_hide_")&&!Arrays.asList("home_product_tab","health_sport_group","system_network_traffic_recharge","system_network_phone_recharge").contains(key);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(a.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=a;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Activity missing");
        for(int i=0;i<60;i++){final boolean[] ready={false};runOnMainSync(()->ready[0]=find(activity.getWindow().getDecorView(),"框架服务已连接")!=null);if(ready[0])break;SystemClock.sleep(100);}
        final Throwable[] error={null};runOnMainSync(()->{SharedPreferences prefs=null,uiPrefs=null;Map<String,?> original=null,originalUi=null;try{
            Field field=activity.getClass().getDeclaredField("prefs");field.setAccessible(true);prefs=(SharedPreferences)field.get(activity);original=new HashMap<>(prefs.getAll());
            Field uiField=activity.getClass().getDeclaredField("uiPrefs");uiField.setAccessible(true);uiPrefs=(SharedPreferences)uiField.get(activity);originalUi=new HashMap<>(uiPrefs.getAll());
            View root=activity.getWindow().getDecorView();List<Switch> all=new ArrayList<>();switches(root,all);
            int groupIndex=0;int[] expectedTotals={2,7,2,2,4};
            for(String title:new String[]{"米家","小米运动健康","小米浏览器","应用商店","系统应用广告"}){
                TextView header=find(root,"▸  "+title);if(header==null)header=find(root,"▾  "+title);check(header!=null,"Group missing "+title);boolean collapsed=header.getText().toString().startsWith("▸");if(collapsed)header.performClick();
                Switch action=button(root,title+"一键关闭广告");check(action!=null,"Bulk button missing "+title);check(action.isShown(),"Bulk button outside open group "+title);check(action.isEnabled(),"Installed group bulk disabled "+title);
                TextView summary=named(root,title+"广告隐藏状态"),scope=named(root,title+"联动项目");check(summary!=null&&scope!=null,"Bulk explanation missing "+title);
                List<Switch> members=new ArrayList<>();switches((View)action.getParent(),members);Set<String> eligible=new HashSet<>();
                SharedPreferences.Editor seed=prefs.edit();for(Switch toggle:members)if(toggle.isEnabled()&&allowed((String)toggle.getTag())){eligible.add((String)toggle.getTag());seed.putBoolean((String)toggle.getTag(),false);}check(eligible.size()>=2,"Missing bulk targets "+title);check(seed.commit(),"Seed save failed");
                check(eligible.size()==expectedTotals[groupIndex++],"Advertising scope changed "+title);
                check(scope.getText().toString().startsWith("联动项目（"+eligible.size()+" 项）："),"Wrong scope list total "+title);
                check(scope.getText().toString().split("\n• ",-1).length-1==eligible.size(),"Scope list has missing or duplicate rows "+title);
                for(Switch child:members)check(listed(scope,child)==eligible.contains(child.getTag()),"Scope list differs from controlled options "+child.getTag());
                if(title.equals("小米运动健康")){check(scope.getText().toString().contains("运动 · 隐藏活动推荐"),"Sport context missing");check(scope.getText().toString().contains("我的 · 隐藏活动横幅"),"Mine context missing");}
                Field rf=activity.getClass().getDeclaredField("refreshers");rf.setAccessible(true);for(Object refresh:(List<?>)rf.get(activity))((Runnable)refresh).run();
                for(Switch toggle:members)if(eligible.contains(toggle.getTag()))check(!toggle.isChecked(),"Seed UI not synchronized");
                check(!action.isChecked(),"Master must show all-off state");
                check(summary.getText().toString().equals("全部关闭 · 已启用广告隐藏 0/"+eligible.size()+" 项"),"All-off count unclear "+title);
                Map<String,?> before=new HashMap<>(prefs.getAll());action.performClick();check(action.isChecked(),"Master must show all-on state");Map<String,?> after=prefs.getAll();
                check(summary.getText().toString().equals("全部开启 · 已启用广告隐藏 "+eligible.size()+"/"+eligible.size()+" 项"),"All-on count unclear "+title);
                if(title.equals("小米运动健康")){
                    Switch compatibility=null;for(Switch child:members)if("health_play_mainland".equals(child.getTag()))compatibility=child;
                    check(compatibility!=null,"Health compatibility row missing");
                    if(!compatibility.isEnabled()){
                        TextView general=descriptionStarting((View)action.getParent(),"通用与横幅，");check(general!=null,"Health general section missing");
                        check(general.getContentDescription().toString().contains("已启用设置 3/3 项（另有 1 项不可用）"),"Unavailable Play compatibility counted in denominator");
                    }
                }
                for(Switch toggle:members)if(eligible.contains(toggle.getTag())){check(Boolean.TRUE.equals(after.get(toggle.getTag())),"Bulk setting not enabled "+toggle.getTag());check(toggle.isChecked(),"UI not refreshed "+toggle.getTag());}
                for(String key:before.keySet())if(!eligible.contains(key))check(Objects.equals(before.get(key),after.get(key)),"Changed another group or experiment "+key);
                action.performClick();check(!action.isChecked(),"Master did not switch off");
                for(Switch toggle:members)if(eligible.contains(toggle.getTag()))check(!toggle.isChecked()&&!prefs.getBoolean((String)toggle.getTag(),true),"Master off did not disable ad rule "+toggle.getTag());
                for(String key:before.keySet())if(!eligible.contains(key))check(Objects.equals(before.get(key),prefs.getAll().get(key)),"Off changed excluded setting "+key);
                for(Switch toggle:members)if(eligible.contains(toggle.getTag())){toggle.performClick();break;}
                check(summary.getText().toString().equals("部分开启 · 已启用广告隐藏 1/"+eligible.size()+" 项"),"Partial count unclear "+title);
                if(Build.VERSION.SDK_INT>=30)check(action.getStateDescription().toString().equals(summary.getText().toString()),"Accessible bulk state differs "+title);
                check(!action.isChecked(),"Partial state displayed as all-on");action.performClick();check(action.isChecked(),"Partial state did not enable remaining ad rules");
                if(collapsed)header.performClick();
            }
            check(button(root,"关于一键关闭广告")==null,"Bulk button added to About");
        }catch(Throwable e){error[0]=e;}finally{try{
            if(prefs!=null&&original!=null){SharedPreferences.Editor restore=prefs.edit();for(String key:prefs.getAll().keySet())if(!original.containsKey(key))restore.remove(key);for(Map.Entry<String,?> item:original.entrySet())if(item.getValue() instanceof Boolean)restore.putBoolean(item.getKey(),(Boolean)item.getValue());check(restore.commit(),"Restore failed");check(original.equals(prefs.getAll()),"Preference snapshot not restored");Class<?> settings=Class.forName("io.github.miclean.module.Settings",true,getTargetContext().getClassLoader());Method backup=settings.getDeclaredMethod("backup",Context.class);backup.setAccessible(true);backup.invoke(null,getTargetContext());}
            if(uiPrefs!=null&&originalUi!=null){SharedPreferences.Editor restoreUi=uiPrefs.edit().clear();for(Map.Entry<String,?> item:originalUi.entrySet())if(item.getValue() instanceof Boolean)restoreUi.putBoolean(item.getKey(),(Boolean)item.getValue());check(restoreUi.commit(),"Fold restore failed");check(originalUi.equals(uiPrefs.getAll()),"Fold snapshot not restored");}
            Method render=activity.getClass().getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Throwable e){error[0]=e;}}});
        if(error[0]!=null)throw error[0];result.putString("stream","PASS: "+checks+" bulk UI checks across five groups, group isolation, immediate switch synchronization, primary navigation and experimental settings retained, all-on/all-off/partial state synchronization; all preferences restored.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
