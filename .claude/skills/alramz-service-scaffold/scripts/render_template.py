#!/usr/bin/env python3
"""Render one scaffold template: literal __KEY__ -> value substitution, then fail on any leftover placeholder.

Usage: render_template.py <template> <output> KEY=VALUE [KEY=VALUE ...]
  KEY is given without underscores (PORT=8086 replaces __PORT__). VALUE is literal: no escaping needed
  for $ { } | # * / ! (that is why this exists instead of sed).
Longer keys are substituted first, so __SERVICE_NAME__ never clobbers __SERVICE_NAME_UPPER__.
"""
import pathlib
import re
import sys

if len(sys.argv) < 3:
    sys.exit(__doc__)
src, out, pairs = sys.argv[1], sys.argv[2], sys.argv[3:]
values = dict(p.split("=", 1) for p in pairs)
text = pathlib.Path(src).read_text()
for key in sorted(values, key=len, reverse=True):
    text = text.replace(f"__{key}__", values[key])
left = sorted(set(re.findall(r"__[A-Z][A-Z0-9_]*__", text)))
if left:
    sys.exit(f"{src}: unfilled placeholders {left}")
path = pathlib.Path(out)
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(text)
