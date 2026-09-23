#!/usr/bin/env python3
"""Summarize sampled Android method-trace CPU time (never use traced runs as FPS results)."""
import argparse
from collections import Counter, defaultdict
from pathlib import Path
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('trace', type=Path)
parser.add_argument('--thread', default='GLThread')
args = parser.parse_args()
raw = args.trace.read_bytes()
start = raw.index(b'*end\n') + len(b'*end\n')
header = raw[:start].decode()
if 'clock=dual' not in header or struct.unpack_from('<H', raw, start + 4)[0] != 3:
    raise ValueError('Expected version 3 dual-clock Android trace')
threads, methods = {}, {}
section = ''
for line in header.splitlines():
    if line.startswith('*'):
        section = line
    elif section == '*threads':
        ident, name = line.split('\t', 1)
        threads[int(ident)] = name
    elif section == '*methods':
        ident, owner, name, *_ = line.split('\t')
        methods[int(ident, 16)] = f'{owner}.{name}'
size = struct.unpack_from('<H', raw, start + 16)[0]
offset = start + struct.unpack_from('<H', raw, start + 6)[0]
stacks = defaultdict(list)
previous = {}
self_us, inclusive_us = Counter(), Counter()
for pos in range(offset, len(raw) - size + 1, size):
    thread, encoded, cpu, wall = struct.unpack_from('<HIII', raw, pos)
    if args.thread not in threads.get(thread, ''):
        continue
    stack = stacks[thread]
    delta = max(0, cpu - previous.get(thread, cpu))
    if stack:
        self_us[stack[-1]] += delta
        for frame in set(stack):
            inclusive_us[frame] += delta
    previous[thread] = cpu
    method, action = encoded & ~3, encoded & 3
    if action == 0:
        stack.append(method)
    elif method in stack:
        # Sampling can omit intermediate exits when unwinding.
        del stack[len(stack) - 1 - stack[::-1].index(method):]
print(f'Thread filter: {args.thread}; sampled CPU total {sum(self_us.values()) / 1000:.1f} ms')
print('Self ms | Inclusive ms | Method')
for method, elapsed in self_us.most_common(30):
    print(f'{elapsed / 1000:8.1f} | {inclusive_us[method] / 1000:12.1f} | {methods.get(method, hex(method))}')
