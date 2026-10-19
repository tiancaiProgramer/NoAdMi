import io.github.miclean.module.Rules;

public final class RulesTest {
    public static void main(String[] args) {
        check(Rules.targetProcess(Rules.HEALTH, Rules.HEALTH));
        check(Rules.targetProcess(Rules.HOME, Rules.HOME));
        check(!Rules.targetProcess(Rules.HEALTH, Rules.HEALTH + ":remote"));
        check(!Rules.targetProcess(Rules.HOME, Rules.HOME + ":plugin"));
        check(!Rules.targetProcess(Rules.HEALTH, Rules.HOME));
        check(!Rules.targetProcess(Rules.MODULE, Rules.MODULE));
        check(!Rules.targetProcess("com.android.settings", "com.android.settings"));
        check(!Rules.targetProcess(null, null));
        check(!Rules.targetProcess(Rules.HEALTH, null));
        check(Rules.blockHomePreference("switch_manger_open_screen_cache_data"));
        check(!Rules.blockHomePreference("sh_ads_cache_data"));
        check(!Rules.blockHomePreference("device_settings"));
        check(!Rules.blockHomePreference(null));
        check(!Rules.blockHomePreference("switch_manger_open_screen_cache_data_extra"));
        check(!Rules.target(Rules.MODULE));
        check(!Rules.target("com.mi.health:remote"));
        check(!Rules.target("com.xiaomi.smarthome.plugin"));
        check(!Rules.deviceBannerSlots(null));
        check(!Rules.deviceBannerSlots(java.util.Collections.emptyList()));
        check(Rules.deviceBannerSlots(java.util.Collections.singletonList(11)));
        check(Rules.deviceBannerSlots(java.util.Collections.singletonList(12)));
        check(Rules.deviceBannerSlots(java.util.Arrays.asList(11, 12)));
        check(!Rules.deviceBannerSlots(java.util.Arrays.asList(11, 13)));
        check(!Rules.deviceBannerSlots(java.util.Collections.singletonList(14)));
        check(!Rules.deviceBannerSlots(java.util.Collections.singletonList("12")));
        check(!Rules.deviceBannerSlots(java.util.Collections.singletonList(12L)));
        check(Rules.deviceBannerSlots(java.util.Arrays.asList(11, 12, 12)));
        System.out.println("PASS: 27 app/process scope and banner-slot assertions");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Unexpected rule selection");
    }
}
