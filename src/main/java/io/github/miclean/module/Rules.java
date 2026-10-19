package io.github.miclean.module;

/** Exact versions extracted from the user's device; unknown builds are left alone. */
public final class Rules {
    public static final String MODULE = "io.github.miclean.module";
    public static final String PREFS = "miclean";
    public static final String HEALTH = "com.mi.health";
    public static final String HOME = "com.xiaomi.smarthome";
    public static final long HEALTH_BUILD = 359001L;
    public static final long HOME_BUILD = 110817051L;
    public static final String HOME_SPLASH_PREF = "switch_manger_open_screen_cache_data";
    public static final String HEALTH_SWITCH = "health_splash";
    public static final String HOME_SWITCH = "home_splash";
    public static final String HOME_BANNER_SWITCH = "home_banner";
    public static final String HOME_PRODUCT_TAB_SWITCH = "home_product_tab";
    public static final String HEALTH_BANNER_SWITCH = "health_banner";
    public static final String HEALTH_HOME_BANNER_SWITCH = "health_home_banner";
    public static final String HEALTH_DEVICE_BANNER_SWITCH = "health_device_banner";
    public static final String HEALTH_SPORT_GROUP_SWITCH = "health_sport_group";
    public static final String HEALTH_SPORT_ACTIVITY_SWITCH = "health_sport_activity";
    public static final String HEALTH_SPORT_BANNER_SWITCH = "health_sport_banner";
    public static final String HEALTH_MEMBER_SWITCH = "health_member_promo";
    public static final String[] HEALTH_MINE_IDS = {"activity", "course", "habit", "weekly", "competition", "friend", "order", "assets"};
    public static final String[] HEALTH_MINE_TITLES = {"我的活动", "我的课程", "小习惯", "周报", "活力竞赛", "我的亲友", "我的订单", "我的资产"};
    public static String healthMineKey(String id) { return "health_mine_hide_" + id; }
    public static final String HOME_BANNER_PREF = "switch_manger_banner_cache_data";

    public static final String HOME_TABS_ENABLED = "home_tabs_enabled";
    public static final String HOME_MINE_ENABLED = "home_mine_enabled";
    public static final String HOME_INDEPENDENT_OPTIONS = "home_independent_options_v1";
    // Before first settings-page migration, preserve the old master's effective state.
    public static boolean effectiveHomeOption(boolean migrated, boolean legacyEnabled, boolean selected) {
        return selected && (migrated || legacyEnabled);
    }
    // Stable keys also make a product-only upgrade retain its previous setting.
    public static final String[] HOME_TAB_IDS = {"DeviceMain", "Scene", "Brand", "ShopYouPin", "Setting"};
    public static final String[] HOME_TAB_TITLES = {"米家", "智能", "产品", "商城", "我的"};
    public static final String[] HOME_MINE_IDS = {"device_consumables", "voice_control", "voice_assistant",
        "multi_device_management", "smart_home_central_control", "home_network", "hub_and_gateway",
        "third_party_platforms", "more_settings", "mall_center", "whole_home_smart", "xiaomi_services", "help_feedback"};
    public static final String[] HOME_MINE_TITLES = {"设备耗材", "语音控制", "语音助手", "多端管理", "智能家庭中控",
        "家庭网络", "中枢与网关", "连接三方平台", "更多设置", "商城中心", "全屋智能", "小米服务", "帮助与反馈"};
    // Verified against this exact host's layout resources, including native-hidden entries.
    public static final int[] HOME_MINE_VIEWS = {0x7f0b1600, 0x7f0b162a, 0x7f0b1629, 0x7f0b1613,
        0x7f0b1616, 0x7f0b1608, 0x7f0b15fa, 0x7f0b1620, 0x7f0b15f4, 0x7f0b161f,
        0x7f0b1610, 0x7f0b1612, 0x7f0b1609};

    public static String tabKey(String id) {
        return productTab(id) ? HOME_PRODUCT_TAB_SWITCH : "home_hide_tab_" + id;
    }
    public static String mineKey(String id) { return "home_hide_mine_" + id; }
    public static boolean hiddenTab(java.util.Set<String> hidden, String id) {
        // A single Mall option covers the two regional variants, without adding absent tabs.
        return hidden.contains("ShopYouPin".equals(id) || "ShopGlobal".equals(id) ? "ShopYouPin" : id);
    }
    public static java.util.List<String> retainedTabs(java.util.List<String> original, java.util.Set<String> hidden) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String id : original) if (!hiddenTab(hidden, id)) result.add(id);
        // Never leave a navigator empty; retain the host's original list in that case.
        return result.isEmpty() ? new java.util.ArrayList<>(original) : result;
    }

    private Rules() {}

    public static boolean supported(String pkg, long version) {
        return (HEALTH.equals(pkg) && version == HEALTH_BUILD)
            || (HOME.equals(pkg) && version == HOME_BUILD);
    }

    public static boolean target(String pkg) {
        return HEALTH.equals(pkg) || HOME.equals(pkg);
    }

    public static boolean blockHomePreference(Object fileName) {
        return HOME_SPLASH_PREF.equals(fileName);
    }

    public static boolean deviceBannerSlots(java.util.List<?> ids) {
        if (ids == null || ids.isEmpty()) return false;
        for (Object id : ids) {
            if (!Integer.valueOf(11).equals(id) && !Integer.valueOf(12).equals(id)) return false;
        }
        return true;
    }

    public static boolean productTab(String id) {
        return "Brand".equals(id);
    }

    public static int visibleTabIndex(java.util.List<String> original, java.util.Set<String> hidden, int selected) {
        java.util.List<String> visible = retainedTabs(original, hidden);
        if (selected >= 0 && selected < original.size() && visible.contains(original.get(selected))) return selected;
        String first = visible.contains("DeviceMain") ? "DeviceMain" : visible.isEmpty() ? null : visible.get(0);
        int index = original.indexOf(first);
        return index >= 0 ? index : 0;
    }
}
