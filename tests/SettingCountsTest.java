package io.github.miclean.module;

public final class SettingCountsTest {
    static int checks;
    public static void main(String[] args) {
        // Exercise every partial/all/off combination for all five group sizes.
        for (int total : new int[]{2, 7, 2, 2, 4}) {
            for (int mask = 0; mask < (1 << total); mask++) {
                SettingCounts counts = new SettingCounts();
                for (int i = 0; i < total; i++) counts.include(true, (mask & (1 << i)) != 0);
                int selected = Integer.bitCount(mask);
                check(counts.available == total && counts.enabled == selected);
                check(counts.allEnabled() == (selected == total));
                String state = selected == 0 ? "全部关闭" : selected == total ? "全部开启" : "部分开启";
                check(counts.state().equals(state));
                check(counts.advertisingSummary().equals(state + " · 已启用广告隐藏 " + selected + "/" + total + " 项"));
                check(counts.settingsSummary().equals("已启用设置 " + selected + "/" + total + " 项"));
                // An unavailable item must not alter numerator, denominator or master state,
                // even if its retained preference is true.
                counts.include(false, true);
                counts.include(false, false);
                check(counts.available == total && counts.enabled == selected && counts.unavailable == 2);
                check(counts.state().equals(state) && counts.allEnabled() == (selected == total));
                check(counts.settingsSummary().endsWith("（另有 2 项不可用）"));
            }
        }
        SettingCounts absent = new SettingCounts();
        absent.include(false, true);
        check(!absent.allEnabled());
        check(absent.advertisingSummary().equals("无可用项目 · 已启用广告隐藏 0/0 项（另有 1 项不可用）"));
        // Current domestic-only health general section: three ads available,
        // retained Play compatibility excluded, irrespective of its value.
        SettingCounts health = new SettingCounts();
        health.include(false, true);
        health.include(true, true);
        health.include(true, false);
        health.include(true, true);
        check(health.settingsSummary().equals("已启用设置 2/3 项（另有 1 项不可用）"));
        System.out.println("PASS: " + checks + " group state, unavailable-item exclusion and shared counting assertions");
    }
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Setting counts " + checks); }
}
