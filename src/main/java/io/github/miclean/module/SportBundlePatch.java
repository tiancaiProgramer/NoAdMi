package io.github.miclean.module;

/** Filter only the Sport tab's own data result, never shared activity detail renderers. */
public final class SportBundlePatch {
    private SportBundlePatch() {}
    private static final String EXPORT = "e.fetchSportTabData=function(t,n)";
    private static final String TARGET = "case 3:if(c=u.sent,!(I&&o<I.time)){";

    private static boolean hasSportModuleId(String module) {
        int begin = module.lastIndexOf("},\"sp-");
        if (begin < 0 || !module.trim().endsWith("]);")) return false;
        begin += 6;
        int end = module.indexOf("\",[", begin);
        if (end <= begin) return false;
        for (int i = begin; i < end; i++) {
            char c = module.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public static String patch(String source, boolean groups, boolean activities, boolean banners) {
        if (!groups && !activities && !banners) return source;
        int position = source.indexOf(EXPORT);
        if (position < 0 || source.indexOf(EXPORT, position + EXPORT.length()) >= 0) return source;
        int start = source.lastIndexOf("\n__d(", position);
        if (start < 0) {
            if (!source.startsWith("__d(")) return source;
            start = 0;
        } else start++;
        int end = source.indexOf("\n__d(", position);
        if (end < 0) end = source.length();
        String module = source.substring(start, end);
        // Host version is checked by HookEntry. OTA bundles may renumber Metro modules.
        // Require the complete Sport data fingerprint; shared cards remain unchanged.
        if (!hasSportModuleId(module)
                || !module.contains("usage:b.SportMenuTabType.Sport,locale:t")
                || !module.contains("{type:O.SportRecord.type,data:A}")
                || !module.contains("{type:O.TrainingIndicators.type,data:B}")
                || !module.contains("{type:O.SportGroup.type,data:I}")
                || !module.contains("type:O.ActivityRecommendScreenSection.type")
                || !module.contains("t.type===y.EN_COMPONENT_TYPE.BANNER")) return source;
        int target = module.indexOf(TARGET);
        if (target < 0 || module.indexOf(TARGET, target + TARGET.length()) >= 0) return source;
        StringBuilder condition = new StringBuilder("true");
        if (groups) condition.append("&&t.type!==\"sportGroup\"");
        if (activities) condition.append("&&t.type!==\"activityRecommend\"");
        if (banners) condition.append("&&t.type!==3");
        String replacement = "case 3:if(c=u.sent.filter(function(t){return !t||(" + condition
            + ");}),!(I&&o<I.time)){";
        String changed = module.substring(0, target) + replacement + module.substring(target + TARGET.length());
        return source.substring(0, start) + changed + source.substring(end);
    }
}
