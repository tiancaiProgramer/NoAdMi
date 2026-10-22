package io.github.miclean.module;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Rules are opt-in and restricted to these two system apps' main UI processes. */
final class SystemAdHooks {
    static void network(ClassLoader loader, SharedPreferences prefs) {
        boolean[] selected = new boolean[Rules.NETWORK_KEYS.length];
        boolean any = false;
        for (int i = 0; i < selected.length; i++) {
            selected[i] = prefs.getBoolean(Rules.NETWORK_KEYS[i], false);
            any |= selected[i];
        }
        if (!any) return;
        try {
            Reflect.findAndHookMethod("com.miui.networkassistant.ui.NetworkAssistantActivity", loader,
                "onCreate", Bundle.class, new MethodHook() {
                    @Override protected void afterHookedMethod(Param p) {
                        if (p.hasThrowable() || !(p.thisObject instanceof Activity)) return;
                        View root = ((Activity) p.thisObject).getWindow().getDecorView();
                        NetworkWatcher watcher = new NetworkWatcher(root, selected);
                        root.addOnAttachStateChangeListener(watcher);
                        watcher.observe();
                        watcher.apply();
                    }
                });
            HookEntry.report("installed system.network.items");
        } catch (Throwable e) { HookEntry.report("skip system.network.items: " + e.getClass().getSimpleName()); }
    }

