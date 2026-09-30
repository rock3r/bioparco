#!/usr/bin/env python3
"""Summarises bioparco Perfetto traces: section costs, and frame pacing.

Usage:
    pip install perfetto
    python3 tracing/tools/trace-summary.py <trace file or folder> [more ...]

A folder is searched for *.perfetto-trace files, so a whole BIOPARCO_TRACE_DIR works. For each
trace it prints every section name with its count, median, 95th percentile and worst duration,
indented under the section it runs in, and, if the trace has `frame` sections, how far apart
frames start: the frame pacing a viewer sees.

The first run downloads Perfetto's trace processor.
"""

import glob
import os
import statistics
import sys

from perfetto.trace_processor import TraceProcessor


def traces(paths):
    for path in paths:
        if os.path.isdir(path):
            yield from sorted(glob.glob(os.path.join(path, "**", "*.perfetto-trace"), recursive=True))
        else:
            yield path


def percentile(values, share):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(share * len(ordered)))]


def milliseconds(nanos):
    return f"{nanos / 1e6:8.2f}"


def summarise(path):
    processor = TraceProcessor(trace=path)
    print(path)
    rows = processor.query(
        """
        select s.name, s.dur, s.depth, coalesce(p.name, '') parent
        from slice s left join slice p on s.parent_id = p.id
        """
    )
    sections = {}
    for row in rows:
        sections.setdefault((row.depth, row.parent, row.name), []).append(row.dur)
    print(f"  {'section (in its parent)':60} {'count':>6} {'median':>8} {'p95':>8} {'worst':>8}   (ms)")
    for (depth, parent, name), durations in sorted(sections.items(), key=lambda kv: (kv[0][0], kv[0][1], -len(kv[1]))):
        label = ("  " * depth + name + (f"  (in {parent})" if parent else ""))[:60]
        print(
            f"  {label:60} {len(durations):6} {milliseconds(statistics.median(durations))}"
            f" {milliseconds(percentile(durations, 0.95))} {milliseconds(max(durations))}"
        )
    starts = [row.ts for row in processor.query("select ts from slice where name = 'frame' order by ts")]
    if len(starts) > 1:
        gaps = [later - earlier for earlier, later in zip(starts, starts[1:])]
        print(
            f"  frame to frame: median {milliseconds(statistics.median(gaps)).strip()} ms,"
            f" p95 {milliseconds(percentile(gaps, 0.95)).strip()} ms,"
            f" worst {milliseconds(max(gaps)).strip()} ms, over {len(starts)} frames"
            " (gaps include idle time with nothing to draw)"
        )
    processor.close()
    print()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for trace in traces(sys.argv[1:]):
        summarise(trace)
