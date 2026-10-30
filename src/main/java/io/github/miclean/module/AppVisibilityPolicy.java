package io.github.miclean.module;

/** GKD's all-apps page hides system overlays and system packages with no Activity. */
public final class AppVisibilityPolicy {
    public static boolean visible(boolean system,boolean overlay,boolean hasActivity){return !system||(!overlay&&hasActivity);}
    private AppVisibilityPolicy(){}
}
