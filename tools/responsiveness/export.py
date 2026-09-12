#!/usr/bin/env -S uv run --script
"""Export measurements for review, excluding device preferences and APKs."""
import argparse
import csv
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from analyze import summarize

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('capture_root', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
runs = sorted(args.capture_root.glob('baseline/*')) + sorted(args.capture_root.glob('improved/*'))
results = []
trials = []
for folder in runs:
    result = summarize(folder)
    meta = json.loads((folder/'run.json').read_text())
    result['apk_sha256'] = meta['sha256']
    results.append(result)
    kernel = list(csv.DictReader((folder/'response-kernel.csv').open()))
    for t in csv.DictReader((folder/'response-trials.csv').open()):
        events = [e for e in kernel if e['trial'] == t['trial']]
        trials.append(dict(run=folder.name, kernel_down_ns=next(e['ns'] for e in events if e['phase']=='down'),
            kernel_first_move_ns=next(e['ns'] for e in events if e['phase']=='move'), **t))
(args.output/'metrics.json').write_text(json.dumps(results, indent=2)+'\n')
with (args.output/'trials.csv').open('w') as f:
    writer = csv.DictWriter(f, fieldnames=list(trials[0])); writer.writeheader(); writer.writerows(trials)
jumps = []
for folder in sorted(args.capture_root.glob('jumps/*')):
    jumps += [dict(version=folder.name, **r) for r in csv.DictReader((folder/'response-jumps.csv').open())]
if jumps:
    with (args.output/'jumps.csv').open('w') as f:
        writer = csv.DictWriter(f, fieldnames=list(jumps[0])); writer.writeheader(); writer.writerows(jumps)
with ZipFile(args.output/'traces.zip', 'w', ZIP_DEFLATED) as z:
    for folder in runs + sorted(args.capture_root.glob('jumps/*')):
        for pattern in ['response-*.csv', 'response-summary.txt', 'device-*.txt', 'clocks.txt']:
            for p in folder.glob(pattern): z.write(p, str(p.relative_to(args.capture_root)))
print(f'{len(results)} latency runs, {len(trials)} gestures, {len(jumps)} jump cases exported to {args.output}')
