package io.github.miclean.module;

/** Remove only known promotion components from this version's Mine page tree. */
public final class MineBundlePatch {
    private MineBundlePatch() {}

    public static String patch(String source, boolean banners, boolean membership) {
        // All markers are required. Other RN modules or changed page layouts are untouched.
        if (!source.contains("e.AdBanner=void 0")
                || !source.contains("e.MembershipCard=void 0")
                || !source.contains("l.default.createElement(s.CNFeatureCards,null)")
                || !source.contains("\"mi-")) return source;
        String changed = source;
        if (banners) changed = changed.replace("l.default.createElement(s.AdBanner,null)", "null");
        if (membership) {
            for (String component : new String[]{"MembershipCard", "UnLoginMembershipCard",
                    "AlipayPaymentCard", "HealthConsultationNew", "UnLoginHealthConsultationNew"}) {
                changed = changed.replace("l.default.createElement(s." + component + ",null)", "null");
            }
        }
        return changed;
    }
}
