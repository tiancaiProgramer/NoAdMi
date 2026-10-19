package io.github.miclean.module;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Hide native tab views while preserving all original pager and fragment indices. */
public final class HomeTabHooks {
    private HomeTabHooks() {}

    public static void install(ClassLoader loader, final Set<String> hidden) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew", loader,
                "notifyDataSetChanged", new XC_MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable() || !(p.thisObject instanceof ViewGroup)) return;
                        try {
                            ViewGroup indicator = (ViewGroup) p.thisObject;
                            List<String> original = new ArrayList<>();
                            for (int i = 0; i < indicator.getChildCount(); i++) {
                                Object descriptor = XposedHelpers.getObjectField(indicator.getChildAt(i), "tab");
                                original.add(id(descriptor));
                            }
                            if (original.isEmpty()) return;
                            List<String> retained = Rules.retainedTabs(original, hidden);
                            // Includes the all-hidden fallback. Never create a missing native tab.
                            for (int i = 0; i < original.size(); i++) {
                                View tab = indicator.getChildAt(i);
                                int visibility = retained.contains(original.get(i)) ? View.VISIBLE : View.GONE;
                                if (tab.getVisibility() != visibility) tab.setVisibility(visibility);
                            }
                            int selected = (Integer) XposedHelpers.callMethod(indicator, "getSelectedTabIndex");
                            int target = Rules.visibleTabIndex(original, hidden, selected);
                            if (target != selected) XposedHelpers.callMethod(indicator, "setCurrentItem", target);
                            if (logged.compareAndSet(false, true)) {
                                HookEntry.report("hit home.tabs.custom visible=" + retained + " nativeCount=" + original.size());
                            }
                        } catch (Throwable e) {
                            HookEntry.report("skip home.tabs.custom: " + e.getClass().getSimpleName());
                        }
                    }
                });
            HookEntry.report("installed home.tabs.custom native-indices");
        } catch (Throwable e) {
            HookEntry.report("skip home.tabs.custom: " + e.getClass().getSimpleName());
        }
    }

    private static String id(Object descriptor) {
        Object value = XposedHelpers.getObjectField(descriptor, "OooO00o");
        if (!(value instanceof Enum<?>)) throw new IllegalStateException("Unknown tab descriptor");
        return ((Enum<?>) value).name();
    }
}
