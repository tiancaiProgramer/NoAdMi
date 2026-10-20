package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class UiSmoke extends Instrumentation {
    int checks;
    volatile Activity activity;
    int displayId;
    volatile String updateUrl;
    final List<String> labels = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); displayId=Integer.parseInt(args.getString("display_id", "0")); start(); }
    private void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    private void texts(View view, List<String> out) {
        if (view instanceof TextView) out.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i=0;i<g.getChildCount();i++) texts(g.getChildAt(i),out);
        }
    }
    private TextView find(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().contains(text)) return (TextView)view;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)view;
            for(int i=0;i<g.getChildCount();i++) { TextView result=find(g.getChildAt(i),text); if(result!=null)return result; }
        }
        return null;
    }
    @Override public void callActivityOnCreate(Activity created, Bundle state) {
        super.callActivityOnCreate(created, state);
        if (created.getClass().getName().equals("io.github.miclean.module.MainActivity")) activity=created;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent launch=new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options=ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            getTargetContext().startActivity(launch, options.toBundle());
            for(int i=0;i<40 && activity==null;i++)SystemClock.sleep(150);
            check(activity!=null,"Activity was not created");
            // Poll the actual Binder-connected status; no arbitrary UI navigation.
            boolean connected=false;
            for(int i=0;i<40;i++) {
                final boolean[] found={false};
                runOnMainSync(() -> found[0]=find(activity.getWindow().getDecorView(),"框架服务已连接")!=null);
                if(found[0]) { connected=true;break; }
                SystemClock.sleep(150);
            }
            check(connected,"Framework service did not connect");
            final Throwable[] issue={null};
            ActivityMonitor monitor=new ActivityMonitor() {
                @Override public ActivityResult onStartActivity(Intent intent) {
                    if(Intent.ACTION_VIEW.equals(intent.getAction())) {
                        updateUrl=intent.getDataString();
                        return new ActivityResult(Activity.RESULT_CANCELED,null);
                    }
                    return null;
                }
            };
            addMonitor(monitor);
            runOnMainSync(() -> {
                try {
                    View root=activity.getWindow().getDecorView();
                    TextView update=find(root,"检查更新");
                    check(update!=null && update.isClickable(),"Update button absent");
                    update.performClick();
                    check("https://github.com/tiancaiProgramer/NoAdMi/releases".equals(updateUrl),"Wrong repository URL");
                    TextView header=find(root,"测试版本说明");
                    check(header!=null,"Tested version header absent");
                    String prior=header.getText().toString();
                    if(prior.startsWith("▸"))header.performClick();
                    texts(root,labels);
                    int homePlay=-1,healthPlay=-1,homeCn=-1,healthCn=-1;
                    for(int i=0;i<labels.size();i++) {
                        String s=labels.get(i);
                        if(s.startsWith("米家（Play"))homePlay=i;
                        if(s.startsWith("运动健康（Play"))healthPlay=i;
                        if(s.startsWith("米家（国内"))homeCn=i;
                        if(s.startsWith("运动健康（国内"))healthCn=i;
                    }
                    check(homeCn>=0 && healthCn>homeCn && homePlay>healthCn && healthPlay>homePlay,"Newest tested records are not first");
                    if(prior.startsWith("▸"))header.performClick();
                    check(header.getText().toString().equals(prior),"Fold state did not restore");
                    check(find(root,"显示桌面图标")!=null,"Launcher option missing");
                } catch(Throwable t) { issue[0]=t; }
            });
            removeMonitor(monitor);
            if(issue[0]!=null)throw issue[0];
            result.putString("stream","PASS: "+checks+" device UI checks; Binder connected; update button opens expected releases URL; 0.8.0 mainland verification records precede 0.7.0 Play records; fold state restored.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","FAIL: "+error.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
