package io.github.miclean.module;

import java.util.ArrayList;
import java.util.List;

/** Fixed official toggle definitions, independently checked against native settings code. */
final class NativeAdRules {
    static final int PREFS = 0, PERSONAL = 1, ACCOUNT = 2, MMKV = 3;
    static final class Entry {
        final String id, group, pkg, title, file, xml, uiKey;
        final String[] keys;
        final int kind;
        Entry(String id, String group, String pkg, String title, String file, String xml,
                String uiKey, int kind, String... keys) {
            this.id=id; this.group=group; this.pkg=pkg; this.title=title; this.file=file;
            this.xml=xml; this.uiKey=uiKey; this.kind=kind; this.keys=keys;
        }
    }
    static final Entry[] ENTRIES = {
        new Entry("system_tools", "account", "com.xiaomi.account", "系统工具广告", "",
            "account_settings_system_ad_preference", "pref_system_ad_checkbox", ACCOUNT, "passport_ad_status"),
        new Entry("system_personal", "system", "com.android.settings", "系统 · 个性化广告推荐", "",
            "ad_service_settings", "personalized_ad_switch", PERSONAL, "personalized_ad_enabled", "personalized_ad_time"),
        new Entry("weather_content", "weather", "com.miui.weather2", "天气 · 内容推广", "com.miui.weather2.information",
            "preference_weather_experinece_plan", "key_information_settings", PREFS, "agree_to_have_information"),
        new Entry("weather_video", "weather", "com.miui.weather2", "天气 · 天气视频卡片", "com.miui.weather2.information",
            "preference_weather_experinece_plan", "key_video_settings", PREFS, "agree_to_have_video"),
        new Entry("cleaner_content", "cleaner", "com.miui.cleanmaster", "垃圾清理 · 推荐内容", "com.miui.cleanmaster_preferences",
            "op_settings_12", "key_information_setting_close", PREFS, "key_information_setting_close"),
        new Entry("market_personal", "market", "com.xiaomi.market", "应用商店 · 个性化服务", "com.xiaomi.market_preferences",
            "recommend_settings", "pref_key_personalized_recommend", PREFS, "pref_key_personalized_recommend"),
        new Entry("browser_ad", "browser", "com.android.browser", "浏览器 · 个性化广告推荐", "kv_prefs",
            "customization_preferences", "pref_ad_personalized", MMKV, "pref_ad_personalized"),
        new Entry("browser_content", "browser", "com.android.browser", "浏览器 · 个性化内容推荐", "kv_prefs",
            "customization_preferences", "personalized_customization", MMKV, "personalized_customization"),
        new Entry("downloads_content", "downloads", "com.android.providers.downloads.ui", "下载管理 · 资源推荐", "com.android.providers.downloads.ui_preferences",
            "prefs_info_flow", "pref_info_flow_show", PREFS, "info_flow_show", "Banner_show"),
        new Entry("downloads_rank", "downloads", "com.android.providers.downloads.ui", "下载管理 · 热榜推荐", "com.android.providers.downloads.ui_preferences",
            "prefs_info_flow", "pref_rank_show", PREFS, "key_rank_show"),
        new Entry("calendar_content", "calendar", "com.android.calendar", "日历 · 内容推广", "com.android.calendar_preferences",
            "user_experience_preferences", "key_content_promotion", PREFS, "key_content_promotion")
    };
    static Entry find(String id) {
        for (Entry e : ENTRIES) if (e.id.equals(id)) return e;
        throw new IllegalArgumentException("未知广告开关");
    }
    static List<Entry> group(String id) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : ENTRIES) if (e.group.equals(id)) result.add(e);
        if (result.isEmpty()) throw new IllegalArgumentException("未知应用分组");
        return result;
    }
    static List<String> groups() {
        List<String> result = new ArrayList<>();
        for (Entry e : ENTRIES) if (!result.contains(e.group)) result.add(e.group);
        return result;
    }
    static boolean supportsBackgroundWrite(Entry entry) {
        return true;
    }
    static int backgroundWriteCount() {
        int count = 0;
        for (Entry entry : ENTRIES) if (supportsBackgroundWrite(entry)) count++;
        return count;
    }
    static int personalizedBits(int original, boolean enabled) {
        return enabled ? original | 1 : original & ~1;
    }
    private NativeAdRules() {}
}
