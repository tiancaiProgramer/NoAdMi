package io.github.miclean.module;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only acknowledgement of our component in the current user's manager state. */
final class AccessibilityBindingPolicy {
    private static final Pattern USER=Pattern.compile("\\bid=(\\d+)\\b");
    private static final Pattern OWN=Pattern.compile("(?:"+Pattern.quote(AccessibilityListPolicy.COMPONENT)+"|"+Pattern.quote(AccessibilityListPolicy.LEGACY_COMPONENT)+"|"+Pattern.quote(AccessibilityListPolicy.PACKAGE+"/.skip.SkipAccessibilityService")+")(?=[\\s}\\],]|$)");
    static Boolean selected(String dump,int user){
        Integer section=null;boolean inUser=false;
        for(String raw:dump.split("\\n")){
            String line=raw.trim();
            if(line.startsWith("User state[")){section=null;inUser=true;}
            if(inUser){Matcher match=USER.matcher(line);if(match.find())section=Integer.parseInt(match.group(1));}
            if(section!=null&&section==user&&line.startsWith("Enabled services:"))return OWN.matcher(line.substring("Enabled services:".length())).find();
        }
        return null;
    }
    private AccessibilityBindingPolicy(){}
}
