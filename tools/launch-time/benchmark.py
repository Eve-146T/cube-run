#!/usr/bin/env python3
"""Record process-cold launches, without changing device settings or app data.

uv run --no-project tools/launch-time/benchmark.py --serial emulator-5560 \
    --output captures/launch-time/final --runs 10

Use --record for a separate visual run (recording affects timing). APK installation
and first-install measurements are deliberately separate from repeated launches.
"""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--runs', type=int, default=10)
parser.add_argument('--record', action='store_true')
parser.add_argument('--world', type=int, help='Pin the same starting world in both debug APKs')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This benchmark is scoped to a local emulator.')
if args.runs < 1:
    parser.error('--runs must be positive')
args.output.mkdir(parents=True, exist_ok=True)
scratch = Path(__file__).resolve().parents[2] / '.build-tmp'
scratch.mkdir(exist_ok=True)
os.environ['TMPDIR'] = str(scratch)


def adb(*command):
    return subprocess.check_output(['adb', '-s', args.serial, *command], text=True)


metadata = {key: adb('shell', 'getprop', key).strip() for key in
            ['ro.build.version.sdk', 'ro.product.model', 'ro.hardware.egl']}
metadata['size'] = adb('shell', 'wm', 'size').strip()
metadata['density'] = adb('shell', 'wm', 'density').strip()
metadata['recording'] = args.record
(args.output / 'device.json').write_text(json.dumps(metadata, indent=2) + '\n')
results = []
for trial in range(1, args.runs + 1):
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'am', 'force-stop', 'cube.run')
    time.sleep(1)
    adb('logcat', '-c')
    recording = None
    if args.record:
        recording = subprocess.Popen(['adb', '-s', args.serial, 'shell', 'screenrecord',
                                      '--time-limit', '7', '/sdcard/cube-launch.mp4'],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1)
    command = 'log -t CUBE_LAUNCH request; am start -W -n cube.run/.GameActivity'
    if args.world is not None: command += f' --ei world {args.world}'
    start = adb('shell', command)
    time.sleep(3)
    trace = adb('logcat', '-d', '-v', 'monotonic', '-s', 'CUBE_START:D', 'CUBE_LAUNCH:I', 'AndroidRuntime:E', '*:S')
    (args.output / f'start-{trial}.txt').write_text(start)
    (args.output / f'trace-{trial}.txt').write_text(trace)
    if 'FATAL EXCEPTION' in trace:
        raise RuntimeError(f'App crashed in trial {trial}: {trace}')
    stages = {name.strip(): int(ms) for ms, name in re.findall(r'CUBE_START:\s+(\d+)ms (.+)', trace)}
    if 'first frame' not in stages:
        raise RuntimeError(f'No first-frame marker in trial {trial}: {trace}')
    total = re.search(r'TotalTime:\s+(\d+)', start)
    result = {'trial': trial, 'android_total_ms': int(total[1]) if total else None, **stages}
    results.append(result)
    print(json.dumps(result), flush=True)
    if recording:
        recording.wait(timeout=10)
        adb('pull', '/sdcard/cube-launch.mp4', str(args.output / f'launch-{trial}.mp4'))

summary = {}
for key in ['android_total_ms', 'first frame', 'first frame swapped', 'game ready', 'cube revealed', 'native cube draw', 'scene revealed', 'opening finished']:
    samples = [r[key] for r in results if key in r and r[key] is not None]
    if samples:
        summary[key] = {'min': min(samples), 'median': statistics.median(samples), 'max': max(samples)}
(args.output / 'results.json').write_text(json.dumps({'runs': results, 'summary': summary}, indent=2) + '\n')
print(json.dumps(summary, indent=2))
