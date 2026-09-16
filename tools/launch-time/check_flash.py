#!/usr/bin/env python3
"""Reject even one blank cube frame during a recorded Ghost splash handoff.

Uses original device-clock timestamps. Scoped to the test emulator's dark,
empty launcher centre and equipped Ghost skin; inspect source frames too.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from video_clock import frame_times

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('video', type=Path)
p.add_argument('--trace', type=Path, required=True)
a = p.parse_args()
trace = a.trace.read_text()
request = float(re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request', trace, re.M)[1])
revealed = float(re.search(r'^\s*([\d.]+).*CUBE_START: .*scene revealed', trace, re.M)[1])
times = [t-request for t in frame_times(a.video)]
end = revealed-request+.15
decoder = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', str(a.video),
    '-vf', 'scale=216:468', '-fps_mode', 'passthrough', '-enc_time_base', '1:1000000',
    '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'], stdout=subprocess.PIPE)
samples = []
first = None
missing = []
overlaps = []
for i, t in enumerate(times):
    pixels = decoder.stdout.read(216*468*3)
    if len(pixels) != 216*468*3:
        raise RuntimeError('Decoded frame count differs from device-clock metadata')
    if not 0 <= t <= end:
        continue
    # Include both the centred system cube and its initial camera travel.
    visible = 0
    centred = 0
    centre_brightness = []
    for y in range(175, 365):
        for x in range(65, 151):
            rgb = pixels[(y*216+x)*3:(y*216+x)*3+3]
            if min(rgb) > 65 and max(rgb)-min(rgb) < 40:
                visible += 1
                if 195 <= y < 273 and 80 <= x < 136:
                    centred += 1
                    centre_brightness.append(sum(rgb)/3)
    if first is None and centred >= 100:
        first = i
    sample = {'frame': i, 'seconds': round(t, 6), 'ghost_pixels': visible}
    if centred >= 100 and .35 <= t < revealed-request:
        centre_brightness.sort()
        sample['centre_p90'] = round(centre_brightness[int(.9*centred)], 3)
        # On the ink background Ghost's .72 body alpha and <= .1575 shell
        # alpha yield at most ~205/255. Allow codec error, reject doubled cubes.
        if sample['centre_p90'] > 220:
            overlaps.append(sample)
    samples.append(sample)
    if first is not None and visible < 100:
        missing.append(sample)
if decoder.wait() != 0 or first is None:
    raise RuntimeError('Recording did not contain a detectable Ghost cube')
print(json.dumps({'video': str(a.video), 'first_cube_frame': first,
    'checked_until_seconds': round(end, 6), 'missing_cube_frames': missing,
    'overbright_cube_frames': overlaps,
    'samples': samples}, indent=2))
raise SystemExit(1 if missing or overlaps else 0)
