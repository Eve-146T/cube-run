#!/usr/bin/env -S uv run --script
"""Local Cube Run test bot. No git, network uploads, purchases, or publishing commands."""
import argparse
import contextlib
import datetime
import os
from pathlib import Path
import shlex
import subprocess
import io
import hashlib
import tarfile
import time
from report import generate

ROOT = Path(__file__).resolve().parents[2]
DEVICE_DIR = '/sdcard/Android/data/cube.run/files'


def run(argv, **kwargs):
    return subprocess.run([str(x) for x in argv], check=True, **kwargs)


class Device:
    def __init__(self, serial):
        if not serial:
            lines = subprocess.check_output(['adb', 'devices'], text=True).splitlines()[1:]
            ready = [line.split()[0] for line in lines if line.endswith('\tdevice')]
            if len(ready) != 1: raise RuntimeError('Use --device SERIAL when there is not exactly one authorized device')
            serial = ready[0]
        self.adb = ['adb', '-s', serial]

    def shell(self, *args, **kwargs):
        return run(self.adb + ['shell', shlex.join([str(x) for x in args])], **kwargs)

    def pull(self, name, output):
        run(self.adb + ['pull', f'{DEVICE_DIR}/{name}', output / name])

    @contextlib.contextmanager
    def preserve(self, output):
        self.shell('am', 'force-stop', 'cube.run')
        backup = output / 'prefs-before.tar'
        can_run_as = subprocess.run(self.adb + ['shell', 'run-as', 'cube.run', 'id'], capture_output=True).returncode == 0
        read = ['run-as', 'cube.run', 'tar', '-cf', '-', 'shared_prefs'] if can_run_as else ["su -c 'tar -C /data/user/0/cube.run -cf - shared_prefs'"]
        # Replace the directory: a test may have created a preferences file that
        # did not exist in the backup. Leaving it behind is not a full restore.
        restore = ['run-as', 'cube.run', 'tar', '-xf', '-'] if can_run_as else ["su -c 'tar -C /data/user/0/cube.run -xf -'"]
        with backup.open('wb') as f:
            run(self.adb + ['exec-out'] + read, stdout=f)
        if backup.stat().st_size < 512: raise RuntimeError('Preference backup did not succeed')
        with tarfile.open(backup) as archive:
            if not all(m.name == 'shared_prefs' or m.name.startswith('shared_prefs/') for m in archive):
                raise RuntimeError('Unexpected path in preference backup')
        try:
            yield
        finally:
            self.shell('am', 'force-stop', 'cube.run')
            if can_run_as: self.shell('run-as', 'cube.run', 'rm', '-rf', 'shared_prefs')
            else: self.shell('su', '-c', 'rm -rf /data/user/0/cube.run/shared_prefs')
            with backup.open('rb') as f: run(self.adb + ['shell', '-T'] + restore, stdin=f)
            def contents(data):
                with tarfile.open(fileobj=io.BytesIO(data)) as archive:
                    return {m.name: archive.extractfile(m).read() for m in archive if m.isfile()}
            expected = contents(backup.read_bytes())
            for attempt in range(3):
                after = subprocess.check_output(self.adb + ['exec-out'] + read)
                if expected == contents(after): break
                time.sleep(.1)
            else: raise RuntimeError(f'Preference restore verification failed; backup: {backup}')
            print(f'Restored pre-test preferences from {backup}')

    def instrument(self, cls, output, **options):
        cmd = ['am', 'instrument', '-w', '-e', 'class', f'cube.run.bot.{cls}']
        for key, val in options.items(): cmd += ['-e', key, str(val).lower() if isinstance(val, bool) else str(val)]
        cmd += ['cube.run.test/androidx.test.runner.AndroidJUnitRunner']
        result = self.shell(*cmd, capture_output=True, text=True)
        (output / f'{cls}.txt').write_text(result.stdout + result.stderr)
        print(result.stdout)
        if 'OK (' not in result.stdout or any(x in result.stdout for x in ('FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED')):
            raise RuntimeError(f'{cls} failed; inspect {output}')

    @contextlib.contextmanager
    def recording(self, output, enabled):
        if not enabled:
            yield; return
        # The PID belongs to this exact recorder; never kill another recording session.
        command = f'echo $$; exec screenrecord --bit-rate 6000000 --time-limit 180 {DEVICE_DIR}/bot-demo.mp4'
        process = subprocess.Popen(self.adb + ['shell', command], stdout=subprocess.PIPE, text=True)
        pid = process.stdout.readline().strip()
        if not pid.isdigit(): raise RuntimeError(f'Recorder did not start: {pid}')
        try:
            yield
        finally:
            if process.poll() is None:
                subprocess.run(self.adb + ['shell', 'kill', '-2', pid], check=False)
            process.wait(timeout=15)
            self.pull('bot-demo.mp4', output)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['verify', 'survey', 'replay', 'input', 'play', 'report'])
    p.add_argument('--device'); p.add_argument('--output', type=Path)
    p.add_argument('--no-build', action='store_true')
    p.add_argument('--seconds', type=int, default=60); p.add_argument('--record', action='store_true')
    p.add_argument('--section', type=int, default=-1); p.add_argument('--world', type=int, default=-1)
    p.add_argument('--magnet', action='store_true', help='Start a live test with a 20-second magnet to check coin-lane preference')
    p.add_argument('--boosts', choices=[str(n) for n in range(11)], default='0'); p.add_argument('--allow-death', action='store_true')
    p.add_argument('--speeds', default='12.4,21.6,27,30,40,52.5,65,80,90')
    p.add_argument('--beam', type=int, default=32); p.add_argument('--threads', type=int, default=8)
    p.add_argument('--fixtures', type=Path); p.add_argument('--survey-dir', type=Path)
    p.add_argument('--kernel', action='store_true', help='Use the verified rooted Motorola touchscreen probe')
    args = p.parse_args()
    if not 5 <= args.seconds <= (165 if args.record else 600): p.error('seconds must be 5..165 for recording, or 5..600 otherwise')
    if args.beam < 1 or args.threads < 1: p.error('beam and threads must be positive')
    if args.mode == 'replay':
        if not args.fixtures or not args.survey_dir: p.error('replay needs --fixtures and --survey-dir')
        expected = next(line.split('=', 1)[1].strip() for line in (args.survey_dir / 'run.properties').read_text().splitlines() if line.startswith('fixtureSha256='))
        if hashlib.sha256(args.fixtures.read_bytes()).hexdigest() != expected: p.error('fixtures do not match this survey')
    output = (args.output or ROOT / 'captures' / 'bot' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')).resolve()
    output.mkdir(parents=True, exist_ok=True)
    temp = ROOT / 'captures' / 'tmp'; temp.mkdir(parents=True, exist_ok=True)
    os.environ['TMPDIR'] = str(temp)
    if args.mode == 'report':
        if not args.survey_dir: p.error('report needs --survey-dir')
        print(generate(args.survey_dir, output)); return
    if not args.no_build:
        run([ROOT / 'gradlew', f'-Djava.io.tmpdir={temp}', 'assembleDebug', 'assembleDebugAndroidTest', ':bot:installDist', '--offline'], cwd=ROOT)
    if args.mode == 'survey' and args.fixtures:
        fixtures = args.fixtures.resolve()
    else:
        device = Device(args.device)
        with device.preserve(output):
            run(device.adb + ['install', '-r', ROOT / 'app/build/outputs/apk/debug/app-debug.apk'])
            run(device.adb + ['install', '-r', ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
            if args.mode in ('survey', 'verify'):
                device.instrument('BotModelTest', output, variants=6)
                device.pull('bot-courses.bin', output)
                fixtures = output / 'bot-courses.bin'
            elif args.mode == 'play':
                try:
                    with device.recording(output, args.record):
                        device.instrument('BotPlayTest', output, seconds=args.seconds, section=args.section,
                            world=args.world, boosts=args.boosts, magnet=args.magnet, requireSurvival=not args.allow_death)
                finally:
                    device.pull('bot-play.csv', output)
                    device.pull('bot-play-summary.txt', output)
            elif args.mode == 'input':
                if args.kernel:
                    sdk = Path(os.environ.get('ANDROID_HOME', '/home/user1/android-sdk'))
                    compilers = sorted(sdk.glob('ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang'))
                    if not compilers: raise RuntimeError('An arm64 Android NDK compiler is required for --kernel')
                    binary = output / 'input_probe'
                    run([compilers[-1], '-O2', '-Wall', '-Wextra', ROOT / 'tools/bot/native/input_probe.c', '-o', binary])
                    run(device.adb + ['push', binary, '/data/local/tmp/cube-run-bot-input'])
                    run(device.adb + ['push', ROOT / 'tools/bot/native/probe.sh', '/data/local/tmp/cube-run-bot-probe.sh'])
                    device.shell('chmod', '755', '/data/local/tmp/cube-run-bot-input')
                    device.shell('chmod', '755', '/data/local/tmp/cube-run-bot-probe.sh')
                try:
                    device.instrument('InputCapacityTest', output, kernel=args.kernel)
                    device.pull('bot-input-kernel.csv' if args.kernel else 'bot-input.csv', output)
                finally:
                    if args.kernel: device.shell('rm', '/data/local/tmp/cube-run-bot-input', '/data/local/tmp/cube-run-bot-probe.sh')
            elif args.mode == 'replay':
                generate(args.survey_dir, output)
                run(device.adb + ['push', args.fixtures, f'{DEVICE_DIR}/bot-courses.bin'])
                run(device.adb + ['push', output / 'bot-witnesses.zip', f'{DEVICE_DIR}/bot-witnesses.zip'])
                try: device.instrument('BotReplayTest', output)
                finally: device.pull('bot-witness-results.csv', output)
    if args.mode == 'survey':
        env = os.environ.copy(); env['JAVA_OPTS'] = f'-Xmx3g -Djava.io.tmpdir={temp}'
        if Path('/usr/lib/jvm/java-17-openjdk-amd64').is_dir(): env['JAVA_HOME'] = '/usr/lib/jvm/java-17-openjdk-amd64'
        survey = output / 'survey'
        run([ROOT / 'tools/bot/build/install/bot/bin/bot', fixtures, survey, args.speeds, args.beam, args.threads], env=env)
        print(generate(survey, output))
    print(f'Local results: {output}')


if __name__ == '__main__':
    main()
