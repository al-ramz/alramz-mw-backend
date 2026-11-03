#!/usr/bin/env python3
"""Collect and compare Checkstyle / PMD / SpotBugs results for the alramz-mw-oss reactor. Stdlib only.

  collect --repo <root> --out <dir>    copy services/*/target/{checkstyle-result,pmd,spotbugsXml}.xml into
                                       <dir>/raw/, write <dir>/findings.json + <dir>/summary.md
  compare --before <dir> --after <dir> --out <dir>
                                       write comparison.{md,json}: per tool/module/rule before -> after,
                                       fixed / new findings (matched by tool+file+rule+message, line-insensitive)
"""
import argparse
import collections
import json
import re
import os
import shutil
import sys
import xml.etree.ElementTree as ET

TOOLS = {"checkstyle": "checkstyle-result.xml", "pmd": "pmd.xml", "spotbugs": "spotbugsXml.xml"}
# One shared severity scale so the report can rank across tools.
PMD_SEV = {"1": "HIGH", "2": "HIGH", "3": "MEDIUM", "4": "LOW", "5": "LOW"}
SB_SEV = {"1": "HIGH", "2": "MEDIUM", "3": "LOW"}


def _local(tag):
    return tag.rsplit("}", 1)[-1]


def _rel(path, repo):
    path = os.path.realpath(path)
    repo = os.path.realpath(repo)
    return os.path.relpath(path, repo) if path.startswith(repo) else path


def parse_checkstyle(xml, module, repo):
    for f in ET.parse(xml).getroot():
        if _local(f.tag) != "file":
            continue
        for e in f:
            if _local(e.tag) != "error":
                continue
            yield {"tool": "checkstyle", "module": module, "file": _rel(f.get("name"), repo),
                   "line": int(e.get("line", 0)), "rule": e.get("source", "").rsplit(".", 1)[-1].removesuffix("Check"),
                   "severity": {"error": "HIGH", "warning": "MEDIUM"}.get(e.get("severity"), "LOW"),
                   "message": e.get("message", "")}


def parse_pmd(xml, module, repo):
    for f in ET.parse(xml).getroot():
        if _local(f.tag) != "file":
            continue
        for v in f:
            if _local(v.tag) != "violation":
                continue
            yield {"tool": "pmd", "module": module, "file": _rel(f.get("name"), repo),
                   "line": int(v.get("beginline", 0)), "rule": v.get("rule"), "category": v.get("ruleset"),
                   "severity": PMD_SEV.get(v.get("priority"), "LOW"), "message": (v.text or "").strip()}


def parse_spotbugs(xml, module, repo):
    root = ET.parse(xml).getroot()
    src_dirs = [s.text for s in root.iter("SrcDir") if s.text]
    for b in root.iter("BugInstance"):
        sl = next((s for s in b.findall("SourceLine") if s.get("sourcepath")), None)
        if sl is None:
            sl = next(b.iter("SourceLine"), None)
        file = ""
        if sl is not None and sl.get("sourcepath"):
            cands = [os.path.join(d, sl.get("sourcepath")) for d in src_dirs]
            hit = next((c for c in cands if os.path.exists(c)), None)
            file = _rel(hit, repo) if hit else f"services/{module}/src/main/java/{sl.get('sourcepath')}"
        msg = b.findtext("LongMessage") or b.findtext("ShortMessage") or ""
        yield {"tool": "spotbugs", "module": module, "file": file,
               "line": int(sl.get("start", 0) or 0) if sl is not None else 0, "rule": b.get("type"),
               "category": b.get("category"), "severity": SB_SEV.get(b.get("priority"), "LOW"),
               "rank": int(b.get("rank", 20)), "message": msg.strip()}


PARSERS = {"checkstyle": parse_checkstyle, "pmd": parse_pmd, "spotbugs": parse_spotbugs}


def collect(repo, out):
    raw = os.path.join(out, "raw")
    os.makedirs(raw, exist_ok=True)
    findings, scanned = [], collections.defaultdict(list)
    svc_root = os.path.join(repo, "services")
    for module in sorted(os.listdir(svc_root)):
        tgt = os.path.join(svc_root, module, "target")
        for tool, name in TOOLS.items():
            xml = os.path.join(tgt, name)
            if not os.path.isfile(xml):
                continue
            shutil.copy2(xml, os.path.join(raw, f"{module}.{name}"))
            scanned[tool].append(module)
            findings.extend(PARSERS[tool](xml, module, repo))
    findings.sort(key=lambda f: (f["tool"], f["module"], f["file"], f["line"], f["rule"]))
    with open(os.path.join(out, "findings.json"), "w") as fh:
        json.dump({"scanned": scanned, "findings": findings}, fh, indent=1)
    write_summary(findings, scanned, os.path.join(out, "summary.md"))
    print(f"{len(findings)} findings -> {out}/findings.json")


def _table(rows, head):
    lines = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    lines += ["| " + " | ".join(str(c).replace("|", "/") for c in r) + " |" for r in rows]
    return "\n".join(lines)


