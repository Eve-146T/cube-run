#!/usr/bin/env -S uv run --script
"""Build the standalone measurement report from exported results."""
import argparse
import json
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('reference', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
runs = {r['run']: r for r in json.loads((a.reference/'metrics.json').read_text())}
versions = [('1.4','1.4'), ('2.0','2.0'), ('2.1','2.1'), ('2.2-performance','2.2 before'), ('2.2-responsive','2.2 after')]
conditions = [('cruise-8','Short flick · speed 10'), ('cruise-120','120 ms drag · speed 10'), ('fast-8','Short flick · speed 30')]
rows = ''
for condition, label in conditions:
    cells = ''
    for version, _ in versions:
        m = runs[version+'-'+condition]['metrics_ms']['down_to_render']
        cells += f'<td>{m["p50"]:.1f} <span>/ {m["p95"]:.1f}</span></td>'
    rows += f'<tr><th scope="row">{label}</th>{cells}</tr>'
columns = ''.join(f'<th scope="col">{label}</th>' for _, label in versions)
count = sum(r['trials'] for r in runs.values())
applied = sum(r['applied'] for r in runs.values())
html = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cube Run responsiveness</title><style>
:root{font:16px/1.55 system-ui,sans-serif;color:#241d39;background:#f5f2ea}*{box-sizing:border-box}body{margin:0}main{max-width:1100px;margin:auto;padding:36px 24px}h1{font-size:clamp(28px,5vw,44px);line-height:1.1;margin:12px 0}h2{font-size:21px;margin:0 0 12px}p{max-width:850px}a{color:#5934a8}small,.muted,td span{color:#6f647e}.panel{background:white;border:1px solid #ddd6e5;border-radius:14px;padding:22px;margin:24px 0}.scroll{overflow:auto}table{width:100%;border-collapse:collapse;white-space:nowrap;font-size:15px}th,td{padding:13px 12px;text-align:right;border-bottom:1px solid #e8e2ed}th:first-child{text-align:left}td:last-child,thead th:last-child{background:#edf5ef;color:#21543a}tbody tr:last-child>*{border-bottom:0}.grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}.links{display:flex;flex-wrap:wrap;gap:10px}.links a{padding:10px 16px;border:1px solid #d6cce2;border-radius:9px;min-height:44px;text-decoration:none;background:white}a:focus-visible{outline:3px solid #7044cc;outline-offset:3px}@media(max-width:680px){main{padding:20px 14px}.panel{padding:16px}.grid{grid-template-columns:1fr;gap:10px}th,td{padding:10px 8px}}
</style><main><small>MOTOROLA G7 POWER · 60 Hz · 12 SEPTEMBER 2026</small>
<h1>Cube Run responsiveness</h1>
<p>The new controls submit movement <strong>7–27 ms sooner</strong> in these tests. The historical comparison did not reproduce a general 2.0 latency regression on this phone. The platform-edge jump rejection was reproduced and fixed.</p>
<section class="panel"><h2>Touch to submitted movement</h2><p class="muted">Median / p95 in milliseconds · 48 gestures per cell · lower is better</p>
<div class="scroll"><table><thead><tr><th scope="col">Test</th>''' + columns + '</tr></thead><tbody>' + rows + '''</tbody></table></div>
<p><strong>''' + str(applied) + '/' + str(count) + ''' actions registered.</strong> “2.2 before” is the unreleased performance commit; “2.2 after” adds the responsiveness fixes.</p></section>
<div class="grid"><section><h2>What changed</h2><p>Classic flicks recognize at 5.5% of screen width, down from 8.5%. Android delivers their moves without batching. A release can complete a short flick. Smooth controls retain their existing sensitivity and batching.</p><p>Lane easing, gravity and jump height remain unchanged.</p></section>
<section><h2>Platform jumps</h2><p>Versions 2.0, 2.1 and the original 2.2 candidate rejected every tested UP after walking off a platform. The new build accepts jumps inside a <strong>100 ms grace window</strong> and remembers an UP just before landing.</p><p>Normal jumps do not grant a double jump. Down, pause, flight, hover, saves and bounce pads clear pending input.</p></section></div>
<section class="panel"><h2>What these numbers cover</h2><p>Rooted Motorola, normal CPU limits, battery 100%, sound and haptics enabled. Identical kernel-touch sequences probe source builds of 1.4, 2.0, 2.1 and 2.2. Touches sweep across display phases; the high-speed condition runs at 30 world units/s.</p><p>Timing ends when the first render callback containing movement returns. <strong>Physical digitizer scanning and display presentation are outside these numbers.</strong> The latency fixture clears nearby hazards; separate performance tests use the playing bot and count protected collisions. This does not establish that another player's lag report was never real.</p></section>
<nav class="links" aria-label="Evidence"><a href="responsiveness.md">Method and findings</a><a href="trials.csv">720 input trials</a><a href="jumps.csv">Jump cases</a><a href="traces.zip">Raw traces</a><a href="https://github.com/Eve-146T/cube-run/issues/3">Original report</a></nav>
</main></html>'''
a.output.write_text(html)
print(a.output)
