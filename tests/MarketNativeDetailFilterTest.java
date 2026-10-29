package io.github.miclean.module;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MarketNativeDetailFilterTest {
    static int checks;
    static class Component {}
    static final class EqualResponse {
        @Override public boolean equals(Object other){return other instanceof EqualResponse;}
        @Override public int hashCode(){return 1;}
    }
    public static void main(String[] args)throws Exception{
        MarketNativeDetailFilter filter=new MarketNativeDetailFilter();
        Object recommend=new EqualResponse(),comment=new EqualResponse(),basic=new Object();
        List<Component> cards=Collections.unmodifiableList(Arrays.asList(new Component(),new Component()));
        filter.remember("usercomment/app/directScreen4Pad/list",comment);
        filter.remember("app/tabs/basicInfo/{appId}",basic);
        filter.remember("app/directScreenFeeds4Pad/{appId}",recommend);
        check(filter.filter(comment,cards,Component.class)==cards);
        check(((List<?>)MarketNativeDetailFilter.withoutRecommendations(cards,Component.class)).isEmpty());
        check(MarketNativeDetailFilter.withoutRecommendations(Arrays.asList(new Component(),"unknown"),Component.class) instanceof List<?>);
        check(filter.filter(basic,cards,Component.class)==cards);
        check(filter.filter(new EqualResponse(),cards,Component.class)==cards);
        Object removed=filter.filter(recommend,cards,Component.class);
        check(removed!=cards&&((List<?>)removed).isEmpty());
        check(cards.size()==2);
        // Coroutine/thread handoff and a repeated parse of the same cached response.
        final Object[] threaded=new Object[1];
        Thread worker=new Thread(()->threaded[0]=filter.filter(recommend,cards,Component.class));
        worker.start();worker.join();
        check(threaded[0] instanceof List<?> && ((List<?>)threaded[0]).isEmpty());
        check(((List<?>)filter.filter(recommend,cards,Component.class)).isEmpty());
        List<?> unknown=Arrays.asList(new Component(),"unknown");
        check(MarketNativeDetailFilter.withoutRecommendations(unknown,Component.class)==unknown);
        check(MarketNativeDetailFilter.withoutRecommendations(null,Component.class)==null);
        check(MarketNativeDetailFilter.withoutRecommendations(cards,null)==cards);
        List<?> nullable=Arrays.asList(new Component(),null);
        check(filter.filter(recommend,unknown,Component.class)==unknown);
        check(filter.filter(recommend,nullable,Component.class)==nullable);
        check(filter.filter(recommend,cards,null)==cards);
        check(filter.filter(recommend,null,Component.class)==null);
        check(filter.filter(recommend,"unexpected",Component.class).equals("unexpected"));
        List<?> empty=Collections.emptyList();
        check(filter.filter(recommend,empty,Component.class)==empty);
        check(filter.filter(null,cards,Component.class)==cards);
        Object currentRecommend=new Object();
        filter.remember("app/directScreenFeeds",currentRecommend);
        check(((List<?>)filter.filter(currentRecommend,cards,Component.class)).isEmpty());
        check(filter.filter(comment,cards,Component.class)==cards);
        for(String api:new String[]{null,"","app/directScreenFeeds4Pad/123","app/directScreenFeeds4Pad/{appId}/","app/tabs/recommend/{appId}","app/directScreenFeeds/{appId}"}){
            Object response=new Object();filter.remember(api,response);
            check(filter.filter(response,cards,Component.class)==cards);
        }
        filter.remember("app/directScreenFeeds4Pad/{appId}",null);
        filter.remember("app/directScreenFeeds4Pad/{appId}",recommend);
        check(((List<?>)filter.filter(recommend,cards,Component.class)).isEmpty());
        System.out.println("PASS: "+checks+" native-detail response isolation and preservation assertions");
    }
    static void check(boolean pass){checks++;if(!pass)throw new AssertionError("Native detail boundary "+checks);}
}
