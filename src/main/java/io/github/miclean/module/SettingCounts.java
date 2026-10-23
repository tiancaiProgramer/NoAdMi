package io.github.miclean.module;

/** Counts configuration switches, excluding items whose application is absent. */
final class SettingCounts {
    int enabled, available, unavailable;

    void include(boolean installed, boolean selected) {
        if (!installed) {
            unavailable++;
            return;
        }
        available++;
        if (selected) enabled++;
    }

    boolean allEnabled() { return available > 0 && enabled == available; }

    String state() {
        if (available == 0) return "无可用项目";
        if (enabled == 0) return "全部关闭";
        return allEnabled() ? "全部开启" : "部分开启";
    }

    String advertisingSummary() {
        return state() + " · 已启用广告隐藏 " + enabled + "/" + available + " 项" + unavailableSuffix();
    }

    String settingsSummary() {
        return "已启用设置 " + enabled + "/" + available + " 项" + unavailableSuffix();
    }

    private String unavailableSuffix() {
        return unavailable == 0 ? "" : "（另有 " + unavailable + " 项不可用）";
    }

    SettingCounts() {}
}
