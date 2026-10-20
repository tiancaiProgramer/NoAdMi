package io.github.miclean.module;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

final class HookRuntime {
    private static XposedInterface api;
    static void initialize(XposedInterface framework) { api = framework; }
    static void hookMethod(Method method, final MethodHook hook) {
        method.setAccessible(true);
        api.hook(method).intercept(chain -> intercept(hook, chain));
    }
    static Object intercept(MethodHook hook, XposedInterface.Chain chain) throws Throwable {
        return intercept(hook, chain, HookEntry::report);
    }
    static Object intercept(MethodHook hook, XposedInterface.Chain chain,
            java.util.function.Consumer<String> report) throws Throwable {
        MethodHook.Param p = new MethodHook.Param(chain.getThisObject(), chain.getArgs().toArray());
        Object[] original = p.args.clone();
        try { hook.beforeHookedMethod(p); }
        catch (Throwable error) {
            report.accept("rule callback failed before; original call retained: " + error.getClass().getSimpleName());
            return chain.proceed(original);
        }
        if (!p.stopped) {
            try { p.completed(chain.proceed(p.args)); }
            catch (Throwable error) { p.failed(error); }
        }
        Object priorResult = p.getResult();
        Throwable priorError = p.getThrowable();
        try { hook.afterHookedMethod(p); }
        catch (Throwable error) {
            report.accept("rule callback failed after: " + error.getClass().getSimpleName());
            // Never invoke the host twice after an after-callback failure.
            if (priorError != null) throw priorError;
            return priorResult;
        }
        return p.finish();
    }
    static void log(String message) { if (api != null) api.log(android.util.Log.INFO, "MiClean", message); }
}
