"""Run the real Android tree adapter against counted JVM platform doubles.

The supplied jars must contain the production adapter/core. No doubles are
included in the Android APK. Optional baseline jar measures the same workload.
"""
from pathlib import Path
import argparse, os, subprocess

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--java-home',required=True);p.add_argument('--compiler-dir',required=True)
 p.add_argument('--runtime-jar',required=True);p.add_argument('--baseline-jar')
 p.add_argument('--work-dir',required=True);a=p.parse_args()
 root=Path(__file__).resolve().parents[1];work=Path(a.work_dir).resolve();work.mkdir(parents=True,exist_ok=True)
 classes=work/'classes';classes.mkdir(exist_ok=True)
 jdk=Path(a.java_home);suffix='.exe' if os.name=='nt' else ''
 java=jdk/'bin'/('java'+suffix);javac=jdk/'bin'/('javac'+suffix)
 def run(args):subprocess.run(list(map(str,args)),check=True)
 run([javac,'-d',classes,*sorted((root/'tests/android-tree/android').rglob('*.java'))])
 deps=sorted((root/'third_party/dependencies').glob('*.jar'))
 compiler=sorted(Path(a.compiler_dir).glob('*.jar'))
 testjar=work/'tests.jar';cp=os.pathsep.join(map(str,[classes,Path(a.runtime_jar),*deps]))
 run([java,'-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
      '-no-stdlib','-no-reflect','-jvm-target','11','-classpath',cp,'-d',testjar,root/'tests/android-tree/AndroidSkipTreeTest.kt'])
 for jar,label in [(a.baseline_jar,'baseline'),(a.runtime_jar,'optimized')]:
  if jar:run([java,'-cp',os.pathsep.join(map(str,[classes,testjar,Path(jar),*deps])),
              'io.github.miclean.module.skip.AndroidSkipTreeTestKt',label])

if __name__=='__main__':main()
