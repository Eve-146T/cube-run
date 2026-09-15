#!/usr/bin/env python3
"""Export source frames at device-clock offsets, with no retiming of the recording."""
import argparse
from pathlib import Path
import re
import subprocess
from video_clock import frame_times

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('video', type=Path)
p.add_argument('--trace', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--step', type=float, default=.1)
p.add_argument('--duration', type=float, default=2.5)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
request = float(re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request', a.trace.read_text(), re.M)[1])
times = [t-request for t in frame_times(a.video)]
count = len(times)
chosen = sorted(set(min(range(count),key=lambda i: abs(times[i]-n*a.step)) for n in range(int(a.duration/a.step)+1)))
process = subprocess.Popen(['ffmpeg','-v','error','-i',str(a.video),'-vf','scale=216:468','-fps_mode','passthrough','-enc_time_base','1:1000000','-pix_fmt','rgb24','-f','rawvideo','-'],stdout=subprocess.PIPE)
files=[]
for i,t in enumerate(times):
    pixels=process.stdout.read(216*468*3)
    assert len(pixels)==216*468*3
    if i in chosen:
        ppm=a.output/f'{i:04}.ppm'; ppm.write_bytes(b'P6\n216 468\n255\n'+pixels)
        png=ppm.with_suffix('.png')
        subprocess.run(['magick',str(ppm),'-background','#14102e','-fill','white','-gravity','south','-splice','0x28','-pointsize','16','-annotate','+0+5',f'{t:.3f}s  f{i}',str(png)],check=True)
        ppm.unlink(); files.append(str(png))
assert process.wait()==0
subprocess.run(['magick','montage',*files,'-tile','6x','-geometry','+2+2',str(a.output/'sheet.png')],check=True)
