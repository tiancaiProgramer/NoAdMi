package io.github.miclean.module;

/** Limits the Play-region workaround to download/migration checks. */
public final class RegionPolicy {
    static final String FITNESS_REGION_UTIL = "com.xiaomi.fitness.login.utils.RegionUtil";
    static final String FITNESS_STARTUP = "com.xiaomi.fitness.login.SplashActivity$startNextActivity$1";

    private RegionPolicy() {}

    public static boolean mainland(Object countryCode) {
        return countryCode instanceof String && "cn".equalsIgnoreCase((String) countryCode);
    }

    public static boolean migrationRegionCheck(StackTraceElement[] stack) {
        if (stack == null) return false;
        // Xposed/reflection frames vary between framework releases. The nearest host
        // frame must be a known post-consent routing check, not a nested service call.
        for (StackTraceElement frame : stack) {
            String cls = frame.getClassName();
            if (!cls.startsWith("com.xiaomi.fitness.") || FITNESS_REGION_UTIL.equals(cls)) continue;
            String method = frame.getMethodName();
            return (FITNESS_STARTUP.equals(cls) && "invokeSuspend".equals(method))
                || ("com.xiaomi.fitness.login.privacy.PrivacyActivity".equals(cls)
                    && "handlePrivacyAgree".equals(method))
                || ("com.xiaomi.fitness.login.welcome.WelcomeActivity".equals(cls)
                    && "onCreate$lambda$3".equals(method))
                || ("com.xiaomi.fitness.login.internaltest.InternalTestActivity".equals(cls)
                    && "onNextStep".equals(method));
        }
        return false;
    }
}
