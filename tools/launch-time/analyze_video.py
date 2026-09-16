#!/usr/bin/env python3
"""Measure first visible cube using screenrecord's device-clock metadata.

uv run --no-project tools/launch-time/analyze_video.py VIDEO --trace TRACE

The emulator's launcher must have a dark, empty screen centre (the Pixel 5 test
AVD does). Detection requires 100 bright pixels in the centre of a 216×468
analysis frame. Check the reported frame visually; this is not a generic detector
for arbitrary wallpapers. No timing is inferred from host-side ADB latency.

Timestamp format: Android frameworks/av/cmds/screenrecord/screenrecord.cpp,
writeWinscopeMetadataLegacy (#VV1NSC0PET1ME!#, count, elapsed-realtime microseconds).
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from video_clock import frame_times

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('video', type=Path)
parser.add_argument('--trace', type=Path, required=True)
args = parser.parse_args()
trace = args.trace.read_text()
marker = re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request', trace, re.M)
if not marker:
    parser.error('Trace is missing the device-clock CUBE_LAUNCH request marker.')
launch_ms = float(marker[1]) * 1000
timestamps = frame_times(args.video)
count = len(timestamps)
decoder = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', str(args.video),
                            '-vf', 'scale=216:468', '-fps_mode', 'passthrough', '-enc_time_base', '1:1000000',
                            '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'], stdout=subprocess.PIPE)
first = None
previous_ms = None
decoded = 0
frame_bytes = 216 * 468 * 3
try:
    while True:
        frame = decoder.stdout.read(frame_bytes)
        if not frame:
            break
        if len(frame) != frame_bytes or decoded >= count:
            raise RuntimeError('Decoded frames and timestamp metadata disagree.')
        timestamp_ms = timestamps[decoded] * 1000
        if timestamp_ms >= launch_ms and first is None:
            bright = sum(max(frame[(y*216+x)*3:(y*216+x)*3+3]) > 60
                         for y in range(195, 273) for x in range(80, 136))
            if bright >= 100:
                first = {'frame': decoded, 'video_seconds': round(timestamps[decoded]-timestamps[0], 6),
                         'first_cube_after_request_ms': round(timestamp_ms-launch_ms, 2),
                         'previous_frame_after_request_ms': None if previous_ms is None else round(previous_ms-launch_ms, 2),
                         'bright_pixels': bright}
        previous_ms = timestamp_ms
        decoded += 1
finally:
    decoder.stdout.close()
    decoder.wait(timeout=10)
if decoder.returncode != 0 or decoded != count:
    raise RuntimeError(f'Decoded {decoded} frames, metadata has {count}.')
if first is None:
    raise RuntimeError('No visible cube detected. Inspect the recording.')
print(json.dumps(first, indent=2))
