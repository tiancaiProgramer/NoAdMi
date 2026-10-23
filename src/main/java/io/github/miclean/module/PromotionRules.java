package io.github.miclean.module;

/** Narrow native advertising decisions, verified against the tested Browser/GetApps APKs. */
final class PromotionRules {
    static final String BROWSER_SPLASH="browser_splash",BROWSER_FEED="browser_feed_ads";
    static final String MARKET_SPLASH="market_splash",MARKET_RECOMMEND="market_recommend";
    static final String[] LEGACY_MARKET_RECOMMEND={"market_start_recommend","market_page_recommend","market_minicard_ads","market_detail_likes","market_detail_tools","market_home_banner"};
    static final String[] KEYS={BROWSER_SPLASH,BROWSER_FEED,MARKET_SPLASH,MARKET_RECOMMEND};
    static boolean mergedRecommendations(boolean hasUnified,boolean unified,boolean...legacy){
        if(hasUnified)return unified;
        for(boolean value:legacy)if(value)return true;
        return false;
    }
    static final class Call {
        final String pkg,key,owner,name,result;
        final String[] args;
        Call(String pkg,String key,String owner,String name,String result,String...args){this.pkg=pkg;this.key=key;this.owner=owner;this.name=name;this.result=result;this.args=args;}
    }
    static final Call[] CALLS={
        new Call(Rules.BROWSER,BROWSER_SPLASH,"com.android.browser.splash.SplashActiveAdManager","m","void","int"),
        new Call(Rules.BROWSER,BROWSER_SPLASH,"com.android.browser.splash.b","e","boolean"),
        new Call(Rules.BROWSER,BROWSER_SPLASH,"com.android.browser.splash.b","f","boolean","java.lang.String"),
        new Call(Rules.MARKET,MARKET_SPLASH,"com.xiaomi.market.ui.BaseActivity","needShowSplash","boolean"),
        new Call(Rules.MARKET,MARKET_SPLASH,"com.xiaomi.market.ui.splash.SplashManager","needShowSplash","boolean","android.app.Activity"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.RecommendPageController","needShowFirstRecommend","boolean","android.app.Activity"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.RecommendPageController","needShowRecallRecommend","boolean","android.app.Activity"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isDownloadRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isUpdateAppRelatedRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isUpdateGridRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isUpdateHistoryGridRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isUpdateHistoryListRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.model.AdSwitch","isUpdateListRecommendSupported","boolean"),
        new Call(Rules.MARKET,MARKET_RECOMMEND,"com.xiaomi.market.ui.minicard.data.SuperMiniCardAppInfo","isAdEnable","boolean")
    };
    static boolean belongs(String key,String pkg){return Rules.BROWSER.equals(pkg)?BROWSER_SPLASH.equals(key)||BROWSER_FEED.equals(key):Rules.MARKET.equals(pkg)&&(MARKET_SPLASH.equals(key)||MARKET_RECOMMEND.equals(key));}
    private PromotionRules(){}
}
