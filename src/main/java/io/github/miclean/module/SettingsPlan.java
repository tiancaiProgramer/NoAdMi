package io.github.miclean.module;

import java.util.HashMap;
import java.util.Map;

/** Computes migration changes without resetting existing framework settings. */
public final class SettingsPlan {
    private SettingsPlan() {}
    public static Map<String, Boolean> importValues(Map<String, ?> local, Map<String, ?> remote,
            boolean explicitlyStaged) {
        Map<String, Boolean> changes = new HashMap<>();
        if (!explicitlyStaged && Boolean.TRUE.equals(remote.get("modern_settings_ready_v1"))) return changes;
        for (Map.Entry<String, ?> entry : local.entrySet()) {
            String key = entry.getKey();
            if ("pending_remote_import_v1".equals(key) || "modern_settings_ready_v1".equals(key)) continue;
            if (entry.getValue() instanceof Boolean && (explicitlyStaged || !remote.containsKey(key)))
                changes.put(key, (Boolean) entry.getValue());
        }
        return changes;
    }
}
