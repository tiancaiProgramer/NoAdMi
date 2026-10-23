package io.github.miclean.module;

public final class BulkEnablePolicyTest {
    static int checks;
    public static void main(String[] args){
        for(String key:new String[]{null,"unknown",Rules.HOME_REGION_SWITCH,Rules.HEALTH_REGION_SWITCH,Rules.HEALTH_SPORT_GROUP_SWITCH,"show_launcher_icon","about","personalized_ad"})check(!BulkEnablePolicy.eligible(key));
        for(String id:Rules.HOME_TAB_IDS)check(!BulkEnablePolicy.eligible(Rules.tabKey(id)));
        for(String id:Rules.HOME_MINE_IDS)check(!BulkEnablePolicy.eligible(Rules.mineKey(id)));
        for(String id:Rules.HEALTH_MINE_IDS)check(!BulkEnablePolicy.eligible(Rules.healthMineKey(id)));
        for(String key:PromotionRules.KEYS)check(BulkEnablePolicy.eligible(key));
        for(String key:new String[]{Rules.HOME_SWITCH,Rules.HOME_BANNER_SWITCH,Rules.HEALTH_SWITCH,Rules.HEALTH_HOME_BANNER_SWITCH,Rules.HEALTH_DEVICE_BANNER_SWITCH,Rules.HEALTH_BANNER_SWITCH,Rules.HEALTH_MEMBER_SWITCH,Rules.HEALTH_SPORT_ACTIVITY_SWITCH,Rules.HEALTH_SPORT_BANNER_SWITCH,Rules.GAME_RECOMMEND_SWITCH,Rules.GAME_POPUP_SWITCH,Rules.NETWORK_KEYS[0],Rules.NETWORK_KEYS[1],Rules.NETWORK_KEYS[2],Rules.NETWORK_KEYS[3]})check(BulkEnablePolicy.eligible(key));
        System.out.println("PASS: "+checks+" advertising bulk eligibility and excluded functional entries, navigation, experiments and unknown-setting boundary assertions");
    }
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Bulk policy "+checks);}
}
