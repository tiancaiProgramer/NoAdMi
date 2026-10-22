package io.github.miclean.module;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedModule;
import android.content.pm.PackageInfo;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HookEntry extends XposedModule {
    private static final String TAG = "MiClean";
    private String processName;
    private final AtomicBoolean installed = new AtomicBoolean();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        HookRuntime.initialize(this);
    }

    @Override public void onPackageReady(final PackageReadyParam param) {
        final String pkg = param.getPackageName();
        if (!param.isFirstPackage() || !Rules.targetProcess(pkg, processName)) return;
        Reflect.findAndHookMethod(Application.class, "attach", Context.class, new MethodHook() {
            @Override protected void afterHookedMethod(Param p) {
                if (!installed.compareAndSet(false, true)) return;
                try {
                    install((Context) p.args[0], pkg, param.getClassLoader());
                } catch (Throwable e) {
                    report("setup failed; original behavior retained: " + e.getClass().getSimpleName());
                }
            }
        });
    }

    private void install(Context context, String pkg, ClassLoader loader) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
        if (Rules.ACCOUNT.equals(pkg)) {
            Class<?> applicationClass=Reflect.findClass(context.getApplicationInfo().className,loader);
            Method onCreate=applicationClass.getMethod("onCreate");
            HookRuntime.hookMethod(onCreate,new MethodHook(){
                @Override protected void afterHookedMethod(Param p){
                    try{AccountAdHost.install((Application)p.thisObject,loader);}
                    catch(Throwable error){report("skip account.ad.background: "+error.getClass().getSimpleName());}
                }
            });
            return;
        }
        SharedPreferences prefs = getRemotePreferences(Rules.PREFS);
        if (!prefs.getBoolean("modern_settings_ready_v1", false)) {
            report("remote settings not initialized; open NoAdMi then restart target");
            return;
        }
        if (Rules.SECURITY_CENTER.equals(pkg)) SystemAdHooks.network(loader, prefs);
        if (Rules.SECURITY_ADD.equals(pkg)) SystemAdHooks.game(loader, prefs);
        if (Rules.HEALTH_PLAY.equals(pkg) && prefs.getBoolean(Rules.HEALTH_REGION_SWITCH, false)) {
            RegionHooks.fitness(loader);
        }
        if (Rules.healthRules(pkg) && prefs.getBoolean(Rules.HEALTH_SWITCH, true)) {
            // SplashActivity already goes to its normal destination when this returns false.
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdPreference",
                "checkShowSplashAdEnable", boolean.class, Boolean.FALSE);
            // Both callers belong exclusively to the splash advertisement subsystem.
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdImpl",
                "checkRefreshSplashInfo", boolean.class, Boolean.FALSE, boolean.class);
            constant(loader, "com.xiaomi.fitness.login.ad.SplashAdImpl",
                "refreshSplashAdInfo", void.class, null, boolean.class);
        }
        if (Rules.healthRules(pkg)) {
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
            if (prefs.getBoolean(Rules.HOME_REGION_SWITCH, false)) RegionHooks.home(loader);
            final boolean splash = prefs.getBoolean(Rules.HOME_SWITCH, true);
            final boolean banners = prefs.getBoolean(Rules.HOME_BANNER_SWITCH, true);
            // Preserve all other shared preferences. This particular file controls opening ads.
            try {
                Method m = homeMethod(loader, new String[][]{{"_m_j.uf1", "OoooOOO"},
                    {"_m_j.l22", "OooOooo"}}, boolean.class, true,
                    Context.class, String.class, String.class, boolean.class);
                HookRuntime.hookMethod(m, new MethodHook() {
                    private final AtomicBoolean splashLogged = new AtomicBoolean();
                    private final AtomicBoolean bannerLogged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p) {
                        if (splash && Rules.blockHomePreference(p.args[1])) {
                            p.setResult(Boolean.FALSE);
                            if (splashLogged.compareAndSet(false, true)) report("hit home.splash.preference");
                        } else if (banners && Rules.HOME_BANNER_PREF.equals(p.args[1])) {
                            p.setResult(Boolean.FALSE);
                            if (bannerLogged.compareAndSet(false, true)) report("hit home.banner.preference");
                        }
                    }
                });
                report("installed home.preferences " + m.getDeclaringClass().getName() + "." + m.getName()
                    + " splash=" + splash + " banners=" + banners);
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
            Class<?> cls = Reflect.findClass(className, loader);
            Method m = cls.getDeclaredMethod(methodName, parameters);
            if (m.getReturnType() != resultType) throw new NoSuchMethodException("return type");
            final String id = className + "." + methodName;
            HookRuntime.hookMethod(m, new MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void beforeHookedMethod(Param p) {
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
            Class<?> request = Reflect.findClass("com.fitness.banner.export.BannerRequestParamV2", loader);
            Reflect.findAndHookMethod(
                "com.xiaomi.fitness.device.manager.ui.tab.DeviceTabV4ViewModel", loader,
                "fillBannerList", java.util.List.class, request, boolean.class, new MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p) {
                        try {
                            Object ids = Reflect.callMethod(p.args[1], "getResourceIdList");
                            if (!(ids instanceof java.util.List)
                                    || !Rules.deviceBannerSlots((java.util.List<?>) ids)) return;
                            ((java.util.List<?>) p.args[0]).clear();
                            Object visible = Reflect.callMethod(p.thisObject, "getBannerItems");
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
            final Class<?> liveData = Reflect.findClass("androidx.lifecycle.MutableLiveData", loader);
            final java.lang.reflect.Constructor<?> constructor = liveData.getConstructor(Object.class);
            Reflect.findAndHookMethod("com.xiaomi.fitness.homepage.HealthViewModel", loader,
                "queryOperationalBannerList", new MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p) {
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
            Method method = homeMethod(loader, new String[][]{
                {"com.xiaomi.smarthome.miio.page.SettingMainPageV2", "Oooo000"},
                {"com.xiaomi.smarthome.miio.page.SettingMainPageV2", "Oooo0"}},
                void.class, false, android.view.View.class, ArrayList.class, boolean.class);
            HookRuntime.hookMethod(method,
                new MethodHook() {
                    private final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p) {
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

    private static Method homeMethod(ClassLoader loader, String[][] candidates,
            Class<?> result, boolean isStatic, Class<?>... parameters) throws NoSuchMethodException {
        for (String[] candidate : candidates) {
            Class<?> cls = Reflect.findClassIfExists(candidate[0], loader);
            if (cls == null) continue;
            try {
                Method method = cls.getDeclaredMethod(candidate[1], parameters);
                if (method.getReturnType() == result
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers()) == isStatic)
                    return method;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("known home method shape");
    }

    static void report(String text) {
        Log.i(TAG, text);
        HookRuntime.log("[" + TAG + "] " + text);
    }
}
