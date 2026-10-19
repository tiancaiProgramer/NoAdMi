import android.content.Context;
import android.content.pm.ApplicationInfo;
import dalvik.system.PathClassLoader;
import io.github.miclean.module.Rules;
import java.lang.reflect.*;
import java.util.*;

/** Read-only app_process probe: validates installed APK shapes without running app business code. */
public final class PlayHostProbe {
    private static int checks;
    private static Class<?> cls(ClassLoader loader, String name) throws Exception {
        return Class.forName(name, false, loader);
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    private static Method method(ClassLoader loader, String owner, String name,
            Class<?> result, boolean isStatic, Class<?>... args) throws Exception {
        Method method = cls(loader, owner).getDeclaredMethod(name, args);
        check(method.getReturnType() == result && Modifier.isStatic(method.getModifiers()) == isStatic,
            owner + "." + name);
        return method;
    }
    private static ClassLoader loader(Context context, String pkg) throws Exception {
        ApplicationInfo app = context.getPackageManager().getApplicationInfo(pkg, 0);
        StringBuilder paths = new StringBuilder(app.sourceDir);
        if (app.splitSourceDirs != null) for (String path : app.splitSourceDirs) paths.append(':').append(path);
        return new PathClassLoader(paths.toString(), app.nativeLibraryDir, PlayHostProbe.class.getClassLoader());
    }
    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            System.exit(1);
        });
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
        Context home = system.createPackageContext(Rules.HOME, Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader h = loader(system, Rules.HOME);
        method(h, "_m_j.l22", "OooOooo", boolean.class, true, Context.class, String.class, String.class, boolean.class);
        method(h, "com.xiaomi.smarthome.miio.page.SettingMainPageV2", "Oooo0", void.class, false,
            android.view.View.class, ArrayList.class, boolean.class);
        method(h, "com.xiaomi.smarthome.miio.page.SettingMainPageV2", "onViewCreated", void.class, false,
            android.view.View.class, android.os.Bundle.class);
        method(h, "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew", "OooO00o", void.class, false);
        method(h, "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew", "getSelectedTabIndex", int.class, false);
        method(h, "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew", "setCurrentItem", void.class, false, int.class);
        Class<?> descriptor = cls(h, "_m_j.hh6"), tabId = cls(h, "com.xiaomi.smarthome.tab.TabId");
        check(cls(h, "_m_j.s1e").getDeclaredField("o00O0O").getType() == descriptor, "Play tab descriptor field");
        check(descriptor.getDeclaredField("OooO00o").getType() == tabId && tabId.isEnum(), "Play TabId field");
        method(h, "_m_j.s1e", "setTabInfo", void.class, false, descriptor);
        for (int i = 0; i < Rules.HOME_PLAY_MINE_VIEWS.length; i++)
            check(Rules.HOME_PLAY_MINE_RESOURCE_NAMES[i].equals(home.getResources().getResourceEntryName(
                Rules.HOME_PLAY_MINE_VIEWS[i])), "Play menu " + Rules.HOME_MINE_IDS[i]);
        method(h, "_m_j.wq5", "OoooOO0", boolean.class, true);
        method(h, "com.xiaomi.smarthome.task.OooOO0", "OooO0o0", Object.class, false,
            cls(h, "androidx.fragment.app.FragmentActivity"), cls(h, "kotlin.coroutines.Continuation"));
        ClassLoader f = loader(system, Rules.HEALTH_PLAY);
        method(f, "com.xiaomi.fitness.login.ad.SplashAdPreference", "checkShowSplashAdEnable", boolean.class, false);
        method(f, "com.xiaomi.fitness.login.ad.SplashAdImpl", "checkRefreshSplashInfo", boolean.class, false, boolean.class);
        method(f, "com.xiaomi.fitness.login.ad.SplashAdImpl", "refreshSplashAdInfo", void.class, false, boolean.class);
        method(f, "com.xiaomi.fitness.homepage.HealthViewModel", "queryOperationalBannerList",
            cls(f, "androidx.lifecycle.LiveData"), false);
        check(cls(f, "androidx.lifecycle.MutableLiveData").getConstructor(Object.class) != null, "empty LiveData constructor");
        Class<?> request = cls(f, "com.fitness.banner.export.BannerRequestParamV2");
        method(f, "com.xiaomi.fitness.device.manager.ui.tab.DeviceTabV4ViewModel", "fillBannerList",
            void.class, false, List.class, request, boolean.class);
        method(f, "com.fitness.banner.export.BannerRequestParamV2", "getResourceIdList", List.class, false);
        method(f, "com.xiaomi.fitness.device.manager.ui.tab.DeviceTabV4ViewModel", "getBannerItems",
            cls(f, "androidx.databinding.ObservableArrayList"), false);
        for (String type : new String[]{"com.facebook.react.bridge.CatalystInstanceImpl", "com.facebook.yrn.cxx.bridge.YRNCatalystInstanceImpl"})
            method(f, type, "loadScriptFromFile", void.class, false, String.class, String.class, boolean.class);
        method(f, "com.xiaomi.fitness.login.utils.RegionUtil", "isInnerCN", boolean.class, true);
        method(f, "com.xiaomi.fitness.login.region.RegionSelectListActivity", "shouldShowMainLandSwitchDialog",
            boolean.class, false, cls(f, "com.xiaomi.fitness.account.api.bean.CountryInfo"));
        System.out.println("PASS: " + checks + " installed Play host shape checks; no business methods invoked");
    }
}
