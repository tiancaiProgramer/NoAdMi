package io.github.miclean.module;

import android.content.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional client-side Play region compatibility; never changes the saved region. */
final class RegionHooks {
    private RegionHooks() {}

    static void fitness(ClassLoader loader) {
        try {
            Class<?> country = Reflect.findClass("com.xiaomi.fitness.account.api.bean.CountryInfo", loader);
            final Method name = country.getDeclaredMethod("getName");
            if (name.getReturnType() != String.class) throw new NoSuchMethodException("country name");
            Method gate = Reflect.findClass(
                "com.xiaomi.fitness.login.region.RegionSelectListActivity", loader)
                .getDeclaredMethod("shouldShowMainLandSwitchDialog", country);
            require(gate, boolean.class, false);
            HookRuntime.hookMethod(gate, new MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void beforeHookedMethod(Param p) {
                    try {
                        if (p.args[0] == null || !RegionPolicy.mainland(name.invoke(p.args[0]))) return;
                        p.setResult(Boolean.FALSE);
                        hit(logged, "fitness.play.mainland-selection");
                    } catch (Throwable ignored) { /* Keep the host decision when inspection fails. */ }
                }
            });
            HookEntry.report("installed fitness.play.mainland-selection");
        } catch (Throwable e) { skip("fitness.play.mainland-selection", e); }

        try {
            Method startup = Reflect.findClass(RegionPolicy.FITNESS_STARTUP, loader)
                .getDeclaredMethod("invokeSuspend", Object.class);
            require(startup, Object.class, false);
            Method gate = Reflect.findClass(RegionPolicy.FITNESS_REGION_UTIL, loader)
                .getDeclaredMethod("isInnerCN");
            require(gate, boolean.class, true);
            HookRuntime.hookMethod(gate, new MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable() || !Boolean.TRUE.equals(p.getResult())) return;
                    if (!RegionPolicy.migrationRegionCheck(Thread.currentThread().getStackTrace())) return;
                    p.setResult(Boolean.FALSE);
                    hit(logged, "fitness.play.mainland-startup");
                }
            });
            HookEntry.report("installed fitness.play.mainland-startup");
        } catch (Throwable e) { skip("fitness.play.mainland-startup", e); }
    }

    static void home(ClassLoader loader) {
        try {
            Class<?> utility = Reflect.findClass("_m_j.wq5", loader);
            // The companion download URL builder is part of this method's shape.
            require(utility.getDeclaredMethod("OooOO0O", Context.class), String.class, true);
            Method prompt = utility.getDeclaredMethod("OoooOO0");
            require(prompt, boolean.class, true);
            HookRuntime.hookMethod(prompt, new MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void beforeHookedMethod(Param p) {
                    p.setResult(Boolean.FALSE);
                    hit(logged, "home.play.split-package-prompt");
                }
            });
            HookEntry.report("installed home.play.split-package-prompt");
        } catch (Throwable e) { skip("home.play.split-package-prompt", e); }

        try {
            Class<?> job = Reflect.findClass("com.xiaomi.smarthome.task.OooOO0", loader);
            Class<?> activity = Reflect.findClass("androidx.fragment.app.FragmentActivity", loader);
            Class<?> continuation = Reflect.findClass("kotlin.coroutines.Continuation", loader);
            Class<?> resume = Reflect.findClass(
                "com.xiaomi.smarthome.task.SplitePackageCheckJob$onStart$1", loader);
            // Check the dedicated coroutine's enclosing job field before hooking an
            // obfuscated class. Return the host's Unit so its task graph can continue.
            if (resume.getDeclaredField("this$0").getType() != job)
                throw new NoSuchFieldException("split-package job");
            Class<?> unit = Reflect.findClass("_m_j.wne", loader);
            final Object done = Reflect.getStaticObjectField(unit, "OooO00o");
            if (!unit.isInstance(done)) throw new IllegalStateException("Unit singleton");
            Method run = job.getDeclaredMethod("OooO0o0", activity, continuation);
            require(run, Object.class, false);
            HookRuntime.hookMethod(run, new MethodHook() {
                private final AtomicBoolean logged = new AtomicBoolean();
                @Override protected void beforeHookedMethod(Param p) {
                    p.setResult(done);
                    hit(logged, "home.play.split-package-check");
                }
            });
            HookEntry.report("installed home.play.split-package-check");
        } catch (Throwable e) { skip("home.play.split-package-check", e); }
    }

    private static void require(Method method, Class<?> result, boolean isStatic)
            throws NoSuchMethodException {
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers()) != isStatic)
            throw new NoSuchMethodException("method shape");
    }

    private static void hit(AtomicBoolean logged, String id) {
        if (logged.compareAndSet(false, true)) HookEntry.report("hit " + id);
    }

    private static void skip(String id, Throwable e) {
        HookEntry.report("skip " + id + ": " + e.getClass().getSimpleName());
    }
}
