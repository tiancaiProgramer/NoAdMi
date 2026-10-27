package io.github.miclean.tests;
import android.app.*;
import android.os.*;
import android.view.accessibility.*;

/** Uses NoAdMi's official permission UI; never presses the external grant dialog. */
public final class ShizukuAccessSmoke extends Instrumentation {
    Bundle args;
    @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    void select(ExternalSkipSmoke ui,String name){AccessibilityNodeInfo row=ui.find(ExternalSkipSmoke.MODULE,n->n.getText()!=null&&n.getText().toString().equals("无障碍方式"));ui.click(row);ui.click(name);}
    @Override public void onStart(){Bundle result=new Bundle();ExternalSkipSmoke ui=new ExternalSkipSmoke();boolean changed=false,originalService=false,originalShizuku=false;
        try{
            ui.automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);android.accessibilityservice.AccessibilityServiceInfo info=ui.automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.automation.setServiceInfo(info);
            ui.mainControls();ui.settings();originalService=ui.checked("无障碍服务");originalShizuku=ui.has(ExternalSkipSmoke.MODULE,"Shizuku  ›");
            select(ui,"Shizuku");changed=true;ui.toggle("无障碍服务",false);ui.toggle("无障碍服务",true);
            for(int i=0;i<150&&!ui.has(ExternalSkipSmoke.MODULE,"服务已连接");i++)SystemClock.sleep(100);ui.check(ui.has(ExternalSkipSmoke.MODULE,"服务已连接"),"Shizuku enabled config but service did not connect");
            result.putString("stream","PASS: inline Shizuku method picker, existing official grant, UserService IPC, direct off/on without navigation and real service reconnection.\n");
        }catch(Throwable error){ui.diagnostic();result.putString("stream","FAIL: "+error+"\n");}
        finally{if(changed)try{ui.mainControls();ui.settings();ui.toggle("无障碍服务",originalService);select(ui,originalShizuku?"Shizuku":"Root");ui.collapse("设置");ui.collapse("订阅管理");ui.collapse("执行记录");}catch(Throwable cleanup){result.putString("stream",result.getString("stream","")+"RESTORE FAILED: "+cleanup+"\n");}}
        finish(Activity.RESULT_OK,result);
    }
}
