package io.github.miclean.module;

import android.content.Context;

/** Uniform proportional vertical spacing in density-independent units; text keeps its sp size. */
final class UiSpacing {
    private static final float VERTICAL_SCALE = 0.75f;
    static int vertical(Context context, int baseDp) {
        return Math.round(baseDp * VERTICAL_SCALE * context.getResources().getDisplayMetrics().density);
    }
    private UiSpacing() {}
}
