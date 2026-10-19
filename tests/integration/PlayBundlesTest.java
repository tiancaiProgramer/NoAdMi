import io.github.miclean.module.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Run separately with extracted, user-supplied app bundles; no app code is executed. */
public final class PlayBundlesTest {
    private static int assertions, cases;
    private static Path output;
    public static void main(String[] args) throws Exception {
        Path bundles = Paths.get(args[0]);
        output = Paths.get(args[1]);
        Files.createDirectories(output);
        String mine = read(bundles, "mine"), sport = read(bundles, "sport");
        for (int bits = 0; bits < 4; bits++) {
            String patched = MineBundlePatch.patch(mine, (bits & 1) != 0, (bits & 2) != 0);
            check(patched.equals(mine) == (bits == 0));
            check(module(patched, "e.QuickActions=void 0").equals(module(mine, "e.QuickActions=void 0")));
            check(module(patched, "e.UnLoginFeatureCards=e.CNFeatureCards=void 0")
                .equals(module(mine, "e.UnLoginFeatureCards=e.CNFeatureCards=void 0")));
            check(MineBundlePatch.patch(patched, (bits & 1) != 0, (bits & 2) != 0).equals(patched));
            save("mine-ads-" + bits, patched);
        }
        Set<String> all = new HashSet<>(Arrays.asList(Rules.HEALTH_MINE_IDS));
        for (String id : Rules.HEALTH_MINE_IDS) {
            String patched = MineEntriesPatch.patch(mine, Collections.singleton(id));
            // The tested built-in Play page has Prize instead of Assets. Do not
            // reinterpret the Assets toggle as permission to remove another entry.
            check(patched.equals(mine) == "assets".equals(id));
            check(module(patched, "e.QuickActions=void 0").contains("icon:\"prize\""));
            String unaffected = Arrays.asList("habit", "weekly", "competition").contains(id)
                ? "e.QuickActions=void 0" : "e.UnLoginFeatureCards=e.CNFeatureCards=void 0";
            check(module(patched, unaffected).equals(module(mine, unaffected)));
            check(MineEntriesPatch.patch(patched, Collections.singleton(id)).equals(patched));
            check(module(patched, "e.ForeignFeatureCards=void 0").equals(module(mine, "e.ForeignFeatureCards=void 0")));
            check(module(patched, "e.RegionPageMap=e.MyProfileMapPage=void 0")
                .contains("l.default.createElement(s.ForeignFeatureCards,null)") != "competition".equals(id));
            save("mine-entry-" + id, patched);
        }
        String combined = MineEntriesPatch.patch(MineBundlePatch.patch(mine, true, true), all);
        check(!combined.equals(mine));
        check(SportBundlePatch.patch(combined, true, true, true).equals(combined));
        save("mine-all", combined);
        for (int bits = 0; bits < 8; bits++) {
            String patched = SportBundlePatch.patch(sport, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
            check(patched.equals(sport) == (bits == 0));
            check(MineBundlePatch.patch(patched, true, true).equals(patched));
            check(MineEntriesPatch.patch(patched, all).equals(patched));
            check(SportBundlePatch.patch(patched, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0).equals(patched));
            save("sport-" + bits, patched);
        }
        for (String page : new String[]{"health", "common", "dial", "init"}) {
            String original = read(bundles, page);
            check(MineBundlePatch.patch(original, true, true).equals(original));
            check(MineEntriesPatch.patch(original, all).equals(original));
            check(SportBundlePatch.patch(original, true, true, true).equals(original));
        }
        System.out.println("PASS: " + assertions + " actual Play bundle assertions; " + cases + " scripts saved for syntax checks");
    }
    private static String read(Path root, String page) throws Exception {
        return new String(Files.readAllBytes(root.resolve(page + "/extracted/index.android.bundle")), StandardCharsets.UTF_8);
    }
    private static String module(String source, String marker) {
        int at = source.indexOf(marker);
        if (at < 0 || source.indexOf(marker, at + marker.length()) >= 0) throw new AssertionError("Expected unique module");
        int start = source.lastIndexOf("\n__d(", at), end = source.indexOf("\n__d(", at);
        return source.substring(start < 0 ? 0 : start, end < 0 ? source.length() : end);
    }
    private static void save(String name, String source) throws Exception {
        Files.write(output.resolve(name + ".js"), source.getBytes(StandardCharsets.UTF_8));
        cases++;
    }
    private static void check(boolean result) {
        assertions++;
        if (!result) throw new AssertionError("Actual bundle assertion " + assertions);
    }
}
