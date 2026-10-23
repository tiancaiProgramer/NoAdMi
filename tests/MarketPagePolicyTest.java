package io.github.miclean.module;

public final class MarketPagePolicyTest {
    static int checks;
    public static void main(String[] args){
        for(String path:new String[]{"/data/user/0/com.xiaomi.market/files/web-res-3176/detailV2.html","/detail.html","detail.html","detailV2.html"})check(MarketPagePolicy.detailPath(path));
        for(String path:new String[]{null,"","/detail.html.evil","/foo.html","/detail.html/","/details.html","/comment.html","/search.html"})check(!MarketPagePolicy.detailPath(path));
        for(String type:new String[]{"nativeFeaturedHorizontalVideoList","nativeFeaturedBannerList"}){
            check(MarketPagePolicy.banner(type,0));for(int i=-1;i<=3;i++)if(i!=0)check(!MarketPagePolicy.banner(type,i));
        }
        for(String retained:new String[]{null,"","horizontalApps","nativeFeaturedMatrixList","recommend","listApp","nativeMyPageCarouselBanner","nativeFeaturedHorizontalVideoList_extra"})check(!MarketPagePolicy.banner(retained,0));
        System.out.println("PASS: "+checks+" exact detail-path and first-home-banner boundary assertions");
    }
    static void check(boolean b){checks++;if(!b)throw new AssertionError("Market page boundary "+checks);}
}
