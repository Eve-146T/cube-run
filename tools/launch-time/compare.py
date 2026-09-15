#!/usr/bin/env python3
"""Align screenrecord videos to the launch request, retaining pre-request frames.

Trimming before PTS-STARTPTS silently subtracts each recording's different gap
to its next frame. Shift the original timestamps first and sample at a shared
60 Hz clock so that both videos have the same time zero.
"""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--before', type=Path, required=True)
parser.add_argument('--after', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--before-label', default='Original intro')
parser.add_argument('--after-label', default='Moving startup cube')
args = parser.parse_args()


def request_offset(directory):
    video = directory / 'launch-2.mp4'
    trace = (directory / 'trace-2.txt').read_text()
    request = float(re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request', trace, re.M)[1])
    metadata = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(video),
                                       '-map', '0:1', '-c', 'copy', '-f', 'data', '-'])
    magic = b'#VV1NSC0PET1ME!#'
    if not metadata.startswith(magic):
        raise ValueError('Missing Winscope frame timestamps')
    first_time, = struct.unpack_from('<Q', metadata, len(magic) + 4)
    frames = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
                                                '-show_entries', 'frame=pts_time', '-of', 'json', str(video)]))
    offset = request - first_time / 1e6 + float(frames['frames'][0]['pts_time'])
    return video, offset


before, before_offset = request_offset(args.before)
after, after_offset = request_offset(args.after)
filters = []
for index, offset, title, subtitle in [
    (0, before_offset, 'Before', args.before_label),
    (1, after_offset, 'After', args.after_label),
]:
    filters.append(
        f'[{index}:v]setpts=PTS-{offset:.9f}/TB,fps=60:start_time=0:round=up,trim=duration=3.5,'
        f'scale=360:780,pad=360:900:0:120:color=0x14102e,'
        f"drawtext=text='{title}':fontcolor=white:fontsize=24:x=20:y=14,"
        f"drawtext=text='{subtitle}':fontcolor=white:fontsize=17:x=20:y=48[v{index}]"
    )
filters.append("[v0][v1]hstack=inputs=2,drawtext=text='Launch + %{pts\\:flt} s':"
               'fontcolor=white:fontsize=22:x=(w-tw)/2:y=85[out]')
subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-i', str(before), '-i', str(after),
                '-filter_complex', ';'.join(filters), '-map', '[out]', '-t', '3.5', '-c:v', 'libx264',
                '-preset', 'fast', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
                str(args.output)], check=True)
print(json.dumps({'before_request_pts': before_offset, 'after_request_pts': after_offset,
                  'output': str(args.output)}, indent=2))