def write_summary(findings, scanned, path):
    by_tm = collections.Counter((f["tool"], f["module"]) for f in findings)
    by_rule = collections.Counter((f["tool"], f["rule"], f["severity"]) for f in findings)
    by_sev = collections.Counter((f["tool"], f["severity"]) for f in findings)
    out = ["# Static analysis summary", "",
           "Scanned modules: " + "; ".join(f"{t}: {', '.join(m)}" for t, m in scanned.items()), "",
           "## By tool and severity", "",
           _table([(t, s, n) for (t, s), n in sorted(by_sev.items())], ["Tool", "Severity", "Count"]), "",
           "## By tool and module", "",
           _table([(t, m, n) for (t, m), n in sorted(by_tm.items())], ["Tool", "Module", "Count"]), "",
           "## By rule (all)", "",
           _table([(t, r, s, n) for (t, r, s), n in by_rule.most_common()], ["Tool", "Rule", "Severity", "Count"]), ""]
    with open(path, "w") as fh:
        fh.write("\n".join(out))


def _key(f):
    # numbers masked: metric rules (GodClass WMC=.., coupling 21 -> 22) restate values when code changes
    return (f["tool"], f["module"], f["file"], f["rule"], re.sub(r"\d+(\.\d+)?", "#", f["message"]))


def compare(before_dir, after_dir, out):
    os.makedirs(out, exist_ok=True)
    b = json.load(open(os.path.join(before_dir, "findings.json")))["findings"]
    a = json.load(open(os.path.join(after_dir, "findings.json")))["findings"]
    cb, ca = collections.Counter(map(_key, b)), collections.Counter(map(_key, a))
    fixed, new = cb - ca, ca - cb
    by_key_b = {_key(f): f for f in b}
    by_key_a = {_key(f): f for f in a}

    def roll(fs, keyf):
        return collections.Counter(keyf(f) for f in fs)

    tool_mod = sorted(set(roll(b, lambda f: (f["tool"], f["module"]))) | set(roll(a, lambda f: (f["tool"], f["module"]))))
    tmb, tma = roll(b, lambda f: (f["tool"], f["module"])), roll(a, lambda f: (f["tool"], f["module"]))
    rb, ra = roll(b, lambda f: (f["tool"], f["rule"])), roll(a, lambda f: (f["tool"], f["rule"]))
    sb, sa = roll(b, lambda f: (f["tool"], f["severity"])), roll(a, lambda f: (f["tool"], f["severity"]))
    tb, ta = roll(b, lambda f: f["tool"]), roll(a, lambda f: f["tool"])

    res = {"totals": {t: {"before": tb[t], "after": ta[t]} for t in sorted(set(tb) | set(ta))},
           "fixed": [dict(by_key_b[k], count=n) for k, n in fixed.items()],
           "new": [dict(by_key_a[k], count=n) for k, n in new.items()]}
    with open(os.path.join(out, "comparison.json"), "w") as fh:
        json.dump(res, fh, indent=1)

    def delta(x, y):
        return f"{y - x:+d}"

    md = ["# Before / after comparison", "",
          "## Totals", "",
          _table([(t, tb[t], ta[t], delta(tb[t], ta[t])) for t in sorted(set(tb) | set(ta))],
                 ["Tool", "Before", "After", "Delta"]), "",
          "## By tool and severity", "",
          _table([(t, s, sb[(t, s)], sa[(t, s)], delta(sb[(t, s)], sa[(t, s)]))
                  for t, s in sorted(set(sb) | set(sa))], ["Tool", "Severity", "Before", "After", "Delta"]), "",
          "## By tool and module", "",
          _table([(t, m, tmb[(t, m)], tma[(t, m)], delta(tmb[(t, m)], tma[(t, m)])) for t, m in tool_mod],
                 ["Tool", "Module", "Before", "After", "Delta"]), "",
          "## By rule (changed rules only)", "",
          _table(sorted([(t, r, rb[(t, r)], ra[(t, r)], delta(rb[(t, r)], ra[(t, r)]))
                         for t, r in set(rb) | set(ra) if rb[(t, r)] != ra[(t, r)]], key=lambda x: int(x[4])),
                 ["Tool", "Rule", "Before", "After", "Delta"]), "",
          f"## New findings introduced ({sum(new.values())})", "",
          _table([(by_key_a[k]["tool"], by_key_a[k]["rule"], f'{by_key_a[k]["file"]}:{by_key_a[k]["line"]}', n)
                  for k, n in sorted(new.items())], ["Tool", "Rule", "Where", "Count"]), ""]
    with open(os.path.join(out, "comparison.md"), "w") as fh:
        fh.write("\n".join(md))
    print(f"fixed={sum(fixed.values())} new={sum(new.values())} -> {out}/comparison.md")


def main():
    p = argparse.ArgumentParser()
    sp = p.add_subparsers(dest="cmd", required=True)
    c = sp.add_parser("collect")
    c.add_argument("--repo", required=True)
    c.add_argument("--out", required=True)
    k = sp.add_parser("compare")
    k.add_argument("--before", required=True)
    k.add_argument("--after", required=True)
    k.add_argument("--out", required=True)
    a = p.parse_args()
    if a.cmd == "collect":
        collect(a.repo, a.out)
    else:
        compare(a.before, a.after, a.out)


if __name__ == "__main__":
    sys.exit(main())
