package io.github.miclean.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.lang.reflect.*;

/** Regression for vertically displaced min-height rows and new application folds. */
public final class UiAlignmentSmoke extends Instrumentation {
    volatile Activity activity;int checks;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);activity=a;}
    void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    View tag(View v,String key){if(key.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tag(((ViewGroup)v).getChildAt(i),key);if(found!=null)return found;}return null;}
    TextView text(View v,String needle){if(v instanceof TextView&&((TextView)v).getText().toString().contains(needle))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),needle);if(found!=null)return found;}return null;}
    void rows(View v){if(v instanceof TextView){TextView t=(TextView)v;if(t.getMinHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density))check((t.getGravity()&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.CENTER_VERTICAL,"Uncentered row: "+t.getText());}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)rows(((ViewGroup)v).getChildAt(i));}
    void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    int switches(View v){int n=v instanceof Switch?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=switches(((ViewGroup)v).getChildAt(i));return n;}
    @Override public void onStart(){Bundle result=new Bundle();SharedPreferences ui=getTargetContext().getSharedPreferences("miclean_ui",0);Map<String,?> original=new HashMap<>(ui.getAll());String[] keys={"advertising","app_ads","about","tested_versions","open_source","ad_home","ad_health","ad_browser","ad_market","ad_network","ad_game"};
        try{for(int i=0;i<80&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Module not launched");main(()->{try{
            SharedPreferences.Editor edit=ui.edit().putBoolean("advertising",true).putBoolean("app_ads",true).putBoolean("about",true).putBoolean("tested_versions",false);
            for(String id:new String[]{"ad_home","ad_health","ad_browser","ad_market","ad_network","ad_game"})edit.putBoolean(id,false);edit.commit();Method render=activity.getClass().getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();SystemClock.sleep(350);
        main(()->{View root=activity.getWindow().getDecorView();rows(root);TextView tested=text(root,"测试版本说明");check(tested!=null&&tested.isShown(),"Tested header not visible");
            int available=tested.getHeight()-tested.getPaddingTop()-tested.getPaddingBottom()-tested.getLayout().getHeight();check(Math.abs((tested.getTotalPaddingTop()-tested.getPaddingTop())-available/2)<=2,"Tested text is not measured in the vertical center");
            String[] groups={"ad_home","ad_health","ad_browser","ad_market","ad_network","ad_game"};int[] counts={2,7,2,2,4,2};for(int i=0;i<groups.length;i++){View body=tag(root,"body:"+groups[i]),head=tag(root,"fold:"+groups[i]);check(body!=null&&head!=null,"Ad category missing");check(body.getVisibility()==View.GONE,"Ad category not collapsed");check(switches(body)==counts[i],"Ad category options changed");head.performClick();check(body.getVisibility()==View.VISIBLE,"Ad category did not expand");head.performClick();}
            View license=tag(root,"license_header");check(license!=null&&license.isShown(),"License not in overall About");check(license.getTop()>tested.getBottom(),"About fold headers have no spacing");check(text(root,"检查更新").getParent()==tested.getParent(),"About controls escaped their card");
        });result.putString("stream","PASS: "+checks+" measured row alignment, About ownership, six application folds and retained 19 ad controls.\n");
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");}
        finally{SharedPreferences.Editor edit=ui.edit();for(String key:keys){if(original.containsKey(key))edit.putBoolean(key,(Boolean)original.get(key));else edit.remove(key);}edit.commit();}
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
