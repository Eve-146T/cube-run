#!/usr/bin/env -S uv run --script
"""Benchmark historical game APKs through the verified rooted Motorola touchscreen."""
import argparse
import hashlib
import json
from pathlib import Path
import os
import shlex
import subprocess
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/bot'))
from bot import Device


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--device', default='ZY323NNKTB')
    p.add_argument('--count', type=int, default=48)
    p.add_argument('--duration', type=int, default=8)
    p.add_argument('--fast', action='store_true')
    p.add_argument('--no-build', action='store_true')
    args = p.parse_args()
    if not 4 <= args.count <= 256 or not 8 <= args.duration <= 200: p.error('count must be 4..256 and duration 8..200 ms')
    out = args.output.resolve(); out.mkdir(parents=True, exist_ok=True)
    temp = ROOT / 'captures/tmp'; temp.mkdir(parents=True, exist_ok=True)
    os.environ['TMPDIR'] = str(temp)
    if not args.no_build:
        subprocess.run([str(ROOT/'gradlew'),f'-Djava.io.tmpdir={temp}','assembleDebug','assembleDebugAndroidTest','--offline'],cwd=ROOT,check=True)
    sdk = Path(os.environ.get('ANDROID_HOME','/home/user1/android-sdk'))
    compiler = sorted(sdk.glob('ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang'))[-1]
    native = out/'touch_probe'
    subprocess.run([str(compiler),'-O2','-Wall','-Wextra',str(ROOT/'tools/responsiveness/touch_probe.c'),'-o',str(native)],check=True)
    d = Device(args.device)
    (out/'run.json').write_text(json.dumps(dict(apk=str(args.apk.resolve()), sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(), device=args.device, count=args.count, duration=args.duration, fast=args.fast), indent=2)+'\n')
    with d.preserve(out):
        try:
            subprocess.run(d.adb+['install','-r','-d',str(args.apk.resolve())],check=True)
            subprocess.run(d.adb+['install','-r',str(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')],check=True)
            # External storage is not executable; use a dedicated, removed-after-test directory.
            remote='/sdcard/Android/data/cube.run/files/response-touch-probe'
            subprocess.run(d.adb+['push',str(native),remote],check=True)
            d.shell('su','-c','mkdir -p /data/local/cube-run-responsiveness && cp '+shlex.quote(remote)+' /data/local/cube-run-responsiveness/touch_probe && chmod 755 /data/local/cube-run-responsiveness/touch_probe')
            (out/'device-before.txt').write_text(d.shell('dumpsys','battery',capture_output=True,text=True).stdout)
            (out/'clocks.txt').write_text(d.shell('sh','-c','for p in /sys/devices/system/cpu/cpufreq/policy*; do echo "$p"; cat "$p/scaling_min_freq" "$p/scaling_max_freq" "$p/scaling_governor"; done',capture_output=True,text=True).stdout)
            d.shell('rm','-f',*[f'/sdcard/Android/data/cube.run/files/{name}' for name in ['response-kernel.csv','response-trials.csv','response-frames.csv','response-summary.txt']])
            result=d.shell('am','instrument','-w','-e','class','cube.run.response.ResponsivenessProbeTest',
                '-e','count',args.count,'-e','duration',args.duration,'-e','fast',str(args.fast).lower(),
                'cube.run.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True)
            (out/'instrumentation.txt').write_text(result.stdout+result.stderr)
            print(result.stdout)
            assert 'OK (' in result.stdout and 'FAILURES!!!' not in result.stdout
            for name in ['response-kernel.csv','response-trials.csv','response-frames.csv','response-summary.txt']:
                d.pull(name,out)
            (out/'device-after.txt').write_text(d.shell('dumpsys','battery',capture_output=True,text=True).stdout)
        finally:
            d.shell('am','force-stop','cube.run')
            d.shell('su','-c','rm -rf /data/local/cube-run-responsiveness')
            d.shell('rm','-f','/sdcard/Android/data/cube.run/files/response-touch-probe')
            d.shell('rm','-f','/sdcard/Android/data/cube.run/files/response-command.sh')
            subprocess.run(d.adb+['install','-r','-d',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk')],check=True)
    print(f'Latency traces: {out}')


if __name__ == '__main__': main()
