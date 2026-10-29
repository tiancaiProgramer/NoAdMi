package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import io.github.miclean.module.skip.*;
import java.io.*;

/** External driver terminates only our root/shell worker twice. No global restart. */
public final class SkipRuntimeRecoverySmoke extends Instrumentation {
    private int checks;
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void phase(String value)throws Exception {
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),"runtime-recovery-phase.txt"))){out.write(value.getBytes("UTF-8"));}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();SkipRuntimeClient[] client={null};try{
        phase("STARTING");
        runOnMainSync(()->{client[0]=new SkipRuntimeClient(getTargetContext(),scene->kotlin.Unit.INSTANCE);client[0].enabled(true);});
        for(int i=0;i<350&&client[0].getConnectionGeneration()==0;i++)SystemClock.sleep(100);
        check(client[0].getConnectionGeneration()==1,"Initial verified handshake missing: "+SkipRuntimeClient.status);
        check(client[0].getScene()!=null&&client[0].getScene().getListening(),"No actual task listener");
        for(int round=1;round<=2;round++){
            long before=client[0].getConnectionGeneration(),started=SystemClock.elapsedRealtime();phase("KILL_OWN_WORKER_"+round);
            for(int i=0;i<400&&client[0].getConnectionGeneration()==before;i++)SystemClock.sleep(100);
            check(client[0].getConnectionGeneration()==before+1,"Worker did not reconnect once: "+SkipRuntimeClient.status);
            check(client[0].getScene()!=null&&client[0].getScene().getListening(),"Fresh foreground task unavailable");
            check("Shizuku 增强已连接".equals(SkipRuntimeClient.status),"False ready status");
            Bundle progress=new Bundle();progress.putString("stream","Automatic own-worker recovery "+round+" verified; fresh scene received; external-request-to-ready="+(SystemClock.elapsedRealtime()-started)+"ms.\n");sendStatus(0,progress);
        }
        // A valid empty snapshot clears the old target while staying connected.
        // An older CONNECT reply/callback cannot restore that stale target.
        SkipRuntimeScene priorScene=client[0].getScene();
        Class<?> snapshot=Class.forName("io.github.miclean.module.skip.SkipRuntimeClient$Snapshot");
        java.lang.reflect.Constructor<?> ctor=snapshot.getDeclaredConstructor(SkipRuntimeScene.class,boolean.class,long.class);ctor.setAccessible(true);
        java.lang.reflect.Field ef=SkipRuntimeClient.class.getDeclaredField("epoch");ef.setAccessible(true);long token=ef.getLong(client[0]);
        java.lang.reflect.Method accept=SkipRuntimeClient.class.getDeclaredMethod("accept",long.class,snapshot);accept.setAccessible(true);
        Object empty=ctor.newInstance(null,true,priorScene.getGeneration()+1),older=ctor.newInstance(priorScene,true,priorScene.getGeneration());
        runOnMainSync(()->{try{accept.invoke(client[0],token,empty);check(client[0].getScene()==null,"Empty foreground retained old target");check("Shizuku 增强已连接".equals(SkipRuntimeClient.status),"Connected empty scene shown as pending");accept.invoke(client[0],token,older);check(client[0].getScene()==null,"Late old scene restored after empty snapshot");}catch(Exception e){throw new RuntimeException(e);}});
        long generation=client[0].getConnectionGeneration();
        java.lang.reflect.Field f=SkipRuntimeClient.class.getDeclaredField("connection");f.setAccessible(true);
        ServiceConnection late=(ServiceConnection)f.get(client[0]);
        java.lang.reflect.Field bf=SkipRuntimeClient.class.getDeclaredField("binder");bf.setAccessible(true);IBinder old=(IBinder)bf.get(client[0]);
        runOnMainSync(()->{client[0].close();late.onServiceConnected(new ComponentName(getTargetContext(),SkipRuntimeService.class),old);late.onServiceDisconnected(new ComponentName(getTargetContext(),SkipRuntimeService.class));});
        SystemClock.sleep(1600);
        check(client[0].getConnectionGeneration()==generation,"Late callback resurrected connection");
        check(client[0].getScene()==null,"Closed client retained privileged scene");
        phase("PASS");out.putString("stream","PASS: "+checks+" recovery checks; two real worker deaths recovered automatically, duplicate/late callbacks cannot resurrect closed client.\n");
    }catch(Throwable error){out.putString("stream","FAIL: "+error+"\n");try{phase("FAIL");}catch(Exception ignored){}}
    finally{runOnMainSync(()->{if(client[0]!=null)client[0].close();});}
    finish(out.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
