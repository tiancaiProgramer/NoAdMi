package io.github.miclean.module;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs exclusively inside the genuine Xiaomi Account application process. */
final class AccountAdHost {
    private static final AtomicBoolean installed=new AtomicBoolean();
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"NoAdMi-account-ad");t.setDaemon(true);return t;
    });
    static void install(Application app,ClassLoader loader) throws Exception {
        if(Build.VERSION.SDK_INT<34 || !Rules.ACCOUNT.equals(app.getPackageName()))return;
        PermissionInfo permission=app.getPackageManager().getPermissionInfo(AccountAdProtocol.PERMISSION,0);
        if(!Rules.MODULE.equals(permission.packageName)
                || (permission.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_SIGNATURE)
            throw new SecurityException("control permission owner");
        int moduleUid=app.getPackageManager().getApplicationInfo(Rules.MODULE,0).uid;
        Binding binding=new Binding(loader);
        if(!installed.compareAndSet(false,true))return;
        app.registerReceiver(new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent request){
                if(!isOrderedBroadcast() || !AccountAdProtocol.ACTION.equals(request.getAction())
                        || !Rules.ACCOUNT.equals(request.getPackage())
                        || !AccountAdProtocol.sender(getSentFromUid(),moduleUid,getSentFromPackage()))return;
                String op=request.getStringExtra("op"),nonce=request.getStringExtra("nonce");
                Bundle extras=request.getExtras();
                boolean hasValue=request.hasExtra("enabled");
                if(!AccountAdProtocol.valid(op,nonce,hasValue) || extras==null
                        || extras.size()!=(hasValue?3:2)
                        || (hasValue && !(extras.get("enabled") instanceof Boolean)))return;
                final boolean desired=request.getBooleanExtra("enabled",false);
                PendingResult pending=goAsync();
                WORK.execute(()->{
                    Bundle response=new Bundle();response.putString("nonce",nonce);
                    try{
                        if("write".equals(op))binding.save(app,desired);
                        String value=Settings.Global.getString(app.getContentResolver(),"passport_ad_status");
                        if(!"ON".equals(value)&&!"OFF".equals(value))throw new IllegalStateException("账号广告状态未初始化");
                        boolean enabled="ON".equals(value);
                        if("write".equals(op)&&enabled!=desired)throw new IllegalStateException("账号状态回读未确认成功");
                        response.putBoolean("ok",true);response.putBoolean("enabled",enabled);
                        response.putBoolean("confirmed","write".equals(op));
                    }catch(Throwable error){
                        response.putBoolean("ok",false);
                        response.putString("error",error instanceof IllegalStateException?error.getMessage():"账号后台接口不可用，请使用官方设置");
                    }finally{
                        pending.setResult(AccountAdProtocol.RESPONSE,null,response);pending.finish();
                    }
                });
            }
        },new IntentFilter(AccountAdProtocol.ACTION),AccountAdProtocol.PERMISSION,null,Context.RECEIVER_EXPORTED);
        HookEntry.report("installed account.ad.background bridge");
    }
    private static final class Binding {
        final Constructor<?> constructor;
        final Method background,child;
        final Field code,status;
        Binding(ClassLoader loader)throws Exception{
            Class<?> task=Class.forName("com.xiaomi.account.settings.SystemAdFragment$c",false,loader);
            Constructor<?> selected=null;
            for(Constructor<?> candidate:task.getDeclaredConstructors()){
                Class<?>[] args=candidate.getParameterTypes();
                if(args.length==3&&args[0]==Context.class&&args[1].isInterface()&&args[2]==String.class){
                    if(selected!=null)throw new NoSuchMethodException("ambiguous account ad worker");
                    selected=candidate;
                }
            }
            if(selected==null)throw new NoSuchMethodException("account ad worker constructor");
            constructor=selected;constructor.setAccessible(true);
            background=task.getDeclaredMethod("b",Void[].class);background.setAccessible(true);
            Class<?> result=background.getReturnType();
            if(result==Object.class||result.isPrimitive())throw new NoSuchMethodException("account ad result");
            code=result.getDeclaredField("a");status=result.getDeclaredField("c");
            if(code.getType()!=int.class||status.getType()!=String.class)throw new NoSuchFieldException("account ad status");
            code.setAccessible(true);status.setAccessible(true);
            child=Class.forName("com.xiaomi.accountsdk.account.a",false,loader).getDeclaredMethod("b",Context.class);
            if(child.getReturnType()!=boolean.class||!Modifier.isStatic(child.getModifiers()))throw new NoSuchMethodException("child account guard");
        }
        void save(Application app,boolean desired)throws Exception{
            if(Boolean.TRUE.equals(child.invoke(null,app)))throw new IllegalStateException("儿童账号不支持修改此项");
            String expected=desired?"ON":"OFF";
            Object task=constructor.newInstance(app,null,expected);
            // Invoke only the original worker: its normal post-execute callback requires an Activity.
            Object result=background.invoke(task,new Object[]{new Void[0]});
            if(result==null)throw new IllegalStateException("账号服务未返回结果，请确认登录和网络");
            if(code.getInt(result)!=0||!expected.equals(status.get(result)))
                throw new IllegalStateException("账号服务未确认保存，请使用官方设置重试");
        }
    }
    private AccountAdHost(){}
}
