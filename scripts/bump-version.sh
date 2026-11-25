#!/usr/bin/env bash
set -euo pipefail

file="app/build.gradle.kts"

python3 - "$file" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()

code_match = re.search(r'versionCode = (\d+)', text)
name_match = re.search(r'versionName = "(\d+)\.(\d+)\.(\d+)"', text)
if not code_match or not name_match:
    raise SystemExit('versionCode/versionName not found in app/build.gradle.kts')

code = int(code_match.group(1)) + 1
major, minor, patch = map(int, name_match.groups())
patch += 1
name = f'{major}.{minor}.{patch}'

text = re.sub(r'versionCode = \d+', f'versionCode = {code}', text, count=1)
text = re.sub(r'versionName = "\d+\.\d+\.\d+"', f'versionName = "{name}"', text, count=1)
path.write_text(text)
print(f'versionCode={code} versionName={name}')
PY
