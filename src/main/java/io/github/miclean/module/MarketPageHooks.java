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
            Class<?> detail=marketClass(loader,"h52native.detail.DetailDirectFragment","business_ui.detail.DetailDirectFragment");
            Class<?> component=marketClass(loader,"h52native.componentbeans.BaseNativeComponent","common.component.componentbeans.BaseNativeComponent");
            if("com.xiaomi.market.business_ui.detail.DetailDirectFragment".equals(detail.getName())){
                Method bind=detail.getDeclaredMethod("bindRecommendData",List.class);
                if(bind.getReturnType()!=void.class)throw new NoSuchMethodException("native recommendation binding");
                HookRuntime.hookMethod(bind,new MethodHook(){
                    final AtomicBoolean logged=new AtomicBoolean();
                    @Override protected void beforeHookedMethod(Param p){
                        Object original=p.args[0],filtered=MarketNativeDetailFilter.withoutRecommendations(original,component);
                        if(filtered!=original){p.args[0]=filtered;
                            if(logged.compareAndSet(false,true))HookEntry.report("hit market.detail.native binding removed="+((List<?>)original).size());}
                    }
                });
                HookEntry.report("installed market.detail.native recommendation binding");
                return;
            }
            Class<?> task=marketClass(loader,"h52native.base.ResponseTask","business_ui.base.ResponseTask");
            Class<?> context=marketClass(loader,"h52native.INativeFragmentContext","common.component.base.INativeFragmentContext");
            Method request=task.getMethod("getRequestApi"),data=task.getMethod("getData");
            Class<?> model=marketClass(loader,"h52native.base.BaseViewModel","business_ui.base.BaseViewModel");
            Method parse=model.getDeclaredMethod("parseResponse",context,int.class,org.json.JSONObject.class);
            if(request.getReturnType()!=String.class||data.getReturnType()!=org.json.JSONObject.class||parse.getReturnType()!=List.class)
                throw new NoSuchMethodException("native detail response");
            MarketNativeDetailFilter filter=new MarketNativeDetailFilter();
            // The response is queued and parsed on another coroutine. Track its identity,
            // rather than a thread-local flag, and leave the response/queue callbacks intact.
            Method receiver=null;boolean modelReceiver=false;
            try{receiver=detail.getDeclaredMethod("handleResponseData",task);}
            catch(NoSuchMethodException moved){
                for(Method candidate:model.getDeclaredMethods()){
                    Class<?>[] args=candidate.getParameterTypes();
                    if(candidate.getName().equals("handleResponseData")&&candidate.getReturnType()==void.class
                            &&args.length==3&&args[0]==context&&args[1]==task&&args[2].isInterface()){
                        if(receiver!=null)throw new NoSuchMethodException("ambiguous native response receiver");
                        receiver=candidate;modelReceiver=true;
                    }
                }
            }
            if(receiver==null)throw new NoSuchMethodException("native response receiver");
            final boolean fromModel=modelReceiver;
            HookRuntime.hookMethod(receiver,new MethodHook(){
                @Override protected void beforeHookedMethod(Param p)throws Throwable{
                    Object owner=fromModel?p.args[0]:p.thisObject;
                    Object responseTask=p.args[fromModel?1:0];
                    if(!detail.isInstance(owner)||!task.isInstance(responseTask))return;
                    String api=(String)request.invoke(responseTask);
                    filter.remember(api,data.invoke(responseTask));
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
            Reflect.findAndHookMethod(marketClass(loader,"webview.CommonWebView$3","common.webview.CommonWebView$3"),"onPageFinished",WebView.class,String.class,new MethodHook(){
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
            Class<?> context=marketClass(loader,"h52native.INativeFragmentContext","common.component.base.INativeFragmentContext");
            Class<?> home=marketClass(loader,"h52native.pages.HomeFeatureTabFragment","business_ui.main.home.HomeFeatureTabFragment");
            Class<?> component=marketClass(loader,"h52native.componentbeans.BaseNativeComponent","common.component.componentbeans.BaseNativeComponent");
            Method type=component.getMethod("getComponentType");
            Method parse=marketClass(loader,"h52native.base.BaseViewModel","business_ui.base.BaseViewModel").getDeclaredMethod("parseResponse",context,int.class,org.json.JSONObject.class);
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
    private static Class<?> marketClass(ClassLoader loader,String previous,String current){
        Class<?> type=Reflect.findClassIfExists("com.xiaomi.market."+previous,loader);
        return type!=null?type:Reflect.findClass("com.xiaomi.market."+current,loader);
    }
    private MarketPageHooks(){}
}
