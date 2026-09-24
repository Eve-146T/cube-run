"""Exercise the real watchdog against a filesystem-backed fake adb, never a phone.

TMPDIR must name a workspace directory. Run with:
  uv run --no-project -m unittest discover -s tools/performance -p 'test_throttle.py'
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class ClockWatchdogTest(unittest.TestCase):
    def exercise(self, outcome):
        with tempfile.TemporaryDirectory(dir=os.environ['TMPDIR']) as scratch:
            base = Path(scratch)
            device = base / 'device'
            device.mkdir()
            values = {
                '/sys/module/cpu_boost/parameters/input_boost_freq': '0:1401600 1:0 2:0 3:0 4:0 5:0 6:0 7:0',
                '/sys/module/msm_performance/parameters/cpu_max_freq': ' '.join(f'{i}:4294967295' for i in range(8)),
                '/sys/module/big_cluster_min_freq_adjust/parameters/min_freq_floor': '1094400',
                '/sys/class/kgsl/kgsl-3d0/devfreq/max_freq': '725000000',
                '/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies': '133330000 216000000 320000000 725000000',
            }
            for policy, low in ((0, 614400), (4, 633600)):
                prefix = f'/sys/devices/system/cpu/cpufreq/policy{policy}/'
                values[prefix + 'scaling_min_freq'] = str(low if policy == 0 else 1094400)
                values[prefix + 'scaling_max_freq'] = '1804800'
                values[prefix + 'scaling_available_frequencies'] = f'{low} 1094400 1804800'
            for name, value in values.items():
                path = device / name.lstrip('/')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value + '\n')
            for suffix in ('devices/system/cpu/cpufreq/policy0/scaling_',
                           'devices/system/cpu/cpufreq/policy4/scaling_',
                           'class/kgsl/kgsl-3d0/devfreq/'):
                path = device / 'sys' / (suffix + 'cur_freq')
                if outcome == 'overridden' and 'policy4' in suffix:
                    path.write_text('1094400\n')
                else:
                    path.symlink_to(path.with_name(path.name.replace('cur_freq', 'max_freq')))
            (device / 'data/local/tmp').mkdir(parents=True)
            fake = base / 'adb'
            fake.write_text(f'#!{sys.executable}\n' + r'''
import os, pathlib, shlex, subprocess, sys
root = os.environ['FAKE_DEVICE']
def translate(s):
    return s.replace('/sys/', root + '/sys/').replace('/data/local/tmp/', root + '/data/local/tmp/')
a = sys.argv[3:]
if a[0] == 'push':
    pathlib.Path(translate(a[2])).write_text(translate(pathlib.Path(a[1]).read_text()))
elif a[0] == 'shell':
    command = shlex.split(a[1])[2]
    if command.startswith('getprop '):
        print('running')
    else:
        raise SystemExit(subprocess.run(translate(command), shell=True).returncode)
else:
    raise SystemExit('Unexpected fake adb command: ' + repr(a))
''')
            fake.chmod(0o755)
            env = dict(os.environ, PATH=str(base) + os.pathsep + os.environ['PATH'], FAKE_DEVICE=str(device))
            marker = base / 'command-ran'
            command = [sys.executable, '-c',
                       f'from pathlib import Path; Path({str(marker)!r}).touch(); raise SystemExit({7 if outcome == "failure" else 0})']
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('throttle.py')),
                                     '--serial', 'fake', '--out', str(base / 'out'), '--timeout', '20', '--', *command],
                                    env=env, capture_output=True, text=True, timeout=18)
            self.assertEqual(7 if outcome == 'failure' else 1 if outcome == 'overridden' else 0,
                             result.returncode, result.stdout + result.stderr)
            self.assertEqual(outcome != 'overridden', marker.exists())
            original = json.loads((base / 'out/original-clocks.json').read_text())
            restored = json.loads((base / 'out/restored-clocks.json').read_text())
            self.assertEqual(original, restored)
            for path, value in values.items():
                self.assertEqual(value, (device / path.lstrip('/')).read_text().strip(), path)
            if outcome == 'overridden':
                self.assertIn('Clock caps did not hold before launch', result.stderr)

    def test_success_restores_every_changed_control(self):
        self.exercise('success')

    def test_failed_command_restores_controls_and_retains_exit_code(self):
        self.exercise('failure')

    def test_overridden_cap_prevents_launch_and_restores_controls(self):
        self.exercise('overridden')


if __name__ == '__main__':
    unittest.main()
