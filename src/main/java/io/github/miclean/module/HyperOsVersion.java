package io.github.miclean.module;

import android.os.Build;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HyperOsVersion {
    private static final Pattern VERSION = Pattern.compile("^OS([1-9][0-9]?)\\.[0-9]");
    static final int MAJOR = major(Build.VERSION.INCREMENTAL);

    static int major(String incremental) {
        if (incremental == null) return 0;
        Matcher match = VERSION.matcher(incremental);
        return match.find() ? Integer.parseInt(match.group(1)) : 0;
    }

    private HyperOsVersion() {}
}
