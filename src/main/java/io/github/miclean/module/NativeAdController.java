package io.github.miclean.module;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class NativeAdController {
    static JSONObject read(Context context, String group) throws Exception {
        NativeAdRules.group(group);
        if ("account".equals(group)) return AccountAdClient.read(context);
        return root(context, group, "read", null);
    }
    static JSONObject write(Context context, String id, boolean enabled) throws Exception {
        NativeAdRules.find(id);
        if ("system_tools".equals(id)) return AccountAdClient.write(context, enabled);
        return root(context, id, "write", String.valueOf(enabled));
    }
    static JSONObject root(Context context, String id, String action, String value) throws Exception {
        String command = "CLASSPATH=" + quote(context.getApplicationInfo().sourceDir)
            + " app_process /system/bin " + NativeAdWorker.class.getName() + " " + id + " " + action
            + (value == null ? "" : " " + value);
        // Leave the caller's per-app data mount view only in this authorized Root helper.
        java.lang.Process process = new ProcessBuilder("su", "-M", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try { byte[] buffer = new byte[2048]; int count;
                while ((count = process.getInputStream().read(buffer)) != -1)
                    if (output.size() + count < 65536) output.write(buffer, 0, count);
            } catch (Exception ignored) { }
        }, "NoAdMi-native-output");
        reader.start();
        try {
            if (!process.waitFor(55, TimeUnit.SECONDS)) throw new IllegalStateException("操作超时，请刷新核对实际状态");
            reader.join(1000);
            String[] lines = output.toString("UTF-8").split("\\r?\\n");
            JSONObject result = null;
            for (String line : lines) if (line.startsWith("NOADMI_NATIVE=")) {
                if (result != null) throw new IllegalStateException("返回状态异常");
                result = new JSONObject(line.substring("NOADMI_NATIVE=".length()));
            }
            if (result == null || process.exitValue() != 0) throw new IllegalStateException("未取得 Root 权限或原生接口不可用");
            if (result.has("error")) throw new IllegalStateException(result.getString("error"));
            return result;
        } finally { process.destroy(); }
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private NativeAdController() {}
}
