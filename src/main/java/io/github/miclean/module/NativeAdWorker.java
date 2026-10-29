package io.github.miclean.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.os.Looper;
import android.os.Process;
import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Root entry with a fixed command table. No arbitrary paths, keys or components accepted. */
public final class NativeAdWorker {
    public static void main(String[] args) {
        JSONObject answer = new JSONObject();
        try {
            if (Process.myUid() != 0) throw new IllegalStateException("需要 Root 授权");
            if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("参数错误");
            String action = args[1];
            if (!action.equals("read") && !action.equals("write") && !action.equals("restore") && !action.equals("wake") && !action.equals("open"))
                throw new IllegalArgumentException("不支持的操作");
            boolean query=action.equals("read")||action.equals("wake")||action.equals("open");
            if ((action.equals("wake")||action.equals("open")) && !"account".equals(args[0]))
                throw new IllegalArgumentException("未知账号操作");
            if (query != (args.length == 2)) throw new IllegalArgumentException("参数错误");
            List<NativeAdRules.Entry> entries;
            NativeAdRules.Entry selected = null;
            if (query) entries = NativeAdRules.group(args[0]);
            else { selected = NativeAdRules.find(args[0]); entries = NativeAdRules.group(selected.group); }
            if (selected != null && selected.kind == NativeAdRules.ACCOUNT)
                throw new IllegalStateException("此项暂不支持后台修改，未打开任何页面");
            boolean desired = false;
            JSONObject restore = null;
            if (action.equals("write")) {
                if (!args[2].equals("true") && !args[2].equals("false")) throw new IllegalArgumentException("无效开关值");
                desired = Boolean.parseBoolean(args[2]);
            } else if (action.equals("restore")) {
                if (selected.kind != NativeAdRules.PREFS && selected.kind != NativeAdRules.MMKV && selected.kind != NativeAdRules.PERSONAL)
                    throw new IllegalArgumentException("此开关不支持配置快照恢复");
                restore = new JSONObject(args[2]);
                if (restore.length() != selected.keys.length) throw new IllegalArgumentException("无效快照");
                for (String key : selected.keys) {
                    Object value = restore.get(key);
                    boolean valid=selected.kind==NativeAdRules.PERSONAL
                        ?value instanceof String&&((String)value).matches("-?[0-9]{1,19}")
                        :value instanceof Boolean;
                    if (value != JSONObject.NULL && !valid) throw new IllegalArgumentException("无效快照值");
                }
            }
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
            // Refuse to act on another user's data through the user-0 system context.
            if (!command("am", "get-current-user").trim().equals("0"))
                throw new IllegalStateException("当前仅支持机主用户空间");
            NativeAdRules.Entry first = entries.get(0);
            int flags = first.kind == NativeAdRules.MMKV ? Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY : 0;
            Context target = system.createPackageContext(first.pkg, flags);
            // Weather's FBE preference wrapper explicitly uses device-protected storage on API 26+.
            if (first.group.equals("weather")) target = target.createDeviceProtectedStorageContext();
            if (!target.getApplicationInfo().enabled) throw new IllegalStateException("应用已停用");
            if (action.equals("wake") || action.equals("open")) {
                String component=action.equals("wake")
                    ? "com.xiaomi.account.authenticator.XmAuthenticationService"
                    : "com.xiaomi.account.settings.SystemAdActivity";
                android.content.ComponentName name=new android.content.ComponentName(Rules.ACCOUNT,component);
                boolean active=action.equals("wake")
                    ? target.getPackageManager().getServiceInfo(name,0).enabled
                    : target.getPackageManager().getActivityInfo(name,0).enabled;
                if (!active) throw new IllegalStateException("账号组件已停用");
                String result=action.equals("wake")
                    ? command("am","startservice","--user","0","-n",name.flattenToString())
                    : command("am","start","--user","0","-W","-n",name.flattenToString());
                if(result.contains("Error") || result.contains("Exception"))
                    throw new IllegalStateException("账号组件未能启动");
                System.out.println("NOADMI_NATIVE=" + new JSONObject().put("started",true));
                System.exit(0);
                return;
            }
            JSONArray results = new JSONArray();
            if (first.kind == NativeAdRules.PREFS || first.kind == NativeAdRules.MMKV) {
                if (selected != null) {
                    schema(target, selected);
                    // The stopped host cannot overwrite the fresh persisted preferences.
                    command("am", "force-stop", "--user", "0", first.pkg);
                }
                ClassLoader loader = target.getClassLoader();
                int uid = target.getApplicationInfo().uid;
                Os.setgid(uid); Os.setuid(uid);
                File data = target.getDataDir();
                if (!data.isDirectory() || !data.canRead() || !data.canWrite())
                    throw new IllegalStateException("应用目录权限不可用，未读取默认值");
                SharedPreferences prefs = first.kind == NativeAdRules.MMKV
                    ? mmkv(target, loader) : target.getSharedPreferences(first.file, Context.MODE_PRIVATE);
                for (NativeAdRules.Entry e : entries) {
                    if (selected != null && e != selected) continue;
                    try {
                        schema(target, e);
                        boolean before = enabled(prefs, e);
                        if (selected != null) {
                            SharedPreferences.Editor edit = prefs.edit();
                            for (String key : e.keys) {
                                if (restore != null && restore.isNull(key)) edit.remove(key);
                                else edit.putBoolean(key, restore == null ? desired : restore.getBoolean(key));
                            }
                            if (!edit.commit()) throw new IllegalStateException("应用配置保存失败");
                        }
                        JSONObject row = state(prefs, e);
                        if (selected != null && restore == null && row.getBoolean("enabled") != desired)
                            throw new IllegalStateException("回读校验失败，未确认成功");
                        row.put("changed", selected != null && before != row.getBoolean("enabled"));
                        results.put(row);
                    } catch (Throwable error) { results.put(failed(e.id, error)); }
                }
            } else {
                NativeAdRules.Entry e = first;
                try {
                    schema(target, e);
                    boolean before = specialRead(system, e);
                    if (restore != null) {
                        for(String key:e.keys){
                            if(restore.isNull(key))command("settings","delete","global",key);
                            else command("settings","put","global",key,restore.getString(key));
                        }
                    } else if (selected != null && before != desired) {
                        command("settings","put","global","personalized_ad_enabled",String.valueOf(NativeAdRules.personalizedBits(personalBits(),desired)));
                        command("settings","put","global","personalized_ad_time",String.valueOf(System.currentTimeMillis()));
                    }
                    boolean after = specialRead(system, e);
                    if (selected != null && restore==null && after != desired) throw new IllegalStateException("官方设置未确认成功");
                    JSONObject row=new JSONObject().put("id", e.id).put("ok", true).put("enabled", after)
                        .put("changed", selected != null && before != after);
                    if(e.kind==NativeAdRules.PERSONAL){
                        JSONObject snapshot=new JSONObject();
                        for(String key:e.keys){String value=command("settings","get","global",key).trim();
                            snapshot.put(key,"null".equals(value)?JSONObject.NULL:value);}
                        row.put("values",snapshot);
                    }
                    results.put(row);
                } catch (Throwable error) { results.put(failed(e.id, error)); }
            }
            answer.put("results", results);
        } catch (Throwable error) {
            try { answer.put("error", message(error)); } catch (Exception ignored) { }
        }
        System.out.println("NOADMI_NATIVE=" + answer.toString());
        // ActivityThread's Binder/Looper workers must not keep this short-lived helper alive.
        System.exit(0);
    }

