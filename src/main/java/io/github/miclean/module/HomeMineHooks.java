package io.github.miclean.module;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the native My page's verified menu rows; native-hidden rows remain hidden. */
public final class HomeMineHooks {
    private static final String ITEM = "com.xiaomi.smarthome.miio.page.ItemOptionView";
    private HomeMineHooks() {}

    public static void install(ClassLoader loader, final Set<Integer> hidden) {
        try {
            final Map<View, Boolean> watched = new WeakHashMap<>();
            final AtomicBoolean logged = new AtomicBoolean();
            final AtomicBoolean resourceRejected = new AtomicBoolean();
            final AtomicBoolean lifecycle = new AtomicBoolean();
            java.util.function.Consumer<View> attach=root->{
                // Resolve each distribution independently: integer IDs overlap.
                final Set<Integer> selected;
                try {
                    Set<Integer> byTitle=new HashSet<>();
                    resolveRows(root,hidden,byTitle);
                    if(!byTitle.isEmpty()){
                        selected=byTitle;
                    } else if (matches(root, Rules.HOME_MINE_VIEWS, Rules.HOME_MINE_RESOURCE_NAMES)) {
                        selected = Rules.homeMineViews(hidden, false);
                    } else if (matches(root, Rules.HOME_PLAY_MINE_VIEWS, Rules.HOME_PLAY_MINE_RESOURCE_NAMES)) {
                        selected = Rules.homeMineViews(hidden, true);
                    } else {
                        // Obfuscated IDs change between builds. Restrict this fallback
                        // to this fragment's native ItemOptionView and exact menu title.
                        selected = new HashSet<>();
                        resolveRows(root,hidden,selected);
                        if(selected.isEmpty()){
                            if(resourceRejected.compareAndSet(false,true))
                                HookEntry.report("skip home.mine.items: no verified menu rows");
                            return;
                        }
                    }
                } catch (android.content.res.Resources.NotFoundException e) {
                    if (resourceRejected.compareAndSet(false, true))
                        HookEntry.report("skip home.mine.items: resources unavailable");
                    return;
                }
                if (watched.containsKey(root)) return;
                watched.put(root, Boolean.TRUE);
                Watcher watcher = new Watcher(root, selected, logged);
                root.addOnAttachStateChangeListener(watcher);
                watcher.observe();
                watcher.apply();
            };
            Reflect.findAndHookMethod("com.xiaomi.smarthome.miio.page.SettingMainPageV2",
                loader,"onViewCreated",View.class,Bundle.class,new MethodHook(){
                    @Override protected void afterHookedMethod(Param p){
                        if(!p.hasThrowable()&&p.args[0] instanceof View)attach.accept((View)p.args[0]);
                    }
                });
            Reflect.findAndHookMethod("com.xiaomi.smarthome.miio.page.SettingMainPageV2",
                loader,"onResume",new MethodHook(){
                    @Override protected void afterHookedMethod(Param p){
                        if(p.hasThrowable())return;
                        Object root=Reflect.getObjectField(p.thisObject,"mRootView");
                        if(lifecycle.compareAndSet(false,true))HookEntry.report("hit home.mine.resume root="+(root==null?"null":root.getClass().getName()));
                        if(root instanceof View)attach.accept((View)root);
                    }
                });
            HookEntry.report("installed home.mine.items lifecycle=view+resume count=" + hidden.size());
        } catch (Throwable e) {
            HookEntry.report("skip home.mine.items: " + e.getClass().getSimpleName());
        }
    }

    private static void resolveRows(View view,Set<Integer> hidden,Set<Integer> result){
        if(isItem(view)){
            for(int i=0;i<Rules.HOME_MINE_VIEWS.length;i++)
                if(hidden.contains(Rules.HOME_MINE_VIEWS[i])&&hasTitle(view,Rules.HOME_MINE_TITLES[i])&&view.getId()!=View.NO_ID)
                    result.add(view.getId());
            return;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)resolveRows(group.getChildAt(i),hidden,result);
        }
    }
    private static boolean hasTitle(View view,String title){
        if(view instanceof TextView&&title.contentEquals(((TextView)view).getText()))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(hasTitle(group.getChildAt(i),title))return true;
        }
        return false;
    }
    private static boolean isItem(View view){
        if(view==null)return false;
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass())
            if(ITEM.equals(type.getName()))return true;
        return false;
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
                    if (!isItem(row)) continue;
                    matched++;
                    if (row.getVisibility() != View.GONE) row.setVisibility(View.GONE);
                    ViewParent parent = row.getParent();
                    while (parent instanceof View && parent != root) {
                        View group = (View) parent;
                        if ("miuix.smooth.SmoothFrameLayout".equals(group.getClass().getName())
                                || "miuix.smooth.SmoothFrameLayout2".equals(group.getClass().getName())) {
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
            if (isItem(view)) return view.getVisibility() == View.VISIBLE;
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
