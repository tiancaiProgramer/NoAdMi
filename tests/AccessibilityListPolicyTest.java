package io.github.miclean.module;

public final class AccessibilityListPolicyTest {
    static int count;
    static void check(boolean value){count++;if(!value)throw new AssertionError("Accessibility-list regression "+count);}
    public static void main(String[] args){
        String own=AccessibilityListPolicy.COMPONENT,shortName="io.github.miclean.module/.skip.SkipAccessibilityService";
        String[] other={"a.app/.Reader","b.app/b.app.Helper","io.github.miclean.module/.OtherService"};
        for(int mask=0;mask<8;mask++){
            String raw="";for(int i=0;i<3;i++)if((mask&(1<<i))!=0)raw+=(raw.isEmpty()?"":":")+other[i];
            String added=AccessibilityListPolicy.change(raw,true);check(AccessibilityListPolicy.enabled(added));check(AccessibilityListPolicy.change(added,true).equals(added));check(AccessibilityListPolicy.change(added,false).equals(raw));check(AccessibilityListPolicy.change(raw,false).equals(raw));
            for(String alias:new String[]{own,shortName}){String mixed=alias+(raw.isEmpty()?"":":"+raw)+":"+own;check(AccessibilityListPolicy.change(mixed,false).equals(raw));check(AccessibilityListPolicy.enabled(mixed));}
        }
        for(String empty:new String[]{null,"","null"}){check(AccessibilityListPolicy.change(empty,true).equals(own));check(AccessibilityListPolicy.change(empty,false).isEmpty());check(!AccessibilityListPolicy.enabled(empty));}
        check(!AccessibilityListPolicy.enabled(own+"Other"));check(AccessibilityListPolicy.change("x.app/.Service",true).equals("x.app/.Service:"+own));
        System.out.println("PASS: "+count+" own-component accessibility edits, alias/idempotence and other-service preservation checks");
    }
}
