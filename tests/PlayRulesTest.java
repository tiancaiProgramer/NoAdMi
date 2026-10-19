import io.github.miclean.module.Rules;
import java.util.*;

public final class PlayRulesTest {
    private static int assertions;
    public static void main(String[] args) {
        check(Rules.healthRules(Rules.HEALTH));
        check(Rules.healthRules(Rules.HEALTH_PLAY));
        for (String pkg : new String[]{null, Rules.HOME, Rules.MODULE,
                Rules.HEALTH_PLAY + ":remote", Rules.HEALTH_PLAY + ".other"})
            check(!Rules.healthRules(pkg));
        check(!Rules.targetProcess(Rules.HEALTH_PLAY, Rules.HEALTH));
        check(!Rules.targetProcess(Rules.HEALTH, Rules.HEALTH_PLAY));
        check(Rules.HOME_PLAY_MINE_VIEWS.length == Rules.HOME_MINE_IDS.length);
        check(Rules.HOME_PLAY_MINE_RESOURCE_NAMES.length == Rules.HOME_MINE_IDS.length);
        Set<Integer> selected = new HashSet<>();
        Set<Integer> play = new HashSet<>();
        for (int i = 0; i < Rules.HOME_MINE_IDS.length; i++) {
            int mainlandId = Rules.HOME_MINE_VIEWS[i], playId = Rules.HOME_PLAY_MINE_VIEWS[i];
            check(Rules.homeMineViews(Collections.singleton(mainlandId), false)
                .equals(Collections.singleton(mainlandId)));
            check(Rules.homeMineViews(Collections.singleton(mainlandId), true)
                .equals(Collections.singleton(playId)));
            selected.add(mainlandId);
            play.add(playId);
        }
        check(play.size() == 13);
        check(new HashSet<>(Arrays.asList(Rules.HOME_PLAY_MINE_RESOURCE_NAMES)).size() == 13);
        check(Rules.homeMineViews(selected, true).equals(play));
        check(Rules.homeMineViews(selected, false).equals(selected));
        check(selected.size() == 13);
        check(Rules.homeMineViews(Collections.emptySet(), true).isEmpty());
        // Unknown resource IDs must not be carried into either distribution's page.
        check(Rules.homeMineViews(Collections.singleton(-1), true).isEmpty());
        check(Rules.homeMineViews(Collections.singleton(-1), false).isEmpty());
        check(!Rules.HOME_REGION_SWITCH.equals(Rules.HEALTH_REGION_SWITCH));
        System.out.println("PASS: " + assertions + " Play rule loading and independent resource-map assertions");
    }
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Play rule boundary " + assertions);
    }
}
