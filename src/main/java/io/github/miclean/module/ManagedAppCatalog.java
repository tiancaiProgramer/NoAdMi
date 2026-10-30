package io.github.miclean.module;

import android.content.*;
import android.content.pm.*;
import android.content.res.XmlResourceParser;
import java.util.*;

/** Public package/resource APIs only; callers run this work off the main thread. */
final class ManagedAppCatalog {
    static final class App {
        final String id,name,version;final int code,type;final boolean system,launcher;
        App(PackageInfo info,String name,boolean launcher){
            ApplicationInfo ai=info.applicationInfo;id=info.packageName;this.name=name;version=info.versionName;
            code=android.os.Build.VERSION.SDK_INT>=28?(int)info.getLongVersionCode():info.versionCode;
            system=(ai.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
            type=system?AppRuleListModel.SYSTEM:AppRuleListModel.USER;this.launcher=launcher;
        }
    }
    static List<App> load(Context context){
        PackageManager pm=context.getPackageManager();List<App> result=new ArrayList<>();
        ResolveInfo home=pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        String launcher=home==null||home.activityInfo==null?null:home.activityInfo.packageName;
        for(PackageInfo info:pm.getInstalledPackages(PackageManager.GET_ACTIVITIES|PackageManager.MATCH_DISABLED_COMPONENTS)){
            if(Thread.currentThread().isInterrupted())return Collections.emptyList();
            ApplicationInfo ai=info.applicationInfo;if(ai==null||(ai.flags&ApplicationInfo.FLAG_INSTALLED)==0)continue;
            boolean system=(ai.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
            boolean hasActivity=info.activities!=null&&info.activities.length>0;
            if(system&&!hasActivity)hasActivity=pm.getLaunchIntentForPackage(info.packageName)!=null||
                !pm.queryIntentActivities(new Intent().setPackage(info.packageName),PackageManager.MATCH_DISABLED_COMPONENTS).isEmpty();
            if(!AppVisibilityPolicy.visible(system,system&&hasActivity&&overlay(pm,ai),hasActivity))continue;
            String name=info.packageName;try{name=ai.loadLabel(pm).toString();}catch(RuntimeException ignored){}
            result.add(new App(info,name,info.packageName.equals(launcher)));
        }
        return result;
    }
    private static boolean overlay(PackageManager pm,ApplicationInfo info){
        // overlayTarget is a hidden framework field. The manifest exposes the
        // same target through a public XML parser without hidden-API exemptions.
        try(XmlResourceParser xml=pm.getResourcesForApplication(info).getAssets().openXmlResourceParser("AndroidManifest.xml")){
            for(int event=xml.next();event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT;event=xml.next())
                if(event==org.xmlpull.v1.XmlPullParser.START_TAG&&xml.getDepth()==2&&"overlay".equals(xml.getName()))
                    return xml.getAttributeValue("http://schemas.android.com/apk/res/android","targetPackage")!=null;
        }catch(Exception unavailable){/* A failed resource read must not hide an otherwise visible app. */}
        return false;
    }
    private ManagedAppCatalog(){}
}
