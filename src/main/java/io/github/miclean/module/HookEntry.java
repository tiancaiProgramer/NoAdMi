package io.github.miclean.module;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "MiClean";
    private final AtomicBoolean installed = new AtomicBoolean();

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (Rules.MODULE.equals(lp.packageName)) {
            // Only an injected module process may report an active framework.
            XposedHelpers.findAndHookMethod("io.github.miclean.module.MainActivity", lp.classLoader,
                "getFrameworkApiVersion", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(XposedBridge.getXposedVersion());
                    }
                });
            return;
        }
        // Primary processes only: leave BLE, device plugins and background sync alone.
        if (!Rules.targetProcess(lp.packageName, lp.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!installed.compareAndSet(false, true)) return;
                try {
                    install((Context) p.args[0], lp.packageName, lp.classLoader);
                } catch (Throwable e) {
                    report("setup failed; original behavior retained: " + e.getClass().getSimpleName());
                }
            }
        });
    }

    private void install(Context context, String pkg, ClassLoader loader) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
        XSharedPreferences prefs = new XSharedPreferences(Rules.MODULE, Rules.PREFS);
        prefs.reload();
        if (!prefs.getFile().canRead()) {
            report("settings unavailable; open module after enabling LSPosed, then restart target");
            return;
        }
        if (Rules.HEALTH.equals(pkg) && prefs.getBoolean(Rules.HEALTH_SWITCH, true)) {
            // SplashActivity already goes to its normal destination when this returns false.
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdPreference",
                "checkShowSplashAdEnable", boolean.class, Boolean.FALSE);
            // Both callers belong exclusively to the splash advertisement subsystem.
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdImpl",
                "checkRefreshSplashInfo", boolean.class, Boolean.FALSE, boolean.class);
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdImpl",
                "refreshSplashAdInfo", void.class, null, boolean.class);
        }
        if (Rules.HEALTH.equals(pkg)) {
            if (prefs.getBoolean(Rules.HEALTH_HOME_BANNER_SWITCH, true)) healthHomeBanner(loader);
            if (prefs.getBoolean(Rules.HEALTH_DEVICE_BANNER_SWITCH, true)) healthDeviceBanners(loader);
            java.util.Set<String> hiddenMineEntries = new java.util.HashSet<>();
            for (String id : Rules.HEALTH_MINE_IDS) {
                if (prefs.getBoolean(Rules.healthMineKey(id), true)) hiddenMineEntries.add(id);
            }
            BundleHooks.install(context, loader,
                prefs.getBoolean(Rules.HEALTH_BANNER_SWITCH, true),
                prefs.getBoolean(Rules.HEALTH_MEMBER_SWITCH, true),
                prefs.getBoolean(Rules.HEALTH_SPORT_GROUP_SWITCH, true),
                prefs.getBoolean(Rules.HEALTH_SPORT_ACTIVITY_SWITCH, true),
                prefs.getBoolean(Rules.HEALTH_SPORT_BANNER_SWITCH, true), hiddenMineEntries);
        }
        if (Rules.HOME.equals(pkg)) {
            final boolean splash = prefs.getBoolean(Rules.HOME_SWITCH, true);
            final boolean banners = prefs.getBoolean(Rules.HOME_BANNER_SWITCH, true);
            // Preserve all other shared preferences. This particular file controls opening ads.
            try {
                Class<?> cls = XposedHelpers.findClass("_m_j.uf1", loader);
                Method m = cls.getDeclaredMethod("OoooOOO", Context.class, String.class,
                    String.class, boolean.class);
                if (m.getReturnType() != boolean.class) throw new NoSuchMethodException("return type");
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    private final AtomicBoolean splashLogged = new AtomicBoolean();
                    private final AtomicBoolean bannerLogged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (splash && Rules.blockHomePreference(p.args[1])) {
                            p.setResult(Boolean.FALSE);
                            if (splashLogged.compareAndSet(false, true)) report("hit home.splash.preference");
                        } else if (banners && Rules.HOME_BANNER_PREF.equals(p.args[1])) {
                            p.setResult(Boolean.FALSE);
                            if (bannerLogged.compareAndSet(false, true)) report("hit home.banner.preference");
                        }
                    }
                });
                report("installed home.preferences splash=" + splash + " banners=" + banners);
            } catch (Throwable e) {
                report("skip home.splash.preference: " + e.getClass().getSimpleName());
            }
            if (banners) homeBannerList(loader);
            boolean independent = prefs.getBoolean(Rules.HOME_INDEPENDENT_OPTIONS, false);
            {
                java.util.Set<String> hidden = new java.util.HashSet<>();
                for (String id : Rules.HOME_TAB_IDS) {
                    if (Rules.effectiveHomeOption(independent, prefs.getBoolean(Rules.HOME_TABS_ENABLED, true),
                            prefs.getBoolean(Rules.tabKey(id), Rules.productTab(id)))) hidden.add(id);
                }
                if (!hidden.isEmpty()) HomeTabHooks.install(loader, hidden);
            }
            {
                java.util.Set<Integer> hidden = new java.util.HashSet<>();
                for (int i = 0; i < Rules.HOME_MINE_IDS.length; i++) {
                    if (Rules.effectiveHomeOption(independent, prefs.getBoolean(Rules.HOME_MINE_ENABLED, false),
                            prefs.getBoolean(Rules.mineKey(Rules.HOME_MINE_IDS[i]), false))) hidden.add(Rules.HOME_MINE_VIEWS[i]);
                }
                if (!hidden.isEmpty()) HomeMineHooks.install(loader, hidden);
            }
        }
        report("configuration loaded: " + pkg + " " + info.versionName);
    }

    private static void constant(ClassLoader loader, String className, String methodName,
            Class<?> resultType, final Object result, Class<?>... parameters) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, loader);
            Method m = cls.getDeclaredMethod(methodName, parameters);
            if (m.getReturnType() != resultType) throw new NoSuchMethodException("return type");
            final String id = className + "." + methodName;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    p.setResult(result);
                    if (logged.compareAndSet(false, true)) report("hit " + id);
                }
            });
            report("installed " + id);
        } catch (Throwable e) {
            report("skip " + className + "." + methodName + ": " + e.getClass().getSimpleName());
        }
    }

    private static void healthDeviceBanners(ClassLoader loader) {
        try {
            Class<?> request = XposedHelpers.findClass("com.fitness.banner.export.BannerRequestParamV2", loader);
            XposedHelpers.findAndHookMethod(
                "com.xiaomi.fitness.device.manager.ui.tab.DeviceTabV4ViewModel", loader,
                "fillBannerList", java.util.List.class, request, boolean.class, new XC_MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            Object ids = XposedHelpers.callMethod(p.args[1], "getResourceIdList");
                            if (!(ids instanceof java.util.List)
                                    || !Rules.deviceBannerSlots((java.util.List<?>) ids)) return;
                            ((java.util.List<?>) p.args[0]).clear();
                            Object visible = XposedHelpers.callMethod(p.thisObject, "getBannerItems");
                            ((java.util.List<?>) visible).clear();
                            // Without the ad callback, the dedicated banner row is never inserted.
                            p.setResult(null);
                            if (logged.compareAndSet(false, true)) report("hit health.device.operational-banners");
                        } catch (Throwable e) {
                            report("skip health.device.operational-banners: " + e.getClass().getSimpleName());
                        }
                    }
                });
            report("installed health.device.operational-banners");
        } catch (Throwable e) {
            report("skip health.device.operational-banners: " + e.getClass().getSimpleName());
        }
    }

    private static void healthHomeBanner(ClassLoader loader) {
        try {
            final Class<?> liveData = XposedHelpers.findClass("androidx.lifecycle.MutableLiveData", loader);
            final java.lang.reflect.Constructor<?> constructor = liveData.getConstructor(Object.class);
            XposedHelpers.findAndHookMethod("com.xiaomi.fitness.homepage.HealthViewModel", loader,
                "queryOperationalBannerList", new XC_MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            // The page receives the ordinary empty-list state; no ad request starts.
                            Object empty = constructor.newInstance(Collections.emptyList());
                            p.setResult(empty);
                            if (logged.compareAndSet(false, true)) report("hit health.home.operational-banners");
                        } catch (Throwable e) {
                            report("skip health.home.operational-banners: " + e.getClass().getSimpleName());
                        }
                    }
                });
            report("installed health.home.operational-banners");
        } catch (Throwable e) {
            report("skip health.home.operational-banners: " + e.getClass().getSimpleName());
        }
    }

    private static void homeBannerList(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("com.xiaomi.smarthome.miio.page.SettingMainPageV2",
                loader, "Oooo000", android.view.View.class, ArrayList.class, boolean.class,
                new XC_MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        // Existing empty-data branch collapses the whole ad container.
                        p.args[1] = new ArrayList<Object>();
                        p.args[2] = Boolean.TRUE;
                        if (logged.compareAndSet(false, true)) report("hit home.mine.banner-list");
                    }
                });
            report("installed home.mine.banner-list");
        } catch (Throwable e) {
            report("skip home.mine.banner-list: " + e.getClass().getSimpleName());
        }
    }

    static void report(String text) {
        Log.i(TAG, text);
        XposedBridge.log("[" + TAG + "] " + text);
    }
}
