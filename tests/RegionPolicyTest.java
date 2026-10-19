import io.github.miclean.module.RegionPolicy;
import io.github.miclean.module.Rules;

public final class RegionPolicyTest {
    private static int assertions;
    private static final String STARTUP = "com.xiaomi.fitness.login.SplashActivity$startNextActivity$1";
    public static void main(String[] args) {
        check(RegionPolicy.mainland("CN"));
        check(RegionPolicy.mainland("cn"));
        check(RegionPolicy.mainland("Cn"));
        for (Object code : new Object[]{null, "", " CN", "cn ", "US", "HK", "TW", 86})
            check(!RegionPolicy.mainland(code));
        check(RegionPolicy.migrationRegionCheck(stack(STARTUP, "invokeSuspend")));
        check(!RegionPolicy.migrationRegionCheck(stack(STARTUP, "create")));
        check(!RegionPolicy.migrationRegionCheck(stack(STARTUP + "$1", "invokeSuspend")));
        check(!RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.region.RegionSelectActivity", "onCreate")));
        check(!RegionPolicy.migrationRegionCheck(null));
        check(!RegionPolicy.migrationRegionCheck(new StackTraceElement[0]));
        check(RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.privacy.PrivacyActivity", "handlePrivacyAgree")));
        check(RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.welcome.WelcomeActivity", "onCreate$lambda$3")));
        check(RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.internaltest.InternalTestActivity", "onNextStep")));
        check(!RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.privacy.PrivacyActivity", "showCnPrivacyEnable")));
        check(!RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.welcome.WelcomeActivity", "onCreate")));
        // A nested backend query must retain the real region even during startup.
        check(!RegionPolicy.migrationRegionCheck(new StackTraceElement[]{
            frame("io.github.miclean.module.RegionHooks$2", "afterHookedMethod"),
            frame("com.xiaomi.fitness.login.utils.RegionUtil", "isInnerCN"),
            frame("com.xiaomi.fitness.network.RegionEndpoint", "resolve"),
            frame(STARTUP, "invokeSuspend")}));
        check(!RegionPolicy.migrationRegionCheck(stack("com.xiaomi.fitness.login.SplashActivity", "onCreate")));
        check(Rules.targetProcess(Rules.HEALTH_PLAY, Rules.HEALTH_PLAY));
        check(!Rules.targetProcess(Rules.HEALTH_PLAY, Rules.HEALTH_PLAY + ":remote"));
        check(!Rules.targetProcess(Rules.HEALTH_PLAY, Rules.HEALTH));
        System.out.println("PASS: " + assertions + " Play region, startup caller and process boundary assertions");
    }
    private static StackTraceElement[] stack(String cls, String method) {
        return new StackTraceElement[]{
            frame("java.lang.Thread", "getStackTrace"),
            frame("io.github.miclean.module.RegionHooks$2", "afterHookedMethod"),
            frame("de.robv.android.xposed.XposedBridge", "handleHookedMethod"),
            frame("java.lang.reflect.Method", "invoke"),
            frame("com.xiaomi.fitness.login.utils.RegionUtil", "isInnerCN"),
            frame(cls, method)};
    }
    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, "Host.kt", 1);
    }
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Region policy assertion " + assertions);
    }
}
