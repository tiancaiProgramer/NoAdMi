package io.github.miclean.module;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Only the native My page's verified menu rows; native-hidden rows remain hidden. */
public final class HomeMineHooks {
    private static final String ITEM = "com.xiaomi.smarthome.miio.page.ItemOptionView";
    private HomeMineHooks() {}

    public static void install(ClassLoader loader, final Set<Integer> hidden) {
        try {
            final Map<View, Boolean> watched = new WeakHashMap<>();
            final AtomicBoolean logged = new AtomicBoolean();
            final AtomicBoolean resourceRejected = new AtomicBoolean();
            XposedHelpers.findAndHookMethod("com.xiaomi.smarthome.miio.page.SettingMainPageV2",
                loader, "onViewCreated", View.class, Bundle.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable() || !(p.args[0] instanceof View)) return;
                        View root = (View) p.args[0];
                        // Resolve each distribution independently: integer IDs overlap.
                        final boolean play;
                        try {
                            if (matches(root, Rules.HOME_MINE_VIEWS, Rules.HOME_MINE_RESOURCE_NAMES)) {
                                play = false;
                            } else if (matches(root, Rules.HOME_PLAY_MINE_VIEWS, Rules.HOME_PLAY_MINE_RESOURCE_NAMES)) {
                                play = true;
                            } else {
                                if (resourceRejected.compareAndSet(false, true))
                                    HookEntry.report("skip home.mine.items: resource map changed");
                                return;
                            }
                        } catch (android.content.res.Resources.NotFoundException e) {
                            if (resourceRejected.compareAndSet(false, true))
                                HookEntry.report("skip home.mine.items: resources unavailable");
                            return;
                        }
                        if (watched.containsKey(root)) return;
                        watched.put(root, Boolean.TRUE);
                        Watcher watcher = new Watcher(root, Rules.homeMineViews(hidden, play), logged);
                        root.addOnAttachStateChangeListener(watcher);
                        watcher.observe();
                        watcher.apply();
                    }
                });
            HookEntry.report("installed home.mine.items count=" + hidden.size());
        } catch (Throwable e) {
            HookEntry.report("skip home.mine.items: " + e.getClass().getSimpleName());
        }
    }

    private static boolean matches(View root, int[] ids, String[] names) {
        try {
            for (int i = 0; i < ids.length; i++) {
                if (!names[i].equals(root.getResources().getResourceEntryName(ids[i]))) return false;
            }
            return true;
        } catch (android.content.res.Resources.NotFoundException e) { return false; }
    }

    private static final class Watcher implements ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
        final View root;
        final Set<Integer> hidden;
        final AtomicBoolean logged;
        final Map<View, Integer> collapsed = new HashMap<>();
        ViewTreeObserver observer;
        boolean applying;
        Watcher(View root, Set<Integer> hidden, AtomicBoolean logged) {
            this.root = root;
            this.hidden = hidden;
            this.logged = logged;
        }
        void observe() {
            // Wait for attachment to avoid registering twice when a floating observer merges.
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
            try {
                int matched = 0;
                Set<View> groups = new HashSet<>();
                for (Integer id : hidden) {
                    View row = root.findViewById(id);
                    if (row == null || !ITEM.equals(row.getClass().getName())) continue;
                    matched++;
                    if (row.getVisibility() != View.GONE) row.setVisibility(View.GONE);
                    ViewParent parent = row.getParent();
                    while (parent instanceof View && parent != root) {
                        View group = (View) parent;
                        if ("miuix.smooth.SmoothFrameLayout".equals(group.getClass().getName())) {
                            groups.add(group);
                            break;
                        }
                        parent = group.getParent();
                    }
                }
                for (View group : groups) {
                    // Record only groups we collapse, so native visibility is never forced open.
                    if (!hasVisibleItem(group)) {
                        if (!collapsed.containsKey(group)) collapsed.put(group, group.getVisibility());
                        if (group.getVisibility() != View.GONE) group.setVisibility(View.GONE);
                    } else if (collapsed.containsKey(group)) {
                        int previous = collapsed.remove(group);
                        if (group.getVisibility() != previous) group.setVisibility(previous);
                    }
                }
                if (matched > 0 && logged.compareAndSet(false, true)) {
                    HookEntry.report("hit home.mine.items matched=" + matched);
                }
            } catch (Throwable e) {
                HookEntry.report("skip home.mine.items: " + e.getClass().getSimpleName());
            } finally { applying = false; }
        }
        boolean hasVisibleItem(View view) {
            if (ITEM.equals(view.getClass().getName())) return view.getVisibility() == View.VISIBLE;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    if (hasVisibleItem(group.getChildAt(i))) return true;
                }
            }
            return false;
        }
    }
}
