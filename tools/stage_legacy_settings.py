#!/usr/bin/env python3
"""Back up and stage NoAdMi legacy NSP preferences on a rooted, authorized device.

Run before upgrading, or while the old NSP directory still exists. This does not
install an APK, modify LSPosed configuration, or change any rule value.
"""
import argparse
import os
from pathlib import Path
import shlex
import subprocess
import xml.etree.ElementTree as ET

MODULE = 'io.github.miclean.module'
ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', default='adb')
    p.add_argument('--serial', required=True)
    p.add_argument('--backup-dir', required=True, help='Private backup directory outside the source tree')
    p.add_argument('--legacy-dir', help='Use a specific /data/misc/apexdata/.../prefs/module path if multiple copies exist')
    a = p.parse_args()
    backup = Path(a.backup_dir).resolve()
    if backup == ROOT or ROOT in backup.parents:
        p.error('Keep private device backups outside the public source tree.')
    os.umask(0o077)
    backup.mkdir(parents=True, exist_ok=True, mode=0o700)
    backup.chmod(0o700)
    (backup / '.gitignore').write_text('*\n')
    base = [a.adb, '-s', a.serial]

    def root(command):
        return subprocess.check_output(base + ['exec-out', 'su -c ' + shlex.quote(command)])

    if not root('id').startswith(b'uid=0'):
        raise RuntimeError('Authorized root access is required to read the old NSP directory.')
    subprocess.run(base + ['shell', 'am', 'force-stop', MODULE], check=True)
    if a.legacy_dir:
        legacy = a.legacy_dir.rstrip('/')
        if not legacy.startswith('/data/misc/apexdata/') or not legacy.endswith('/prefs/' + MODULE):
            p.error('Unexpected legacy directory; use this module\'s NSP directory only.')
    else:
        matches = root("find /data/misc/apexdata -path '*/prefs/" + MODULE + "/miclean.xml'").decode().splitlines()
        if len(matches) != 1:
            raise RuntimeError('Expected one legacy settings directory; specify --legacy-dir if needed.')
        legacy = matches[0].rsplit('/', 1)[0]
    uid = root('stat -c %u /data/user/0/' + MODULE).decode().strip()
    if not uid.isdigit():
        raise RuntimeError('Unexpected module UID')
    for old, new in [('miclean.xml', 'noadmi-legacy-settings.xml'), ('miclean_ui.xml', 'noadmi-legacy-ui.xml')]:
        data = root('cat ' + shlex.quote(legacy + '/' + old))
        if len(data) > 65536:
            raise RuntimeError('Unexpectedly large preference file')
        xml = ET.fromstring(data)
        if xml.tag != 'map' or any(n.tag != 'boolean' for n in xml):
            raise RuntimeError('Legacy file has unsupported preference types; original retained')
        dest = backup / old
        dest.write_bytes(data)
        dest.chmod(0o600)
        temporary = '/data/local/tmp/' + new
        target = '/data/user/0/' + MODULE + '/files/' + new
        subprocess.run(base + ['push', str(dest), temporary], check=True)
        root('set -e\nmkdir -p ' + shlex.quote(str(Path(target).parent))
             + '\nchown ' + uid + ':' + uid + ' ' + shlex.quote(str(Path(target).parent))
             + '\nrestorecon ' + shlex.quote(str(Path(target).parent))
             + '\ncp ' + shlex.quote(temporary) + ' ' + shlex.quote(target)
             + '\nchown ' + uid + ':' + uid + ' ' + shlex.quote(target)
             + '\nchmod 600 ' + shlex.quote(target)
             + '\nrestorecon ' + shlex.quote(target)
             + '\nrm ' + shlex.quote(temporary))
    print('Original settings backed up and staged privately. Install/open the modern NoAdMi APK, then restart target apps.')


if __name__ == '__main__':
    main()
