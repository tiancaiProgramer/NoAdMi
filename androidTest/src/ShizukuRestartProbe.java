package io.github.miclean.tests;
import android.app.*;import android.os.*;import android.view.accessibility.*;
/** Development-only restart of the already authorized Shizuku service via its own UI. */
public final class ShizukuRestartProbe extends Instrumentation {
 private Bundle args;
 @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 @Override public void onStart(){Bundle out=new Bundle();try{ExternalSkipSmoke ui=new ExternalSkipSmoke();ui.automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);android.accessibilityservice.AccessibilityServiceInfo info=ui.automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.automation.setServiceInfo(info);SystemClock.sleep(700);
  String target=args==null?"":args.getString("click","");if(!target.isEmpty()){AccessibilityNodeInfo n=ui.find("moe.shizuku.privileged.api",node->node.getText()!=null&&target.contentEquals(node.getText()));if(n==null)throw new AssertionError("Shizuku control missing: "+target);ui.click(n);SystemClock.sleep(1500);}
  ui.find("moe.shizuku.privileged.api",node->{if(node.getText()!=null){Bundle p=new Bundle();p.putString("stream","Shizuku UI: "+node.getText()+"\n");sendStatus(0,p);}return false;});out.putString("stream","PASS: official Shizuku UI inspected.\n");
 }catch(Throwable e){out.putString("stream","FAIL: "+e+"\n");}finish(Activity.RESULT_OK,out);}
}
