package io.github.miclean.module;

import java.util.HashSet;
import java.util.Set;

public final class NativeAdRulesTest {
    static int count;
    public static void main(String[] args) {
        check(NativeAdRules.ENTRIES.length == 11);
        Set<String> ids = new HashSet<>();
        for (NativeAdRules.Entry e : NativeAdRules.ENTRIES) {
            check(ids.add(e.id));
            check(e.id.matches("[a-z_]+") && e.group.matches("[a-z]+"));
            check(e.pkg.matches("[a-z0-9.]+"));
            check(NativeAdRules.find(e.id) == e);
            for (NativeAdRules.Entry other : NativeAdRules.group(e.group)) {
                check(other.pkg.equals(e.pkg));
                check(other.kind == e.kind);
                check(other.file.equals(e.file));
            }
            check(!e.pkg.equals(Rules.MODULE));
            check(!e.pkg.equals("android"));
            check(!e.pkg.equals("com.android.fileexplorer"));
            check(!e.pkg.equals(Rules.SECURITY_CENTER));
            if (e.kind == NativeAdRules.PREFS || e.kind == NativeAdRules.MMKV) {
                check(e.keys.length > 0);
                check(new HashSet<>(java.util.Arrays.asList(e.keys)).size() == e.keys.length);
            }
        }
        check(NativeAdRules.find("downloads_content").keys.length == 2);
        check(NativeAdRules.find("system_tools").kind == NativeAdRules.ACCOUNT);
        for (String bad : new String[]{null,"","../x",";reboot","system_tools false","unknown"}) {
            try { NativeAdRules.find(bad); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { count++; }
            try { NativeAdRules.group(bad); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { count++; }
        }
        check(NativeAdRules.groups().size() == 8);
        check(NativeAdRules.backgroundWriteCount() == 11);
        for (NativeAdRules.Entry entry : NativeAdRules.ENTRIES)
            check(NativeAdRules.supportsBackgroundWrite(entry));
        for (int bits = 0; bits < 256; bits++) {
            check((NativeAdRules.personalizedBits(bits, true) & 1) == 1);
            check((NativeAdRules.personalizedBits(bits, false) & 1) == 0);
            check((NativeAdRules.personalizedBits(bits, true) & ~1) == (bits & ~1));
            check((NativeAdRules.personalizedBits(bits, false) & ~1) == (bits & ~1));
        }
        System.out.println("PASS: " + count + " official toggle allowlist, grouped storage and command-boundary assertions");
    }
    static void check(boolean ok) { count++; if(!ok)throw new AssertionError("Native rules " + count); }
}
