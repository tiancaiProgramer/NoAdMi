package io.github.miclean.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;
import io.github.libxposed.service.XposedService;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.xmlpull.v1.XmlPullParser;

final class Settings {
    private static volatile SharedPreferences remote;
    private static volatile XposedService service;
    private static volatile String error;
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    static SharedPreferences local(Context context) {
        return context.getSharedPreferences("noadmi_settings", Context.MODE_PRIVATE);
    }
    static synchronized void prepareLocal(Context context) {
        SharedPreferences local = local(context);
        importPending(context, "noadmi-legacy-settings.xml", local);
        importPending(context, "noadmi-legacy-ui.xml", context.getSharedPreferences("miclean_ui", Context.MODE_PRIVATE));
    }
    private static void importPending(Context context, String name, SharedPreferences dest) {
        File input = new File(context.getFilesDir(), name);
        if (!input.isFile()) return;
        try (FileInputStream stream = new FileInputStream(input)) {
            if (input.length() > 65536) throw new IllegalArgumentException("settings file too large");
            XmlPullParser xml = Xml.newPullParser();
            xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xml.setInput(stream, "UTF-8");
            java.util.Map<String, Boolean> values = new java.util.HashMap<>();
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                if (!"map".equals(xml.getName()) && !"boolean".equals(xml.getName()))
                    throw new IllegalArgumentException("unsupported legacy settings element");
                if ("boolean".equals(xml.getName())) {
                    String key = xml.getAttributeValue(null, "name");
                    String value = xml.getAttributeValue(null, "value");
                    if (key == null || key.length() > 120 || values.size() >= 512
                            || !("true".equals(value) || "false".equals(value)))
                        throw new IllegalArgumentException("invalid legacy setting");
                    values.put(key, Boolean.valueOf(value));
                }
            }
            SharedPreferences.Editor edit = dest.edit();
            for (Map.Entry<String, Boolean> item : values.entrySet()) edit.putBoolean(item.getKey(), item.getValue());
            if (!edit.commit()) throw new IllegalStateException("migration write failed");
            if ("noadmi-legacy-settings.xml".equals(name))
                local(context).edit().putBoolean("pending_remote_import_v1", true).commit();
            if (!input.delete()) error = "旧设置已保留，待清理迁移副本";
        } catch (Exception e) { error = "旧设置导入失败，请保留备份后重试"; }
    }
    static synchronized void bind(Context context, XposedService bound) {
        try {
            if (bound.getApiVersion() < 102) throw new IllegalStateException("API 102 required");
            SharedPreferences prefs = bound.getRemotePreferences(Rules.PREFS);
            boolean importing = local(context).getBoolean("pending_remote_import_v1", false);
            if (importing || !prefs.getBoolean("modern_settings_ready_v1", false)) {
                SharedPreferences.Editor edit = prefs.edit();
                for (Map.Entry<String, Boolean> item : SettingsPlan.importValues(
                        local(context).getAll(), prefs.getAll(), importing).entrySet())
                    edit.putBoolean(item.getKey(), item.getValue());
                edit.putBoolean("modern_settings_ready_v1", true);
                if (!edit.commit()) throw new IllegalStateException("remote settings save failed");
                local(context).edit().remove("pending_remote_import_v1").commit();
            }
            service = bound;
            remote = prefs;
            backup(context);
        } catch (Exception e) {
            remote = null;
            service = null;
            error = "远程设置连接失败，请重新打开模块";
        }
        notifyChanged();
    }
    static synchronized void disconnect(XposedService dead) {
        if (service != dead) return;
        remote = null; service = null;
        notifyChanged();
    }
    static void backup(Context context) {
        SharedPreferences current = remote;
        if (current == null) return;
        SharedPreferences.Editor edit = local(context).edit();
        for (Map.Entry<String, ?> item : current.getAll().entrySet())
            if (item.getValue() instanceof Boolean) edit.putBoolean(item.getKey(), (Boolean) item.getValue());
        edit.commit();
    }
    static SharedPreferences current(Context context) { return remote != null ? remote : local(context); }
    static boolean ready() { return remote != null && service != null; }
    static String status() { return error; }
    static void listen(Runnable listener) { listeners.add(listener); }
    static void unlisten(Runnable listener) { listeners.remove(listener); }
    private static void notifyChanged() { for (Runnable listener : listeners) listener.run(); }
}
