import io.github.miclean.module.SettingsPlan;
import java.util.*;
public final class SettingsPlanTest {
    static int assertions;
    public static void main(String[] args) {
        for (boolean staged : new boolean[]{false, true}) {
            for (boolean ready : new boolean[]{false, true}) {
                for (boolean old : new boolean[]{false, true}) {
                    for (boolean current : new boolean[]{false, true}) {
                        Map<String, Object> local = new HashMap<>();
                        local.put("home_splash", old);
                        local.put("new_flag", false);
                        local.put("pending_remote_import_v1", true);
                        local.put("unsupported_type", "string");
                        Map<String, Object> remote = new HashMap<>();
                        remote.put("home_splash", current);
                        remote.put("modern_settings_ready_v1", ready);
                        Map<String, Boolean> result = SettingsPlan.importValues(local, remote, staged);
                        if (!staged && ready) check(result.isEmpty());
                        else {
                            check(Boolean.FALSE.equals(result.get("new_flag")));
                            if (staged) check(result.get("home_splash").equals(old));
                            else check(!result.containsKey("home_splash"));
                        }
                        check(!result.containsKey("pending_remote_import_v1"));
                        check(!result.containsKey("unsupported_type"));
                        check(remote.get("home_splash").equals(current));
                        check(local.get("home_splash").equals(old));
                    }
                }
            }
        }
        Map<String,Object> old = Collections.singletonMap("health_play_mainland", false);
        check(SettingsPlan.importValues(old, Collections.emptyMap(), false).get("health_play_mainland").equals(false));
        Map<String,Object> modern = new HashMap<>();
        modern.putAll(SettingsPlan.importValues(old, modern, false));
        modern.put("modern_settings_ready_v1", true);
        check(SettingsPlan.importValues(old, modern, false).isEmpty());
        System.out.println("PASS: " + assertions + " preference migration, false-state, idempotence and conflict assertions");
    }
    static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Migration " + assertions); }
}
