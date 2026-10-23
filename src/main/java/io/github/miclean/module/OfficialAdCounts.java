package io.github.miclean.module;

/** Unknown official states must never be presented as successfully closed. */
final class OfficialAdCounts {
    int closed, enabled, unknown;
    void include(Boolean value) {
        if (value == null) unknown++;
        else if (value) enabled++;
        else closed++;
    }
    int total() { return closed + enabled + unknown; }
    boolean allClosed() { return total() > 0 && closed == total(); }
    String summary() {
        return "已关闭 " + closed + "/" + total() + " 项"
            + (enabled == 0 ? "" : " · 仍开启 " + enabled + " 项")
            + (unknown == 0 ? "" : " · 未确认 " + unknown + " 项")
            + (allClosed() ? " · 已完成" : "");
    }
}
