import io.github.miclean.module.SportBundlePatch;

public final class SportBundlePatchTest {
    private static int count;
    private static final String MODULE = "__d(function(){e.fetchSportTabData=function(t,n);"
        + "usage:b.SportMenuTabType.Sport,locale:t;"
        + "{type:O.SportRecord.type,data:A};{type:O.TrainingIndicators.type,data:B};"
        + "{type:O.SportGroup.type,data:I};type:O.ActivityRecommendScreenSection.type;"
        + "t.type===y.EN_COMPONENT_TYPE.BANNER;"
        + "case 3:if(c=u.sent,!(I&&o<I.time)){}},\"sp-1124\",[]);";
    public static void main(String[] args) {
        String prefix = "__d(function(){unrelatedPlan();},\"other\",[]);\n";
        String suffix = "\n__d(function(){activityDetail();},\"other2\",[]);";
        String source = prefix + MODULE + suffix;
        for (int bits = 0; bits < 8; bits++) {
            boolean groups = (bits & 1) != 0, activities = (bits & 2) != 0, banners = (bits & 4) != 0;
            String patched = SportBundlePatch.patch(source, groups, activities, banners);
            check(patched.contains("t.type!==\"sportGroup\"") == groups);
            check(patched.contains("t.type!==\"activityRecommend\"") == activities);
            check(patched.contains("t.type!==3") == banners);
        }
        String changed = SportBundlePatch.patch(source, true, true, true);
        check(changed.startsWith(prefix) && changed.endsWith(suffix));
        check(SportBundlePatch.patch(source, false, false, false).equals(source));
        check(SportBundlePatch.patch(changed, true, true, true).equals(changed));
        String missingScope = source.replace("usage:b.SportMenuTabType.Sport,locale:t", "usage:b.SportMenuTabType.Plan,locale:t");
        check(SportBundlePatch.patch(missingScope, true, true, true).equals(missingScope));
        String changedShape = source.replace("case 3:if(c=u.sent,", "case 3:if(c=updated,");
        check(SportBundlePatch.patch(changedShape, true, true, true).equals(changedShape));
        String ambiguous = source + "\n" + MODULE;
        check(SportBundlePatch.patch(ambiguous, true, true, true).equals(ambiguous));
        String renumbered = source.replace("\"sp-1124\"", "\"sp-1132\"");
        check(!SportBundlePatch.patch(renumbered, true, true, true).equals(renumbered));
        String renamed = source.replace("\"sp-1124\"", "\"sp-new\"");
        check(SportBundlePatch.patch(renamed, true, true, true).equals(renamed));
        String foreign = "e.MembershipCard=void 0;e.AdBanner=void 0;";
        check(SportBundlePatch.patch(foreign, true, true, true).equals(foreign));
        System.out.println("PASS: " + count + " sport isolation, eight toggle combinations and fallback assertions");
    }
    private static void check(boolean value) {
        count++;
        if (!value) throw new AssertionError("Sport patch assertion " + count);
    }
}
