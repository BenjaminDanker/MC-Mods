#!/usr/bin/env python3
"""Summarize monotonic stage timings from a synthetic Dev Player Markdown session."""
import re
import statistics
import sys
from pathlib import Path


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, int((len(ordered) * fraction + .999999)) - 1))]


def main():
    text = Path(sys.argv[1]).read_text(encoding="utf-8")
    calls = [part for part in re.split(r"(?=^## say at )", text, flags=re.M)
             if part.startswith("## say at ")]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else len(calls)
    calls = calls[-count:]
    print(f"Synthetic conversation calls: {len(calls)}")
    stages = {}
    for call in calls:
        for name, value in re.findall(r"Timing `([^`]+)`: (\d+) ms", call):
            stages.setdefault(name, []).append(int(value))
    for name, values in stages.items():
        if len(values) != len(calls):
            continue
        print(f"{name:38s} median={statistics.median(values):6.1f} "
              f"p95={percentile(values,.95):4d} range={min(values)}..{max(values)} ms")
    print("Per-call service pipeline:", stages.get("service_pipeline_to_validated_reply", []))
    print("Per-call provider:", stages.get("provider_complete", []))
    print("Per-call memory retrieval:", stages.get("memory_retrieval_all", []))


if __name__ == "__main__":
    main()
