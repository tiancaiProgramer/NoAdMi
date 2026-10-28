package io.github.miclean.module;

import java.text.Collator;
import java.util.*;

/** Pure list projection: display filters never change rule enablement. */
public final class AppRuleListModel {
    public static final int SYSTEM=1,USER=2,UNINSTALLED=4;
    public static final int NAME=0,USED=1,TRIGGERED=2;
    public static final class Entry {
        public final String id,name; public final int type,groups; public final long used,triggered;
        public Entry(String id,String name,int type,int groups,long used,long triggered){this.id=id;this.name=name;this.type=type;this.groups=groups;this.used=used;this.triggered=triggered;}
    }
    public static List<Entry> filter(List<Entry> entries,String query,int mask,int order,boolean onlyWhitelist,Set<String> whitelist){
        String needle=query.trim().toLowerCase(Locale.ROOT);List<Entry> result=new ArrayList<>();
        for(Entry e:entries)if((mask&e.type)!=0&&(!onlyWhitelist||whitelist.contains(e.id))&&(e.name+" "+e.id).toLowerCase(Locale.ROOT).contains(needle))result.add(e);
        Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Comparator<Entry> byName=(a,b)->{int installed=Boolean.compare(a.type==UNINSTALLED,b.type==UNINSTALLED);if(installed!=0)return installed;int name=collator.compare(a.name,b.name);return name!=0?name:a.id.compareTo(b.id);};
        Comparator<Entry> comparator=order==USED?Comparator.<Entry>comparingLong(e->e.used).reversed().thenComparing(byName):order==TRIGGERED?Comparator.<Entry>comparingLong(e->e.triggered).reversed().thenComparing(byName):byName;
        result.sort(comparator);return result;
    }
    private AppRuleListModel(){}
}
