package io.github.miclean.tests;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
/** Observes real fixture transitions; this probe never taps the target itself. */
public final class AppManagementEngineProbe extends Instrumentation {
    private volatile Activity current;private boolean expectClick;
    @Override public void onCreate(Bundle args){super.onCreate(args);expectClick=Boolean.parseBoolean(args.getString("click","false"));start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);current=a;}
    private boolean has(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(has(((ViewGroup)v).getChildAt(i),s))return true;return false;}
    @Override public void onStart(){Bundle out=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        for(int i=0;i<100&&current==null;i++)SystemClock.sleep(50);if(current==null)throw new AssertionError("Fixture did not open");
        boolean[] clicked={false},target={false};long deadline=SystemClock.elapsedRealtime()+(expectClick?5000:2000);
        do{runOnMainSync(()->{clicked[0]=has(current.getWindow().getDecorView(),"测试广告已关闭");target[0]=has(current.getWindow().getDecorView(),"重复场景目标");});if(clicked[0])break;SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<deadline);
        if(clicked[0]!=expectClick||(!expectClick&&!target[0]))throw new AssertionError("Expected click="+expectClick+", actual="+clicked[0]+", target="+target[0]);
        out.putString("stream","PASS: actual fixture "+(clicked[0]?"target clicked and closed":"target remained untouched for 2 seconds")+"\n");
    }catch(Throwable e){out.putString("stream","FAIL: "+e+"\n");}finish(out.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
