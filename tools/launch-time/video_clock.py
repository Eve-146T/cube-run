"""Read and validate screenrecord's original per-frame device clock."""
import json
from pathlib import Path
import struct
import subprocess


def frame_times(video):
    video = Path(video)
    magic = b'#VV1NSC0PET1ME!#'
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(video),
                                    '-map', '0:1', '-c', 'copy', '-f', 'data', '-'])
    if not data.startswith(magic):
        # Some emulator MP4s contain the packet but FFmpeg's demuxer exposes no
        # data packets. Recover the original bytes, never infer a launch offset.
        container = video.read_bytes()
        if container.count(magic) != 1:
            raise ValueError(f'Missing or ambiguous device-clock metadata: {video}')
        data = container[container.index(magic):]
    count, = struct.unpack_from('<I', data, len(magic))
    if count > 100000:
        raise ValueError(f'Invalid metadata frame count: {count}')
    times = [t/1e6 for t in struct.unpack_from(f'<{count}Q', data, len(magic)+4)]
    decoded = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
        '-show_frames', '-show_entries', 'frame=best_effort_timestamp_time', '-of', 'json', str(video)]))
    pts = [float(f['best_effort_timestamp_time']) for f in decoded['frames']]
    if not times or len(pts) != count or any(b <= a for a, b in zip(times, times[1:])):
        raise ValueError(f'Device clock does not match decoded frames: {video}')
    error = max(abs((t-times[0])-(p-pts[0])) for t, p in zip(times, pts))
    if error > .002:
        raise ValueError(f'Device-clock intervals differ from video PTS by {error}s: {video}')
    return times
