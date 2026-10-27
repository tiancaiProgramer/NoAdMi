#!/usr/bin/env python3
"""Download the pinned Kotlin/D8 build tools; verify SHA-256 before using each file."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
for item in json.loads(Path(__file__).with_name('smart-skip-toolchain.json').read_text()):
    name = item['url'].rsplit('/', 1)[1]
    destination = a.output / name
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() == item['sha256']:
        print('Verified:', name)
        continue
    request = urllib.request.Request(item['url'], headers={'User-Agent': 'NoAdMi-build'})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != item['sha256']:
        raise ValueError('Download checksum mismatch: ' + name)
    destination.write_bytes(data)
    print('Downloaded and verified:', name)
