package io.github.miclean.module;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService / Root worker: one fixed operation, no caller-provided shell text. */
public final class AccessibilityControlService extends Binder {
    static final String DESCRIPTOR="io.github.miclean.module.AccessibilityControl";
    static final int SET=FIRST_CALL_TRANSACTION;
    private final int ownerUid;
    public AccessibilityControlService(Context context){
        if(!AccessibilityListPolicy.PACKAGE.equals(context.getPackageName()))throw new SecurityException("应用身份不匹配");
        ownerUid=context.getApplicationInfo().uid;
        attachInterface(null,DESCRIPTOR);
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        if(code==16777115){if(Binder.getCallingUid()!=ownerUid&&Binder.getCallingUid()!=Process.myUid())throw new SecurityException("无权停止服务");System.exit(0);return true;}
        if(Binder.getCallingUid()!=ownerUid)throw new SecurityException("仅允许 NoAdMi 调用");
        if(code!=SET)return super.onTransact(code,data,reply,flags);
        data.enforceInterface(DESCRIPTOR);
        try{boolean enabled=data.readInt()!=0;control(enabled,ownerUid/100000);reply.writeNoException();reply.writeInt(enabled?1:0);}
        catch(Exception error){reply.writeException(new IllegalStateException(error.getMessage(),error));}
        return true;
    }
    static void control(boolean enabled,int user)throws Exception{
        int uid=Process.myUid();if(uid!=0&&uid!=2000)throw new SecurityException("需要 Root 或 Shizuku 授权");
        if(!command("/system/bin/am","get-current-user").trim().equals(String.valueOf(user)))throw new SecurityException("请在当前用户空间操作");
        if(enabled&&android.os.Build.VERSION.SDK_INT>=33)
            command("/system/bin/cmd","appops","set","--user",String.valueOf(user),AccessibilityListPolicy.PACKAGE,"ACCESS_RESTRICTED_SETTINGS","allow");
        String before=command("/system/bin/settings","--user",String.valueOf(user),"get","secure","enabled_accessibility_services").trim();
        // A selected but disconnected component is not rebound by writing the same value.
        if(enabled&&AccessibilityListPolicy.enabled(before)){
            String detached=AccessibilityListPolicy.change(before,false);
            command("/system/bin/settings","--user",String.valueOf(user),"put","secure","enabled_accessibility_services",detached);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(true){
                Boolean selected=AccessibilityBindingPolicy.selected(command("/system/bin/dumpsys","accessibility"),user);
                if(Boolean.FALSE.equals(selected))break;
                if(selected==null){Thread.sleep(350);break;}
                if(System.nanoTime()>=deadline)throw new IllegalStateException("系统尚未完成 NoAdMi 服务断开，请重试");
                Thread.sleep(100);
            }
            // Read again so another service enabled in the meantime is retained too.
            before=command("/system/bin/settings","--user",String.valueOf(user),"get","secure","enabled_accessibility_services").trim();
        }
        String after=AccessibilityListPolicy.change(before,enabled);
        if(!after.equals(before))command("/system/bin/settings","--user",String.valueOf(user),"put","secure","enabled_accessibility_services",after);
        if(enabled)command("/system/bin/settings","--user",String.valueOf(user),"put","secure","accessibility_enabled","1");
        else if(after.isEmpty())command("/system/bin/settings","--user",String.valueOf(user),"put","secure","accessibility_enabled","0");
        String actual=command("/system/bin/settings","--user",String.valueOf(user),"get","secure","enabled_accessibility_services").trim();
        if(AccessibilityListPolicy.enabled(actual)!=enabled)throw new IllegalStateException("系统没有接受修改，请打开系统设置核对");
    }
    private static String command(String... args)throws Exception{
        java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{byte[] buffer=new byte[2048];int n;while((n=process.getInputStream().read(buffer))!=-1)if(out.size()+n<32768)out.write(buffer,0,n);}catch(Exception ignored){}});reader.start();
        try{if(!process.waitFor(12,TimeUnit.SECONDS))throw new IllegalStateException("系统操作超时");reader.join(1000);if(process.exitValue()!=0)throw new IllegalStateException("系统拒绝无障碍设置修改："+out.toString("UTF-8").trim());return out.toString("UTF-8");}
        finally{process.destroy();}
    }
    public static void main(String[] args){
        try{if(Process.myUid()!=0||args.length!=2||!(args[0].equals("0")||args[0].equals("1")))throw new IllegalArgumentException("参数错误");int user=Integer.parseInt(args[1]);if(user<0)throw new IllegalArgumentException("用户错误");control(args[0].equals("1"),user);System.out.println("NOADMI_ACCESS_OK");}
        catch(Exception error){System.err.println(error.getMessage());System.exit(1);}
    }
}
