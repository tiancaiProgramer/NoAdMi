package io.github.miclean.module;

/** A narrow, signature-protected IPC protocol; no account data crosses this boundary. */
final class AccountAdProtocol {
    static final String ACTION=Rules.MODULE+".action.ACCOUNT_AD_CONTROL";
    static final String PERMISSION=Rules.MODULE+".permission.ACCOUNT_AD_CONTROL";
    static final int RESPONSE=0x4e41;
    static boolean valid(String operation,String nonce,boolean hasValue) {
        return nonce!=null && nonce.matches("[a-f0-9]{32}")
            && (("ping".equals(operation)&&!hasValue)||("write".equals(operation)&&hasValue));
    }
    static boolean sender(int actualUid,int expectedUid,String actualPackage) {
        return expectedUid>=10000 && actualUid==expectedUid && Rules.MODULE.equals(actualPackage);
    }
    private AccountAdProtocol(){}
}
