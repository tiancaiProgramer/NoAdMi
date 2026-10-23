package io.github.miclean.module;

import java.util.ArrayList;
import java.util.List;

/** Copy only changed, homogeneous feed lists. Unknown and unrelated results stay identical. */
final class AdFeedFilter {
    interface Check { boolean ad(Object item)throws Exception; }
    static Object filter(Object result,Class<?> model,Check check)throws Exception{
        if(!(result instanceof List<?>))return result;
        List<?> source=(List<?>)result;
        for(Object item:source)if(item!=null&&!model.isInstance(item))return result;
        ArrayList<Object> copy=null;
        for(int i=0;i<source.size();i++){
            Object item=source.get(i);
            if(item!=null&&check.ad(item)){
                if(copy==null){copy=new ArrayList<>(source.size());copy.addAll(source.subList(0,i));}
            }else if(copy!=null)copy.add(item);
        }
        return copy==null?result:copy;
    }
    private AdFeedFilter(){}
}
