package io.github.miclean.module;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NetworkProductPromotionsTest {
    public static void main(String[] args) throws Exception {
        List<String> first = Arrays.asList("联网管理", "192G大流量卡", "流量排行", "特惠流量包，低至2元/G");
        List<String> second = Collections.singletonList("１２０ｇ 流量卡");
        List<List<String>> feed = Arrays.asList(first, second);
        List<?> filtered = NetworkProductPromotions.filter(feed, Object::toString);
        if (!filtered.equals(Arrays.asList(Arrays.asList("联网管理", "流量排行"), Collections.emptyList())))
            throw new AssertionError("Filter changed slot positions or kept a promotion");
        if (feed.get(0) != first || first.size() != 4 || feed.get(1) != second || second.size() != 1)
            throw new AssertionError("Original response was modified");

        List<?> unaffected = Collections.singletonList(Arrays.asList(null, "流量充值", "话费充值",
                "1元/天任性用", "0元领通用大流量卡", "流量包管理", "已购特惠流量包"));
        if (NetworkProductPromotions.filter(unaffected, Object::toString) != unaffected)
            throw new AssertionError("Four existing switches and non-promotion entries must stay independent");
        List<?> unknownShape = Arrays.asList(first, "unexpected");
        if (NetworkProductPromotions.filter(unknownShape, Object::toString) != unknownShape)
            throw new AssertionError("Unrecognized feed must stay intact");
        if (!BulkEnablePolicy.eligible(Rules.NETWORK_PRODUCT_PROMOTION))
            throw new AssertionError("Promotion switch missing from the advertising master");
        System.out.println("PASS: network offer matching, per-SIM ordering, unchanged source data and isolated switches");
    }
}
