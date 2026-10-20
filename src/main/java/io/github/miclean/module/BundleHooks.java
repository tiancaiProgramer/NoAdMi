package io.github.miclean.module;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class BundleHooks {
    private BundleHooks() {}

    public static void install(final Context context, ClassLoader loader,
            final boolean banners, final boolean membership,
            final boolean sportGroups, final boolean sportActivities, final boolean sportBanner,
            final java.util.Set<String> hiddenMineEntries) {
        if (!banners && !membership && !sportGroups && !sportActivities && !sportBanner && hiddenMineEntries.isEmpty()) return;
        String[] classes = {"com.facebook.react.bridge.CatalystInstanceImpl",
            "com.facebook.yrn.cxx.bridge.YRNCatalystInstanceImpl"};
        for (String cls : classes) {
            try {
                Reflect.findAndHookMethod(cls, loader, "loadScriptFromFile",
                    String.class, String.class, boolean.class, new MethodHook() {
                        @Override protected void beforeHookedMethod(Param p) {
                            try {
                                File input = new File((String) p.args[0]);
                                if (!input.isFile() || input.length() > 12 * 1024 * 1024
                                        || input.getName().startsWith("miclean-patched-") || input.getName().startsWith("miclean-mine-")) return;
                                byte[] bytes = Files.readAllBytes(input.toPath());
                                String original = new String(bytes, StandardCharsets.UTF_8);
                                String mineAdsPatched = MineBundlePatch.patch(original, banners, membership);
                                String minePatched = MineEntriesPatch.patch(mineAdsPatched, hiddenMineEntries);
                                String patched = SportBundlePatch.patch(minePatched, sportGroups, sportActivities, sportBanner);
                                if (patched.equals(original)) return;
                                boolean sport = !patched.equals(minePatched);
                                File dir = new File(context.getCacheDir(), "miclean");
                                if (!dir.isDirectory() && !dir.mkdirs()) return;
                                byte[] digest = MessageDigest.getInstance("SHA-256")
                                    .digest(patched.getBytes(StandardCharsets.UTF_8));
                                StringBuilder hash = new StringBuilder();
                                for (int i = 0; i < 12; i++) hash.append(String.format("%02x", digest[i] & 255));
                                File output = new File(dir, "miclean-patched-" + hash + ".bundle");
                                synchronized (BundleHooks.class) {
                                    if (!output.isFile()) {
                                        File temporary = File.createTempFile("miclean-", ".tmp", dir);
                                        try {
                                            Files.write(temporary.toPath(), patched.getBytes(StandardCharsets.UTF_8));
                                            if (!temporary.renameTo(output)) return;
                                        } finally {
                                            temporary.delete();
                                        }
                                    }
                                }
                                // Keep original source URL and app bundle untouched.
                                p.args[0] = output.getAbsolutePath();
                                if (sport) HookEntry.report("hit health.sport.bundle groups=" + sportGroups
                                    + " activities=" + sportActivities + " banner=" + sportBanner);
                                else HookEntry.report("hit health.mine.bundle banners=" + banners
                                    + " membership=" + membership + " entries=" + hiddenMineEntries);
                            } catch (Throwable e) {
                                HookEntry.report("skip health.page.bundle: " + e.getClass().getSimpleName());
                            }
                        }
                    });
                HookEntry.report("installed health.page.loader " + cls);
            } catch (Throwable e) {
                HookEntry.report("skip health.page.loader " + cls + ": " + e.getClass().getSimpleName());
            }
        }
    }
}