    private static boolean enabled(SharedPreferences prefs, NativeAdRules.Entry e) {
        boolean result = true;
        for (String key : e.keys) result &= prefs.getBoolean(key, true);
        return result;
    }
    private static JSONObject state(SharedPreferences prefs, NativeAdRules.Entry e) throws Exception {
        JSONObject values = new JSONObject();
        for (String key : e.keys) values.put(key, prefs.contains(key) ? prefs.getBoolean(key, true) : JSONObject.NULL);
        return new JSONObject().put("id", e.id).put("ok", true).put("enabled", enabled(prefs, e)).put("values", values);
    }
    private static SharedPreferences mmkv(Context target, ClassLoader loader) throws Exception {
        Class<?> type = Class.forName("com.tencent.mmkv.MMKV", false, loader);
        if (!SharedPreferences.class.isAssignableFrom(type)) throw new IllegalStateException("浏览器配置结构变化");
        Method init = method(type, new String[]{"initialize", "o"}, String.class, Context.class);
        Method open = method(type, new String[]{"mmkvWithID", "t"}, type, String.class, int.class);
        init.invoke(null, target);
        return (SharedPreferences) open.invoke(null, "kv_prefs", 4);
    }
    private static Method method(Class<?> type, String[] names, Class<?> result, Class<?>... args) throws Exception {
        for (String name : names) try {
            Method method = type.getDeclaredMethod(name, args);
            if (method.getReturnType() == result && Modifier.isStatic(method.getModifiers())) return method;
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException("官方配置接口变化");
    }
    private static boolean specialRead(Context system, NativeAdRules.Entry e) throws Exception {
        if (e.kind == NativeAdRules.ACCOUNT) {
            String value = command("settings", "get", "global", "passport_ad_status").trim();
            if (!"ON".equals(value) && !"OFF".equals(value))
                throw new IllegalStateException("账号广告状态未初始化，暂无法读取");
            return "ON".equals(value);
        }
        return (personalBits()&1)!=0;
    }
    private static int personalBits()throws Exception{
        // MiuiSettings.Ad uses Global.getInt(key, 1). Use its verified default
        // and preserve the other bits. cmd settings avoids attributing this Root
        // subprocess to the system Context's different UID on Android 16.
        String value=command("settings","get","global","personalized_ad_enabled").trim();
        if("null".equals(value))return 1;
        if(!value.matches("-?[0-9]{1,10}"))throw new IllegalStateException("系统广告配置格式未识别，保留原状态");
        return Integer.parseInt(value);
    }
    private static void schema(Context target, NativeAdRules.Entry e) throws Exception {
        int id = target.getResources().getIdentifier(e.xml, "xml", e.pkg);
        if (id == 0) {
            if ("weather".equals(e.group) && flutterWeather(target,e)) return;
            throw new IllegalStateException("此应用版本未识别到官方开关");
        }
        try (XmlResourceParser xml = target.getResources().getXml(id)) {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                for (int i = 0; i < xml.getAttributeCount(); i++) {
                    if (!"key".equals(xml.getAttributeName(i))) continue;
                    int resource = xml.getAttributeResourceValue(i, 0);
                    String key = resource == 0 ? xml.getAttributeValue(i) : target.getString(resource);
                    if (e.uiKey.equals(key)) return;
                }
            }
        }
        throw new IllegalStateException("未匹配官方开关，保留原状态");
    }

