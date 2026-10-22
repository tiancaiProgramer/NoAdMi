package io.github.miclean.module;
public final class AccountAdProtocolTest {
 static int checks;
 public static void main(String[] args){
  String nonce="0123456789abcdef0123456789abcdef";
  check(AccountAdProtocol.valid("ping",nonce,false));check(AccountAdProtocol.valid("write",nonce,true));
  for(String op:new String[]{null,"","read","restore","open",";reboot"})check(!AccountAdProtocol.valid(op,nonce,true));
  for(String bad:new String[]{null,"",nonce+"0",nonce.substring(1),nonce.toUpperCase(),"../../../../../../../../../../../../"})check(!AccountAdProtocol.valid("write",bad,true));
  check(!AccountAdProtocol.valid("ping",nonce,true));check(!AccountAdProtocol.valid("write",nonce,false));
  for(int uid:new int[]{0,1000,2000,9999,10326,10327,110326}){
   check(AccountAdProtocol.sender(uid,10326,Rules.MODULE)==(uid==10326));
   check(!AccountAdProtocol.sender(uid,10326,"com.xiaomi.account"));
   check(!AccountAdProtocol.sender(uid,10326,null));
  }
  check(!AccountAdProtocol.sender(2000,2000,Rules.MODULE));
  check(Rules.targetProcess(Rules.ACCOUNT,Rules.ACCOUNT));check(!Rules.targetProcess(Rules.ACCOUNT,Rules.ACCOUNT+":remote"));
  check(!Rules.healthRules(Rules.ACCOUNT));
  System.out.println("PASS: "+checks+" account IPC sender, nonce, operation and process assertions");
 }
 static void check(boolean value){checks++;if(!value)throw new AssertionError("Account IPC "+checks);}
}
