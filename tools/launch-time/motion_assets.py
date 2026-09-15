#!/usr/bin/env python3
"""Generate the moving vector and the debug-only 1080p60 video experiment.

uv run --no-project tools/launch-time/motion_assets.py [--video]
The geometry is projected in 3D; animation never rotates a flat cube picture.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from math import sin, cos, atan2, atan, tan, pi, sqrt
from pathlib import Path
import subprocess
import re

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--video', action='store_true')
args = parser.parse_args()
FACES = [[1,5,7,3], [4,0,2,6], [2,3,7,6], [0,4,5,1], [5,4,6,7], [0,1,3,2]]
NORMALS = [(1,0,0), (-1,0,0), (0,1,0), (0,-1,0), (0,0,1), (0,0,-1)]


def ease(t):
    t = min(1, max(0,t))
    return t*t*t*(t*(t*6-15)+10)


def frame(t, width=288, height=288, density=1, icon=True):
    move = 0 if icon else ease(t/1.15)
    yaw = (-125*(1-move)+40*t)*pi/180
    tilt = -12*(1-move)*pi/180
    camera_y, camera_z = 2.6+3.035*move, 6.8+2.2*move
    pitch = atan2(-2.15,6.8)*(1-move)+atan2(-4.81,23)*move
    height_dp = 620 if icon else height/density
    fov = 2*atan(tan(pi/9)*height_dp/620)*(1-move)+(pi/3)*move
    focal = (620 if icon else height)/(2*tan(fov/2))
    def rotate(x,y,z):
        x,y = x*cos(tilt)-y*sin(tilt), x*sin(tilt)+y*cos(tilt)
        return x*cos(yaw)+z*sin(yaw),y,-x*sin(yaw)+z*cos(yaw)
    out=[]
    for shell in range(2):
        settle=min(1,max(0,(t-1.04)/.53))
        squash=.12*sin(settle*pi*2)*(1-settle)
        size=.82+.18*move
        breathe=1+.03*move*sin(t*2.4)
        sx=.9*(1.18+.06*sin(t*8))*size if shell else .9*size*(1+squash*.5)*breathe
        sy=sx if shell else .9*size*(1-squash)/breathe
        vertices=[rotate((.5 if i&1 else -.5)*sx,(.5 if i&2 else -.5)*sy,(.5 if i&4 else -.5)*sx) for i in range(8)]
        vertices=[(x,y+.45,z) for x,y,z in vertices]
        points=[]
        for x,y,z in vertices:
            dy,dz=y-camera_y,z-camera_z
            depth=dy*sin(pitch)-dz*cos(pitch)
            points.append((width/2+x*focal/depth,height/2-(dy*cos(pitch)+dz*sin(pitch))*focal/depth))
        base=(1,.595,.802) if shell else (1,.55,.78)
        for face,normal in enumerate(NORMALS):
            nx,ny,nz=rotate(*normal)
            px,py,pz=vertices[FACES[face][0]]
            visible=nx*(-px)+ny*(camera_y-py)+nz*(camera_z-pz)>0
            alpha=(.22+.08*sin(t*6)) if shell else 1
            if not visible: alpha=0
            l1=max(0,(nx*.45+ny*.85+nz*.35)/sqrt(.45**2+.85**2+.35**2))
            l2=max(0,(-nx*.6+ny*.2-nz*.5)/sqrt(.6**2+.2**2+.5**2))
            color='#'+''.join(f'{round(255*min(1,base[i]*(a+l1*b+l2*c))):02X}' for i,(a,b,c) in enumerate(zip((.55,.55,.6),(.85,.85,.8),(.25,.22,.3))))
            path='M'+' L'.join(f'{points[i][0]:.3f},{points[i][1]:.3f}' for i in FACES[face])+' Z'
            out.append((path,color,alpha))
    return out


res=ROOT/'app/src/main/res'
(res/'animator').mkdir(exist_ok=True)
for stale in (res/'animator').glob('launch_face_*.xml'): stale.unlink()
initial=frame(0)
vector=['<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" tools:ignore="VectorRaster" android:width="288dp" android:height="288dp" android:viewportWidth="288" android:viewportHeight="288">']
animated=['<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/launch_cube_animated_base">']
samples=[frame(i/60) for i in range(61)]
# The entire moving shell must fit, including pulse peaks between path segments.
for sample in samples:
    for path, _, alpha in sample:
        if alpha == 0: continue
        values=list(map(float,re.findall(r'-?\d+\.\d+',path)))
        assert all((x-144)**2+(y-144)**2 <= 96**2 for x,y in zip(values[::2],values[1::2])), 'Animated cube clips the system icon mask'

for face,(path,color,alpha) in enumerate(initial):
    if not any(sample[face][2] > 0 for sample in samples): continue
    vector.append(f'<path android:name="face{face}" android:fillColor="{color}" android:fillAlpha="{alpha:.4f}" android:pathData="{path}"/>')
    animated.append(f'<target android:name="face{face}" android:animation="@animator/launch_face_{face}"/>')
    # Android's inflater only loads float/int/color keyframes. Path morphs need
    # valueFrom/valueTo animators, grouped sequentially (not path keyframes).
    animator=['<set xmlns:android="http://schemas.android.com/apk/res/android" android:ordering="together">']
    for property_name,value_type,index in [('pathData','pathType',0),('fillColor','colorType',1),('fillAlpha','floatType',2)]:
        if index == 0:
            animator.append('<set android:ordering="sequentially">')
            for i in range(8):
                start=frame(i/8)[face][0]; end=frame((i+1)/8)[face][0]
                animator.append(f'<objectAnimator android:propertyName="pathData" android:valueType="pathType" android:valueFrom="{start}" android:valueTo="{end}" android:duration="125" android:interpolator="@android:interpolator/linear"/>')
            animator.append('</set>')
        else:
            animator.append('<objectAnimator android:duration="1000" android:interpolator="@android:interpolator/linear">')
            animator.append(f'<propertyValuesHolder android:propertyName="{property_name}" android:valueType="{value_type}">')
            for i in range(31):
                value=samples[i*2][face][index]
                if index==2: value=f'{value:.4f}'
                animator.append(f'<keyframe android:fraction="{i/30:.6f}" android:value="{value}"/>')
            animator.extend(['</propertyValuesHolder>','</objectAnimator>'])
    animator.append('</set>')
    (res/f'animator/launch_face_{face}.xml').write_text('\n'.join(animator)+'\n')
vector.append('</vector>');animated.append('</animated-vector>')
(res/'drawable/launch_cube_animated_base.xml').write_text('\n'.join(vector)+'\n')
(res/'drawable/launch_cube_motion.xml').write_text('\n'.join(animated)+'\n')

if args.video:
    directory=ROOT/'.build-tmp/video-frames'
    directory.mkdir(parents=True,exist_ok=True)
    def render(i):
        svg=directory/f'{i:03}.svg'; png=directory/f'{i:03}.png'
        paths=frame(i/60,1080,2340,2.75,icon=False)
        svg.write_text('<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="2340"><rect width="1080" height="2340" fill="#14102E"/>'+''.join(f'<path d="{p}" fill="{c}" opacity="{a:.4f}"/>' for p,c,a in paths)+'</svg>')
        subprocess.run(['magick',str(svg),str(png)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    with ThreadPoolExecutor(max_workers=4) as pool: list(pool.map(render,range(106)))
    output=ROOT/'app/src/debug/res/raw/intro_probe.mp4'; output.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['ffmpeg','-v','error','-framerate','60','-i',str(directory/'%03d.png'),'-c:v','libx264','-preset','medium','-crf','16','-pix_fmt','yuv420p','-movflags','+faststart',str(output)],check=True)
    print(f'Video: {output.stat().st_size} bytes')
