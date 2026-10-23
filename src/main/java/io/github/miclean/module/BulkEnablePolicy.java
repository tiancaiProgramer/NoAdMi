package io.github.miclean.module;

import java.util.Arrays;

/** Bulk advertising only: functional entry hiding is always an individual choice. */
final class BulkEnablePolicy {
    static boolean eligible(String key){
        if(key==null)return false;
        if(Arrays.asList(PromotionRules.KEYS).contains(key))return true;
        return Arrays.asList(Rules.HOME_SWITCH,Rules.HOME_BANNER_SWITCH,Rules.HEALTH_SWITCH,Rules.HEALTH_BANNER_SWITCH,
            Rules.HEALTH_HOME_BANNER_SWITCH,Rules.HEALTH_DEVICE_BANNER_SWITCH,Rules.HEALTH_MEMBER_SWITCH,
            Rules.HEALTH_SPORT_ACTIVITY_SWITCH,Rules.HEALTH_SPORT_BANNER_SWITCH,
            Rules.GAME_RECOMMEND_SWITCH,Rules.GAME_POPUP_SWITCH,Rules.NETWORK_KEYS[2],Rules.NETWORK_KEYS[3]).contains(key);
    }
    private BulkEnablePolicy(){}
}
