package io.github.miclean.module;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import io.github.miclean.module.skip.SkipRepository;
import io.github.miclean.module.skip.SkipAccessibilityService;
import rikka.shizuku.Shizuku;

/** Own-component control; permission requests still use the official Shizuku dialog. */
final class AccessibilityAccess implements AutoCloseable {
    private static final int REQUEST=4812;
    private static final ExecutorService CONTROL=Executors.newSingleThreadExecutor(task->new Thread(task,"NoAdMi-accessibility-control"));
    private final Activity activity;
    private final SkipRepository repository;
    private final Runnable refresh;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Boolean pending;
    private volatile boolean busy,closed;
    private volatile long sequence;
    private String error;
    private ServiceConnection connection;
    private Shizuku.UserServiceArgs args;
    private Runnable timeout;
    private final Runnable connected=()->{if(!closed)changed();};
    private final Shizuku.OnBinderReceivedListener received=()->changed();
    private final Shizuku.OnBinderDeadListener died=()->{if(busy&&connection!=null)finish(sequence,"Shizuku 已断开，请启动后重试");else changed();};
    private final Shizuku.OnRequestPermissionResultListener permission=(request,result)->{
        if(request!=REQUEST)return;Boolean desired=pending;pending=null;
        if(result==PackageManager.PERMISSION_GRANTED&&desired!=null)setEnabled(desired);
        else {changed();if(result!=PackageManager.PERMISSION_GRANTED)failure("Shizuku 未授权，请在 Shizuku 应用管理中允许 NoAdMi");}
    };
    AccessibilityAccess(Activity activity,Runnable refresh){
        this.activity=activity;repository=SkipRepository.get(activity);this.refresh=refresh;
        Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(died);Shizuku.addRequestPermissionResultListener(permission);SkipAccessibilityService.observeConnection(connected);
    }
    boolean busy(){return busy;}
    String error(){return error;}
    boolean shizukuMode(){return "shizuku".equals(repository.getPreferences().getString("access_method","root"));}
    void method(boolean shizuku){if(busy)return;pending=null;error=null;repository.getPreferences().edit().putString("access_method",shizuku?"shizuku":"root").apply();changed();}
    String status(){
        try{if(!Shizuku.pingBinder())return "Shizuku 未运行";if(Shizuku.getVersion()<13)return "需要 Shizuku 13 或更新版本";return Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"Shizuku 已授权":"Shizuku 尚未授权";}
        catch(RuntimeException unavailable){return "Shizuku 状态暂不可用";}
    }
    void authorize(){
        try{
            if(!Shizuku.pingBinder()){failure("请先启动 Shizuku，再返回重试");return;}
            if(Shizuku.getVersion()<13){failure("请更新至 Shizuku 13 或更新版本");return;}
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){changed();return;}
            if(Shizuku.shouldShowRequestPermissionRationale()){failure("请在 Shizuku 的应用管理中允许 NoAdMi");return;}
            Shizuku.requestPermission(REQUEST);
        }catch(RuntimeException unavailable){failure("Shizuku 连接不可用，请启动后重试");}
    }
    void setEnabled(boolean enabled){
        if(busy||closed)return;error=null;
        if(shizukuMode()){
            try{
                if(!Shizuku.pingBinder()||Shizuku.getVersion()<13){failure("请先启动 Shizuku 13 或更新版本，或选择 Root");return;}
                if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){pending=enabled;authorize();changed();return;}
            }catch(RuntimeException unavailable){failure("Shizuku 连接不可用，请重新授权或选择 Root");return;}
        }
        long token=++sequence;busy=true;changed();Log.i("NoAdMiAccess","request="+token+" mode="+(shizukuMode()?"shizuku":"root")+" enabled="+enabled);
        if(shizukuMode())bind(token,enabled);else CONTROL.execute(()->{String problem=null;try{root(enabled);}catch(Exception failed){problem=failed.getMessage();}String result=problem;main.post(()->finish(token,result));});
    }
    private void bind(long token,boolean enabled){
        try{
            int version=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
            args=new Shizuku.UserServiceArgs(new ComponentName(activity,AccessibilityControlService.class)).tag("noadmi_accessibility").version(version).daemon(false).processNameSuffix("accessibility_control");
            // Remove only our worker's stale server record before requesting a fresh binding.
            Shizuku.unbindUserService(args,null,true);
            connection=new ServiceConnection(){
                public void onServiceConnected(ComponentName name,IBinder binder){
                    if(!current(token))return;Log.i("NoAdMiAccess","Shizuku worker connected request="+token);
                    CONTROL.execute(()->{
                        if(!current(token))return;String problem=null;Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                        try{data.writeInterfaceToken(AccessibilityControlService.DESCRIPTOR);data.writeInt(enabled?1:0);if(!binder.transact(AccessibilityControlService.SET,data,reply,0))throw new IllegalStateException("Shizuku 控制接口不可用");reply.readException();if((reply.readInt()!=0)!=enabled)throw new IllegalStateException("系统回读不一致");}
                        catch(Exception failed){problem=failed.getMessage();}finally{data.recycle();reply.recycle();}
                        String result=problem;main.post(()->finish(token,result));
                    });
                }
                public void onServiceDisconnected(ComponentName name){if(current(token))finish(token,"Shizuku 控制服务已断开，请重试");}
            };
            timeout=()->{if(current(token))finish(token,"Shizuku 控制进程未回连，请在 Shizuku 中重新启动服务后重试");};main.postDelayed(timeout,25000);
            Shizuku.bindUserService(args,connection);
        }catch(Exception failed){finish(token,failed.getMessage()==null?"Shizuku 服务启动失败":failed.getMessage());}
    }
    private boolean current(long token){return !closed&&busy&&sequence==token;}
    private void root(boolean enabled)throws Exception{
        String apk="'"+activity.getApplicationInfo().sourceDir.replace("'","'\\''")+"'";
        String command="CLASSPATH="+apk+" app_process /system/bin "+AccessibilityControlService.class.getName()+" "+(enabled?"1":"0")+" "+(android.os.Process.myUid()/100000);
        java.lang.Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{byte[] b=new byte[2048];int n;while((n=process.getInputStream().read(b))!=-1)if(output.size()+n<32768)output.write(b,0,n);}catch(Exception ignored){}});reader.start();
        try{if(!process.waitFor(40,TimeUnit.SECONDS))throw new IllegalStateException("Root 授权或系统操作超时");reader.join(1000);if(process.exitValue()!=0||!output.toString("UTF-8").contains("NOADMI_ACCESS_OK"))throw new IllegalStateException("Root 操作未完成："+output.toString("UTF-8").trim());}finally{process.destroy();}
    }
    private void finish(long token,String problem){
        if(!current(token))return;busy=false;if(timeout!=null)main.removeCallbacks(timeout);unbind();error=problem;Log.i("NoAdMiAccess","complete="+token+" result="+(problem==null?"settings accepted":problem));changed();if(problem!=null)notice(problem);
    }
    private void unbind(){ServiceConnection prior=connection;connection=null;Shizuku.UserServiceArgs priorArgs=args;args=null;if(priorArgs!=null)try{Shizuku.unbindUserService(priorArgs,prior,true);}catch(RuntimeException ignored){}}
    private void failure(String message){error=message;changed();notice(message);Log.w("NoAdMiAccess",message);}
    private void changed(){main.post(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())refresh.run();});}
    private void notice(String text){if(!closed&&!activity.isFinishing())Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    @Override public void close(){closed=true;sequence++;main.removeCallbacksAndMessages(null);SkipAccessibilityService.removeConnectionObserver(connected);Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(died);Shizuku.removeRequestPermissionResultListener(permission);unbind();}
}
