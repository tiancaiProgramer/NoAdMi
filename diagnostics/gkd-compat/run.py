#!/usr/bin/env python3
"""Run the read-only compatibility probe against a locally supplied subscription."""
from pathlib import Path
import argparse, os, subprocess, sys
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',required=True,type=Path)
p.add_argument('--kotlin-compiler-dir',required=True,type=Path)
p.add_argument('--subscription',required=True,type=Path)
p.add_argument('--module-jar',type=Path,default=root/'build/smart-skip.jar')
p.add_argument('--output-dir',type=Path,default=root/'build/gkd-compat')
a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
java=a.java_home/'bin'/('java.exe' if os.name=='nt' else 'java')
runtime=sorted((root/'third_party/dependencies').glob('*.jar'))
compiler=sorted(a.kotlin_compiler_dir.glob('*.jar'))
if not java.is_file() or not a.module_jar.is_file() or not a.subscription.is_file():p.error('JDK, compiled module JAR or subscription file is missing')
if not any(x.name.startswith('kotlin-compiler-embeddable-2.4.21') for x in compiler):p.error('Kotlin 2.4.21 compiler dependencies are required')
cp=os.pathsep.join(map(str,[a.module_jar,*runtime]));out=a.output_dir/'probe.jar'
subprocess.run([str(java),'-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11','-classpath',cp,'-d',str(out),str(Path(__file__).with_name('CompatibilityProbe.kt'))],check=True)
result=subprocess.check_output([str(java),'-cp',str(out)+os.pathsep+cp,'audit.CompatibilityProbeKt',str(a.subscription)],text=True)
(a.output_dir/'result.txt').write_text(result,encoding='utf-8');print(result,end='')