    private static boolean flutterWeather(Context target,NativeAdRules.Entry e)throws Exception {
        // Flutter weather removed its preference XML. Its platform channel still
        // uses the same device-protected file and boolean keys. Require both the
        // compiled Dart declarations and existing typed values; never invent defaults.
        if(!new File(target.getDataDir(),"shared_prefs/"+e.file+".xml").isFile())return false;
        SharedPreferences prefs=target.getSharedPreferences(e.file,Context.MODE_PRIVATE);
        for(String key:e.keys)if(!(prefs.getAll().get(key) instanceof Boolean))return false;
        try(java.util.zip.ZipFile apk=new java.util.zip.ZipFile(target.getApplicationInfo().sourceDir)){
            for(String abi:android.os.Build.SUPPORTED_ABIS){
                java.util.zip.ZipEntry entry=apk.getEntry("lib/"+abi+"/libapp.so");
                if(entry==null)continue;
                if(entry.getSize()<=0||entry.getSize()>64*1024*1024)return false;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(java.io.InputStream in=apk.getInputStream(entry)){
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){if(bytes.size()+n>64*1024*1024)return false;bytes.write(buffer,0,n);}
                }
                String code=bytes.toString("ISO-8859-1");
                if(!code.contains("hyperos/shared_preferences")||!code.contains(e.file))return false;
                for(String key:e.keys)if(!code.contains(key))return false;
                return true;
            }
        }
        return false;
    }

    private static String command(String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        java.lang.Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try { byte[] buffer = new byte[2048]; int count;
                while ((count = process.getInputStream().read(buffer)) != -1)
                    if (output.size() + count <= 32768) output.write(buffer, 0, count);
            } catch (Exception ignored) { }
        });
        reader.start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroy(); throw new IllegalStateException("原生操作超时"); }
        reader.join(1000);
        String value = output.toString("UTF-8");
        if (process.exitValue() != 0) throw new IllegalStateException("原生操作未成功");
        return value;
    }
    private static JSONObject failed(String id, Throwable error) throws Exception {
        return new JSONObject().put("id", id).put("ok", false).put("error", message(error));
    }
    private static String message(Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null) error = error.getCause();
        if (error instanceof IllegalStateException || error instanceof IllegalArgumentException) return String.valueOf(error.getMessage());
        return "应用配置或权限未匹配（" + error.getClass().getSimpleName() + "）";
    }
}
