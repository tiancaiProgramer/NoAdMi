package io.github.miclean.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import org.json.*;
/** Signed local test of NoAdMi's own client; never instruments Xiaomi Account. */
public final class AccountAdSmoke extends Instrumentation {
 Bundle args;
 volatile Activity activity;
 @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(a.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=a;}
 private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 private void verifyFallback(JSONObject live)throws Exception{
  for(int i=0;i<30&&activity==null;i++)SystemClock.sleep(100);
  if(activity==null)throw new AssertionError("No module Activity");
  final Throwable[] failure={null};
  for(int retry=0;retry<30;retry++){
   final boolean[] ready={false};runOnMainSync(()->{try{Object panel=field(activity,"nativeAds");java.util.Map<?,?> rows=(java.util.Map<?,?>)field(panel,"rows");Object row=rows.get("system_tools");ready[0]=field(row,"enabled")!=null;}catch(Exception ignored){}});
   if(ready[0])break;SystemClock.sleep(200);
  }
  runOnMainSync(()->{
   try{
    Object panel=field(activity,"nativeAds");Object row=((java.util.Map<?,?>)field(panel,"rows")).get("system_tools");
    android.widget.Switch toggle=(android.widget.Switch)field(row,"toggle");android.widget.TextView label=(android.widget.TextView)field(row,"state");
    Method apply=panel.getClass().getDeclaredMethod("apply",java.util.Map.class);apply.setAccessible(true);
    java.util.Map<String,JSONObject> values=new java.util.LinkedHashMap<>();values.put("system_tools",new JSONObject().put("id","system_tools").put("ok",true).put("enabled",false).put("writable",false));apply.invoke(panel,values);
    if(toggle.getVisibility()!=android.view.View.GONE||!label.isClickable()||!label.getText().toString().contains("打开官方设置"))throw new AssertionError("Fallback must be link only");
    values.put("system_tools",live.getJSONArray("results").getJSONObject(0));apply.invoke(panel,values);
    if(toggle.getVisibility()!=android.view.View.VISIBLE||label.isClickable())throw new AssertionError("Live bridge must restore real toggle");
   }catch(Throwable error){failure[0]=error;}
  });
  if(failure[0]!=null)throw new IllegalStateException("fallback UI failed",failure[0]);
 }

 @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Class<?> client=getTargetContext().getClassLoader().loadClass("io.github.miclean.module.AccountAdClient");
   boolean write="write".equals(args.getString("operation","read"));
   boolean desired=Boolean.parseBoolean(args.getString("enabled","false"));
   Method method=write?client.getDeclaredMethod("write",Context.class,boolean.class):client.getDeclaredMethod("read",Context.class);method.setAccessible(true);
   JSONObject data=(JSONObject)(write?method.invoke(null,getTargetContext(),desired):method.invoke(null,getTargetContext()));
   JSONObject row=data.getJSONArray("results").getJSONObject(0);
   if(!row.getBoolean("ok") || (write && (!row.getBoolean("confirmed") || row.getBoolean("enabled")!=desired)))throw new AssertionError("No confirmed result");
   if("ui".equals(args.getString("operation")))verifyFallback(data);
   result.putString("stream","ACCOUNT_RESULT="+data+("ui".equals(args.getString("operation"))?" FALLBACK_LINK_ONLY=PASS":"")+"\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable e){while(e instanceof InvocationTargetException&&e.getCause()!=null)e=e.getCause();result.putString("stream","ACCOUNT_ERROR="+e.getClass().getSimpleName()+(e instanceof AssertionError?": "+e.getMessage():"")+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
}
