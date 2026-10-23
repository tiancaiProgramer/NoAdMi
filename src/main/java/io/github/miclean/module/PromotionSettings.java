package io.github.miclean.module;

import android.content.SharedPreferences;

final class PromotionSettings {
    static boolean recommendations(SharedPreferences prefs){
        boolean[] legacy=new boolean[PromotionRules.LEGACY_MARKET_RECOMMEND.length];
        for(int i=0;i<legacy.length;i++)legacy[i]=prefs.getBoolean(PromotionRules.LEGACY_MARKET_RECOMMEND[i],false);
        return PromotionRules.mergedRecommendations(prefs.contains(PromotionRules.MARKET_RECOMMEND),prefs.getBoolean(PromotionRules.MARKET_RECOMMEND,false),legacy);
    }
    private PromotionSettings(){}
}
