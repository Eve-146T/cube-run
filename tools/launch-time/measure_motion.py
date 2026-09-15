#!/usr/bin/env python3
"""Conservative motion confirmation from normalized cube silhouettes.

This reports an UPPER BOUND, not the first instant of motion: it ignores the
first 300 ms (Android's window zoom) and requires a changed silhouette on two
consecutive source frames. The first cube is reported separately. Review the
source filmstrip; the detector is specific to the test emulator's empty centre.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from video_clock import frame_times

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('video',type=Path)
p.add_argument('--trace',type=Path,required=True)
a=p.parse_args()
request=float(re.search(r'^\s*([\d.]+).*CUBE_LAUNCH: request',a.trace.read_text(),re.M)[1])
times=[v-request for v in frame_times(a.video)]
count=len(times)
# Grayscale is sufficient for the silhouette, at half emulator resolution.
process=subprocess.Popen(['ffmpeg','-v','error','-i',str(a.video),'-vf','scale=540:1170,format=gray','-fps_mode','passthrough','-enc_time_base','1:1000000','-f','rawvideo','-'],stdout=subprocess.PIPE)
first=None; reference=None; consecutive=0; motion=None
samples=[]
for i,t in enumerate(times):
    pixels=process.stdout.read(540*1170)
    assert len(pixels)==540*1170
    if not 0 <= t <= 1.2: continue
    rows=[pixels[y*540+150:y*540+390] for y in range(480,690)]
    background=pixels[585*540+140]
    peak=max(map(max,rows))
    threshold=background+(peak-background)*.36
    mask=[(x,y) for y,row in enumerate(rows) for x,v in enumerate(row) if v>threshold and v-background>24]
    if len(mask)<1200: continue
    left=min(x for x,y in mask); right=max(x for x,y in mask)
    top=min(y for x,y in mask); bottom=max(y for x,y in mask)
    if first is None: first={'frame':i,'seconds':round(t,6)}
    if t<.3 or motion: continue
    # 24 row samples of both contours, normalized to the body bounding box.
    contours=[]
    for n in range(24):
        y=top+round((bottom-top)*(n+.5)/24)
        xs=[x for x,v in enumerate(rows[y]) if v>threshold and v-background>24]
        if not xs: xs=[(left+right)//2]
        contours.extend([(min(xs)-left)/(right-left),(max(xs)-left)/(right-left)])
    if reference is None: reference=contours; reference_frame=i
    change=sum(abs(x-y) for x,y in zip(reference,contours))/len(contours)
    samples.append({'frame':i,'seconds':round(t,6),'contour_change':round(change,5)})
    consecutive=consecutive+1 if change>.025 else 0
    if consecutive>=2: motion={'reference_frame':reference_frame,'frame':i,'seconds':round(t,6),'mean_contour_change':round(change,5)}
assert process.wait()==0
print(json.dumps({'first_cube':first,'motion_confirmed_by':motion,'samples':samples},indent=2))
