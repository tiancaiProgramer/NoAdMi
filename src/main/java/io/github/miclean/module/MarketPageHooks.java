package io.github.miclean.module;

import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class MarketPageHooks {
    static void install(ClassLoader loader,SharedPreferences prefs){
        if(PromotionSettings.recommendations(prefs)){detail(loader);nativeDetail(loader);home(loader);}
    }
    private static void nativeDetail(ClassLoader loader){
        try{
            Class<?> detail=Reflect.findClass("com.xiaomi.market.h52native.detail.DetailDirectFragment",loader);
            Class<?> task=Reflect.findClass("com.xiaomi.market.h52native.base.ResponseTask",loader);
            Class<?> context=Reflect.findClass("com.xiaomi.market.h52native.INativeFragmentContext",loader);
            Class<?> component=Reflect.findClass("com.xiaomi.market.h52native.componentbeans.BaseNativeComponent",loader);
            Method request=task.getMethod("getRequestApi"),data=task.getMethod("getData");
            Method handle=detail.getDeclaredMethod("handleResponseData",task);
            Method parse=Reflect.findClass("com.xiaomi.market.h52native.base.BaseViewModel",loader).getDeclaredMethod("parseResponse",context,int.class,org.json.JSONObject.class);
            if(request.getReturnType()!=String.class||data.getReturnType()!=org.json.JSONObject.class||handle.getReturnType()!=void.class||parse.getReturnType()!=List.class)
                throw new NoSuchMethodException("native detail response");
            MarketNativeDetailFilter filter=new MarketNativeDetailFilter();
            // The response is queued and parsed on another coroutine. Track its identity,
            // rather than a thread-local flag, and leave the response/queue callbacks intact.
            HookRuntime.hookMethod(handle,new MethodHook(){
                @Override protected void beforeHookedMethod(Param p)throws Throwable{
                    if(detail.isInstance(p.thisObject)&&task.isInstance(p.args[0]))
                        filter.remember((String)request.invoke(p.args[0]),data.invoke(p.args[0]));
                }
            });
            HookRuntime.hookMethod(parse,new MethodHook(){
                final AtomicBoolean logged=new AtomicBoolean();
                @Override protected void afterHookedMethod(Param p){
                    if(p.hasThrowable()||!detail.isInstance(p.args[0])||!Integer.valueOf(0).equals(p.args[1]))return;
                    Object before=p.getResult(),after=filter.filter(p.args[2],before,component);
                    if(after!=before){
                        p.setResult(after);
                        if(logged.compareAndSet(false,true))HookEntry.report("hit market.detail.native removed="+((List<?>)before).size());
                    }
                }
            });
            HookEntry.report("installed market.detail.native recommendation filter");
        }catch(Throwable e){HookEntry.report("skip market.detail.native: "+e.getClass().getSimpleName());}
    }
    private static void detail(ClassLoader loader){
        try{
            Reflect.findAndHookMethod("com.xiaomi.market.webview.CommonWebView$3",loader,"onPageFinished",WebView.class,String.class,new MethodHook(){
                final AtomicBoolean logged=new AtomicBoolean();
                @Override protected void afterHookedMethod(Param p){
                    if(p.hasThrowable()||!(p.args[0] instanceof WebView)||!(p.args[1] instanceof String))return;
                    WebView web=(WebView)p.args[0];String resource;
                    try{resource=web.getResources().getResourceEntryName(web.getId());}catch(RuntimeException e){return;}
                    if(!"inner_webview".equals(resource))return;
                    Uri uri=Uri.parse((String)p.args[1]);
                    if(!MarketPagePolicy.detailPath(uri.getPath()))return;
                    boolean trusted="file".equals(uri.getScheme())||("https".equals(uri.getScheme())&&("app.market.xiaomi.com".equals(uri.getHost())||"web.market.xiaomi.com".equals(uri.getHost())));
                    if(!trusted)return;
                    web.evaluateJavascript(MarketDetailScript.source(),null);
                    if(logged.compareAndSet(false,true))HookEntry.report("installed market.detail recommendation observer");
                    web.postDelayed(()->web.evaluateJavascript("window.__noadmiMarketDetail?window.__noadmiMarketDetail.counts:null",value->{
                        try{org.json.JSONObject counts=new org.json.JSONObject(value);int removed=counts.optInt("cards");
                            if(removed>0)HookEntry.report("hit market.detail.cards removed="+removed);
                        }catch(Exception ignored){}
                    }),2200);
                }
            });
            HookEntry.report("installed market.detail.page-finished");
        }catch(Throwable e){HookEntry.report("skip market.detail.page-finished: "+e.getClass().getSimpleName());}
    }
    private static void home(ClassLoader loader){
        try{
            Class<?> context=Reflect.findClass("com.xiaomi.market.h52native.INativeFragmentContext",loader);
            Class<?> home=Reflect.findClass("com.xiaomi.market.h52native.pages.HomeFeatureTabFragment",loader);
            Class<?> component=Reflect.findClass("com.xiaomi.market.h52native.componentbeans.BaseNativeComponent",loader);
            Method type=component.getMethod("getComponentType");
            Method parse=Reflect.findClass("com.xiaomi.market.h52native.base.BaseViewModel",loader).getDeclaredMethod("parseResponse",context,int.class,org.json.JSONObject.class);
            if(parse.getReturnType()!=List.class||type.getReturnType()!=String.class)throw new NoSuchMethodException("home components");
            HookRuntime.hookMethod(parse,new MethodHook(){
                final AtomicBoolean logged=new AtomicBoolean();
                @Override protected void afterHookedMethod(Param p)throws Throwable{
                    if(p.hasThrowable()||!home.isInstance(p.args[0])||!Integer.valueOf(0).equals(p.args[1])||!(p.getResult() instanceof List<?>))return;
                    List<?> list=(List<?>)p.getResult();for(Object item:list)if(!component.isInstance(item))return;
                    List<Object> result=new ArrayList<>();
                    for(int i=0;i<list.size();i++){Object item=list.get(i);String kind=(String)type.invoke(item);if(!MarketPagePolicy.banner(kind,i))result.add(item);}
                    if(result.size()!=list.size()){p.setResult(result);if(logged.compareAndSet(false,true))HookEntry.report("hit market_home_banner removed="+(list.size()-result.size()));}
                }
            });HookEntry.report("installed market_home_banner component filter");
        }catch(Throwable e){HookEntry.report("skip market_home_banner: "+e.getClass().getSimpleName());}
    }
    private MarketPageHooks(){}
}
