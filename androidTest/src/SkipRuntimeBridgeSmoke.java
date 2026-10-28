package io.github.miclean.tests;
import android.app.*;import android.os.*;import io.github.miclean.module.skip.*;
/** Connection-only probe: existing official grant, no permission mutation or input. */
public final class SkipRuntimeBridgeSmoke extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle out=new Bundle();SkipRuntimeClient[] holder={null};try{
  runOnMainSync(()->{holder[0]=new SkipRuntimeClient(getTargetContext(),scene->kotlin.Unit.INSTANCE);holder[0].enabled(true);});
  String prior="";for(int i=0;i<320;i++){String s=SkipRuntimeClient.status;if(!s.equals(prior)){Bundle p=new Bundle();p.putString("stream","Runtime: "+s+"\n");sendStatus(0,p);prior=s;}if(s.equals("Shizuku 增强已连接"))break;SystemClock.sleep(100);}
  if(!"Shizuku 增强已连接".equals(SkipRuntimeClient.status))throw new AssertionError(SkipRuntimeClient.status);
  SkipRuntimeScene scene=holder[0].getScene();if(scene==null||!scene.getListening())throw new AssertionError("No verified task listener");
  out.putString("stream","PASS: authorized dedicated Shizuku worker connected; verified default-display task metadata and registered task listener.\n");
 }catch(Throwable e){out.putString("stream","FAIL: "+e+"\n");}finally{runOnMainSync(()->{if(holder[0]!=null)holder[0].close();});}finish(Activity.RESULT_OK,out);}
}
