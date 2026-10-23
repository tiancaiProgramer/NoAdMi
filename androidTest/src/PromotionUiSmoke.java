package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Current advertising navigation and distinct promotion controls; never changes rule values. */
public final class PromotionUiSmoke extends Instrumentation {
    volatile Activity activity; int checks;
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    View tag(View v,String key){if(key.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tag(((ViewGroup)v).getChildAt(i),key);if(found!=null)return found;}return null;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity created,Bundle state){super.callActivityOnCreate(created,state);if(created.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=created;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Activity absent");waitForIdleSync();
        final Throwable[] issue={null};runOnMainSync(()->{SharedPreferences ui=null;Map<String,?> original=null;try{
            Field field=activity.getClass().getDeclaredField("uiPrefs");field.setAccessible(true);ui=(SharedPreferences)field.get(activity);original=new HashMap<>(ui.getAll());View root=activity.getWindow().getDecorView();
            for(String id:new String[]{"advertising","app_ads"}){View header=tag(root,"fold:"+id);check(header!=null,"Missing fold "+id);if(header.getContentDescription().toString().endsWith("点击展开"))header.performClick();}
            View parent=tag(root,"body:app_ads");check(parent!=null&&parent.isShown(),"App ads hidden");
            for(String key:new String[]{"browser_splash","browser_feed_ads","market_splash","market_recommend"}){View v=tag(root,key);check(v instanceof Switch&&v.getParent()==parent,"Distinct promotion control absent "+key);check(v.isShown(),"Promotion control hidden "+key);}
            check(!tag(root,"browser_splash").equals(tag(root,"market_splash")),"Splash switches merged");
        }catch(Throwable error){issue[0]=error;}finally{if(ui!=null&&original!=null){SharedPreferences.Editor edit=ui.edit().clear();for(Map.Entry<String,?> item:original.entrySet())edit.putBoolean(item.getKey(),(Boolean)item.getValue());check(edit.commit(),"Fold restore failed");try{Method render=activity.getClass().getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);}catch(Exception error){issue[0]=error;}}}});
        if(issue[0]!=null)throw issue[0];result.putString("stream","PASS: "+checks+" current promotion navigation and independent switch checks; folds restored, rule settings untouched.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
