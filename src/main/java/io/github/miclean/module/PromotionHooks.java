package io.github.miclean.module;

import android.content.SharedPreferences;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only host-native advertising decisions; no global networking or webpage interception. */
final class PromotionHooks {
    static void install(String pkg,ClassLoader loader,SharedPreferences prefs){
        for(PromotionRules.Call call:PromotionRules.CALLS){
            if(!call.pkg.equals(pkg)||!(PromotionRules.MARKET_RECOMMEND.equals(call.key)?PromotionSettings.recommendations(prefs):prefs.getBoolean(call.key,false)))continue;
            try{
                Method method=null;PromotionRules.Call selected=null;
                for(PromotionRules.Call variant=call;variant!=null;variant=variant.fallback){
                    try{
                        Class<?> owner=Reflect.findClass(variant.owner,loader);Class<?>[] args=new Class<?>[variant.args.length];
                        for(int i=0;i<args.length;i++)args[i]=type(variant.args[i],loader);
                        Method candidate=owner.getDeclaredMethod(variant.name,args);
                        if(candidate.getReturnType()!=type(variant.result,loader))continue;
                        method=candidate;selected=variant;break;
                    }catch(IllegalArgumentException|NoSuchMethodException absent){ }
                }
                if(method==null)throw new NoSuchMethodException("advertising return contract");
                final PromotionRules.Call resolved=selected;
                HookRuntime.hookMethod(method,new MethodHook(){
                    final AtomicBoolean logged=new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p){
                        p.setResult(call.result.equals("boolean")?Boolean.FALSE:null);
                        if(logged.compareAndSet(false,true))HookEntry.report("hit "+call.key+" "+resolved.owner+"."+resolved.name);
                    }
                });
                HookEntry.report("installed "+call.key+" "+resolved.owner+"."+resolved.name);
            }catch(Throwable e){HookEntry.report("skip "+call.key+" "+call.owner+"."+call.name+": "+e.getClass().getSimpleName());}
        }
        if(Rules.MARKET.equals(pkg))MarketPageHooks.install(loader,prefs);
        if(Rules.BROWSER.equals(pkg)&&prefs.getBoolean(PromotionRules.BROWSER_FEED,false))feed(loader);
    }
    private static void feed(ClassLoader loader){
        try{
            Class<?> model=Reflect.findClass("com.xiaomi.browser.infoflow.BrowserFeedModel",loader);
            Class<?> adInfo=Reflect.findClass("com.xiaomi.feed.model.AdInfo",loader);
            Method ad=model.getMethod("getAdInfo"),tag=adInfo.getMethod("getTagId"),template=adInfo.getMethod("getTemplate");
            if(ad.getReturnType()!=adInfo||tag.getReturnType()!=String.class||template.getReturnType()!=String.class)throw new NoSuchMethodException("feed ad contract");
            Method result=Reflect.findClass("com.xiaomi.browser.infoflow.ResultResponse",loader).getDeclaredMethod("getData");
            if(result.getReturnType()!=Object.class)throw new NoSuchMethodException("feed response contract");
            HookRuntime.hookMethod(result,new MethodHook(){
                final AtomicBoolean logged=new AtomicBoolean();
                @Override protected void afterHookedMethod(Param p)throws Throwable{
                    if(p.hasThrowable())return;
                    Object original=p.getResult();
                    Object filtered=AdFeedFilter.filter(original,model,item->{Object info=ad.invoke(item);return info!=null&&(marked(tag.invoke(info))||marked(template.invoke(info)));});
                    if(filtered!=original){p.setResult(filtered);if(logged.compareAndSet(false,true))HookEntry.report("hit browser_feed_ads marked feed cards filtered");}
                }
            });
            HookEntry.report("installed browser_feed_ads marked feed response filter");
        }catch(Throwable e){HookEntry.report("skip browser_feed_ads: "+e.getClass().getSimpleName());}
    }
    private static boolean marked(Object value){return value instanceof String&&!((String)value).trim().isEmpty();}
    private static Class<?> type(String name,ClassLoader loader)throws ClassNotFoundException{
        if(name.equals("boolean"))return boolean.class;if(name.equals("void"))return void.class;if(name.equals("int"))return int.class;return Reflect.findClass(name,loader);
    }
    private PromotionHooks(){}
}
