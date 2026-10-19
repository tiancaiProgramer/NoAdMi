package io.github.miclean.module;

import java.util.Set;

/** Hide selected Mine-page entries inside their own Metro modules. */
public final class MineEntriesPatch {
    private MineEntriesPatch() {}
    private static final String LEFT = "l.default.createElement(n.View,{flex:1},u)";
    private static final String WEEKLY = "l.default.createElement(c.SportsHealthWeekly,null)";
    private static final String COMPETITION = "l.default.createElement(f.VitalityCompetitionCard,null)";
    private static final String GAP = "l.default.createElement(n.View,{height:\"10px\"})";
    private static final String SPACER = "l.default.createElement(n.View,{width:\"10px\"})";
    private static final String RIGHT = "l.default.createElement(n.View,{flex:1,justifyContent:\"space-between\"},";
    private static final String ROW = "l.default.createElement(n.View,{mt:\"12px\"},l.default.createElement(n.HStack,null,";
    private static final String LAYOUT = ROW + LEFT + "," + SPACER + "," + RIGHT
        + WEEKLY + "," + GAP + "," + COMPETITION + ")))";
    private static final String ACTION_ROW = "c.default.createElement(l.HStack,{mt:\"8px\",pt:\"18px\",mb:\"5px\",justifyContent:\"space-between\"},";
    private static final String[][] ACTIONS = {
        {"activity", "mine.assets.item.active"}, {"course", "mine.assets.item.course"},
        {"friend", "mine.assets.item.friend"}, {"order", "mine.assets.item.order"},
        {"assets", "mine.assets.title"}
    };

    public static String patch(String source, Set<String> hidden) {
        if (hidden.isEmpty() || !source.contains("e.AdBanner=void 0")
                || !source.contains("e.MembershipCard=void 0")
                || !source.contains("l.default.createElement(s.CNFeatureCards,null)")) return source;
        String changed = quickActions(source, hidden);
        return features(changed, hidden);
    }

    private static int[] bounds(String source, String export) {
        int at = source.indexOf(export);
        if (at < 0 || source.indexOf(export, at + export.length()) >= 0) return null;
        int begin = source.lastIndexOf("\n__d(", at);
        if (begin < 0) {
            if (!source.startsWith("__d(")) return null;
            begin = 0;
        } else begin++;
        int end = source.indexOf("\n__d(", at);
        if (end < 0) end = source.length();
        return new int[]{begin, end};
    }

    private static String quickActions(String source, Set<String> hidden) {
        boolean requested = false;
        for (String[] action : ACTIONS) requested |= hidden.contains(action[0]);
        if (!requested) return source;
        int[] b = bounds(source, "e.QuickActions=void 0");
        if (b == null) return source;
        String module = source.substring(b[0], b[1]);
        if (!module.contains("\"[QuickActions]\"") || !module.contains("\"mi-")) return source;
        String changed = module;
        for (String[] action : ACTIONS) {
            String id = action[0];
            if (!hidden.contains(id)) continue;
            String start = "c.default.createElement(p.EntranceItem,{icon:\"" + id + "\",onPress:function(){";
            String end = ".default.trans(\"" + action[1] + "\"))";
            int first = changed.indexOf(start), last = changed.indexOf(end);
            if (first < 0 || last <= first || changed.indexOf(start, first + start.length()) >= 0
                    || changed.indexOf(end, last + end.length()) >= 0) continue;
            String entry = changed.substring(first, last + end.length());
            if (entry.indexOf("c.default.createElement(p.EntranceItem,", start.length()) >= 0) continue;
            changed = changed.substring(0, first) + "null" + changed.substring(last + end.length());
        }
        // Only remove the known row when every child is null; retain unknown entries.
        int row = changed.indexOf(ACTION_ROW);
        if (row >= 0 && changed.indexOf(ACTION_ROW, row + ACTION_ROW.length()) < 0) {
            int end = row + ACTION_ROW.length();
            while (changed.startsWith("null", end)) {
                end += 4;
                if (changed.startsWith(",null", end)) { end++; continue; }
                if (changed.startsWith(")", end)) {
                    changed = changed.substring(0, row) + "null" + changed.substring(end + 1);
                }
                break;
            }
        }
        return source.substring(0, b[0]) + changed + source.substring(b[1]);
    }

    private static String features(String source, Set<String> hidden) {
        boolean habit = !hidden.contains("habit"), weekly = !hidden.contains("weekly"),
            competition = !hidden.contains("competition");
        if (habit && weekly && competition) return source;
        int[] b = bounds(source, "e.UnLoginFeatureCards=e.CNFeatureCards=void 0");
        if (b == null) return source;
        String module = source.substring(b[0], b[1]);
        if (!module.contains("\"[CNFeatureCards]\"") || !module.contains("\"mi-")
                || !module.contains("l.default.createElement(C.LittleHabitCards,null)")
                || !module.contains("l.default.createElement(C.UnLoginLittleHabitCards,null)")) return source;
        int at = module.indexOf(LAYOUT);
        if (at < 0 || module.indexOf(LAYOUT, at + LAYOUT.length()) >= 0) return source;
        String layout = "null";
        if (habit || weekly || competition) {
            String right = weekly || competition ? RIGHT + (weekly ? WEEKLY : "")
                + (weekly && competition ? "," + GAP + "," : "")
                + (competition ? COMPETITION : "") + ")" : "";
            layout = ROW + (habit ? LEFT : "")
                + (habit && !right.isEmpty() ? "," + SPACER + "," : "") + right + "))";
        }
        String changed = module.substring(0, at) + layout + module.substring(at + LAYOUT.length());
        return source.substring(0, b[0]) + changed + source.substring(b[1]);
    }
}
