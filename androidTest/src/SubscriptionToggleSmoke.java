package io.github.miclean.tests;

import android.app.*;
import android.content.SharedPreferences;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.miclean.module.skip.*;

/** Only the two empty, disabled test sources are changed; real subscriptions are preserved. */
public final class SubscriptionToggleSmoke extends Instrumentation {
    private volatile Activity current;private SkipRepository repository;private int checks;
    private final long a=-20261027011L,b=-20261027012L;
    private Switch first,second;private List<View> rows;private String phase="startup";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);current=activity;}
    private void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private View tag(View root,String value){if(value.equals(root.getTag()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=tag(((ViewGroup)root).getChildAt(i),value);if(result!=null)return result;}return null;}
    private boolean text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return true;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(text(((ViewGroup)root).getChildAt(i),value))return true;return false;}
    private void controls(){View root=current.getWindow().getDecorView();for(String key:new String[]{"advertising","smart_skip","skip_compact_sources"})if(tag(root,"body:"+key).getVisibility()!=View.VISIBLE)tag(root,"fold:"+key).performClick();first=(Switch)tag(root,"skip_source_toggle:"+a);second=(Switch)tag(root,"skip_source_toggle:"+b);check(first!=null&&second!=null,"Test toggles missing");}
    private void stableRows(){View root=current.getWindow().getDecorView();ViewGroup list=(ViewGroup)tag(root,"skip_source_list");check(list.getChildCount()==rows.size(),"Row count changed on a toggle");for(int i=0;i<rows.size();i++)check(rows.get(i)==list.getChildAt(i),"Other subscription view was replaced at "+phase+", row "+i);check(first.isEnabled()&&second.isEnabled(),"Saving disabled a switch");}
    private void awaitValues(boolean av,boolean bv){for(int i=0;i<200;i++){boolean stored=repository.getSources().find(a).getEnabled()==av&&repository.getSources().find(b).getEnabled()==bv;final boolean[] visible={false};main(()->visible[0]=first.isChecked()==av&&second.isChecked()==bv&&first.isEnabled()&&second.isEnabled()&&!String.valueOf(first.getStateDescription()).contains("正在保存")&&!String.valueOf(second.getStateDescription()).contains("正在保存"));if(stored&&visible[0]){checks++;return;}SystemClock.sleep(25);}throw new AssertionError("Visible state and persisted state did not converge");}
    @Override public void onStart(){Bundle result=new Bundle();SharedPreferences ui=getTargetContext().getSharedPreferences("miclean_ui",0);Map<String,?> before=new HashMap<>(ui.getAll());CountDownLatch release=new CountDownLatch(1);AtomicBoolean blocked=new AtomicBoolean();
        try{
            for(int i=0;i<80&&current==null;i++)SystemClock.sleep(100);check(current!=null,"Module not launched");
            repository=SkipRepository.get(getTargetContext());check(repository.getSources().list().stream().noneMatch(s->s.getId()==a||s.getId()==b),"Reserved test ID already used");
            boolean master=repository.getEnabled();String method=repository.getPreferences().getString("access_method","root");
            for(long id:new long[]{a,b})repository.getSources().add("{id:"+id+",name:'NoAdMi 开关验证 "+(id==a?"A":"B")+"',version:1,apps:[]}","",false);
            for(int i=0;i<60;i++){boolean[] ready={false};main(()->ready[0]=text(current.getWindow().getDecorView(),"框架服务已连接"));if(ready[0])break;SystemClock.sleep(100);}
            main(()->{try{java.lang.reflect.Method render=current.getClass().getDeclaredMethod("render");render.setAccessible(true);render.invoke(current);controls();}catch(Exception error){throw new RuntimeException(error);}});
            waitForIdleSync();SystemClock.sleep(300);main(()->{controls();rows=new ArrayList<>();ViewGroup list=(ViewGroup)tag(current.getWindow().getDecorView(),"skip_source_list");for(int i=0;i<list.getChildCount();i++)rows.add(list.getChildAt(i));});
            CountDownLatch entered=new CountDownLatch(1);SkipRepository.executor.execute(()->{blocked.set(true);entered.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException ignored){}finally{blocked.set(false);}});check(entered.await(3,TimeUnit.SECONDS),"Could not pause subscription network queue");
            phase="initial rapid clicks";main(()->{first.performClick();stableRows();check(first.isChecked()&&!second.isChecked(),"First click changed another source");first.performClick();first.performClick();stableRows();});awaitValues(true,false);check(blocked.get(),"Switch writes waited for network tasks");main(this::stableRows);
            for(int i=0;i<24;i++){phase="rapid click "+i;main(()->{first.performClick();stableRows();check(!second.isChecked(),"Other source flickered to checked");});SystemClock.sleep(i%3==0?40:5);}
            main(()->{if(first.isChecked())first.performClick();second.performClick();first.performClick();first.performClick();stableRows();});awaitValues(false,true);check(blocked.get(),"Rapid saves were serialized behind downloads");main(this::stableRows);
            SkipSources disk=new SkipSources(new File(getTargetContext().getFilesDir(),"smart_skip/sources.json"));check(!disk.find(a).getEnabled()&&disk.find(b).getEnabled(),"Disk reload differs from visible state");
            Activity old=current;main(old::recreate);for(int i=0;i<80&&current==old;i++)SystemClock.sleep(100);check(current!=old,"Activity did not recreate");main(()->{controls();check(!first.isChecked()&&second.isChecked(),"Recreated UI lost saved state");});
            check(repository.getEnabled()==master&&method.equals(repository.getPreferences().getString("access_method","root")),"Unrelated settings changed");
            result.putString("stream","PASS: "+checks+" stable native switch views, independent values, rapid off/on, unblocked saves with network queue paused, last-intent persistence, disk reload and recreated Activity checks.\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");}
        finally{release.countDown();if(repository!=null)for(long id:new long[]{a,b})if(repository.getSources().list().stream().anyMatch(s->s.getId()==id))repository.getSources().remove(id);SharedPreferences.Editor edit=ui.edit();for(String key:new String[]{"advertising","smart_skip","skip_compact_sources"}){if(before.containsKey(key))edit.putBoolean(key,(Boolean)before.get(key));else edit.remove(key);}edit.commit();}
        finish(result.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
