package io.github.miclean.module.skip

fun main() {
 var time=0L;var checks=0
 fun verify(value:Boolean){checks++;check(value){"Recovery assertion $checks"}}
 val policy=SkipRuntimeRecovery{time}
 verify(policy.failed()==1000L);verify(policy.failed()==3000L);verify(policy.failed()==8000L)
 repeat(100){verify(policy.failed()==null)}
 policy.reset();verify(policy.failed()==1000L)
 policy.connected();time+=20000;verify(policy.failed()==3000L)
 policy.connected();time+=29999;verify(policy.failed()==8000L)
 policy.connected();time+=30000;verify(policy.failed()==1000L)
 policy.connected();time+=15000;policy.connected();time+=15000
 verify(policy.failed()==1000L) // repeated ready notification cannot postpone stable recovery
 policy.reset();verify(policy.failed()==1000L);policy.connected()
 time+=10;verify(policy.failed()==3000L);policy.connected();time+=10
 verify(policy.failed()==8000L);policy.connected();time+=10;verify(policy.failed()==null)
 policy.reset();verify(policy.failed()==1000L)
 println("PASS: $checks bounded-runtime reconnect, flapping-worker budget and stable-reset checks")
}
