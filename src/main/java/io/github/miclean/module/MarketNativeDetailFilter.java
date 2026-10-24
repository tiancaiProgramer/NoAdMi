package io.github.miclean.module;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Only the separate native detail recommendation response; never basic info/comments. */
final class MarketNativeDetailFilter {
    private final List<WeakReference<Object>> responses=new ArrayList<>();

    synchronized void remember(String api,Object response){
        if(!"app/directScreenFeeds4Pad/{appId}".equals(api)||response==null)return;
        for(Iterator<WeakReference<Object>> i=responses.iterator();i.hasNext();){
            Object existing=i.next().get();
            if(existing==null)i.remove();
            else if(existing==response)return;
        }
        responses.add(new WeakReference<>(response));
    }

    synchronized Object filter(Object response,Object result,Class<?> component){
        boolean matched=false;
        for(Iterator<WeakReference<Object>> i=responses.iterator();i.hasNext();){
            Object existing=i.next().get();
            if(existing==null)i.remove();
            else if(existing==response)matched=true;
        }
        if(!matched||!(result instanceof List<?>)||component==null)return result;
        List<?> list=(List<?>)result;
        if(list.isEmpty())return result;
        for(Object item:list)if(!component.isInstance(item))return result;
        // Do not mutate cached/shared lists. The caller still completes normal loading.
        return new ArrayList<>();
    }
}
