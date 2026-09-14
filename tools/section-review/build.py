"""Build a standalone section review page from the real authored catalog."""
from pathlib import Path
import json, re

root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/kotlin/cube/run/game/track/Sections.kt').read_text()
ratings = {r['id']: r for r in json.loads((root / 'docs/bot-reference/section-ratings.json').read_text())}
base = dict(dg=0, ft=10, sld=20, hw=50, dw=60, hg=70, pn=80, st=110, vw=120, pl=130, pf=140, pd=150)
codes = dict(JP=30, DK=31, VD=32, PS=33, SW=34, TW=37, PM=38, EM=40, CF=41)

def preview(code):
    row = [0, 0, 0]
    def others(lane, kind):
        for i in range(3):
            if i != lane: row[i] = kind
    full = {30:2,31:3,32:4,33:5,34:6,37:13,38:14,41:11}
    if code in full: return [full[code]] * 3
    for start, kind in [(0,1),(50,2),(60,3),(80,10),(110,7),(120,4)]:
        if start <= code <= start + 2: others(code-start,kind)
    for start, kind in [(10,1),(20,8),(140,9),(150,12)]:
        if start <= code <= start + 2: row[code-start] = kind
    if 70 <= code <= 72: others(code-70,2); row[code-70] = 1
    if 130 <= code <= 132: others(code-130,1); row[code-130] = 9
    return row

# Match the selectable in-game catalog, excluding internal intro/bonus patterns.
catalog = source.split('val lib = listOf(', 1)[1].split('\n    )', 1)[0]
sections = []
for m in re.finditer(r'Sect\((-?\d+),\s*(\d+),\s*[\d.]+f,\s*"([^"]+)",\s*intArrayOf\((.*?)\)\s*(?:,\s*mirrorable\s*=\s*false)?\),?\s*(?://[^\n]*)?$', catalog, re.MULTILINE):
    ident, tier, name, steps = m.groups()
    parsed = []
    for step in steps.split(','):
        step = step.strip()
        call = re.fullmatch(r'(\w+)\((\d)\)',step)
        parsed.append(base[call[1]] + int(call[2]) if call else codes[step])
    ident = int(ident)
    sections.append(dict(id=ident,tier=int(tier),name=name,rows=[preview(s) for s in parsed],bot=ratings.get(ident)))
assert sections and len(sections) == len(re.findall(r'\bSect\(', catalog))
assert len({s['id'] for s in sections}) == len(sections)
# SectionsView presents tiers in order, then IDs within each tier.
sections.sort(key=lambda s:(s['tier'],s['id']))
template = (Path(__file__).parent/'template.html').read_text()
def write_page(path, data, page_template):
    encoded = json.dumps(data, separators=(',', ':')).replace('</', '<\\/')
    path.write_text(page_template.replace('/*SECTION_DATA*/[]', encoded))
    print(f'{path}: {len(data)} sections')

write_page(root/'docs/section-review.html', sections, template)
# Keep the existing cleanup link's drafts and original feedback available while
# generating its current catalog from exactly the same game source.
previous = {r['id']: r for r in json.loads((Path(__file__).parent/'previous-ratings.json').read_text())}
cleanup = [dict(s, previous=[previous[s['id']]] if s['id'] in previous else []) for s in sections]
cleanup_template = template.replace('cube-run-section-review-v1', 'cube-run-section-review-cleanup-worlds-v1')
write_page(root/'docs/section-review-cleanup.html', cleanup, cleanup_template)
