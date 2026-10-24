package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;

/** Captures the actual module view, then commits the caller's original fold snapshot. */
public final class LayoutCapture extends Instrumentation {
    volatile Activity activity; Bundle arguments; SharedPreferences prefs; Map<String,Boolean> original=new HashMap<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void callActivityOnCreate(Activity created,Bundle state){super.callActivityOnCreate(created,state);if(created.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=created;}
    Object field(Object target,String key)throws Exception{Field f=target.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(target);}
    View tagged(View v,String key){if(key.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=tagged(((ViewGroup)v).getChildAt(i),key);if(found!=null)return found;}return null;}
    ScrollView scroll(View v){if(v instanceof ScrollView)return(ScrollView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ScrollView found=scroll(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    void render()throws Exception{Method m=activity.getClass().getDeclaredMethod("render");m.setAccessible(true);m.invoke(activity);}
    void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void capture(String mode,String anchor)throws Exception{
        final Throwable[] error={null};
        final boolean programView=mode.equals("program")||mode.equals("hierarchy");
        main(()->{try{
            SharedPreferences.Editor edit=prefs.edit().putBoolean("advertising",!programView).putBoolean("program",programView)
                .putBoolean("app_ads",mode.equals("ads")).putBoolean("system_official",mode.equals("official"))
                .putBoolean("app_home",true).putBoolean("app_health",true).putBoolean("home_tabs",mode.equals("hierarchy")).putBoolean("home_mine",false).putBoolean("health_mine",false);
            if(!edit.commit())throw new AssertionError("Capture fold save failed");render();
        }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);
        if(mode.equals("official")){
            Object panel=field(activity,"nativeAds");
            for(int i=0;i<600&&Boolean.TRUE.equals(field(panel,"busy"));i++)SystemClock.sleep(100);
            if(Boolean.TRUE.equals(field(panel,"busy")))throw new AssertionError("Official read timeout");waitForIdleSync();
        }
        if(anchor!=null)main(()->{
            View root=activity.getWindow().getDecorView();View row=tagged(root,anchor);ScrollView page=scroll(root);
            if(row==null||page==null)throw new AssertionError("Screenshot anchor missing");
            int[] location=new int[2],top=new int[2];row.getLocationOnScreen(location);page.getLocationOnScreen(top);
            page.scrollTo(0,Math.max(0,page.getScrollY()+location[1]-top[1]-page.getPaddingTop()-16));
        });
        if(mode.equals("hierarchy"))main(() -> {
            View root=activity.getWindow().getDecorView();
            View heading=tagged(root,"fold:app_home"),nested=tagged(root,"fold:home_tabs"),guide=tagged(root,"guide:app_home"),innerGuide=tagged(root,"guide:home_tabs");
            if(heading==null||nested==null||guide==null||innerGuide==null)throw new AssertionError("Hierarchy views absent");
            if(!guide.isShown()||!innerGuide.isShown()||guide.getWidth()<=0||guide.getHeight()<=0||innerGuide.getHeight()<=0)throw new AssertionError("Expanded guide has no measured area");
            int[] outer=new int[2],inner=new int[2];heading.getLocationOnScreen(outer);nested.getLocationOnScreen(inner);
            if(inner[0]<=outer[0])throw new AssertionError("Nested menu lacks visible indentation");
            if(heading.getBackground()==null||nested.getBackground()==null)throw new AssertionError("Nested title surfaces absent");
        });
        // Frame callbacks run before capture; this also avoids an in-progress launch animation.
        SystemClock.sleep(400);waitForIdleSync();
        View decor=activity.getWindow().getDecorView();
        Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
        java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);
        int[] resultCode={-1};
        runOnMainSync(() -> android.view.PixelCopy.request(activity.getWindow(),image,result -> {resultCode[0]=result;copied.countDown();},new Handler(Looper.getMainLooper())));
        if(!copied.await(8,java.util.concurrent.TimeUnit.SECONDS)||resultCode[0]!=android.view.PixelCopy.SUCCESS){image.recycle();throw new AssertionError("Module-window capture failed: "+resultCode[0]);}
        try(FileOutputStream file=new FileOutputStream(new File(activity.getFilesDir(),"layout-"+mode+".png"))){if(!image.compress(Bitmap.CompressFormat.PNG,100,file))throw new AssertionError("Screenshot write failed");}finally{image.recycle();}
    }
    @Override public void onStart(){Bundle result=new Bundle();Throwable failure=null;try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);if(activity==null)throw new AssertionError("Activity absent");
        Class<?> settings=getTargetContext().getClassLoader().loadClass("io.github.miclean.module.Settings");Method ready=settings.getDeclaredMethod("ready");ready.setAccessible(true);
        for(int i=0;i<60&&!Boolean.TRUE.equals(ready.invoke(null));i++)SystemClock.sleep(100);
        if(!Boolean.TRUE.equals(ready.invoke(null)))throw new AssertionError("Framework not connected");waitForIdleSync();
        prefs=(SharedPreferences)field(activity,"uiPrefs");
        JSONObject snapshot=new JSONObject(new String(android.util.Base64.decode(arguments.getString("ui_snapshot"),android.util.Base64.DEFAULT),"UTF-8"));
        Iterator<String> keys=snapshot.keys();while(keys.hasNext()){String key=keys.next();original.put(key,snapshot.getBoolean(key));}
        capture("overview",null);
        if(!"overview".equals(arguments.getString("capture_mode"))){capture("ads","fold:app_ads");capture("program","fold:program");capture("hierarchy","fold:program");capture("official","fold:system_official");}
    }catch(Throwable e){failure=e;}finally{if(prefs!=null&&!original.isEmpty()){
        final Throwable[] restoreError={null};main(()->{try{SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,Boolean> item:original.entrySet())edit.putBoolean(item.getKey(),item.getValue());if(!edit.commit()||!original.equals(prefs.getAll()))throw new AssertionError("Exact fold restoration failed");render();}catch(Throwable e){restoreError[0]=e;}});
        if(restoreError[0]!=null)failure=restoreError[0];
    }}
        result.putString("stream",failure==null?("overview".equals(arguments.getString("capture_mode"))?"PASS: actual module overview screenshot; exact original fold snapshot committed and verified.\n":"PASS: 5 actual layout screenshots and measured nesting geometry; exact original fold snapshot committed and verified.\n"):"FAIL: "+failure+"\n");finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
