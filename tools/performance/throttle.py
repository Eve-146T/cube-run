#!/usr/bin/env python3
"""Run a command under verified Moto G7 Power clock caps, then restore every setting.

Requires rooted adb. Leaves governors and thermal protection active. A device-side
watchdog restores limits even if the host command disconnects or is interrupted.
Power/performance services stay active; the custom big-cluster frequency floor
is temporarily lowered with the CPU ceilings.
Clocks are sampled once a second; a cap violation invalidates the run.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--cpu0', type=int, default=614400)
parser.add_argument('--cpu4', type=int, default=633600)
parser.add_argument('--gpu', type=int, default=320000000)
parser.add_argument('--timeout', type=int, default=900)
parser.add_argument('command', nargs=argparse.REMAINDER)
args = parser.parse_args()
command = args.command[1:] if args.command[:1] == ['--'] else args.command
if not command:
    parser.error('a command is required after --')
if args.timeout < 20:
    parser.error('--timeout must be at least 20 seconds')
args.out.mkdir(parents=True, exist_ok=True)


def interrupted(signum, frame):
    raise KeyboardInterrupt(f'Interrupted by signal {signum}')


signal.signal(signal.SIGTERM, interrupted)


def adb(*parts, **kwargs):
    return subprocess.run(['adb', '-s', args.serial, *parts], check=True,
                          text=True, capture_output=True, timeout=30, **kwargs).stdout.strip()


def root(script):
    return adb('shell', 'su -c ' + shlex.quote(script))


cpu = '/sys/devices/system/cpu/cpufreq/policy'
gpu = '/sys/class/kgsl/kgsl-3d0/devfreq/'
boost = '/sys/module/cpu_boost/parameters/input_boost_freq'
msm = '/sys/module/msm_performance/parameters/cpu_max_freq'
floor = '/sys/module/big_cluster_min_freq_adjust/parameters/min_freq_floor'
changes = {
    floor: str(min(args.cpu4, int(root('cat ' + floor)))),
    boost: '0:0 1:0 2:0 3:0 4:0 5:0 6:0 7:0',
    msm: ' '.join(f'{i}:{args.cpu0 if i < 4 else args.cpu4}' for i in range(8)),
    cpu + '0/scaling_min_freq': '614400',
    cpu + '4/scaling_min_freq': '633600',
    cpu + '0/scaling_max_freq': str(args.cpu0),
    cpu + '4/scaling_max_freq': str(args.cpu4),
    gpu + 'max_freq': str(args.gpu),
}
for policy, cap in ((0, args.cpu0), (4, args.cpu4)):
    if str(cap) not in root(f'cat {cpu}{policy}/scaling_available_frequencies').split():
        raise ValueError(f'Unsupported CPU clock: {cap}')
if str(args.gpu) not in root(f'cat {gpu}available_frequencies').split():
    raise ValueError('Unsupported GPU clock')
services = {name: root('getprop init.svc.' + name) for name in ('vendor.power', 'perf-hal-2-0')}
saved = {path: root('cat ' + path) for path in changes}
(args.out / 'original-clocks.json').write_text(json.dumps(saved, indent=2) + '\n')
remote = f'/data/local/tmp/cube-clocks-{os.getpid()}'
# Restore the maximums before minimums. Performance HAL can still update its own
# minimum votes. Lower the custom kernel floor as well as the policy limits.
restore_order = [floor, boost, msm, cpu + '0/scaling_max_freq', cpu + '4/scaling_max_freq',
                 cpu + '0/scaling_min_freq', cpu + '4/scaling_min_freq', gpu + 'max_freq']
def write(path, value):
    return f'echo {shlex.quote(value)} > {path}'
restore = '\n'.join(write(path, saved[path]) for path in restore_order)
(args.out / 'original-services.json').write_text(json.dumps(services, indent=2) + '\n')
paths = [cpu + str(p) + '/' + name for p in (0, 4)
         for name in ('scaling_max_freq', 'scaling_cur_freq')] + [gpu + 'max_freq', gpu + 'cur_freq']
script = f'''#!/system/bin/sh
restore() {{
{restore}
touch {remote}.restored
}}
trap restore EXIT
trap 'exit 1' HUP INT TERM
{chr(10).join(write(path, value) for path, value in changes.items())}
echo ready > {remote}.ready
deadline=$(($(date +%s) + {args.timeout}))
while [ ! -f {remote}.stop ]; do
  now=$(date +%s)
  if [ "$now" -ge "$deadline" ]; then touch {remote}.expired; break; fi
  echo "$now $(cat {' '.join(paths)} | tr '\\n' ' ')" >> {remote}.samples
  sleep 1
done
'''
local = args.out / 'device-watchdog.sh'
local.write_text(script)
adb('push', str(local), remote + '.sh')
# nohup makes restoration independent of the host adb connection's lifetime.
root(f'nohup sh {remote}.sh > {remote}.log 2>&1 < /dev/null &')
result = None
try:
    for _ in range(30):
        if root(f'[ -f {remote}.ready ] && echo ready || true') == 'ready':
            break
        time.sleep(0.2)
    else:
        raise RuntimeError('Clock watchdog did not start')
    time.sleep(2)
    initial = list(map(int, root('cat ' + ' '.join(paths)).split()))
    limits = [args.cpu0, args.cpu0, args.cpu4, args.cpu4, args.gpu, args.gpu]
    if len(initial) != 6 or any(value > limit for value, limit in zip(initial, limits)):
        raise RuntimeError(f'Clock caps did not hold before launch: {initial}')
    print('Clock caps active:', initial, flush=True)
    result = subprocess.run(command, timeout=max(1, args.timeout - 10))
    if root(f'[ -f {remote}.restored ] && echo early || true') == 'early':
        raise RuntimeError('Clock watchdog ended before the command; exclude this run')
finally:
    root(f'touch {remote}.stop')
    for _ in range(20):
        if root(f'[ -f {remote}.restored ] && echo restored || true') == 'restored':
            break
        time.sleep(0.25)
    else:
        root(restore)
    samples = root(f'cat {remote}.samples')
    (args.out / 'clocks.txt').write_text(samples + '\n')
    (args.out / 'watchdog.log').write_text(root(f'cat {remote}.log') + '\n')
    restored = {path: root('cat ' + path) for path in changes}
    (args.out / 'restored-clocks.json').write_text(json.dumps(restored, indent=2) + '\n')
    restored_services = {name: root('getprop init.svc.' + name) for name in services}
    (args.out / 'restored-services.json').write_text(json.dumps(restored_services, indent=2) + '\n')
    root(f'rm -f {remote}.sh {remote}.ready {remote}.stop {remote}.restored {remote}.samples {remote}.log {remote}.expired')
    print('Restored clock controls:', json.dumps(restored), flush=True)
    # HAL can change minimums asynchronously; all ceilings and boost controls
    # must match the original values exactly.
    for path in (floor, boost, msm, cpu + '0/scaling_max_freq', cpu + '4/scaling_max_freq', gpu + 'max_freq'):
        if restored[path] != saved[path]:
            raise RuntimeError(f'Clock restoration mismatch: {path}')
    if restored_services != services:
        raise RuntimeError('Power-service state changed during the run; inspect service snapshots')
limits = [args.cpu0, args.cpu0, args.cpu4, args.cpu4, args.gpu, args.gpu]
rows = [list(map(int, line.split())) for line in samples.splitlines()]
if not rows or any(len(row) != 7 or any(value > limit for value, limit in zip(row[1:], limits)) for row in rows):
    raise RuntimeError('Clock cap violated; exclude these measurements (see clocks.txt)')
if result is not None:
    raise SystemExit(result.returncode)
