package io.github.miclean.module;

/** Callback shape shared by existing page rules; execution uses libxposed Chain only. */
abstract class MethodHook {
    protected void beforeHookedMethod(Param param) throws Throwable {}
    protected void afterHookedMethod(Param param) throws Throwable {}
    static final class Param {
        final Object thisObject;
        final Object[] args;
        private Object result;
        private Throwable throwable;
        boolean stopped;
        Param(Object receiver, Object[] arguments) { thisObject = receiver; args = arguments; }
        void setResult(Object value) { result = value; throwable = null; stopped = true; }
        Object getResult() { return result; }
        Throwable getThrowable() { return throwable; }
        boolean hasThrowable() { return throwable != null; }
        void completed(Object value) { result = value; }
        void failed(Throwable error) { throwable = error; }
        Object finish() throws Throwable { if (throwable != null) throw throwable; return result; }
    }
}
