package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** Checks the real asynchronous loading UI in NoAdMi, without writing host preferences. */
public final class LoadingUiSmoke extends Instrumentation {
    volatile Activity activity;
    int checks;
    Object panel;
    TextView parent,section;
    boolean parentClosed,sectionClosed;
    static Object field(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    TextView header(View view,String title){
        if(view instanceof TextView){String text=((TextView)view).getText().toString();
            if((text.startsWith("▸")||text.startsWith("▾"))&&text.contains(title))return (TextView)view;}
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){TextView v=header(g.getChildAt(i),title);if(v!=null)return v;}}
        return null;
    }
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(a.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=a;}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<50&&activity==null;i++)SystemClock.sleep(100);
            check(activity!=null,"Activity absent");
            Class<?> settings=getTargetContext().getClassLoader().loadClass("io.github.miclean.module.Settings");
            Method ready=settings.getDeclaredMethod("ready");ready.setAccessible(true);
            for(int i=0;i<50&&!Boolean.TRUE.equals(ready.invoke(null));i++)SystemClock.sleep(100);
            // Let a completed Binder bind render settle before choosing the current panel.
            waitForIdleSync();
            final Throwable[] error={null};
            runOnMainSync(()->{
                try{
                    View root=activity.getWindow().getDecorView();
                    parent=header(root,"系统应用广告");section=header(root,"官方广告开关");
                    check(parent!=null&&section!=null,"Official group absent");
                    parentClosed=parent.getText().toString().startsWith("▸");sectionClosed=section.getText().toString().startsWith("▸");
                    if(parentClosed)parent.performClick();if(sectionClosed)section.performClick();
                    panel=field(activity,"nativeAds");
                    Method refresh=panel.getClass().getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(panel);
                    check(Boolean.TRUE.equals(field(panel,"busy")),"Reading state not immediate");
                    TextView text=(TextView)field(panel,"progress");ProgressBar spinner=(ProgressBar)field(panel,"spinner");
                    check(text.getText().toString().contains("正在读取广告开关，请稍候"),"Wait hint missing");
                    check(spinner.getVisibility()==View.VISIBLE&&spinner.isIndeterminate(),"Spinner absent");
                    check(text.getAccessibilityLiveRegion()==View.ACCESSIBILITY_LIVE_REGION_POLITE,"Accessible status missing");
                    check(!((Button)field(panel,"refresh")).isEnabled()&&!((Button)field(panel,"close")).isEnabled(),"Busy controls active");
                    LinearLayout group=(LinearLayout)field(panel,"root");
                    check(group.indexOfChild((View)text.getParent())<group.indexOfChild((View)field(panel,"refresh")),"Loading hint below buttons");
                }catch(Throwable t){error[0]=t;}
            });
            if(error[0]!=null)throw error[0];
            for(int i=0;i<600&&Boolean.TRUE.equals(field(panel,"busy"));i++)SystemClock.sleep(100);
            runOnMainSync(()->{
                try{
                    check(!Boolean.TRUE.equals(field(panel,"busy")),"Loading did not complete");
                    check(((View)field(panel,"spinner")).getVisibility()==View.GONE,"Spinner left visible");
                    check(((Button)field(panel,"refresh")).isEnabled()&&((Button)field(panel,"close")).isEnabled(),"Controls did not recover");
                    check(((TextView)field(panel,"progress")).getText().toString().startsWith("已读取 "),"Completion status absent");
                    if(sectionClosed)section.performClick();if(parentClosed)parent.performClick();
                }catch(Throwable t){error[0]=t;}
            });
            if(error[0]!=null)throw error[0];
            result.putString("stream","PASS: "+checks+" real loading UI checks; immediate top hint/spinner, disabled buttons, actual reads completed, spinner hidden and controls recovered; fold states restored.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
