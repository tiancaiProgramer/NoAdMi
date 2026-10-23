package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Checks the real asynchronous loading UI in NoAdMi, without writing host preferences. */
public final class LoadingUiSmoke extends Instrumentation {
    volatile Activity activity;
    int checks;
    Object panel;
    View parent,section;
    boolean parentClosed,sectionClosed;
    static Object field(Object target,String name)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    View header(View view,String id){
        if (("fold:"+id).equals(view.getTag())) return view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View v=header(g.getChildAt(i),id);if(v!=null)return v;}}
        return null;
    }
    boolean closed(View header){ return header.getContentDescription().toString().endsWith("点击展开"); }
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(a.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=a;}
    @Override public void onStart(){
        Bundle result=new Bundle();
        android.content.SharedPreferences[] savedPrefs={null};
        final Map<String,?>[] snapshot=new Map[]{null};
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
                    savedPrefs[0]=(android.content.SharedPreferences)field(activity,"uiPrefs");
                    snapshot[0]=new HashMap<>(savedPrefs[0].getAll());
                    View root=activity.getWindow().getDecorView();
                    parent=header(root,"advertising");section=header(root,"system_official");
                    check(parent!=null&&section!=null,"Official group absent");
                    parentClosed=closed(parent);sectionClosed=closed(section);
                    if(parentClosed)parent.performClick();if(sectionClosed)section.performClick();
                    panel=field(activity,"nativeAds");
                    Method refresh=panel.getClass().getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(panel);
                    check(Boolean.TRUE.equals(field(panel,"busy")),"Reading state not immediate");
                    TextView text=(TextView)field(panel,"progress");ProgressBar spinner=(ProgressBar)field(panel,"spinner");
                    check(text.getText().toString().contains("正在读取广告开关，请稍候"),"Wait hint missing");
                    check(spinner.getVisibility()==View.VISIBLE&&spinner.isIndeterminate(),"Spinner absent");
                    check(text.getAccessibilityLiveRegion()==View.ACCESSIBILITY_LIVE_REGION_POLITE,"Accessible status missing");
                    check(!((View)field(panel,"refresh")).isEnabled()&&!((View)field(panel,"close")).isEnabled(),"Busy controls active");
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
                    check(((View)field(panel,"refresh")).isEnabled(),"Refresh did not recover");
                    Switch master=(Switch)field(panel,"close");check(master.isEnabled()!=master.isChecked(),"Completed master state incorrect");
                    check(((TextView)field(panel,"progress")).getText().toString().startsWith("已读取 "),"Completion status absent");
                    if(sectionClosed)section.performClick();if(parentClosed)parent.performClick();
                }catch(Throwable t){error[0]=t;}
            });
            if(error[0]!=null)throw error[0];
            runOnMainSync(() -> {
                android.content.SharedPreferences.Editor restore=savedPrefs[0].edit().clear();
                for(Map.Entry<String,?> item:snapshot[0].entrySet())restore.putBoolean(item.getKey(),(Boolean)item.getValue());
                check(restore.commit(),"Fold restore did not reach disk");
            });
            result.putString("stream","PASS: "+checks+" real loading UI checks; immediate top hint/spinner, disabled buttons, actual reads completed, spinner hidden and controls recovered; fold states restored.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){
            if(savedPrefs[0]!=null&&snapshot[0]!=null)runOnMainSync(() -> {
                android.content.SharedPreferences.Editor restore=savedPrefs[0].edit().clear();
                for(Map.Entry<String,?> item:snapshot[0].entrySet())restore.putBoolean(item.getKey(),(Boolean)item.getValue());
                restore.commit();
            });
            result.putString("stream","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
