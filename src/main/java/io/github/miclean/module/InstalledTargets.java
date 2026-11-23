package io.github.miclean.module;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Package names authorized by the manifest, resolved for the current Android user. */
final class InstalledTargets {
    static Map<String, PackageInfo> read(PackageManager packages) {
        Set<String> supported = new LinkedHashSet<>(Arrays.asList(Rules.HOME, Rules.HEALTH,
                Rules.HEALTH_PLAY, Rules.SECURITY_CENTER, Rules.SECURITY_ADD, Rules.BROWSER, Rules.MARKET));
        for (NativeAdRules.Entry rule : NativeAdRules.ENTRIES) supported.add(rule.pkg);
        Map<String, PackageInfo> installed = new LinkedHashMap<>();
        for (String pkg : supported) {
            try {
                installed.put(pkg, packages.getPackageInfo(pkg, 0));
            } catch (PackageManager.NameNotFoundException absent) {
                // The target is unavailable in this user profile.
            }
        }
        return installed;
    }

    private InstalledTargets() {}
}
