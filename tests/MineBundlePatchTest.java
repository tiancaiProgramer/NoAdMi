import io.github.miclean.module.MineBundlePatch;

public final class MineBundlePatchTest {
    public static void main(String[] args) {
        String source = "e.AdBanner=void 0;e.MembershipCard=void 0;\"mi-400\";"
            + "l.default.createElement(s.CNFeatureCards,null);"
            + "l.default.createElement(s.AdBanner,null);"
            + "l.default.createElement(s.MembershipCard,null);"
            + "l.default.createElement(s.AlipayPaymentCard,null);"
            + "l.default.createElement(s.HealthConsultationNew,null);"
            + "l.default.createElement(s.SettingsMenu,null);";
        check(MineBundlePatch.patch(source, false, false).equals(source));
        String ads = MineBundlePatch.patch(source, true, false);
        check(!ads.contains("createElement(s.AdBanner,null)"));
        check(ads.contains("createElement(s.MembershipCard,null)"));
        String members = MineBundlePatch.patch(source, false, true);
        check(members.contains("createElement(s.AdBanner,null)"));
        check(!members.contains("createElement(s.MembershipCard,null)"));
        check(!members.contains("createElement(s.AlipayPaymentCard,null)"));
        check(!members.contains("createElement(s.HealthConsultationNew,null)"));
        check(members.contains("createElement(s.SettingsMenu,null)"));
        check(members.contains("createElement(s.CNFeatureCards,null)"));
        String foreignModule = source.replace("e.MembershipCard=void 0", "e.Other=void 0");
        check(MineBundlePatch.patch(foreignModule, true, true).equals(foreignModule));
        String updatedStructure = source.replace("l.default.createElement(s.CNFeatureCards,null)", "newStructure()");
        check(MineBundlePatch.patch(updatedStructure, true, true).equals(updatedStructure));
        String both = MineBundlePatch.patch(source, true, true);
        check(MineBundlePatch.patch(both, true, true).equals(both));
        System.out.println("PASS: 12 dynamic page isolation, independent toggle and fallback assertions");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Unexpected dynamic page patch");
    }
}
