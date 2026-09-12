#!/usr/bin/env -S uv run --script
"""Summarize probe CSVs; timestamps measure submission, not physical display latency."""
import argparse
import csv
import json
from pathlib import Path


def percentile(values, fraction):
    values = sorted(values)
    if not values:
        return None
    at = (len(values) - 1) * fraction
    lo = int(at)
    return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (at - lo)


def summarize(folder):
    kernel = list(csv.DictReader((folder / 'response-kernel.csv').open()))
    trials = list(csv.DictReader((folder / 'response-trials.csv').open()))
    metrics = {}
    def add(name, end, start):
        if end and start:
            assert end >= start, (folder, name, end, start)
            metrics.setdefault(name, []).append((end - start) / 1e6)
    for t in trials:
        events = [e for e in kernel if e['trial'] == t['trial']]
        assert len([e for e in events if e['phase'] == 'down']) == 1
        down = int(next(e['ns'] for e in events if e['phase'] == 'down'))
        move = int(next(e['ns'] for e in events if e['phase'] == 'move'))
        v = {k: int(x) for k, x in t.items()}
        add('kernel_down_to_ui', v['ui_down_ns'], down)
        add('ui_down_to_gl', v['gl_down_ns'], v['ui_down_ns'])
        add('kernel_move_to_gl', v['gl_first_move_ns'], move)
        add('down_to_action', v['applied_ns'], down)
        add('down_to_render', v['first_render_ns'], down)
        add('action_to_render', v['first_render_ns'], v['applied_ns'])
        add('action_to_half_lane', v['half_lane_ns'], v['applied_ns'])
        add('action_to_ninety_lane', v['ninety_lane_ns'], v['applied_ns'])
    for f in csv.DictReader((folder/'response-frames.csv').open()):
        for key, value in f.items():
            metrics.setdefault(key, []).append(int(value) / 1e6)
    result = dict(run=folder.name, trials=len(trials), applied=sum(int(t['applied_ns']) > 0 for t in trials), rendered=sum(int(t['first_render_ns']) > 0 for t in trials))
    result['metrics_ms'] = {k: dict(n=len(v), p50=round(percentile(v, .5), 2), p95=round(percentile(v, .95), 2), p99=round(percentile(v, .99), 2)) for k, v in metrics.items()}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folders', nargs='+', type=Path)
    args = parser.parse_args()
    print(json.dumps([summarize(p) for p in args.folders], indent=2))
