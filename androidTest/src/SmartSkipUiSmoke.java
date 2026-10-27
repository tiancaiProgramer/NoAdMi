package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import io.github.miclean.module.skip.SkipRepository;

/** Tests only NoAdMi. The temporary subscription is disabled and removed in finally. */
public final class SmartSkipUiSmoke extends Instrumentation {
    private volatile Activity current;
    private int checks;
    private final long id=-20261027001L;
    private SkipRepository repository;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);current=activity;}
    private void onMain(Runnable action){final Throwable[] issue={null};runOnMainSync(()->{try{action.run();}catch(Throwable error){issue[0]=error;}});if(issue[0]!=null)throw new AssertionError(issue[0]);}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private View tag(View view,String tag){if(tag.equals(view.getTag()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=tag(((ViewGroup)view).getChildAt(i),tag);if(found!=null)return found;}return null;}
    private TextView text(View view,String text){if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return(TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void click(String label){onMain(()->{TextView found=text(current.getWindow().getDecorView(),label);check(found!=null&&found.isClickable(),"Missing click listener: "+label);found.performClick();});waitForIdleSync();SystemClock.sleep(200);}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String value,boolean editor){
        if(node==null)return null;
        if(editor?node.isEditable():value.contentEquals(node.getText()==null?"":node.getText()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i),found=find(child,value,editor);if(found!=null)return found;if(child!=null)child.recycle();}return null;
    }
    private void dialogAction(String label,String input){
        UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        AccessibilityNodeInfo node=null;
        for(int attempt=0;attempt<25&&node==null;attempt++){
            android.util.SparseArray<java.util.List<android.view.accessibility.AccessibilityWindowInfo>> displays=automation.getWindowsOnAllDisplays();
            for(int d=0;d<displays.size()&&node==null;d++)for(android.view.accessibility.AccessibilityWindowInfo window:displays.valueAt(d)){
                if(displays.keyAt(d)!=0||(!window.isActive()&&!window.isFocused()))continue;AccessibilityNodeInfo root=window.getRoot();
                if(root!=null&&"io.github.miclean.module".contentEquals(root.getPackageName()))node=find(root,label,input!=null);
                if(root!=null&&root!=node)root.recycle();if(node!=null)break;
            }
            if(node==null)SystemClock.sleep(120);
        }
        check(node!=null,"Module dialog control absent on all displays: "+label);
        android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
        Bundle args=new Bundle();if(input!=null)args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,input);
        boolean accepted=node.performAction(input==null?AccessibilityNodeInfo.ACTION_CLICK:AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        if(!accepted&&input==null&&!bounds.isEmpty()){
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            accepted=automation.injectInputEvent(down,true)&&automation.injectInputEvent(up,true);down.recycle();up.recycle();
        }
        check(accepted,"Dialog action rejected: "+label);
        node.recycle();waitForIdleSync();SystemClock.sleep(300);
    }
    private void captureDialog(String filename){
        android.graphics.Bitmap bitmap=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(bitmap==null)throw new AssertionError("Dialog screenshot unavailable");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(),filename))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new AssertionError(error);}finally{bitmap.recycle();}
    }
    private void checkBottomActions(){onMain(()->{
        LinearLayout body=(LinearLayout)tag(current.getWindow().getDecorView(),"body:skip_compact_sources");
        String[] labels={"添加订阅链接  ›","导入本地规则  ›","立即更新订阅"};
        for(int i=0;i<labels.length;i++)check(body.getChildAt(body.getChildCount()-3+i)==text(body,labels[i]),"Subscription action moved from footer: "+labels[i]);
        check(body.indexOfChild(tag(body,"skip_source_list"))<body.getChildCount()-3,"Source list below footer");
    });}
    private void awaitSource(boolean exists){for(int i=0;i<50;i++){if(repository.getSources().list().stream().anyMatch(s->s.getId()==id)==exists)return;SystemClock.sleep(100);}throw new AssertionError("Subscription operation timed out");}
    @Override public void onStart(){
        Bundle result=new Bundle();SharedPreferences ui=getTargetContext().getSharedPreferences("miclean_ui",0);Map<String,?> before=new HashMap<>(ui.getAll());
        SharedPreferences prefs=getTargetContext().getSharedPreferences("smart_skip",0);boolean oldWifi=prefs.getBoolean("wifi_only",true),oldAuto=prefs.getBoolean("auto_update",false);
        try{
            for(int i=0;i<80&&current==null;i++)SystemClock.sleep(100);check(current!=null,"Main activity not launched");
            repository=SkipRepository.get(getTargetContext());boolean originalMaster=repository.getEnabled();String originalServices=android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);String originalMethod=prefs.getString("access_method","root");check(repository.getSources().list().stream().noneMatch(s->s.getId()==id),"Reserved test ID already used");
            onMain(()->{
                View root=current.getWindow().getDecorView(),ad=tag(root,"body:advertising"),skip=tag(root,"body:smart_skip");check(ad!=null&&skip!=null,"Folds missing");
                if(ad.getVisibility()!=View.VISIBLE)tag(root,"fold:advertising").performClick();if(skip.getVisibility()!=View.VISIBLE)tag(root,"fold:smart_skip").performClick();
                View group=tag(root,"group:smart_skip");check(group.getParent()==ad,"Smart skip outside advertising");check(tag(root,"group:app_ads").getParent()==group.getParent(),"Not parallel folds");
                check(text(root,"启用") instanceof Switch,"Master missing");
                check(text(root,"管理  ›")==null,"Obsolete management entry remains");
                for(String key:new String[]{"skip_compact_sources","skip_compact_settings","skip_compact_logs"}){
                    View h=tag(root,"fold:"+key),b=tag(root,"body:"+key);check(h instanceof LinearLayout&&b!=null,"Inline fold missing: "+key);
                    check((((LinearLayout)h).getGravity()&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.CENTER_VERTICAL,"Fold title not centered");
                    if(!before.containsKey(key))check(b.getVisibility()==View.GONE,"New group not initially collapsed: "+key);
                }
                if(tag(root,"body:skip_compact_sources").getVisibility()!=View.VISIBLE)tag(root,"fold:skip_compact_sources").performClick();
                check(tag(root,"body:skip_compact_sources").isShown(),"Subscriptions not visible");
                check(text(root,"自动更新订阅").getParent()==tag(root,"body:skip_compact_sources"),"Auto update outside subscriptions");
                check(text(root,"移动网络更新").getParent()==tag(root,"body:skip_compact_sources"),"Mobile switch outside subscriptions");
                Switch mobile=(Switch)text(root,"移动网络更新");check(mobile.isChecked()==!oldWifi,"Mobile value changed during upgrade");
                mobile.setChecked(oldWifi);check(prefs.getBoolean("wifi_only",true)==!oldWifi,"Mobile toggle not inverted");mobile.setChecked(!oldWifi);
                Switch auto=(Switch)text(root,"自动更新订阅");auto.setChecked(!oldAuto);check(prefs.getBoolean("auto_update",false)==!oldAuto,"Auto update not persisted");auto.setChecked(oldAuto);
                if(tag(root,"body:skip_compact_settings").getVisibility()!=View.VISIBLE)tag(root,"fold:skip_compact_settings").performClick();LinearLayout settings=(LinearLayout)tag(root,"body:skip_compact_settings");check(settings.isShown(),"Settings not visible");
                check(settings.getChildCount()==2,"Settings has duplicate controls");check(text(settings,"无障碍方式")!=null&&settings.getChildAt(0) instanceof Switch,"Accessibility controls missing");
                check(text(root,"查看完整 GPL-3.0 许可")==null,"License still inside smart skip");
                tag(root,"fold:skip_compact_settings").performClick();
            
            });
            check(current.getClass().getSimpleName().equals("MainActivity"),"Inline controls navigated away");
            checkBottomActions();
            click("添加订阅链接  ›");captureDialog("subscription-link-0.12.3.png");dialogAction("editor","http://example.invalid/rules.json5");dialogAction("读取订阅",null);dialogAction("取消",null);
            click("导入本地规则  ›");captureDialog("subscription-import-0.12.3.png");dialogAction("粘贴或新建",null);
            dialogAction("editor","{id: "+id+", name: 'NoAdMi 界面验证', version: 1, apps: [{id: 'io.github.miclean.skipfixture', name: '本机测试夹具', groups: [{key: 1,name: '测试广告组',rules: '[text=\"测试跳过\"]'}]}]}");
            dialogAction("校验并保存",null);awaitSource(true);SystemClock.sleep(300);check(!repository.getSources().find(id).getEnabled(),"Imported subscription enabled itself");checkBottomActions();
            onMain(()->{View root=current.getWindow().getDecorView();LinearLayout switches=(LinearLayout)tag(root,"skip_source_list"),body=(LinearLayout)tag(root,"body:skip_compact_sources");TextView entry=text(body,"详情与规则  ›");
                check(entry!=null&&entry.isClickable(),"Single detail entry missing");check(tag(root,"skip_source_card:"+id)==null,"Individual source cards leaked to main controls");
                for(int i=0;i<switches.getChildCount();i++)check(switches.getChildAt(i) instanceof Switch,"Non-switch inside subscription switches");
                check(body.indexOfChild(switches)<body.indexOfChild(entry),"Details above subscription switches");entry.performClick();});waitForIdleSync();SystemClock.sleep(200);
            check(current.getClass().getSimpleName().equals("SmartSkipActivity"),"Grouped detail page did not open");captureDialog("subscription-cards-0.12.3.png");
            onMain(()->{View root=current.getWindow().getDecorView(),card=tag(root,"skip_source_card:"+id);LinearLayout details=(LinearLayout)tag(root,"skip_source_details");
                check(card!=null&&card.isClickable(),"Test source card missing");check(details.getChildCount()==repository.getSources().list().size(),"Sources missing independent detail cards");
                for(int i=0;i<details.getChildCount();i++){View view=details.getChildAt(i);check(view instanceof ViewGroup&&((ViewGroup)view).getChildAt(0).isClickable(),"Detail header not clickable");LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)view.getLayoutParams();check(params.topMargin>0&&params.bottomMargin>0,"Detail cards have no spacing");}
                View content=tag(root,"skip_source_content:"+id);check(content!=null&&!content.isShown(),"Source initially expanded");
                card.performClick();check(content.isShown(),"Source did not expand below header");check(tag(root,"skip_source_details")==details,"Source expansion navigated away");card.performClick();check(!content.isShown(),"Source did not collapse");card.performClick();check(content.isShown(),"Source did not reopen");});waitForIdleSync();SystemClock.sleep(200);
            click("应用规则  ›");click("规则组  ›");
            onMain(()->{TextView toggle=text(current.getWindow().getDecorView(),"测试广告组");check(toggle instanceof Switch,"Group not a switch");((Switch)toggle).setChecked(false);});
            SystemClock.sleep(500);check(Boolean.FALSE.equals(repository.getSources().find(id).getGroups().get("io.github.miclean.skipfixture/1")),"Group override not saved");
            click("1 条规则 · 查看规则内容  ›");dialogAction("关闭",null);click("‹  订阅管理");click("‹  订阅管理");
            click("删除订阅");dialogAction("删除",null);awaitSource(false);SystemClock.sleep(300);click("‹  订阅管理");checkBottomActions();check(repository.getEnabled()==originalMaster,"Master changed during UI edits");check(java.util.Objects.equals(originalServices,android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)),"Selected services changed during UI edits");check(originalMethod.equals(prefs.getString("access_method","root")),"Access method changed during UI edits");
            result.putString("stream","PASS: "+checks+" smart skip UI checks; grouped switches above a single details entry, dedicated page with separate source cards, inline source expansion/collapse without a third details page, card spacing and correct source controls, subscription actions stay last before/after add/delete, HTTPS validation keeps dialog open, inline compact folds, default collapse, two-row settings, inverted mobile preference, live controls, JSON5 editor/import, disabled default, app/group pages, persisted group switch, rule dialog, back and deletion.\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n");}
        finally{
            prefs.edit().putBoolean("wifi_only",oldWifi).putBoolean("auto_update",oldAuto).commit();if(repository!=null)repository.schedule();
            if(repository!=null&&repository.getSources().list().stream().anyMatch(s->s.getId()==id))repository.getSources().remove(id);
            SharedPreferences.Editor edit=ui.edit();for(String key:new String[]{"advertising","smart_skip","skip_compact_sources","skip_compact_settings","skip_compact_logs"}){if(before.containsKey(key))edit.putBoolean(key,(Boolean)before.get(key));else edit.remove(key);}edit.commit();
        }
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
