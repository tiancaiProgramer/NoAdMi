#!/usr/bin/env python3
"""Build a small API-82 module without Gradle or packaging the framework API."""
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def run(argv, env=None):
    subprocess.run([str(x) for x in argv], check=True, env=env)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT')))
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--xposed-api', default=os.environ.get('XPOSED_API_JAR'))
    p.add_argument('--platform', default='android-35')
    p.add_argument('--build-tools', default='36.0.0')
    p.add_argument('--work-dir', default=str(ROOT / 'build'))
    p.add_argument('--output', default=str(ROOT.parent / 'NoAdMi-0.6.1-preview.apk'))
    a = p.parse_args()
    if not all((a.sdk, a.java_home, a.xposed_api)):
        p.error('Supply --sdk, --java-home, --xposed-api or their environment variables.')
    sdk, jdk, api = Path(a.sdk).resolve(), Path(a.java_home).resolve(), Path(a.xposed_api).resolve()
    tools = sdk / 'build-tools' / a.build_tools
    android = sdk / 'platforms' / a.platform / 'android.jar'
    env = dict(os.environ, JAVA_HOME=str(jdk))
    build = Path(a.work_dir).resolve()
    build.mkdir(parents=True, exist_ok=True)
    for name in ('classes', 'dex', 'tests'):
        path = build / name
        if path.exists():
            shutil.rmtree(path)
        path.mkdir()
    sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
    run([jdk / 'bin/javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
         '-classpath', os.pathsep.join(map(str, (android, api))), '-d', build / 'classes', *sources])
    test_sources = sorted((ROOT / 'tests').glob('*.java'))
    run([jdk / 'bin/javac', '-encoding', 'UTF-8', '-classpath', build / 'classes',
         '-d', build / 'tests', *test_sources])
    for test in test_sources:
        run([jdk / 'bin/java', '-cp', os.pathsep.join(map(str, (build / 'classes', build / 'tests'))), test.stem])
    run([tools / 'd8', '--min-api', '26', '--lib', android, '--classpath', api,
         '--output', build / 'dex', *sorted((build / 'classes').rglob('*.class'))], env)
    unsigned = build / 'unsigned.apk'
    run([tools / 'aapt', 'package', '-f', '-M', ROOT / 'src/main/AndroidManifest.xml',
         '-S', ROOT / 'src/main/res', '-A', ROOT / 'src/main/assets', '-I', android, '-F', unsigned])
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((build / 'dex').glob('*.dex')):
            archive.write(dex, dex.name)
    aligned = build / 'aligned.apk'
    run([tools / 'zipalign', '-f', '4', unsigned, aligned])
    signing = build / '.signing'
    signing.mkdir(mode=0o700, exist_ok=True)
    key, password = signing / 'preview.p12', signing / 'password.txt'
    if not key.exists():
        password.write_text(secrets.token_urlsafe(30))
        password.chmod(0o600)
        run([jdk / 'bin/keytool', '-genkeypair', '-noprompt', '-keystore', key,
             '-storetype', 'PKCS12', '-storepass:file', password, '-alias', 'miclean-preview',
             '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650',
             '-dname', 'CN=MiClean Preview'])
        key.chmod(0o600)
    output = Path(a.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    run([tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'file:' + str(password),
         '--ks-key-alias', 'miclean-preview', '--out', output, aligned], env)
    run([tools / 'apksigner', 'verify', '--verbose', output], env)
    print('Built:', output)


if __name__ == '__main__':
    main()
