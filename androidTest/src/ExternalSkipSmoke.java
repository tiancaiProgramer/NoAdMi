package io.github.miclean.tests;

import android.app.*;
import android.os.*;
import android.view.accessibility.*;
import android.graphics.Rect;
import java.io.*;
import java.util.*;

/** Runs in the isolated fixture process, so starting instrumentation never stops NoAdMi's service. */
public final class ExternalSkipSmoke extends Instrumentation {
    static final String MODULE="io.github.miclean.module",FIXTURE="io.github.miclean.skipfixture",SUB="NoAdMi 外部执行验证";
    UiAutomation automation;int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    void report(String message){Bundle result=new Bundle();result.putString("stream",message+"\n");sendStatus(0,result);}
    interface Match{boolean accept(AccessibilityNodeInfo node);}
    AccessibilityNodeInfo descend(AccessibilityNodeInfo node,Match match){if(match.accept(node))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;AccessibilityNodeInfo found=descend(child,match);if(found!=null)return found;child.recycle();}return null;}
    AccessibilityNodeInfo find(String pkg,Match match){android.util.SparseArray<List<AccessibilityWindowInfo>> windows=automation.getWindowsOnAllDisplays();for(int d=0;d<windows.size();d++)for(AccessibilityWindowInfo window:windows.valueAt(d)){if(windows.keyAt(d)!=0||(!window.isActive()&&!window.isFocused()))continue;AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;AccessibilityNodeInfo found=pkg.contentEquals(root.getPackageName())?descend(root,match):null;if(root!=found)root.recycle();if(found!=null)return found;}return null;}
    AccessibilityNodeInfo text(String pkg,String value){return find(pkg,node->value.contentEquals(node.getText()==null?"":node.getText())||("无障碍服务".equals(value)&&node.getText()!=null&&node.getText().toString().startsWith("无障碍服务\n")));}
    boolean has(String pkg,String value){AccessibilityNodeInfo node=find(pkg,n->(n.getText()!=null&&n.getText().toString().contains(value))||(n.getStateDescription()!=null&&n.getStateDescription().toString().contains(value)));if(node==null)return false;node.recycle();return true;}
    void click(AccessibilityNodeInfo node){
        check(node!=null,"Missing owned control");Rect bounds=new Rect();node.getBoundsInScreen(bounds);AccessibilityNodeInfo action=node;
        for(int i=0;i<5&&!action.isClickable();i++){AccessibilityNodeInfo parent=action.getParent();if(parent==null)break;action=parent;}
        boolean accepted=action.isClickable()&&action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if(!accepted&&!bounds.isEmpty()){
            long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,bounds.exactCenterX(),bounds.exactCenterY(),0),up=android.view.MotionEvent.obtain(now,now+60,1,bounds.exactCenterX(),bounds.exactCenterY(),0);down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            accepted=automation.injectInputEvent(down,true)&&automation.injectInputEvent(up,true);down.recycle();up.recycle();
        }
        check(accepted,"Click rejected");node.recycle();SystemClock.sleep(350);
    }
    void click(String value){click(text(MODULE,value));}
    void start(String pkg,String activity)throws Exception{try(ParcelFileDescriptor p=automation.executeShellCommand("am start --display 0 -f 0x14000000 -W -n "+pkg+"/"+activity);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){byte[] b=new byte[2048];while(in.read(b)>=0){}}SystemClock.sleep(400);}
    void module()throws Exception{start(MODULE,".MainActivity");}
    void fold(String title){AccessibilityNodeInfo node=find(MODULE,n->n.getContentDescription()!=null&&n.getContentDescription().toString().startsWith(title+"，")&&n.isClickable());if(node!=null){if(node.getContentDescription().toString().endsWith("点击展开"))click(node);else node.recycle();}}
    void top(){for(int i=0;i<4;i++){AccessibilityNodeInfo node=find(MODULE,n->n.getClassName()!=null&&n.getClassName().toString().contains("ScrollView"));if(node==null)return;boolean moved=node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);node.recycle();SystemClock.sleep(150);if(!moved)return;}}
    void collapse(String title){AccessibilityNodeInfo node=find(MODULE,n->n.getContentDescription()!=null&&n.getContentDescription().toString().startsWith(title+"，")&&n.isClickable());if(node!=null){if(node.getContentDescription().toString().endsWith("点击收起"))click(node);else node.recycle();}}
    void mainControls()throws Exception{module();top();fold("广告管理");collapse("App 广告");collapse("官方广告设置");fold("智能跳过");SystemClock.sleep(300);}
    void diagnostic(){find(MODULE,n->{if(n.getText()!=null||n.getContentDescription()!=null)report("Own UI: "+String.valueOf(n.getText())+" | "+String.valueOf(n.getContentDescription()));return false;});}
    boolean checked(String label){AccessibilityNodeInfo node=text(MODULE,label);check(node!=null,"Switch missing: "+label);boolean result=node.isChecked();node.recycle();return result;}
    void toggle(String label,boolean desired){
        for(int i=0;i<400;i++){AccessibilityNodeInfo node=text(MODULE,label);if(node!=null&&node.isEnabled()){if(node.isChecked()!=desired)click(node);else node.recycle();break;}if(node!=null)node.recycle();SystemClock.sleep(100);}
        for(int i=0;i<400;i++){AccessibilityNodeInfo node=text(MODULE,label);boolean ready=node!=null&&node.isEnabled()&&node.isChecked()==desired;if(node!=null)node.recycle();if(ready){checks++;return;}SystemClock.sleep(100);}throw new AssertionError("Switch did not settle: "+label);
    }
    void management(){collapse("设置");collapse("执行记录");fold("订阅管理");}
    void settings(){collapse("订阅管理");collapse("执行记录");fold("设置");}
    void details(){click("详情与规则  ›");AccessibilityNodeInfo card=find(MODULE,n->n.getContentDescription()!=null&&n.getContentDescription().toString().startsWith(SUB+"，详情与规则"));click(card);check(has(MODULE,"-20261027003"),"Wrong subscription details");}
    @Override public void onStart(){Bundle result=new Bundle();boolean master=false,service=false,added=false,captured=false;
        try{
            automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
            mainControls();master=checked("启用");settings();service=checked("无障碍服务");captured=true;report("Initial master="+master+", service="+service);toggle("启用",false);
            toggle("无障碍服务",false);toggle("无障碍服务",true);
            for(int i=0;i<150&&!has(MODULE,"服务已连接");i++)SystemClock.sleep(100);check(has(MODULE,"服务已连接"),"Normal-process accessibility did not connect");report("Normal NoAdMi process: direct service switch connected");
            management();click("导入本地规则  ›");click("粘贴或新建");AccessibilityNodeInfo input=find(MODULE,AccessibilityNodeInfo::isEditable);check(input!=null,"Editor missing");Bundle data=new Bundle();data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"{id:-20261027003,name:'"+SUB+"',version:1,apps:[{id:'"+FIXTURE+"',groups:[{key:1,name:'外部进程验证',actionMaximum:1,forcedTime:3000,rules:{matches:'[text=\"测试跳过\"]',action:'clickNode'}}]}]}");check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data),"Editor rejected text");input.recycle();click("校验并保存");
            for(int i=0;i<50&&text(MODULE,SUB)==null;i++)SystemClock.sleep(100);added=true;toggle(SUB,true);
            start(FIXTURE,".FixtureActivity");SystemClock.sleep(800);check(has(FIXTURE,"测试广告等待关闭"),"Disabled master executed");report("Master off: fixture retained");
            mainControls();toggle("启用",true);start(FIXTURE,".FixtureActivity");
            for(int i=0;i<100&&!has(FIXTURE,"测试广告已关闭");i++)SystemClock.sleep(100);check(has(FIXTURE,"测试广告已关闭"),"Enabled subscription did not execute");report("Master on: real accessibility closed the isolated fixture");mainControls();start(FIXTURE,".FixtureActivity");for(int i=0;i<100&&!has(FIXTURE,"测试广告已关闭");i++)SystemClock.sleep(100);check(has(FIXTURE,"测试广告已关闭"),"Returning from NoAdMi did not reset the per-screen limit");
            mainControls();toggle("启用",false);collapse("订阅管理");collapse("设置");fold("执行记录");check(has(MODULE,"外部进程验证"),"Execution record missing");management();details();click("编辑订阅  ›");click("删除");click("删除");added=false;
            result.putString("stream","PASS: "+checks+" external-process direct-switch, real-service, JSON5 import, disabled-master, automatic action and execution-log checks.\n");
        }catch(Throwable error){diagnostic();result.putString("stream","FAIL: "+error+"\n");}
        finally{if(captured)try{report("Restoring master="+master+", service="+service);mainControls();toggle("启用",false);if(added){management();details();click("编辑订阅  ›");click("删除");click("删除");mainControls();}toggle("启用",false);settings();toggle("无障碍服务",service);collapse("订阅管理");collapse("设置");collapse("执行记录");}catch(Throwable cleanup){result.putString("stream",result.getString("stream","")+"RESTORE FAILED: "+cleanup+"\n");}}
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
