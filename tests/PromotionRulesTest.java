package io.github.miclean.module;

import java.util.*;

public final class PromotionRulesTest {
    static int checks;
    static final class Feed {final int id;final boolean ad;Feed(int id,boolean ad){this.id=id;this.ad=ad;}}
    public static void main(String[] args)throws Exception{
        for(int mask=0;mask<16;mask++){
            List<Feed> original=new ArrayList<>(),expected=new ArrayList<>();
            for(int i=0;i<4;i++){Feed f=new Feed(i,(mask&(1<<i))!=0);original.add(f);if(!f.ad)expected.add(f);}
            List<Feed> snapshot=new ArrayList<>(original);
            Object result=AdFeedFilter.filter(Collections.unmodifiableList(original),Feed.class,item->((Feed)item).ad);
            check(((List<?>)result).equals(expected));check(original.equals(snapshot));check(result instanceof List<?>);
            for(int i=0;i<expected.size();i++)check(((List<?>)result).get(i)==expected.get(i));
        }
        Object[] unchanged={null,"not a feed list",new Object(),Collections.emptyList(),Arrays.asList(new Feed(0,false),null)};
        for(Object value:unchanged)check(AdFeedFilter.filter(value,Feed.class,item->false)==value);
        List<Object> unknown=Arrays.asList(new Feed(0,true),new Object());
        check(AdFeedFilter.filter(unknown,Feed.class,item->{throw new AssertionError("Unknown list must not be inspected");})==unknown);
        List<Object> mixed=Arrays.asList(new Feed(0,false),null,new Feed(1,true));
        check(((List<?>)AdFeedFilter.filter(mixed,Feed.class,item->((Feed)item).ad)).size()==2);
        check(((List<?>)AdFeedFilter.filter(mixed,Feed.class,item->((Feed)item).ad)).get(1)==null);
        List<Feed> errorList=Arrays.asList(new Feed(0,true),new Feed(1,false));List<Feed> snapshot=new ArrayList<>(errorList);
        try{AdFeedFilter.filter(errorList,Feed.class,item->{throw new IllegalStateException("unknown schema");});throw new AssertionError();}catch(IllegalStateException expected){check(errorList.equals(snapshot));}
        Set<String> keys=new HashSet<>(Arrays.asList(PromotionRules.KEYS));check(keys.size()==4);
        for(int mask=0;mask<64;mask++){
            boolean[] legacy=new boolean[6];for(int i=0;i<6;i++)legacy[i]=(mask&(1<<i))!=0;
            check(PromotionRules.mergedRecommendations(false,false,legacy)==(mask!=0));
            check(PromotionRules.mergedRecommendations(false,true,legacy)==(mask!=0));
            check(!PromotionRules.mergedRecommendations(true,false,legacy));
            check(PromotionRules.mergedRecommendations(true,true,legacy));
        }
        check(PromotionRules.LEGACY_MARKET_RECOMMEND.length==6);
        check(!keys.contains("market_detail_likes"));
        for(String pkg:new String[]{Rules.BROWSER,Rules.MARKET}){
            check(Rules.targetProcess(pkg,pkg));check(!Rules.targetProcess(pkg,pkg+":download"));check(!Rules.targetProcess(pkg,"system_server"));
        }
        for(PromotionRules.Call call:PromotionRules.CALLS){
            check(PromotionRules.belongs(call.key,call.pkg));check(keys.contains(call.key));
            check(call.owner.startsWith(call.pkg+"."));check(call.result.equals("boolean")||call.result.equals("void"));
            check(!call.name.equals("equals")&&!call.name.toLowerCase().contains("compat")&&!call.name.toLowerCase().contains("signature")&&!call.name.toLowerCase().contains("install"));
        }
        for(String key:PromotionRules.KEYS)for(String pkg:new String[]{"android","com.android.systemui",Rules.MODULE,Rules.ACCOUNT,"com.miui.packageinstaller","com.miui.securitycenter",null})check(!PromotionRules.belongs(key,pkg));
        check(!PromotionRules.belongs("unknown",Rules.MARKET));check(!PromotionRules.belongs(null,Rules.BROWSER));
        System.out.println("PASS: "+checks+" browser/market scope, retained-content, immutable-list, unknown-schema and advertising-only boundary assertions");
    }
    static void check(boolean b){checks++;if(!b)throw new AssertionError("Promotion check "+checks);}
}
