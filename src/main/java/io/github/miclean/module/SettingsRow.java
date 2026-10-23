package io.github.miclean.module;

import android.content.Context;
import android.graphics.Color;
import android.widget.Switch;

/** Shared native row geometry for advertising masters and individual settings. */
final class SettingsRow {
    static void style(Context context, Switch toggle) {
        toggle.setTextSize(14);
        toggle.setTextColor(Color.rgb(36,44,40));
        int padding = UiSpacing.vertical(context, 12);
        toggle.setPadding(0, padding, 0, padding);
        toggle.setMinHeight(Math.round(52 * context.getResources().getDisplayMetrics().density));
    }
    private SettingsRow() {}
}
