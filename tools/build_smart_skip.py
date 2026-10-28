"""Compile the pinned GKD selector/subscription core and NoAdMi accessibility adapter."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess


def compile_core(root, build, jdk, android, compiler_dir):
    deps = root / 'third_party/dependencies'
    runtime = sorted(deps.glob('*.jar'))
    for item in json.loads((deps / 'manifest.json').read_text()):
        jar = deps / (item['artifact'] + '-' + item['version'] + '.jar')
        if hashlib.sha256(jar.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Dependency checksum mismatch: ' + jar.name)
    compiler = Path(compiler_dir)
    compiler_jars = sorted(compiler.glob('*.jar'))
    if not any(p.name.startswith('kotlin-compiler-embeddable-2.4.21') for p in compiler_jars):
        raise ValueError('Supply --kotlin-compiler-dir with Kotlin 2.4.21 embeddable compiler dependencies')
    stage = build / 'gkd-sources'
    sources = []
    trees = [root / 'third_party/gkd-selector/src', root / 'third_party/gkd-core/src']
    for tree in trees:
        for original in sorted(tree.rglob('*.kt')):
            text = original.read_text(encoding='utf-8')
            # JS exports are not part of the JVM ABI. The original sources remain untouched.
            text = re.sub(r'^import kotlin.js.*\n', '', text, flags=re.M)
            text = re.sub(r'^\s*@Js(?:Export(?:\.Ignore)?|Static)\s*\n', '\n', text, flags=re.M)
            text = text.replace('internal expect fun String.compilePlatformRegex(): RegexCompileResult', '')
            text = text.replace('internal actual fun ', 'internal fun ')
            if original.name == 'RuleExpression.kt':
                start = text.index('    val message: ErrorMessage')
                end = text.index('    data class SelectorError', start)
                text = text[:start] + text[end:]
            if original.name == 'SubscriptionParseException.kt':
                text = '''package li.gkd.app.subscription
class SubscriptionParseException(val path: String, val detail: String, cause: Throwable? = null)
    : IllegalArgumentException("$path: $detail", cause)
'''
            text = re.sub(r'^import li.gkd.app.(?:error|resources).*\n', '', text, flags=re.M)
            output = stage / tree.parent.name / original.relative_to(tree)
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(text, encoding='utf-8')
            sources.append(output)
    sources += sorted((root / 'src/main/kotlin').rglob('*.kt'))
    jar = build / 'smart-skip.jar'
    java = jdk / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    shizuku = root / 'third_party/shizuku'
    for item in json.loads((shizuku / 'manifest.json').read_text()):
        if hashlib.sha256((shizuku / item['file']).read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Shizuku dependency checksum mismatch: ' + item['file'])
    cp = os.pathsep.join(map(str, [android, *runtime, *sorted(shizuku.glob('*.jar'))]))
    subprocess.run([str(java), '-Xmx1536m', '-cp', os.pathsep.join(map(str, compiler_jars)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
        '-Xplugin=' + str(next(compiler.glob('kotlin-serialization-compiler-plugin-embeddable-*.jar'))),
        '-classpath', cp, '-d', str(jar), *map(str, sources)], check=True)
    tests = sorted((root / 'tests/skip').glob('*.kt'))
    if tests:
        test_jar = build / 'smart-skip-tests.jar'
        subprocess.run([str(java), '-cp', os.pathsep.join(map(str, compiler_jars)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
            '-classpath', cp + os.pathsep + str(jar), '-d', str(test_jar), *map(str, tests)], check=True)
        for test in tests:
            subprocess.run([str(java), '-cp', os.pathsep.join(map(str, [test_jar, jar, *runtime])),
                'io.github.miclean.module.skip.' + test.stem + 'Kt'], check=True)
    return [jar, *runtime]
