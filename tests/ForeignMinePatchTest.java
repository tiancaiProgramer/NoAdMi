import io.github.miclean.module.MineEntriesPatch;
import java.util.*;

public final class ForeignMinePatchTest {
    private static int assertions;
    private static final String TARGET = "l.default.createElement(s.ForeignFeatureCards,null)";
    private static final String MARKERS = "__d(function(){e.AdBanner=void 0;e.MembershipCard=void 0;"
        + "l.default.createElement(s.CNFeatureCards,null)},\"mi-1\",[]);\n";
    private static final String CARD = "__d(function(){e.ForeignFeatureCards=void 0;"
        + "s.default.trans(\"mine.pk.my.pk\");L.vitalityCompetitionCardRoute:L.InviteMembersUrl;"
        + "e.ForeignFeatureCards=(0,E.withRenderFC)(function(){var t=(0,u.useState)(_.getCompetingRecordList);"
        + "return u.default.createElement(b.default,null,u.default.createElement(u.Suspense,null,"
        + "u.default.createElement(C,{fetcher:l})))},\"[ForeignFeatureCards]\")},\"mi-2\",[]);\n";
    private static final String PAGE = "__d(function(){e.RegionPageMap=e.MyProfileMapPage=void 0;"
        + TARGET + ";" + TARGET + ";" + TARGET + ";"
        + "E.RegionEnum.FOREIGN,P;E.RegionEnum.FOREIGN_CN,F;\"[MyProfileMapPage]\";settingsAndRouteLibrary();},\"mi-3\",[]);\n";
    public static void main(String[] args) {
        String source = MARKERS + CARD + PAGE;
        Set<String> competition = Collections.singleton("competition");
        String changed = MineEntriesPatch.patch(source, competition);
        check(!changed.equals(source));
        check(changed.startsWith(MARKERS + CARD));
        check(changed.contains("settingsAndRouteLibrary()"));
        check(!changed.substring((MARKERS + CARD).length()).contains(TARGET));
        check(MineEntriesPatch.patch(changed, competition).equals(changed));
        check(MineEntriesPatch.patch(source, Collections.emptySet()).equals(source));
        for (String unrelated : new String[]{"habit", "weekly", "assets"})
            check(MineEntriesPatch.patch(source, Collections.singleton(unrelated)).equals(source));
        for (String marker : new String[]{"mine.pk.my.pk", "vitalityCompetitionCardRoute", "[ForeignFeatureCards]", "E.RegionEnum.FOREIGN_CN,F"}) {
            String unknown = source.replace(marker, "changed");
            check(MineEntriesPatch.patch(unknown, competition).equals(unknown));
        }
        String duplicate = source + PAGE;
        check(MineEntriesPatch.patch(duplicate, competition).equals(duplicate));
        String incomplete = source.replace(TARGET + ";" + TARGET + ";" + TARGET, TARGET);
        check(MineEntriesPatch.patch(incomplete, competition).equals(incomplete));
        System.out.println("PASS: " + assertions + " foreign Mine layout, retained shared card and fallback assertions");
    }
    private static void check(boolean result) {
        assertions++;
        if (!result) throw new AssertionError("Foreign Mine boundary " + assertions);
    }
}
