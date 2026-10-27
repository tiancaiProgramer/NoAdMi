package io.github.miclean.module;
public final class AccessibilityBindingPolicyTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("binding acknowledgement "+checks);}
    public static void main(String[] args){
        String head="currentUserId=0\nUser state[\n attributes:{id=0, currentUser=true}\n";
        check(Boolean.TRUE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{{"+AccessibilityListPolicy.COMPONENT+"}}",0)));
        check(Boolean.FALSE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{}",0)));
        check(Boolean.TRUE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{{io.github.miclean.module/.skip.SkipAccessibilityService}}",0)));
        check(Boolean.FALSE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{{"+AccessibilityListPolicy.COMPONENT+"Other}}",0)));
        check(AccessibilityBindingPolicy.selected(head+" Enabled services:{}",10)==null);
        check(Boolean.FALSE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{}\nUser state[\n attributes:{id=10}\nEnabled services:{{"+AccessibilityListPolicy.COMPONENT+"}}",0)));
        check(Boolean.TRUE.equals(AccessibilityBindingPolicy.selected(head+" Enabled services:{}\nUser state[\n attributes:{id=10}\nEnabled services:{{"+AccessibilityListPolicy.COMPONENT+"}}",10)));
        check(AccessibilityBindingPolicy.selected("unknown format",0)==null);
        System.out.println("PASS: "+checks+" own-component binding acknowledgement, alias, user isolation and unknown-format checks");
    }
}
