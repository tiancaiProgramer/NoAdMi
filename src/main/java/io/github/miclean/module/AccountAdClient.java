package io.github.miclean.module;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class AccountAdClient {
    private static long lastWake;
    static JSONObject read(Context context)throws Exception{
        try{return row(requestReady(context,"ping",null),false);}
        catch(Exception unavailable){
            JSONObject cached=NativeAdController.root(context,"account","read",null);
            JSONArray rows=cached.getJSONArray("results");
            for(int i=0;i<rows.length();i++)rows.getJSONObject(i).put("writable",false)
                .put("bridge_error","账号后台连接不可用；请勾选小米账号作用域并重启账号应用，或打开官方设置");
            return cached;
        }
    }
    static JSONObject write(Context context,boolean desired)throws Exception{
        return row(requestReady(context,"write",desired),true);
    }
    private static JSONObject row(Bundle result,boolean writing)throws Exception{
        if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error","账号后台操作失败"));
        if(writing&&!result.getBoolean("confirmed"))throw new IllegalStateException("账号服务未确认保存");
        JSONObject row=new JSONObject().put("id","system_tools").put("ok",true)
            .put("enabled",result.getBoolean("enabled")).put("writable",true)
            .put("confirmed",result.getBoolean("confirmed"));
        return new JSONObject().put("results",new JSONArray().put(row));
    }
    private static Bundle requestReady(Context context,String op,Boolean desired)throws Exception{
        if(Build.VERSION.SDK_INT<34)throw new IllegalStateException("账号后台操作需要 Android 14 或更高版本");
        PermissionInfo info=context.getPackageManager().getPermissionInfo(AccountAdProtocol.PERMISSION,0);
        if(!Rules.MODULE.equals(info.packageName)
                || (info.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_SIGNATURE)
            throw new SecurityException("control permission owner");
        // Ping before waking: do not repeat a potentially completed server write.
        try{send(context,"ping",null);}catch(Exception absent){
            synchronized(AccountAdClient.class){
                long now=android.os.SystemClock.elapsedRealtime();
                if(lastWake==0||now-lastWake>30000){
                    NativeAdController.root(context,"account","wake",null);lastWake=now;
                }
            }
            Exception last=absent;
            boolean ready=false;
            for(int i=0;i<5;i++){
                try{send(context,"ping",null);ready=true;break;}catch(Exception e){last=e;Thread.sleep(300);}
            }
            if(!ready)throw last;
        }
        return send(context,op,desired);
    }
    private static Bundle send(Context context,String op,Boolean desired)throws Exception{
        String nonce=UUID.randomUUID().toString().replace("-","");
        Intent intent=new Intent(AccountAdProtocol.ACTION).setPackage(Rules.ACCOUNT)
            .putExtra("op",op).putExtra("nonce",nonce);
        if(desired!=null)intent.putExtra("enabled",desired.booleanValue());
        CountDownLatch done=new CountDownLatch(1);Bundle[] answer={null};
        BroadcastReceiver result=new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent unused){
                if(getResultCode()==AccountAdProtocol.RESPONSE){
                    Bundle data=getResultExtras(false);
                    if(data!=null&&nonce.equals(data.getString("nonce")))answer[0]=data;
                }
                done.countDown();
            }
        };
        Bundle options=BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
        context.sendOrderedBroadcast(intent,null,options,result,new Handler(Looper.getMainLooper()),0,null,null);
        if(!done.await("write".equals(op)?45:4,TimeUnit.SECONDS))
            throw new IllegalStateException("账号操作超时，请刷新核对状态");
        if(answer[0]==null)throw new IllegalStateException("未连接账号后台作用域");
        return answer[0];
    }
    static void openOfficial(Activity activity){
        new Thread(()->{
            try{NativeAdController.root(activity.getApplicationContext(),"account","open",null);}
            catch(Exception error){activity.runOnUiThread(()->android.widget.Toast.makeText(activity,
                "未能打开账号设置，请检查 Root 授权",android.widget.Toast.LENGTH_LONG).show());}
        },"NoAdMi-account-settings").start();
    }
    private AccountAdClient(){}
}
