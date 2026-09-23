#!/usr/bin/env python3
"""Sequential, explicitly targeted APK A/B runs; keeps raw logs and installed-APK identity.

uv run --no-project tools/performance/compare.py --serial emulator-5562 \
  --baseline captures/low-battery/baseline.apk \
  --candidate app/build/outputs/apk/debug/app-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --out captures/low-battery/comparison

Clears Cube Run's disposable saves for each run and alternates baseline/candidate order.
No device-wide battery, clock, refresh rate, or power setting is changed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--test-apk', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--seconds', type=int, default=25)
parser.add_argument('--repetitions', type=int, default=2)
parser.add_argument('--modes', default='cruise,hills,jet,second-wind')
parser.add_argument('--section', type=int, default=56)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)


def adb(*arguments, timeout=180):
    result = subprocess.run(['adb', '-s', args.serial, *map(str, arguments)],
                            text=True, capture_output=True, timeout=timeout, check=True)
    return result.stdout


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


manifest = {'serial': args.serial, 'seconds_per_phase': args.seconds,
            'warmup_seconds_per_phase': 5, 'section': args.section,
            'modes': args.modes, 'world': 2, 'track_seed': 73,
            'scenery_seed': 74, 'world_switching': False,
            'note': 'Process allocations include harness/audio CSV logging.',
            'apk_sha256': {'baseline': sha(args.baseline), 'candidate': sha(args.candidate)}, 'runs': []}
graphics = '\n'.join(line for line in adb('shell', 'dumpsys', 'SurfaceFlinger').splitlines() if 'GLES' in line)
(args.out / 'device.txt').write_text(adb('shell', 'getprop') + '\n' + adb('shell', 'dumpsys', 'battery') + '\n' +
                                   adb('shell', 'wm', 'size') + adb('shell', 'wm', 'density') + graphics + '\n')
adb('install', '-r', args.test_apk)
adb('install', '-r', args.baseline)
adb('shell', 'pm', 'clear', 'cube.run')
for repeat in range(args.repetitions):
    for name in (('baseline', 'candidate') if repeat % 2 == 0 else ('candidate', 'baseline')):
        apk = getattr(args, name)
        prefix = args.out / f'{repeat + 1}-{name}'
        print(f'Running {prefix.name}', flush=True)
        adb('install', '-r', apk)
        adb('shell', 'pm', 'clear', 'cube.run')
        adb('logcat', '-c')
        output = adb('shell', 'am', 'instrument', '-w', '-e', 'class', 'cube.run.game.RunPerformanceTest',
                     '-e', 'bot', 'false', '-e', 'audio', 'on', '-e', 'repeatable', 'true', '-e', 'section', args.section,
                     '-e', 'world', '2', '-e', 'modes', args.modes, '-e', 'seconds', args.seconds,
                     'cube.run.test/androidx.test.runner.AndroidJUnitRunner',
                     timeout=args.seconds * len(args.modes.split(',')) + 120)
        prefix.with_suffix('.test.txt').write_text(output)
        logs = adb('logcat', '-d', '-s', 'RUN_BENCH:I', 'AndroidRuntime:E', '*:S')
        prefix.with_suffix('.log').write_text(logs)
        installed = adb('shell', 'pm', 'path', 'cube.run').strip().removeprefix('package:')
        copy = args.out / 'installed.apk'
        adb('pull', installed, copy)
        identity_matches = sha(copy) == manifest['apk_sha256'][name]
        copy.unlink()
        if 'OK (1 test)' not in output or not identity_matches:
            raise RuntimeError(f'{prefix.name}: failed/interrupted test or competing install; see raw logs')
        samples = []
        for line in logs.splitlines():
            match = re.search(r'RUN_BENCH: (\S+) frames=(\d+) frame=([^ ]+) cpu=([^ ]+) threadCpu=([^ ]+).*allocBytes=(\d+) gc=(\d+)', line)
            if match:
                mode, frames, frame_ms, render_ms, cpu_ms, allocated, gc = match.groups()
                samples.append({'mode': mode, 'frames': int(frames),
                                'frame_ms': list(map(float, frame_ms.split('/'))),
                                'render_ms': list(map(float, render_ms.split('/'))),
                                'thread_cpu_ms': list(map(float, cpu_ms.split('/'))),
                                'allocated_bytes': int(allocated), 'gc': int(gc)})
        if len(samples) != len(args.modes.split(',')):
            raise RuntimeError(f'{prefix.name}: missing benchmark phase logs')
        manifest['runs'].append({'name': name, 'repeat': repeat + 1, 'installed_apk_verified': True, 'samples': samples})
        (args.out / 'results.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'Results: {args.out / "results.json"}', flush=True)
