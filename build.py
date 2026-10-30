#!/usr/bin/env python3
"""Build a libxposed API 102 module; only the service/interface client is packaged."""
import sys
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile
import hashlib
import json

ROOT = Path(__file__).resolve().parent


def run(argv, env=None):
    if os.name == 'nt' and Path(argv[0]).name.lower() == 'd8.bat':
        # Direct Java invocation avoids cmd.exe's 8191-character batch limit.
        java = tool(Path((env or os.environ)['JAVA_HOME']) / 'bin', 'java')
        argv = [java, '-Xmx1024m', '-cp', Path(argv[0]).parent / 'lib/d8.jar',
                'com.android.tools.r8.D8', *argv[1:]]
    subprocess.run([str(x) for x in argv], check=True, env=env)


def tool(directory, name):
    suffixes = ('.exe', '.bat', '') if os.name == 'nt' else ('',)
    for suffix in suffixes:
        candidate = directory / (name + suffix)
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f'Missing build tool: {directory / name}')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT')))
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--libxposed-dir', required=True, help='Directory containing extracted api, service and interface AARs (102.0.0)')
    p.add_argument('--kotlin-compiler-dir', required=True, help='Kotlin 2.4.21 embeddable compiler and its dependencies')
    p.add_argument('--d8-jar', required=True, help='Official R8 9.5.28 JAR (D8 entry point)')
    p.add_argument('--platform', default='android-35')
    p.add_argument('--build-tools', default='36.0.0')
    p.add_argument('--work-dir', default=str(ROOT / 'build'))
    p.add_argument('--signing-dir', help='Existing preview.p12 and password.txt directory for upgrade signing')
    p.add_argument('--keystore', help='Existing JKS or PKCS12 signing keystore')
    p.add_argument('--store-pass-file', help='Password file for --keystore; the password is never printed')
    p.add_argument('--key-alias', default='miclean-preview')
    p.add_argument('--output', default=str(ROOT.parent / 'NoAdMi-0.12.18-r2.apk'))
    a = p.parse_args()
    if bool(a.keystore) != bool(a.store_pass_file):
        p.error('--keystore and --store-pass-file must be supplied together.')
    if a.keystore and a.signing_dir:
        p.error('Use --keystore or --signing-dir, not both.')
    if not all((a.sdk, a.java_home)):
        p.error('Supply --sdk and --java-home or their environment variables.')
    sdk, jdk = Path(a.sdk).resolve(), Path(a.java_home).resolve()
    libraries = Path(a.libxposed_dir).resolve()
    api = libraries / 'api/classes.jar'
    runtime = [libraries / 'service/classes.jar', libraries / 'interface/classes.jar']
    if not all(p.is_file() for p in [api, *runtime]):
        raise FileNotFoundError('Missing official libxposed API/service/interface classes.jar')
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
    run([sys.executable, ROOT / 'tools/embed_market_detail.py'])
    sys.path.insert(0, str(ROOT / 'tools'))
    from build_smart_skip import compile_core
    skip_runtime = compile_core(ROOT, build, jdk, android, a.kotlin_compiler_dir)
    run([sys.executable, ROOT / 'tools/test_android_tree.py', '--java-home', jdk,
         '--compiler-dir', a.kotlin_compiler_dir, '--runtime-jar', build / 'smart-skip.jar',
         '--work-dir', build / 'android-tree-tests'])
    for dependency in json.loads((ROOT / 'third_party/shizuku/manifest.json').read_text()):
        jar = ROOT / 'third_party/shizuku' / dependency['file']
        if hashlib.sha256(jar.read_bytes()).hexdigest() != dependency['sha256']:
            raise ValueError('Shizuku dependency checksum mismatch: ' + jar.name)
    skip_runtime += sorted((ROOT / 'third_party/shizuku').glob('*.jar'))
    sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
    run([tool(jdk / 'bin', 'javac'), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
         '-classpath', os.pathsep.join(map(str, (android, api, *runtime, *skip_runtime))), '-d', build / 'classes', *sources])
    test_sources = sorted((ROOT / 'tests').glob('*.java'))
    run([tool(jdk / 'bin', 'javac'), '-encoding', 'UTF-8', '-classpath', os.pathsep.join(map(str, (build / 'classes', api, android))),
         '-d', build / 'tests', *test_sources])
    for test in test_sources:
        import re
        package = re.search(r'^package ([\w.]+);', test.read_text(encoding='utf-8'), re.M)
        test_class = (package.group(1) + '.' if package else '') + test.stem
        run([tool(jdk / 'bin', 'java'), '-cp', os.pathsep.join(map(str, (build / 'classes', build / 'tests', api, android))), test_class])
    run([tool(jdk / 'bin', 'java'), '-Xmx1536m', '-cp', Path(a.d8_jar).resolve(), 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android, '--classpath', api,
         '--output', build / 'dex', *sorted((build / 'classes').rglob('*.class')), *runtime, *skip_runtime], env)
    unsigned = build / 'unsigned.apk'
    run([tool(tools, 'aapt'), 'package', '-f', '-M', ROOT / 'src/main/AndroidManifest.xml',
         '-S', ROOT / 'src/main/res', '-A', ROOT / 'src/main/assets', '-I', android, '-F', unsigned])
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((build / 'dex').glob('*.dex')):
            archive.write(dex, dex.name)
        for resource in sorted((ROOT / 'src/main/resources').rglob('*')):
            if resource.is_file():
                archive.write(resource, resource.relative_to(ROOT / 'src/main/resources').as_posix())
    aligned = build / 'aligned.apk'
    run([tool(tools, 'zipalign'), '-f', '4', unsigned, aligned])
    if a.keystore:
        key, password = Path(a.keystore).resolve(), Path(a.store_pass_file).resolve()
        if not key.is_file() or not password.is_file():
            raise FileNotFoundError('Signing keystore or password file is missing')
    else:
        signing = Path(a.signing_dir).resolve() if a.signing_dir else build / '.signing'
        if a.signing_dir and not all((signing / name).is_file() for name in ('preview.p12', 'password.txt')):
            raise FileNotFoundError('An existing signing directory must contain preview.p12 and password.txt')
        signing.mkdir(mode=0o700, exist_ok=True)
        key, password = signing / 'preview.p12', signing / 'password.txt'
        if not key.exists():
            password.write_text(secrets.token_urlsafe(30))
            password.chmod(0o600)
            run([tool(jdk / 'bin', 'keytool'), '-genkeypair', '-noprompt', '-keystore', key,
                 '-storetype', 'PKCS12', '-storepass:file', password, '-alias', a.key_alias,
                 '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650',
                 '-dname', 'CN=MiClean Preview'])
            key.chmod(0o600)
    output = Path(a.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    run([tool(tools, 'apksigner'), 'sign', '--ks', key, '--ks-pass', 'file:' + str(password),
         '--ks-key-alias', a.key_alias, '--out', output, aligned], env)
    run([tool(tools, 'apksigner'), 'verify', '--verbose', output], env)
    print('Built:', output)


if __name__ == '__main__':
    main()
