"""Drive SkipRuntimeRecoverySmoke on a selected test phone.

Install the module and androidTest APK, grant NoAdMi Shizuku access, and select
Root in NoAdMi first so its regular service does not compete with the probe.
Only the two explicitly requested NoAdMi runtime workers are terminated. The
test never restarts Shizuku or modifies preferences/subscriptions.
"""
from pathlib import Path
import argparse, subprocess, shlex, time
import xml.etree.ElementTree as ET

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
 p.add_argument('--log',required=True);a=p.parse_args()
 base=[a.adb,'-s',a.serial]
 def root(c):return subprocess.check_output(base+['exec-out','su -c '+shlex.quote(c)],text=True)
 prefs=ET.fromstring(root('cat /data/user/0/io.github.miclean.module/shared_prefs/smart_skip.xml'))
 method=next((n.text for n in prefs if n.get('name')=='access_method'),'root')
 if method!='root':raise RuntimeError('Select Root before this isolated probe; no preferences were changed.')
 phase='/data/user/0/io.github.miclean.module/cache/runtime-recovery-phase.txt'
 root('rm -f '+phase)
 process=subprocess.Popen(base+['shell','am','instrument','-w','io.github.miclean.tests/.SkipRuntimeRecoverySmoke'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 completed=set()
 try:
  time.sleep(1)
  subprocess.run(base+['shell','am','start','--user','0','--display','0','-W','-n','io.github.miclean.module/.MainActivity'],stdout=subprocess.DEVNULL,check=True)
  end=time.monotonic()+140
  while process.poll()is None and time.monotonic()<end:
   state=root('cat '+phase+' 2>/dev/null || true').strip()
   if state.startswith('KILL_OWN_WORKER_')and state not in completed:
    own=[]
    for line in root('ps -A -o PID,UID,ARGS').splitlines():
     parts=line.split(None,2)
     if len(parts)==3 and parts[1]in ['0','2000']and parts[2].strip()=='io.github.miclean.module:skip_runtime':own.append(int(parts[0]))
    if len(own)!=1:raise RuntimeError('Expected exactly one owned runtime worker; no process was terminated.')
    root('kill -9 '+str(own[0]));completed.add(state)
   time.sleep(.1)
  if process.poll()is None:raise TimeoutError('Recovery test did not finish')
  output=process.communicate(timeout=10)[0]
  Path(a.log).write_text(output,encoding='utf-8');print(output)
  if len(completed)!=2 or 'PASS: 13 recovery checks'not in output or 'FAIL:'in output:raise RuntimeError('Recovery test failed; see log')
 finally:
  if process.poll()is None:
   # Stop the remote instrumentation as well as the local adb client.
   # Only NoAdMi's process is affected; reopen it without changing options.
   root('am force-stop --user 0 io.github.miclean.module')
   process.kill();process.communicate()
   root('am start --user 0 --display 0 -W -n io.github.miclean.module/.MainActivity')
  root('rm -f '+phase)

if __name__=='__main__':main()
