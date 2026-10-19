import io.github.miclean.module.Rules;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;

public final class HomeOptionsTest {
    private static int assertions;
    public static void main(String[] args) {
        List<String> original = Arrays.asList("DeviceMain", "Scene", "Brand", "ShopYouPin", "Setting");
        check(Rules.retainedTabs(original, Collections.emptySet()).equals(original));
        check(Rules.retainedTabs(original, Collections.singleton("Brand"))
            .equals(Arrays.asList("DeviceMain", "Scene", "ShopYouPin", "Setting")));
        check(Rules.retainedTabs(original, new HashSet<>(original)).equals(original));
        check(original.size() == 5); // Selection helper must never mutate host input.
        check(Rules.retainedTabs(Arrays.asList("DeviceMain", "BBS", "ShopGlobal", "Setting"),
            Collections.singleton("ShopYouPin")).equals(Arrays.asList("DeviceMain", "BBS", "Setting")));
        check(Rules.retainedTabs(Arrays.asList("DeviceMain", "Scene", "Setting"),
            Collections.singleton("ShopYouPin")).equals(Arrays.asList("DeviceMain", "Scene", "Setting")));
        check(Rules.retainedTabs(original, new HashSet<>(Arrays.asList("DeviceMain", "Scene", "Brand", "ShopYouPin")))
            .equals(Collections.singletonList("Setting")));
        check(Rules.visibleTabIndex(Arrays.asList("Scene", "Setting"), Collections.emptySet(), -1) == 0);
        check(Rules.visibleTabIndex(Arrays.asList("Scene", "Setting"), Collections.emptySet(), 1) == 1);
        check(Rules.tabKey("Brand").equals(Rules.HOME_PRODUCT_TAB_SWITCH)); // Existing installations migrate without a reset.
        check(Rules.HOME_MINE_IDS.length == 13 && Rules.HOME_MINE_TITLES.length == 13 && Rules.HOME_MINE_VIEWS.length == 13);
        check(new HashSet<>(Arrays.asList(Rules.HOME_MINE_IDS)).size() == 13);
        HashSet<Integer> views = new HashSet<>();
        for (int id : Rules.HOME_MINE_VIEWS) views.add(id);
        check(views.size() == 13);
        check(!Rules.effectiveHomeOption(false, false, true));
        check(!Rules.effectiveHomeOption(false, false, false));
        check(Rules.effectiveHomeOption(false, true, true));
        check(!Rules.effectiveHomeOption(false, true, false));
        check(Rules.effectiveHomeOption(true, false, true));
        check(!Rules.effectiveHomeOption(true, false, false));
        check(Rules.effectiveHomeOption(true, true, true));
        check(!Rules.effectiveHomeOption(true, true, false));
        System.out.println("PASS: " + assertions + " custom navigation, fallback, migration and row-map assertions");
    }
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Home options assertion " + assertions);
    }
}
