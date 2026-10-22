import io.github.miclean.module.Rules;
import java.util.*;

public final class SystemRulesTest {
    static int assertions;
    public static void main(String[] args) {
        check(Rules.targetProcess(Rules.SECURITY_CENTER, Rules.SECURITY_CENTER));
        check(Rules.targetProcess(Rules.SECURITY_ADD, Rules.SECURITY_ADD));
        for (String pkg : new String[]{Rules.SECURITY_CENTER, Rules.SECURITY_ADD}) {
            check(!Rules.targetProcess(pkg, pkg + ":remote"));
            check(!Rules.targetProcess(pkg, pkg + ".home"));
            check(!Rules.targetProcess(pkg, "system_server"));
        }
        check(!Rules.targetProcess("android", "android"));
        check(!Rules.targetProcess("com.android.settings", "com.android.settings"));
        check(!Rules.targetProcess("com.miui.weather2", "com.miui.weather2"));
        check(Rules.NETWORK_KEYS.length == 4 && Rules.NETWORK_TITLES.length == 4);
        check(new HashSet<>(Arrays.asList(Rules.NETWORK_KEYS)).size() == 4);
        for (int i = 0; i < 4; i++) check(Rules.networkItem(Rules.NETWORK_TITLES[i]) == i);
        for (String retained : new String[]{null, "", "联网管理", "流量排行", "本月已用", "1元/天任性用设置"})
            check(Rules.networkItem(retained) == -1);
        check(!Rules.GAME_RECOMMEND_SWITCH.equals(Rules.GAME_POPUP_SWITCH));
        System.out.println("PASS: " + assertions + " system app scope, independent selections and retained-function assertions");
    }
    static void check(boolean value) { assertions++; if (!value) throw new AssertionError("System rule " + assertions); }
}
