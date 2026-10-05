#!/usr/bin/env python3
"""Summarize isolated Dev Player transport and pipeline measurements."""
import json
import re
import statistics
import sys
from pathlib import Path


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, int(len(ordered) * fraction + .999999) - 1))]


def metric(block, pattern, cast=int):
    match = re.search(pattern, block)
    return cast(match.group(1)) if match else None


def summarize(label, blocks, stage):
    values = [metric(block, stage) for block in blocks]
    values = [value for value in values if value is not None]
    if values:
        print(f"{label:37s} n={len(values):2d} median={statistics.median(values):6.1f} "
              f"p95={percentile(values,.95):4d} range={min(values)}..{max(values)}")


def main():
    report = Path(sys.argv[1]).read_text(encoding="utf-8")
    normal = report.split("## diagnose-provider at ")[0]
    calls = [block for block in re.split(r"(?=^## say at )", normal, flags=re.M)
             if block.startswith("## say at ")][-28:]
    cold, warm = calls[:4], calls[4:]
    print("NORMAL calls", len(calls), "cold", len(cold), "warm", len(warm))
    patterns = {
        "service_pipeline_ms": r"Timing `service_pipeline_to_validated_reply`: (\d+) ms",
        "context_sql_embedding_vector_ms": r"Timing `context_sql_embedding_vector`: (\d+) ms",
        "memory_retrieval_ms": r"Timing `memory_retrieval_all`: (\d+) ms",
        "moderation_local_safety_ms": r"Timing `moderation_and_local_safety`: (\d+) ms",
        "admission_ms": r"Timing `admission_no_queue`: (\d+) ms",
        "prompt_assembly_ms": r"Timing `prompt_assembly`: (\d+) ms",
        "blocking_http_complete_ms": r"Provider HTTP: status=\d+ blocking_send_complete_ms=(\d+)",
        "provider_processing_header_ms": r"openai_processing_ms=(\d+)",
        "model_decode_validation_ms": r"Timing `model_decode_and_validation`: (\d+) ms",
        "sql_commit_ms": r"Timing `sql_commit`: (\d+) ms",
        "cli_total_ms": r"Timing `cli_total_including_embedding_job`: (\d+) ms",
        "request_bytes": r"Provider request: .*?request_bytes=(\d+)",
        "response_bytes": r"Provider HTTP: .*?response_bytes=(\d+)",
        "local_prompt_tokens": r'"totalPromptTokens":(\d+)',
        "provider_prompt_tokens": r"Provider result: .*?prompt_tokens=(\d+)",
        "cached_prompt_tokens": r"Provider result: .*?cached_prompt_tokens=(\d+)",
        "completion_tokens": r"Provider result: .*?completion_tokens=(\d+)",
        "reasoning_tokens": r"Provider result: .*?reasoning_tokens=(\d+)",
    }
    for label, pattern in patterns.items():
        summarize(label + " [warm]", warm, pattern)
    for label in ("service_pipeline_ms", "memory_retrieval_ms", "blocking_http_complete_ms"):
        summarize(label + " [cold]", cold, patterns[label])
    for name in ("systemTokens", "identityTokens", "shortTermDbTokens",
                 "longTermRelationalTokens", "longTermVectorTokens", "recentTurnsDbTokens",
                 "gameContextTokens", "ownerInputTokens", "instructionTokens"):
        summarize(name + " [warm]", warm, rf'"{name}":(\d+)')
    gaps = []
    for block in warm:
        roundtrip = metric(block, patterns["blocking_http_complete_ms"])
        processing = metric(block, patterns["provider_processing_header_ms"])
        if roundtrip is not None and processing is not None:
            gaps.append(roundtrip - processing)
    if gaps:
        print(f"roundtrip_minus_processing_header_ms     n={len(gaps):2d} "
              f"median={statistics.median(gaps):6.1f} "
              f"p95={percentile(gaps,.95):4d} range={min(gaps)}..{max(gaps)}")
    print("provider request ids", sum("x_request_id=req_" in block for block in calls),
          "finish stop", sum('finish_reason="stop"' in block for block in calls),
          "HTTP non-200", sum("Provider HTTP: status=200" not in block for block in calls))
    later = report.split("## diagnose-provider at ")[-1]
    separate_process = [block for block in re.split(r"(?=^## say at )", later, flags=re.M)
                        if block.startswith("## say at ")]
    print("SEPARATE-PROCESS COLD calls", len(separate_process))
    for label in ("service_pipeline_ms", "context_sql_embedding_vector_ms",
                  "moderation_local_safety_ms", "blocking_http_complete_ms", "cli_total_ms"):
        summarize(label, separate_process, patterns[label])
    comparison = later.split("## say at ")[0]
    variants = [block for block in re.split(r"(?=^### Variant )", comparison, flags=re.M)
                if block.startswith("### Variant ")]
    print("CONTROLLED variants", len(variants))
    for name in ("full", "short_same_schema", "full_without_retrieved_memories",
                 "full_reduced_output_schema", "full_low_reasoning"):
        group = [block for block in variants if block.startswith("### Variant " + name + " ")]
        print("VARIANT", name, "n", len(group))
        for label, pattern in (("roundtrip_ms", patterns["blocking_http_complete_ms"]),
                               ("processing_ms", patterns["provider_processing_header_ms"]),
                               ("prompt_tokens", patterns["provider_prompt_tokens"]),
                               ("completion_tokens", patterns["completion_tokens"]),
                               ("reasoning_tokens", patterns["reasoning_tokens"]),
                               ("visible_reply_tokens", r"local reply tokens=(\d+)") ,
                               ("invisible_or_json_tokens", r"remaining completion tokens .*?=(\d+)")):
            summarize("  " + label, group, pattern)
        print("  replies", [metric(block, r"VISIBLE REPLY: `([^`]+)`", str)
                            for block in group])


if __name__ == "__main__":
    main()
