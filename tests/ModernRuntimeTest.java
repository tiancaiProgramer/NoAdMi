package io.github.miclean.module;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.*;

public final class ModernRuntimeTest {
    static int assertions;
    static final class Chain implements XposedInterface.Chain {
        Object[] args = {7};
        int calls;
        Throwable failure;
        Object receiver = new Object();
        @Override public Executable getExecutable() { return null; }
        @Override public Object getThisObject() { return receiver; }
        @Override public List<Object> getArgs() { return Arrays.asList(args); }
        @Override public Object getArg(int i) { return args[i]; }
        @Override public Object proceed() throws Throwable { return proceed(args); }
        @Override public Object proceed(Object[] values) throws Throwable {
            calls++;
            args = values;
            if (failure != null) throw failure;
            return (Integer) values[0] * 2;
        }
        @Override public Object proceedWith(Object obj) throws Throwable { throw new AssertionError(); }
        @Override public Object proceedWith(Object obj, Object[] args) throws Throwable { throw new AssertionError(); }
    }
    public static void main(String[] args) throws Throwable {
        Chain chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {}, chain).equals(14));
        check(chain.calls == 1);
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void beforeHookedMethod(Param p) { p.args[0] = 9; }
        }, chain).equals(18));
        check(chain.calls == 1 && chain.args[0].equals(9));
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void beforeHookedMethod(Param p) { p.setResult(false); }
        }, chain).equals(false));
        check(chain.calls == 0);
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void beforeHookedMethod(Param p) { p.setResult(null); }
        }, chain) == null);
        check(chain.calls == 0);
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void afterHookedMethod(Param p) { p.setResult(99); }
        }, chain).equals(99));
        check(chain.calls == 1);
        chain = new Chain();
        IllegalStateException original = new IllegalStateException("host");
        chain.failure = original;
        try { HookRuntime.intercept(new MethodHook() {}, chain); throw new AssertionError(); }
        catch (IllegalStateException e) { check(e == original); }
        check(chain.calls == 1);
        chain = new Chain();
        chain.failure = original;
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void afterHookedMethod(Param p) {
                check(p.hasThrowable()); p.setResult(4);
            }
        }, chain).equals(4));
        check(chain.calls == 1);
        chain = new Chain();
        final Object receiver = chain.receiver;
        HookRuntime.intercept(new MethodHook() {
            @Override protected void beforeHookedMethod(Param p) { check(p.thisObject == receiver); }
        }, chain);
        check(chain.calls == 1);
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void beforeHookedMethod(Param p) {
                p.args[0] = 123; throw new IllegalStateException("before");
            }
        }, chain, ignored -> {}).equals(14));
        check(chain.calls == 1 && chain.args[0].equals(7));
        chain = new Chain();
        check(HookRuntime.intercept(new MethodHook() {
            @Override protected void afterHookedMethod(Param p) {
                p.setResult(123); throw new IllegalStateException("after");
            }
        }, chain, ignored -> {}).equals(14));
        check(chain.calls == 1);
        chain = new Chain(); chain.failure = original;
        try {
            HookRuntime.intercept(new MethodHook() {
                @Override protected void afterHookedMethod(Param p) {
                    p.setResult(123); throw new IllegalStateException("after");
                }
            }, chain, ignored -> {});
            throw new AssertionError();
        } catch (IllegalStateException e) { check(e == original); }
        check(chain.calls == 1);
        List<Rules.TestedApp> sorted = Rules.testedAppsNewestFirst();
        check(sorted.size() == 14);
        check(sorted.get(0).testedRelease.equals("0.9.3"));
        check(sorted.get(10).label.contains("国内"));
        for (int i = 1; i < sorted.size(); i++) check(sorted.get(i - 1).testedBatch >= sorted.get(i).testedBatch);
        check(Rules.testedVersion(Rules.HOME, "11.8.705", 110817051L));
        check(Rules.testedVersion(Rules.SECURITY_ADD, "9.12.05-260130.0.1", 91205L));
        check(!Rules.testedVersion(Rules.SECURITY_ADD, "9.12.05-260130.0.1", 91206L));
        check(Rules.RELEASES_URL.equals("https://github.com/tiancaiProgramer/NoAdMi/releases"));
        check(!Rules.RELEASES_URL.contains("/latest")); // Includes preview releases as well as stable.
        System.out.println("PASS: " + assertions + " modern hook chain, skip/throw behavior, ordering and update URL assertions");
    }
    static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Modern assertion " + assertions); }
}
