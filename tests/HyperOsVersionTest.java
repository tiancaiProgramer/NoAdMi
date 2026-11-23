package io.github.miclean.module;

public final class HyperOsVersionTest {
    public static void main(String[] args) {
        check("OS3.0.304.0.WMVCNXM", 3);
        check("OS4.0.4.0.XOCCNXM", 4);
        check("OS40.0", 40);
        check("CP2A.260605.016", 0);
        check("OS4.dev", 0);
        check(null, 0);
        System.out.println("PASS: HyperOS build major and unknown-build recognition");
    }

    private static void check(String incremental, int expected) {
        int actual = HyperOsVersion.major(incremental);
        if (actual != expected) throw new AssertionError(incremental + ": " + actual);
    }
}
