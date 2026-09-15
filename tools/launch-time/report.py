#!/usr/bin/env python3
"""Collect auditable frame measurements, raw traces, APK hashes and size results."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import zipfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--before',type=Path,required=True)
p.add_argument('--after',type=Path,required=True)
p.add_argument('--before-apk',type=Path,required=True)
p.add_argument('--after-apk',type=Path,required=True)
p.add_argument('--release-apk',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
def apk(path):
    return {'path':str(path),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
def results(path):
    runs=[]
    for i in range(1,4):
        runs.append({'trial':i,'pixels':json.loads((path/f'pixels-{i}.json').read_text()),
                     'motion':json.loads((path/f'motion-{i}.json').read_text()),
                     'menu':json.loads((path/f'menu-{i}.json').read_text()),
                     'trace':(path/f'trace-{i}.txt').read_text(),
                     'am_start':(path/f'start-{i}.txt').read_text()})
    summary={}
    for name,values in {
        'first_cube_pixels_ms':[r['pixels']['first_cube_after_request_ms'] for r in runs],
        'motion_confirmed_by_ms':[r['motion']['motion_confirmed_by']['seconds']*1000 for r in runs],
        'menu_visually_settled_ms':[r['menu']['menu_settled']['seconds']*1000 for r in runs],
    }.items():summary[name]={'min':min(values),'median':statistics.median(values),'max':max(values)}
    return {'directory':str(path),'device':json.loads((path/'device.json').read_text()),'runs':runs,'summary':summary,
            'instrumentation':json.loads((path/'results.json').read_text())}
report={'baseline_commit':'4a314a9','method':'Three recorded, process-cold launches each; world 0; device-clock request origin. Motion is a conservative upper bound; menu uses the white-counter visual tolerance.',
        'before':results(a.before),'after':results(a.after),
        'apks':{'before_debug':apk(a.before_apk),'after_debug':apk(a.after_apk),'after_release_unsigned':apk(a.release_apk)},
        'previous_release_bytes':8952998,'release_net_bytes_saved':8952998-a.release_apk.stat().st_size}
with zipfile.ZipFile(a.release_apk) as z:
    report['release_largest_entries']=[{'name':i.filename,'packaged_bytes':i.compress_size} for i in sorted(z.infolist(),key=lambda i:i.compress_size,reverse=True)[:10]]
    report['debug_probe_activity_in_release']=any(b'Lcube/run/IntroProbeActivity;' in z.read(i) for i in z.infolist() if i.filename.endswith('.dex'))
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:report[k]['summary'] for k in ['before','after']},indent=2))
print(json.dumps(report['apks'],indent=2))
