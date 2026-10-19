import io.github.miclean.module.Rules;

public final class RulesTest {
    public static void main(String[] args) {
        check(Rules.supported(Rules.HEALTH, 359001));
        check(Rules.supported(Rules.HOME, 110817051));
        check(!Rules.supported(Rules.HEALTH, 359002));
        check(!Rules.supported(Rules.HOME, 110817052));
        check(!Rules.supported(Rules.HOME, 79246));
        check(!Rules.supported("com.android.settings", 359001));
        check(!Rules.supported(null, 359001));
        check(!Rules.supported(Rules.HEALTH, 110817051));
        check(!Rules.supported(Rules.HOME, 359001));
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
        System.out.println("PASS: 27 compatibility and scope assertions");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Unexpected rule selection");
    }
}
