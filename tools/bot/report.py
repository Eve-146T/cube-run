"""Generate the local reference and replay bundle from measured solver trials."""
import csv
import collections
import json
import itertools
from pathlib import Path
import zipfile


def generate(survey: Path, output: Path):
    rows = list(csv.DictReader((survey / 'trials.csv').open()))
    metadata = dict(line.strip().split('=', 1) for line in (survey / 'run.properties').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
    if metadata.get('complete') != 'true' or len(rows) != int(metadata['expectedTrials']):
        raise ValueError('Survey is incomplete; refusing to publish partial coverage as a reference')
    keys = [tuple(r[k] for k in ('id', 'seed', 'mirror', 'entry', 'phase', 'speed', 'profile')) for r in rows]
    if len(set(keys)) != len(keys): raise ValueError('Duplicate trials in the survey')
    output.mkdir(parents=True, exist_ok=True)
    groups = collections.defaultdict(list)
    for row in rows:
        groups[int(row['id'])].append(row)
    speeds = sorted({float(r['speed']) for r in rows})
    profiles = {'perfect', 'pro-model', 'relaxed-model'}
    if len(groups) != int(metadata['sectionCount']): raise ValueError('Missing sections')
    for ident, trials in groups.items():
        variants = {tuple(r[k] for k in ('seed', 'mirror', 'entry', 'phase')) for r in trials}
        if len(variants) != 6 or len(trials) != 6 * len(speeds) * 3:
            raise ValueError(f'Incomplete variant/profile/speed coverage for section {ident}')
        expected = {(v, s, p) for v, s, p in itertools.product(variants, speeds, profiles)}
        actual = {(tuple(r[k] for k in ('seed', 'mirror', 'entry', 'phase')), float(r['speed']), r['profile']) for r in trials}
        if actual != expected: raise ValueError(f'Missing or unexpected trial combinations for section {ident}')
    summaries = []
    for ident, trials in sorted(groups.items()):
        def at(profile, speed):
            return [r for r in trials if r['profile'] == profile and float(r['speed']) == speed]
        def passed(profile, speed, robust=False):
            rs = at(profile, speed)
            return len(rs) == 6 and all(r['solved'] == 'true' and (not robust or int(r['jitter_1_of_12']) >= 11) for r in rs)
        def highest(profile, robust=False):
            return max((s for s in speeds if passed(profile, s, robust)), default=0)
        def first_gap(profile):
            return next((s for s in speeds if not passed(profile, s)), None)
        pro = at('pro-model', 30)
        relaxed = at('relaxed-model', 30)
        jitter = sum(int(r['jitter_1_of_12']) for r in pro) / max(1, len(pro) * 12)
        broad = sum(int(r['jitter_3_of_12']) for r in relaxed) / max(1, len(relaxed) * 12)
        peak = max((int(r['peak_per_second']) for r in pro), default=0)
        if len(pro) != 6 or len(relaxed) != 6:
            rating = 'Not tested at 30'
        elif not passed('pro-model', 30):
            rating = 'Not solved at 30'
        elif peak == 0:
            rating = '1 Gentle'
        elif broad >= .9 and peak <= 3:
            rating = '2 Moderate'
        elif jitter >= .95:
            rating = '3 Skilled'
        elif jitter >= .8:
            rating = '4 Expert timing'
        else:
            rating = '5 Very tight'
        summaries.append(dict(id=ident, name=trials[0]['name'], tier=int(trials[0]['tier']), rating_at_30=rating,
            perfect_highest_all_pass=highest('perfect'), perfect_first_not_solved=first_gap('perfect'),
            pro_cadence_highest_all_pass=highest('pro-model'), pro_robust_highest_all_pass=highest('pro-model', True),
            pro_peak_gestures_s_at_30=peak, pro_jitter_16ms_survival_at_30=round(jitter, 3),
            relaxed_jitter_50ms_survival_at_30=round(broad, 3)))
    with (output / 'section-ratings.csv').open('w') as f:
        w = csv.DictWriter(f, fieldnames=list(summaries[0])); w.writeheader(); w.writerows(summaries)
    (output / 'section-ratings.json').write_text(json.dumps(summaries, indent=2) + '\n')
    lines = [
        '# Section bot reference', '',
        f'{len(rows):,} trials; {len(groups)} patterns ({sum(i >= 0 for i in groups)} library sections, three hill patterns, warm-up and breather); six generated variants per pattern.', '',
        'All work and artifacts are local. These are bounded-search witnesses and reference ratings, not proofs of impossibility or measurements of human players.', '',
        '## How to read the numbers', '',
        '- Speed is world units per simulated second. Ordinary gameplay starts at 12.4, five boosts reach 21.6, cruise is 27, and the ground ceiling is 30. Jet flight can reach 52.5 but grants immunity; the speed survey deliberately tests **grounded, unassisted** traversal, even above 30.',
        '- Fixtures use the actual Track decoder and gap calculation. Each repeats its section twice, preserving lane-walk changes across the join. The six variants cover both mirrors and all three entry lanes, with seeds 73–78 and three animation-clock phases. This is sampled coverage, not every random seed or every pairing of different sections.',
        '- Physics runs at 60 Hz with one classic swipe per allowed input slot. Perfect allows one gesture per frame; the pro model spaces gestures by at least 8 frames (7.5/s); relaxed uses 12 frames (5/s). These are explicit analyst-selected input budgets, not empirically established human limits. The solver knows the course ahead, like a memorized run.',
        f'- Search uses a beam of {metadata["beam"]}, retried at {int(metadata["beam"]) * 3} when it fails. Survival discards fatal trajectories. Ratings ignore optional loot and paid protection; live play values coins and powerups while rejecting predicted crashes.',
        '- A successful route is moved toward the center of verified timing windows, then replayed with independent seeded jitter on each action: ±1 frame (16.7 ms) and ±3 frames (50 ms), 12 trials each. Input order is preserved and same-frame deliveries are serialized. This probes tolerance; it does not simulate visual recognition or fatigue.',
        '- Highest pass means **all six variants** passed at that sampled speed. Pro robust additionally requires at least 11/12 ±1-frame jitter replays for every variant. A failed search is “not solved”; it is not proof that a better bot or human cannot succeed.',
        f'- Moving obstacles and input sampling can make success non-monotonic with speed. Read both highest pass and first gap. Values at the top of the tested grid are lower bounds (≥{speeds[-1]:g}), not finite ceilings. Longitudinal collision checks are expanded across the frame to avoid counting obvious high-speed skips; every accepted route is also checked against the literal discrete model.', '',
        '## Ratings at speed 30', '',
        'Ratings describe found survival routes at the ordinary maximum: 1 needs no gestures; 2 has ≤3 gestures in its busiest second and ≥90% relaxed ±50 ms survival; 3 has ≥95% pro ±16.7 ms survival; 4 has ≥80%; 5 falls below that. They are bot-derived reference labels, not a definitive minimum skill ranking.', '',
        '| ID | Section | Rating at 30 | Perfect highest / first gap | Pro cadence highest | Pro robust highest | Peak gestures/s at 30 |',
        '| ---: | --- | --- | --- | ---: | ---: | ---: |',
    ]
    def fmt(n):
        if n is None: return 'none tested'
        if n == speeds[-1]: return f'≥{n:g}'
        return f'{n:g}'
    for s in summaries:
        gap = 'none tested' if s['perfect_first_not_solved'] is None else f"{s['perfect_first_not_solved']:g}"
        lines.append(f"| {s['id']} | {s['name']} | {s['rating_at_30']} | {fmt(s['perfect_highest_all_pass'])} / {gap} | {fmt(s['pro_cadence_highest_all_pass'])} | {fmt(s['pro_robust_highest_all_pass'])} | {s['pro_peak_gestures_s_at_30']} |")
    lines += ['', 'The CSV and JSON beside this file contain jitter scores for sorting and further analysis. See `docs/bot.md` for device validation, input measurements, limits, and commands.', '']
    (output / 'section-ratings.md').write_text('\n'.join(lines))
    # One normal-ceiling witness and one highest-speed perfect witness per variant.
    chosen = {}
    for row in rows:
        if row['profile'] != 'perfect' or row['solved'] != 'true': continue
        key = tuple(row[k] for k in ('id', 'seed', 'mirror', 'entry', 'phase'))
        speed = float(row['speed'])
        for kind in (['max', 'normal'] if speed == 30 else ['max']):
            old = chosen.get((key, kind))
            if old is None or speed > float(old['speed']): chosen[(key, kind)] = row
    with zipfile.ZipFile(output / 'bot-witnesses.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        added = set()
        for row in chosen.values():
            name = '_'.join(row[k] for k in ('id', 'seed', 'mirror', 'entry', 'phase', 'speed', 'profile')) + '.actions'
            if name not in added:
                archive.write(survey / name, name); added.add(name)
    return dict(trials=len(rows), patterns=len(groups), witnesses=len(added))


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(); p.add_argument('survey', type=Path); p.add_argument('output', type=Path)
    a = p.parse_args(); print(generate(a.survey, a.output))
