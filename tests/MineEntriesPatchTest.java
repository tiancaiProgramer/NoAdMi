import io.github.miclean.module.MineEntriesPatch;
import java.util.*;

public final class MineEntriesPatchTest {
    private static int count;
    private static final String MARKERS = "__d(function(){e.AdBanner=void 0;e.MembershipCard=void 0;"
        + "l.default.createElement(s.CNFeatureCards,null)},\"mi-1\",[]);\n";
    private static final String QUICK = "__d(function(){e.QuickActions=void 0;return c.default.createElement(l.HStack,{mt:\"8px\",pt:\"18px\",mb:\"5px\",justifyContent:\"space-between\"},"
        + "c.default.createElement(p.EntranceItem,{icon:\"activity\",onPress:function(){routeActivity()}},o.default.trans(\"mine.assets.item.active\")),"
        + "c.default.createElement(p.EntranceItem,{icon:\"course\",onPress:function(){routeCourse()}},o.default.trans(\"mine.assets.item.course\")),"
        + "c.default.createElement(p.EntranceItem,{icon:\"friend\",onPress:function(){routeFriend()}},o.default.trans(\"mine.assets.item.friend\")),"
        + "c.default.createElement(p.EntranceItem,{icon:\"order\",onPress:function(){routeOrder()}},o.default.trans(\"mine.assets.item.order\")),"
        + "c.default.createElement(p.EntranceItem,{icon:\"assets\",onPress:function(){routeAssets()}},o.default.trans(\"mine.assets.title\")))},\"[QuickActions]\",\"mi-2\",[]);\n";
    private static final String FEATURES = "__d(function(){e.UnLoginFeatureCards=e.CNFeatureCards=void 0;var o=function(t){var u=t.children;return "
        + "l.default.createElement(n.View,{mt:\"12px\"},l.default.createElement(n.HStack,null,l.default.createElement(n.View,{flex:1},u),"
        + "l.default.createElement(n.View,{width:\"10px\"}),l.default.createElement(n.View,{flex:1,justifyContent:\"space-between\"},"
        + "l.default.createElement(c.SportsHealthWeekly,null),l.default.createElement(n.View,{height:\"10px\"}),l.default.createElement(f.VitalityCompetitionCard,null))))};"
        + "l.default.createElement(C.LittleHabitCards,null);l.default.createElement(C.UnLoginLittleHabitCards,null);\"[CNFeatureCards]\"},\"mi-3\",[]);\n";
    public static void main(String[] args) {
        String source = MARKERS + QUICK + FEATURES;
        String[] ids = {"activity", "course", "habit", "weekly", "competition", "friend", "order", "assets"};
        for (int bits = 0; bits < 256; bits++) {
            Set<String> hidden = new HashSet<>();
            for (int j = 0; j < ids.length; j++) if ((bits & (1 << j)) != 0) hidden.add(ids[j]);
            String patched = MineEntriesPatch.patch(source, hidden);
            check(patched.contains("routeActivity()") == !hidden.contains("activity"));
            check(patched.contains("routeCourse()") == !hidden.contains("course"));
            check(patched.contains("n.View,{flex:1},u") == !hidden.contains("habit"));
            check(patched.contains("createElement(c.SportsHealthWeekly,null)") == !hidden.contains("weekly"));
            check(patched.contains("createElement(f.VitalityCompetitionCard,null)") == !hidden.contains("competition"));
            check(patched.contains("routeFriend()") == !hidden.contains("friend"));
            check(patched.contains("routeOrder()") == !hidden.contains("order"));
            check(patched.contains("routeAssets()") == !hidden.contains("assets"));
            boolean anyAction = !hidden.containsAll(Arrays.asList("activity", "course", "friend", "order", "assets"));
            check(patched.contains("c.default.createElement(l.HStack,") == anyAction);
            check(patched.contains("height:\"10px\"") == (!hidden.contains("weekly") && !hidden.contains("competition")));
            check(patched.contains("width:\"10px\"") == (!hidden.contains("habit") && (!hidden.contains("weekly") || !hidden.contains("competition"))));
            check(MineEntriesPatch.patch(patched, hidden).equals(patched));
        }
        Set<String> all = new HashSet<>(Arrays.asList(ids));
        String foreign = source.replace("e.MembershipCard=void 0", "e.Other=void 0");
        check(MineEntriesPatch.patch(foreign, all).equals(foreign));
        String duplicate = source + QUICK + FEATURES;
        check(MineEntriesPatch.patch(duplicate, all).equals(duplicate));
        String unknownLayout = source.replace("mt:\"12px\"", "mt:\"16px\"");
        check(MineEntriesPatch.patch(unknownLayout, Collections.singleton("habit")).equals(unknownLayout));
        check(MineEntriesPatch.patch(source, Collections.singleton("future")).equals(source));
        check(MineEntriesPatch.patch(source, all).contains("var u=t.children;return null"));
        String unknownEntry = source.replace("routeAssets()", "routeUnknown()").replace("icon:\"assets\"", "icon:\"future\"");
        String retained = MineEntriesPatch.patch(unknownEntry, all);
        check(retained.contains("routeUnknown()") && retained.contains("c.default.createElement(l.HStack,"));
        String assetsMissing = source.replace("mine.assets.title", "mine.assets.newTitle");
        check(MineEntriesPatch.patch(assetsMissing, Collections.singleton("assets")).equals(assetsMissing));
        check(MineEntriesPatch.patch(source, Collections.singleton("friend")).contains("routeAssets()"));
        System.out.println("PASS: " + count + " Mine-entry combinations, retained entries, spacing and fallback assertions");
    }
    private static void check(boolean result) {
        count++;
        if (!result) throw new AssertionError("Mine entry assertion " + count);
    }
}
