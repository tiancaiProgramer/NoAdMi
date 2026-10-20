package io.github.miclean.module;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hide native tab views while preserving all original pager and fragment indices. */
public final class HomeTabHooks {
    private HomeTabHooks() {}

    public static void install(ClassLoader loader, final Set<String> hidden) {
        try {
            Class<?> indicatorClass = Reflect.findClass(
                "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew", loader);
            Method refresh;
            String descriptorField;
            try {
                refresh = indicatorClass.getDeclaredMethod("notifyDataSetChanged");
                descriptorField = "tab";
            } catch (NoSuchMethodException ignored) {
                // Play 11.7.704 obfuscates the refresh method and TabView descriptor.
                Class<?> tab = Reflect.findClass("_m_j.s1e", loader);
                Class<?> descriptor = Reflect.findClass("_m_j.hh6", loader);
                Class<?> tabId = Reflect.findClass("com.xiaomi.smarthome.tab.TabId", loader);
                if (!tabId.isEnum() || tab.getDeclaredField("o00O0O").getType() != descriptor
                        || descriptor.getDeclaredField("OooO00o").getType() != tabId
                        || tab.getDeclaredMethod("setTabInfo", descriptor).getReturnType() != void.class)
                    throw new NoSuchMethodException("Play tab shape");
                refresh = indicatorClass.getDeclaredMethod("OooO00o");
                descriptorField = "o00O0O";
            }
            if (refresh.getReturnType() != void.class || Modifier.isStatic(refresh.getModifiers()))
                throw new NoSuchMethodException("tab refresh shape");
            final String field = descriptorField;
            HookRuntime.hookMethod(refresh, new MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void afterHookedMethod(Param p) {
                        if (p.hasThrowable() || !(p.thisObject instanceof ViewGroup)) return;
                        try {
                            ViewGroup indicator = (ViewGroup) p.thisObject;
                            List<String> original = new ArrayList<>();
                            for (int i = 0; i < indicator.getChildCount(); i++) {
                                Object descriptor = Reflect.getObjectField(indicator.getChildAt(i), field);
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
                            int selected = (Integer) Reflect.callMethod(indicator, "getSelectedTabIndex");
                            int target = Rules.visibleTabIndex(original, hidden, selected);
                            if (target != selected) Reflect.callMethod(indicator, "setCurrentItem", target);
                            if (logged.compareAndSet(false, true)) {
                                HookEntry.report("hit home.tabs.custom visible=" + retained + " nativeCount=" + original.size());
                            }
                        } catch (Throwable e) {
                            HookEntry.report("skip home.tabs.custom: " + e.getClass().getSimpleName());
                        }
                    }
                });
            HookEntry.report("installed home.tabs.custom native-indices refresh=" + refresh.getName());
        } catch (Throwable e) {
            HookEntry.report("skip home.tabs.custom: " + e.getClass().getSimpleName());
        }
    }

    private static String id(Object descriptor) {
        Object value = Reflect.getObjectField(descriptor, "OooO00o");
        if (!(value instanceof Enum<?>)) throw new IllegalStateException("Unknown tab descriptor");
        return ((Enum<?>) value).name();
    }
}
