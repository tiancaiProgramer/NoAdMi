package io.github.miclean.module;
import java.util.*;
public final class AppRuleListModelTest {
    private static void check(boolean b){if(!b)throw new AssertionError();}
    private static String ids(List<AppRuleListModel.Entry> entries){StringJoiner j=new StringJoiner(",");for(AppRuleListModel.Entry e:entries)j.add(e.id);return j.toString();}
    public static void main(String[] args){
        List<AppRuleListModel.Entry> entries=Arrays.asList(new AppRuleListModel.Entry("missing","A missing",4,1,0,0),new AppRuleListModel.Entry("user","B user",2,2,30,10),new AppRuleListModel.Entry("system","A system",1,1,20,40));
        Set<String> white=new HashSet<>(Arrays.asList("user","missing"));
        check(ids(AppRuleListModel.filter(entries,"",7,0,false,white)).equals("system,user,missing"));
        check(ids(AppRuleListModel.filter(entries,"",7,1,false,white)).equals("user,system,missing"));
        check(ids(AppRuleListModel.filter(entries,"",7,2,false,white)).equals("system,user,missing"));
        for(int mask=0;mask<8;mask++){List<AppRuleListModel.Entry> shown=AppRuleListModel.filter(entries,"",mask,0,false,white);check(shown.size()==Integer.bitCount(mask));for(AppRuleListModel.Entry e:shown)check((mask&e.type)!=0);}
        check(ids(AppRuleListModel.filter(entries,"",7,0,true,white)).equals("user,missing"));check(white.size()==2);
        check(ids(AppRuleListModel.filter(entries," SyStEm ",7,0,false,white)).equals("system"));check(AppRuleListModel.filter(entries,"不存在",7,0,false,white).isEmpty());
        List<AppRuleListModel.Entry> large=new ArrayList<>();for(int i=1999;i>=0;i--)large.add(new AppRuleListModel.Entry("pkg."+i,"名称"+i,2,2,i,2000-i));
        List<AppRuleListModel.Entry> projected=AppRuleListModel.filter(large,"",7,1,false,Collections.emptySet());check(projected.size()==2000);check(projected.get(0).used==1999);check(large.get(0).used==1999);check(projected!=large);
        System.out.println("PASS app rules: all display group combinations, independent whitelist filter, name/usage/trigger ordering, search and 2000-app projection");
    }
}
