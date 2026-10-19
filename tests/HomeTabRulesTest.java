import io.github.miclean.module.Rules;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;

public final class HomeTabRulesTest {
    public static void main(String[] args) {
        List<String> tabs = Arrays.asList("DeviceMain", "Scene", "Brand", "Setting");
        Set<String> product = Collections.singleton("Brand");
        check(Rules.visibleTabIndex(tabs, product, 3) == 3); // My retains native index 3, never shifted to 2.
        check(Rules.visibleTabIndex(tabs, product, 1) == 1);
        check(Rules.visibleTabIndex(tabs, product, 2) == 0);
        check(Rules.visibleTabIndex(tabs, product, -1) == 0);
        check(Rules.visibleTabIndex(tabs, product, 99) == 0);
        check(Rules.visibleTabIndex(tabs, Collections.singleton("DeviceMain"), 0) == 1);
        check(Rules.visibleTabIndex(tabs, new HashSet<>(Arrays.asList("DeviceMain", "Scene", "Brand")), 0) == 3);
        check(Rules.visibleTabIndex(tabs, new HashSet<>(tabs), 2) == 2);
        check(Rules.visibleTabIndex(Arrays.asList("DeviceMain", "Scene", "ShopGlobal", "Setting"), Collections.singleton("ShopYouPin"), 3) == 3);
        check(Rules.visibleTabIndex(Arrays.asList("DeviceMain", "Scene", "ShopGlobal", "Setting"), Collections.singleton("ShopYouPin"), 2) == 0);
        check(!Rules.hiddenTab(product, null));
        check(!Rules.hiddenTab(product, "BBS"));
        System.out.println("PASS: 12 native page-index, hidden selection and regional mall assertions");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError("Unexpected native page selection");
    }
}