    static void game(ClassLoader loader, SharedPreferences prefs) {
        if (prefs.getBoolean(Rules.GAME_RECOMMEND_SWITCH, false)) {
            try {
                // The page provider is private to GameBoosterRichWebActivity. Change only
                // its H5 promotion-layout hint; retain language, GPU, account and native region.
                Class<?> cls = Reflect.findClass("com.miui.gamebooster.GameBoosterRichWebActivity$13", loader);
                Class<?> contract = Reflect.findClass("com.miui.securityadd.richweb.bridge.IPageProvider", loader);
                if (!contract.isAssignableFrom(cls)) throw new NoSuchMethodException("page provider contract");
                Method method = cls.getDeclaredMethod("getConfiguration");
                if (method.getReturnType() != String.class) throw new NoSuchMethodException("configuration result");
                HookRuntime.hookMethod(method, new MethodHook() {
                    final AtomicBoolean logged = new AtomicBoolean();
                    @Override protected void afterHookedMethod(Param p) {
                        if (p.hasThrowable() || !(p.getResult() instanceof String)) return;
                        try {
                            JSONObject value = new JSONObject((String) p.getResult());
                            if (!(value.opt("is_international") instanceof Boolean)
                                    || !value.has("language") || !value.has("device") || !value.has("gpu_enable")) return;
                            value.put("is_international", true);
                            p.setResult(value.toString());
                            if (logged.compareAndSet(false, true)) HookEntry.report("hit system.game.promotion-layout");
                        } catch (Exception ignored) { /* Unknown page configuration stays intact. */ }
                    }
                });
                HookEntry.report("installed system.game.promotion-layout");
            } catch (Throwable e) { HookEntry.report("skip system.game.promotion-layout: " + e.getClass().getSimpleName()); }
            gameEmptyPromotion(loader);
        }
        if (prefs.getBoolean(Rules.GAME_POPUP_SWITCH, false)) {
            try {
                Class<?> cls = Reflect.findClass("com.miui.gamebooster.xunyou.XunYouGuideDialogUtils", loader);
                Method method = cls.getDeclaredMethod("isShowNewUserDialog");
                if (method.getReturnType() != boolean.class
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                    throw new NoSuchMethodException("new user condition contract");
                HookRuntime.hookMethod(method, new MethodHook() {
                        final AtomicBoolean logged = new AtomicBoolean();
                        @Override protected void beforeHookedMethod(Param p) {
                            p.setResult(false);
                            if (logged.compareAndSet(false, true)) HookEntry.report("hit system.game.xunyou-new-user");
                        }
                    });
                HookEntry.report("installed system.game.xunyou-new-user");
            } catch (Throwable e) { HookEntry.report("skip system.game.xunyou-new-user: " + e.getClass().getSimpleName()); }
        }
    }

    private static void gameEmptyPromotion(ClassLoader loader) {
        try {
            Reflect.findAndHookMethod("com.miui.gamebooster.GameBoosterRichWebActivity$10", loader,
                "onPageFinished", android.webkit.WebView.class, String.class, new MethodHook() {
                    @Override protected void afterHookedMethod(Param p) {
                        if (p.hasThrowable() || !(p.args[0] instanceof android.webkit.WebView)) return;
                        android.net.Uri uri = android.net.Uri.parse(String.valueOf(p.args[1]));
                        if (!"https".equals(uri.getScheme()) || !"gamespace.sec.miui.com".equals(uri.getHost())
                                || !("/".equals(uri.getPath()) || "".equals(uri.getPath()))) return;
                        android.webkit.WebView web = (android.webkit.WebView) p.args[0];
                        // Match the empty-game promotion's three unique siblings. No global
                        // DOM text scan, network filter, localStorage or settings mutation.
                        String script = "(()=>{if(window.__noadmiEmptyPromotion)return;"
                            + "window.__noadmiEmptyPromotion=true;const apply=()=>{"
                            + "for(const bg of document.querySelectorAll('.text-bg')){const p=bg.parentElement;"
                            + "if(!p||!p.querySelector('.get-width')||!p.querySelector('.mask')"
                            + "||!bg.querySelector('img[alt=\"text_bg\"],img[src*=\"text_bg.png\"]'))continue;"
                            + "p.style.display='none';}};apply();"
                            + "new MutationObserver(apply).observe(document.documentElement,{childList:true,subtree:true});})()";
                        web.evaluateJavascript(script, null);
                        HookEntry.report("applied system.game.empty-promotion observer");
                    }
                });
            HookEntry.report("installed system.game.empty-promotion");
        } catch (Throwable e) { HookEntry.report("skip system.game.empty-promotion: " + e.getClass().getSimpleName()); }
    }

    private static final class NetworkWatcher implements ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
        final View root;
        final boolean[] selected;
        final boolean[] logged = new boolean[Rules.NETWORK_KEYS.length];
        final Map<View, Integer> hidden = new HashMap<>();
        ViewTreeObserver observer;
        boolean applying;
        NetworkWatcher(View root, boolean[] selected) { this.root = root; this.selected = selected.clone(); }
        void observe() {
            if (!root.isAttachedToWindow()) return;
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = root.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
        }
        @Override public void onViewAttachedToWindow(View v) { observe(); apply(); }
        @Override public void onViewDetachedFromWindow(View v) {
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = null;
        }
        @Override public void onGlobalLayout() { apply(); }
        void apply() {
            if (applying) return;
            applying = true;
            try { visit(root); }
            catch (RuntimeException ignored) { /* Never interrupt traffic-management UI. */ }
            finally { applying = false; }
        }
        void visit(View view) {
            String name = view.getClass().getName();
            if (name.equals("com.miui.networkassistant.ui.view.MainToolbarItemView")
                    || name.equals("com.miui.networkassistant.ui.view.EnhancedToolbarItemView")
                    || name.equals("com.miui.networkassistant.ui.view.EnhancedBannerItemView")) {
                int id = view.getResources().getIdentifier("name", "id", Rules.SECURITY_CENTER);
                View title = id == 0 ? null : view.findViewById(id);
                int item = title instanceof TextView ? Rules.networkItem(((TextView) title).getText().toString()) : -1;
                if (item >= 0 && selected[item]) {
                    if (!hidden.containsKey(view)) hidden.put(view, view.getVisibility());
                    if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
                    if (!logged[item]) {
                        logged[item] = true;
                        HookEntry.report("hit " + Rules.NETWORK_KEYS[item]);
                    }
                } else if (hidden.containsKey(view)) {
                    view.setVisibility(hidden.remove(view));
                }
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i));
            }
        }
    }
    private SystemAdHooks() {}
}
