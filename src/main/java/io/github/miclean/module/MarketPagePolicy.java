package io.github.miclean.module;

final class MarketPagePolicy {
    static boolean detailPath(String path){return path!=null&&(path.endsWith("/detail.html")||path.endsWith("/detailV2.html")||path.equals("detail.html")||path.equals("detailV2.html"));}
    static boolean banner(String type,int position){return position==0&&("nativeFeaturedBannerList".equals(type)||"nativeFeaturedHorizontalVideoList".equals(type));}
    private MarketPagePolicy(){}
}
