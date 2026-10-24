package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Local device A/B helper: changes only the existing recommendation switch; restores folds. */
public final class MarketRecommendationControl extends Instrumentation {
    volatile Activity activity; boolean target,expected;
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    View tagged(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tagged(((ViewGroup)v).getChildAt(i),tag);if(found!=null)return found;}return null;}
    TextView text(View v,String value){if(v instanceof TextView&&value.equals(((TextView)v).getText().toString()))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}return null;}
    @Override public void onCreate(Bundle args){super.onCreate(args);check(args!=null&&args.containsKey("enabled")&&args.containsKey("expected"),"Explicit states required");target=Boolean.parseBoolean(args.getString("enabled"));expected=Boolean.parseBoolean(args.getString("expected"));start();}
    @Override public void callActivityOnCreate(Activity created,Bundle state){super.callActivityOnCreate(created,state);if(created.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=created;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Activity absent");
        boolean connected=false;for(int i=0;i<60;i++){final boolean[] ready={false};runOnMainSync(()->ready[0]=text(activity.getWindow().getDecorView(),"框架服务已连接")!=null);if(ready[0]){connected=true;break;}SystemClock.sleep(100);}check(connected,"Framework not connected");
        final Throwable[] error={null};runOnMainSync(()->{SharedPreferences ui=null;Map<String,?> originalUi=null;try{
            SharedPreferences prefs=(SharedPreferences)field("prefs");ui=(SharedPreferences)field("uiPrefs");originalUi=new HashMap<>(ui.getAll());Map<String,?> before=new HashMap<>(prefs.getAll());
            check(prefs.getBoolean("market_recommend",false)==expected,"Unexpected original switch value");
            View root=activity.getWindow().getDecorView();Switch toggle=(Switch)tagged(root,"market_recommend");check(toggle!=null&&toggle.isEnabled(),"Existing switch unavailable");
            check(toggle.isChecked()==expected,"UI differs from stored value");if(toggle.isChecked()!=target)toggle.performClick();
            check(prefs.getBoolean("market_recommend",!target)==target&&toggle.isChecked()==target,"Switch not saved");
            Map<String,?> after=new HashMap<>(prefs.getAll());before.remove("market_recommend");after.remove("market_recommend");check(before.equals(after),"Another rule changed");
        }catch(Throwable e){error[0]=e;}finally{if(ui!=null&&originalUi!=null){SharedPreferences.Editor edit=ui.edit().clear();for(Map.Entry<String,?> item:originalUi.entrySet())edit.putBoolean(item.getKey(),(Boolean)item.getValue());check(edit.commit(),"Fold restore failed");}}});
        if(error[0]!=null)throw error[0];result.putString("stream","PASS: existing market_recommend="+target+"; other rules and fold settings preserved.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
