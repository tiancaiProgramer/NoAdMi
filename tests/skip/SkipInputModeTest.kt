package io.github.miclean.module.skip

private var checks=0
private fun verify(v:Boolean){checks++;check(v){"Input mode assertion $checks"}}
private fun rejects(f:()->Unit){var rejected=false;try{f()}catch(_:IllegalArgumentException){rejected=true};verify(rejected)}
fun main(){
    verify(SkipInputMode.normalize(0)==SkipInputMode.COMPAT);verify(SkipInputMode.normalize(1)==SkipInputMode.GKD_COMMAND);verify(SkipInputMode.normalize(2)==SkipInputMode.GKD_VIRTUAL)
    for(v in listOf(-1,4,Int.MAX_VALUE,Int.MIN_VALUE))verify(SkipInputMode.normalize(v)==SkipInputMode.COMPAT)
    verify(SkipInputMode.normalize(3)==SkipInputMode.ACCESSIBILITY)
    verify(SkipInputMode.gkdCommand(12f,34f,12f,34f,0)=="input tap 12.0 34.0\nexit\n")
    verify(SkipInputMode.gkdCommand(12f,34f,56f,78f,500)=="input swipe 12.0 34.0 56.0 78.0 500\nexit\n")
    verify(SkipInputMode.gkdCommand(1f,1f,1f,1f,60000).endsWith("60000\nexit\n"))
    for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1f)){
        rejects{SkipInputMode.gkdCommand(v,1f,1f,1f,0)};rejects{SkipInputMode.gkdCommand(1f,1f,v,1f,0)}
    }
    rejects{SkipInputMode.gkdCommand(1f,1f,1f,1f,-1)};rejects{SkipInputMode.gkdCommand(1f,1f,1f,1f,60001)}
    verify(SkipInputMode.label(-9)==SkipInputMode.label(0));verify(SkipInputMode.label(1)!=SkipInputMode.label(2))
    println("PASS: $checks compatible-default, explicit input selection and finite fixed-command boundaries")
}
