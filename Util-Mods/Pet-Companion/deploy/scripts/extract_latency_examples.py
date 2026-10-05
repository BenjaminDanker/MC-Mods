#!/usr/bin/env python3
"""Render exact, synthetic Dev Player provider examples from a trace."""
import re
import sys
from pathlib import Path


def fenced(block, heading):
    match = re.search(r"### " + re.escape(heading)
                      + r"\s+```text\n(.*?)\n```", block, re.S)
    if not match:
        raise ValueError("Missing " + heading)
    return match.group(1)


def main():
    raw = Path(sys.argv[1]).read_text(encoding="utf-8")
    compare = raw.split("## diagnose-provider at ", 1)[1].split("## say at ", 1)[0]
    blocks = [block for block in re.split(r"(?=^### Variant )", compare, flags=re.M)
              if block.startswith("### Variant ")]
    names = ("full", "short_same_schema", "full_without_retrieved_memories",
             "full_reduced_output_schema", "full_low_reasoning")
    out = ["# Literal isolated provider requests and complete responses\n",
           "These are the first repetition of each diagnostic variant. All pet context is "
           "synthetic. The request bodies contain no API key; full response content is "
           "shown. Each prompt is sent as one system message.\n"]
    for name in names:
        block = next(block for block in blocks
                     if block.startswith("### Variant " + name + " repetition=1 "))
        out += ["\n## " + name + "\n",
                "\n**Exact system message**\n\n```text\n"
                + fenced(block, "Exact diagnostic prompt") + "\n```\n",
                "\n**Exact JSON Schema**\n\n```json\n"
                + fenced(block, "Exact diagnostic schema") + "\n```\n",
                "\n**Complete structured response**\n\n```json\n"
                + fenced(block, "Complete structured response") + "\n```\n"]
        for prefix in ("Provider request:", "Provider HTTP:", "Provider result:",
                       "VISIBLE REPLY:"):
            line = next(line for line in block.splitlines() if line.startswith(prefix))
            out.append("\n" + line + "\n")
    Path(sys.argv[2]).write_text("".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
