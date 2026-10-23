package io.github.miclean.module;

public final class OfficialAdCountsTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Official counts " + checks); }
    public static void main(String[] args) {
        check(!new OfficialAdCounts().allClosed());
        for (int mask = 0; mask < (1 << 11); mask++) {
            for (int unknown = -1; unknown < 11; unknown++) {
                OfficialAdCounts counts = new OfficialAdCounts();
                int enabled = 0;
                for (int i = 0; i < 11; i++) {
                    Boolean value = i == unknown ? null : (mask & (1 << i)) != 0;
                    counts.include(value);
                    if (Boolean.TRUE.equals(value)) enabled++;
                }
                check(counts.total() == 11);
                check(counts.enabled == enabled && counts.unknown == (unknown < 0 ? 0 : 1));
                check(counts.allClosed() == (unknown < 0 && mask == 0));
                check(counts.summary().contains("已完成") == counts.allClosed());
                if (unknown >= 0) check(counts.summary().contains("未确认 1 项"));
            }
        }
        System.out.println("PASS: " + checks + " official all-closed, enabled, unknown and completion checks");
    }
}
