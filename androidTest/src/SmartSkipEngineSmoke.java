package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import io.github.miclean.module.skip.*;

/** Requires the user-enabled real service; only the isolated fixture is eligible for actions. */
public final class SmartSkipEngineSmoke extends Instrumentation {
    volatile Activity activity;
    int checks;
    static final long ID=-20261027002L;
    static final String FIXTURE="io.github.miclean.skipfixture";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);activity=a;}
    void report(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    boolean contains(AccessibilityNodeInfo node,String value){if(node==null)return false;if(value.contentEquals(node.getText()==null?"":node.getText()))return true;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);boolean found=contains(child,value);if(child!=null)child.recycle();if(found)return true;}return false;}
    boolean shown(String text){
        UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> displays=automation.getWindowsOnAllDisplays();
        for(int d=0;d<displays.size();d++)for(AccessibilityWindowInfo window:displays.valueAt(d)){AccessibilityNodeInfo root=window.getRoot();boolean found=root!=null&&FIXTURE.contentEquals(root.getPackageName())&&contains(root,text);if(root!=null)root.recycle();if(found)return true;}return false;
    }
    android.widget.Switch serviceSwitch(){return (android.widget.Switch)find(activity.getWindow().getDecorView(),"无障碍服务");}
    android.view.View find(android.view.View v,String title){if(v instanceof android.widget.TextView&&title.equals(((android.widget.TextView)v).getText().toString()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){android.view.View child=find(((android.view.ViewGroup)v).getChildAt(i),title);if(child!=null)return child;}return null;}
    void service(boolean enabled,SkipRepository repository){runOnMainSync(()->serviceSwitch().setChecked(enabled));for(int i=0;i<400&&repository.serviceEnabled()!=enabled;i++)SystemClock.sleep(100);check(repository.serviceEnabled()==enabled,"Direct accessibility switch failed; desired="+enabled+", actual="+repository.serviceEnabled());SystemClock.sleep(350);}
    void launch(){runOnMainSync(()->{ActivityOptions options=ActivityOptions.makeBasic();options.setLaunchDisplayId(0);activity.startActivity(new Intent().setClassName(FIXTURE,FIXTURE+".FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK),options.toBundle());});SystemClock.sleep(500);}
    @Override public void onStart(){
        Bundle result=new Bundle();SkipRepository repository=null;boolean hadMaster=false,master=false;boolean added=false,initialService=false;String initialMethod=null;
        try{
            for(int i=0;i<80&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Module launch required");repository=SkipRepository.get(getTargetContext());
            hadMaster=repository.getPreferences().contains("enabled");master=repository.getEnabled();
            initialService=repository.serviceEnabled();initialMethod=repository.getPreferences().getString("access_method",null);
            repository.getPreferences().edit().putString("access_method","root").commit();
            report("Direct switch: reconnect");service(false,repository);service(true,repository);report("Direct switch readback: enabled");
            for(int i=0;i<150&&!SkipAccessibilityService.connected;i++)SystemClock.sleep(100);check(SkipAccessibilityService.connected,"Direct switch did not connect real service");
            report("Real accessibility service connected");check(repository.getSources().list().stream().noneMatch(SkipSource::getEnabled),"Disable existing subscriptions before isolated validation");
            repository.setEnabled(false);
            repository.getSources().add("{id:"+ID+",name:'本机执行验证',version:1,apps:[{id:'"+FIXTURE+"',groups:[{key:1,name:'仅测试夹具',actionMaximum:1,rules:{matches:'[text=\"测试跳过\"]',action:'clickNode'}}]}]}","",true);added=true;
            repository.getSources().enableGroup(ID,FIXTURE,1,false);repository.setEnabled(true);launch();
            for(int i=0;i<40&&!shown("测试广告等待关闭");i++)SystemClock.sleep(100);check(shown("测试广告等待关闭"),"Fixture not visible on active display");SystemClock.sleep(800);check(shown("测试广告等待关闭"),"Disabled group executed");
            repository.setEnabled(false);repository.getSources().enableGroup(ID,FIXTURE,1,true);SystemClock.sleep(800);check(shown("测试广告等待关闭"),"Disabled master executed");
            report("Fixture negative checks passed; enabling master");repository.setEnabled(true);for(int i=0;i<80&&!shown("测试广告已关闭");i++)SystemClock.sleep(100);check(shown("测试广告已关闭"),"Real service failed to click the fixture");
            check(repository.logs().stream().anyMatch(log->FIXTURE.equals(log.getApp())&&"仅测试夹具".equals(log.getGroup())&&log.getResult().contains("已执行")),"Successful action not logged");
            result.putString("stream","PASS: "+checks+" real accessibility checks including direct Root on/off and restoration; disabled group and master block actions; enabled rule clicked only the fixture and wrote a local execution record.\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n");}
        finally{
            try{if(repository!=null){repository.setEnabled(false);if(added)repository.getSources().remove(ID);SharedPreferences.Editor edit=repository.getPreferences().edit();if(hadMaster)edit.putBoolean("enabled",master);else edit.remove("enabled");edit.commit();service(initialService,repository);android.content.SharedPreferences.Editor method=repository.getPreferences().edit();if(initialMethod==null)method.remove("access_method");else method.putString("access_method",initialMethod);method.commit();}}catch(Throwable cleanup){result.putString("stream",result.getString("stream","")+"RESTORE FAILED: "+cleanup+"\n");}
        }
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
