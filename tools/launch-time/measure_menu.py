#!/usr/bin/env python3
"""Measure visible menu settling on the test AVD's fixed white coin counter.

The logo keeps bobbing after the intro, so it cannot define completion. Sample
white counter pixels instead. Settled means within 1/255 average RGB intensity
of the final counter on three consecutive source frames, after one second.
This is a visual tolerance, not the exact mathematical end of the easing curve.
"""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('video',type=Path)
p.add_argument('--trace',type=Path,required=True)
a=p.parse_args()
request=float(re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request',a.trace.read_text(),re.M)[1])
meta=subprocess.check_output(['ffmpeg','-v','error','-i',str(a.video),'-map','0:1','-c','copy','-f','data','-'])
magic=b'#VV1NSC0PET1ME!#';assert meta.startswith(magic)
count,=struct.unpack_from('<I',meta,len(magic))
times=[v/1e6-request for v in struct.unpack_from(f'<{count}Q',meta,len(magic)+4)]
process=subprocess.Popen(['ffmpeg','-v','error','-i',str(a.video),'-vf','scale=216:468','-fps_mode','passthrough','-enc_time_base','1:1000000','-pix_fmt','rgb24','-f','rawvideo','-'],stdout=subprocess.PIPE)
frames=[]
for i,t in enumerate(times):
    pixels=process.stdout.read(216*468*3);assert len(pixels)==216*468*3
    frames.append(b''.join(pixels[(y*216+160)*3:(y*216+205)*3] for y in range(29,44)))
assert process.wait()==0
reference=frames[min(range(count),key=lambda i:abs(times[i]-3.2))]
mask=[i for i in range(0,len(reference),3) if min(reference[i:i+3])>240]
assert len(mask)>30, f'Expected white counter pixels, got {len(mask)}; inspect ROI.'
samples=[];first=None;consecutive=0
for i,t in enumerate(times):
    if t<1:continue
    error=sum(abs(frames[i][j+c]-reference[j+c]) for j in mask for c in range(3))/(len(mask)*3)
    samples.append({'frame':i,'seconds':round(t,6),'mean_difference':round(error,3)})
    consecutive=consecutive+1 if error<=1 else 0
    if consecutive==3:
        first={'frame':i-2,'seconds':round(times[i-2],6),'confirmed_frame':i,'mean_difference':round(error,3),'white_pixels':len(mask)};break
print(json.dumps({'menu_settled':first,'samples':samples},indent=2))
