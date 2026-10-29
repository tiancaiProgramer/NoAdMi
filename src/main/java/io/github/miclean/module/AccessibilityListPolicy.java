package io.github.miclean.module;

import java.util.ArrayList;
import java.util.List;

/** Only the module's own component can be changed; all other services keep their order. */
public final class AccessibilityListPolicy {
    public static final String PACKAGE="io.github.miclean.module";
    public static final String SERVICE_CLASS="com.google.android.accessibility.selecttospeak.SelectToSpeakService";
    public static final String COMPONENT=PACKAGE+"/"+SERVICE_CLASS;
    public static final String LEGACY_COMPONENT=PACKAGE+"/"+PACKAGE+".skip.SkipAccessibilityService";
    private static boolean own(String value){return COMPONENT.equals(value)||LEGACY_COMPONENT.equals(value)||value.equals(PACKAGE+"/.skip.SkipAccessibilityService");}
    public static boolean enabled(String raw){if(raw==null)return false;for(String item:raw.split(":"))if(own(item))return true;return false;}
    public static String change(String raw,boolean enabled){
        List<String> values=new ArrayList<>();boolean found=false;
        if(raw!=null&&!raw.isEmpty()&&!raw.equals("null"))for(String value:raw.split(":")){
            if(own(value)){if(enabled&&!found){values.add(COMPONENT);found=true;}}else if(!value.isEmpty())values.add(value);
        }
        if(enabled&&!found)values.add(COMPONENT);
        return String.join(":",values);
    }
    private AccessibilityListPolicy(){}
}
