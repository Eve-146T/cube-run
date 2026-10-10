#!/usr/bin/env python3
"""Benchmark real user actions and cold launches on an attached Cube Run device.

Build debug/test APKs first. Use uv run tools/performance/actions.py --serial SERIAL.
Reports preserve individual samples, first-use/repeat summaries, device metadata,
APK identity and raw logs. Preferences are backed up and restored even on failure.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import tarfile
import time


def percentile(values, fraction):
    values = sorted(values)
    position = (len(values) - 1) * fraction
    low = int(position)
    high = min(low + 1, len(values) - 1)
    return values[low] + (values[high] - values[low]) * (position - low)


def summarize(samples):
    groups = {}
    for row in samples:
        key = (row['action'], row.get('phase', 'repeat'))
        groups.setdefault(key, []).append(row)
    result = []
    for (action, phase), rows in sorted(groups.items()):
        metrics = {}
        for metric in ['ready_ms', 'first_ui_frame_ms', 'controls_settled_ms']:
            values = [row[metric] for row in rows if metric in row]
            if values:
                metrics[metric] = {'p50': statistics.median(values),
                    'p95': percentile(values, .95), 'min': min(values), 'max': max(values)}
        result.append({'action': action, 'phase': phase, 'count': len(rows), **metrics})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--repeats', type=int, default=5)
    parser.add_argument('--cold-starts', type=int, default=3)
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/debug/app-debug.apk'))
    parser.add_argument('--test-apk', type=Path, default=Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    parser.add_argument('--skip-install', action='store_true')
    parser.add_argument('--out', type=Path)
    parser.add_argument('--baseline', type=Path, help='Compare against a previous results.json')
    args = parser.parse_args()
    if not 1 <= args.repeats <= 100 or not 0 <= args.cold_starts <= 100:
        parser.error('repeats must be 1..100; cold-starts must be 0..100')
    previous = json.loads(args.baseline.read_text()) if args.baseline else None
    if previous is not None and (not previous.get('complete') or previous.get('serial') != args.serial):
        parser.error('baseline must be a complete report from the same device serial')
    out = args.out or Path('.build-tmp/actions') / (datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + args.serial)
    out.mkdir(parents=True, exist_ok=False)
    adb_prefix = ['adb', '-s', args.serial]

    def adb(*command, binary=False, timeout=120):
        return subprocess.check_output(adb_prefix + list(command), text=not binary, timeout=timeout)

    report = {'schema_version': 1, 'utc': datetime.now(timezone.utc).isoformat(),
        'serial': args.serial, 'complete': False, 'cold_start_samples': [], 'samples': []}
    prefs = None
    try:
        if not args.skip_install:
            adb('install', '-r', str(args.apk.resolve()))
            adb('install', '-r', str(args.test_apk.resolve()))
        report['device'] = {'model': adb('shell', 'getprop', 'ro.product.model').strip(),
            'sdk': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
            'fingerprint': adb('shell', 'getprop', 'ro.build.fingerprint').strip(),
            'screen': adb('shell', 'wm', 'size').strip()}
        apk_path = adb('shell', 'pm', 'path', 'cube.run').strip().splitlines()[0].removeprefix('package:')
        report['installed_apk_sha256'] = hashlib.sha256(adb('exec-out', 'cat', apk_path, binary=True)).hexdigest()
        report['source_head'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        report['source_dirty'] = bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip())
        test_path = adb('shell', 'pm', 'path', 'cube.run.test').strip().splitlines()[0].removeprefix('package:')
        report['installed_test_apk_sha256'] = hashlib.sha256(adb('exec-out', 'cat', test_path, binary=True)).hexdigest()
        (out / 'display.txt').write_text(adb('shell', 'dumpsys', 'display'))
        (out / 'battery.txt').write_text(adb('shell', 'dumpsys', 'battery'))
        adb('shell', 'am', 'force-stop', 'cube.run')
        adb('shell', 'run-as', 'cube.run', 'mkdir', '-p', 'shared_prefs')
        prefs = adb('exec-out', 'run-as', 'cube.run', 'tar', '-cf', '-', 'shared_prefs', binary=True)
        (out / 'preferences.tar').write_bytes(prefs)
        for sample in range(args.cold_starts):
            adb('shell', 'am', 'force-stop', 'cube.run')
            start = time.monotonic()
            launch = adb('shell', 'am', 'start', '-W', '-n', 'cube.run/.GameActivity', '-f', '0x10008000')
            (out / f'cold-{sample}-am.txt').write_text(launch)
            pid = adb('shell', 'pidof', 'cube.run').strip()
            deadline = time.monotonic() + 20
            while True:
                trace = adb('logcat', '-d', '--pid', pid, '-v', 'brief', '-s', 'CUBE_START:D', '*:S')
                if 'opening finished' in trace:
                    break
                if time.monotonic() >= deadline:
                    (out / f'cold-{sample}.log').write_text(trace)
                    raise RuntimeError('Cold launch did not complete its opening within 20 seconds')
                time.sleep(.1)
            (out / f'cold-{sample}.log').write_text(trace)
            milestones = {stage: int(ms) for ms, stage in re.findall(r'(\d+)ms ([^\r\n]+)', trace)}
            report['cold_start_samples'].append({'sample': sample,
                'android_launch_ms': {key: int(value) for key, value in re.findall(r'(ThisTime|TotalTime|WaitTime):\s*(\d+)', launch)},
                'app_milestones_ms': milestones,
                'host_observed_opening_ms': (time.monotonic() - start) * 1000})
        adb('shell', 'am', 'force-stop', 'cube.run')
        adb('shell', 'run-as', 'cube.run', 'rm', '-f', 'files/user-action-benchmark.json')
        output = adb('shell', 'am', 'instrument', '-w', '-e', 'class',
            'cube.run.ui.UserActionBenchmarkTest', '-e', 'repeats', str(args.repeats),
            'cube.run.test/androidx.test.runner.AndroidJUnitRunner', timeout=30 + args.repeats * 30)
        (out / 'instrumentation.txt').write_text(output)
        raw = adb('exec-out', 'run-as', 'cube.run', 'cat',
            'files/user-action-benchmark.json')
        (out / 'device-actions.json').write_text(raw)
        device_report = json.loads(raw)
        report['samples'] = device_report['samples']
        report['ui_frame_metric'] = device_report['ui_frame_metric']
        report['action_environment'] = device_report.get('environment', {})
        report['pending_action'] = device_report.get('pending_action')
        if 'OK (1 test)' not in output:
            raise RuntimeError('Action benchmark failed; inspect instrumentation.txt and partial samples')
        report['summary'] = summarize(report['samples'])
        report['cold_start_summary'] = {}
        for metric, extract in {
            'android_activity_displayed_ms': lambda row: row['android_launch_ms']['TotalTime'],
            'app_first_scene_ms': lambda row: row['app_milestones_ms']['scene revealed'],
            'app_opening_finished_ms': lambda row: row['app_milestones_ms']['opening finished'],
            'host_observed_opening_ms': lambda row: row['host_observed_opening_ms'],
        }.items():
            values = [extract(row) for row in report['cold_start_samples']]
            if values:
                report['cold_start_summary'][metric] = {'count': len(values), 'p50': statistics.median(values),
                    'p95': percentile(values, .95), 'min': min(values), 'max': max(values)}
        if previous is not None:
            old = {(row['action'], row['phase']): row for row in previous['summary']}
            report['comparison'] = []
            for row in report['summary']:
                baseline = old.get((row['action'], row['phase']))
                if baseline and baseline['ready_ms']['p50'] > 0:
                    before, after = baseline['ready_ms']['p50'], row['ready_ms']['p50']
                    report['comparison'].append({'action': row['action'], 'phase': row['phase'],
                        'before_p50_ms': before, 'after_p50_ms': after, 'change_percent': (after / before - 1) * 100})
        report['complete'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        try:
            if prefs is not None:
                adb('shell', 'am', 'force-stop', 'cube.run')
                remote = '/data/local/tmp/cube-run-action-benchmark-prefs.tar'
                adb('push', str(out / 'preferences.tar'), remote)
                adb('shell', 'run-as', 'cube.run', 'rm', '-rf', 'shared_prefs')
                adb('shell', 'run-as', 'cube.run', 'tar', '-xf', remote)
                adb('shell', 'rm', remote)
                restored = out / 'restored-preferences.tar'
                restored.write_bytes(adb('exec-out', 'run-as', 'cube.run', 'tar', '-cf', '-', 'shared_prefs', binary=True))
                def contents(path):
                    with tarfile.open(path) as archive:
                        return {member.name: archive.extractfile(member).read() for member in archive if member.isfile()}
                if contents(out / 'preferences.tar') != contents(restored):
                    report['complete'] = False
                    raise RuntimeError('Preference restoration verification failed')
                report['preferences_restored'] = True
        finally:
            (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    lines = ['| Action | Phase | Samples | First UI frame p50 | Ready p50 | Ready p95 |',
        '| --- | --- | ---: | ---: | ---: | ---: |']
    for row in report['summary']:
        lines.append(f"| {row['action']} | {row['phase']} | {row['count']} | "
            f"{row['first_ui_frame_ms']['p50']:.1f} ms | {row['ready_ms']['p50']:.1f} ms | {row['ready_ms']['p95']:.1f} ms |")
    (out / 'summary.md').write_text('\n'.join(lines) + '\n')
    if report['cold_start_summary']:
        lines += ['', '| Cold startup milestone | Samples | p50 | p95 |', '| --- | ---: | ---: | ---: |']
        for metric, row in report['cold_start_summary'].items():
            lines.append(f"| {metric} | {row['count']} | {row['p50']:.1f} ms | {row['p95']:.1f} ms |")
        (out / 'summary.md').write_text('\n'.join(lines) + '\n')
    print('\n'.join(lines))
    print(f'Results: {out / "results.json"}')


if __name__ == '__main__':
    main()
