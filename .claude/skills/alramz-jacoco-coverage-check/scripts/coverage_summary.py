#!/usr/bin/env python3
"""Summarize JaCoCo coverage per service from services/*/target/site/jacoco/jacoco.csv.

usage: coverage_summary.py <out_dir> [--classes]
Copies each jacoco.csv to <out_dir>/<service>-jacoco.csv and writes <out_dir>/summary.md + summary.json.
--classes also lists the classes with the most missed lines (where to add tests next).
"""
import csv, glob, json, os, shutil, sys

GATE = 0.80


def pct(missed, covered):
    total = missed + covered
    return covered / total if total else None


def fmt(r):
    return "n/a" if r is None else f"{r * 100:.1f}%"


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    rows, worst = [], {}
    for path in sorted(glob.glob("services/*/target/site/jacoco/jacoco.csv")):
        svc = path.split("/")[1]
        shutil.copy(path, os.path.join(out, f"{svc}-jacoco.csv"))
        t = dict.fromkeys(["LINE_MISSED", "LINE_COVERED", "BRANCH_MISSED", "BRANCH_COVERED",
                           "INSTRUCTION_MISSED", "INSTRUCTION_COVERED"], 0)
        classes = []
        with open(path) as f:
            for r in csv.DictReader(f):
                for k in t:
                    t[k] += int(r[k])
                classes.append((int(r["LINE_MISSED"]), f'{r["PACKAGE"]}.{r["CLASS"]}',
                                fmt(pct(int(r["LINE_MISSED"]), int(r["LINE_COVERED"])))))
        line = pct(t["LINE_MISSED"], t["LINE_COVERED"])
        rows.append({"service": svc,
                     "line": line,
                     "branch": pct(t["BRANCH_MISSED"], t["BRANCH_COVERED"]),
                     "instruction": pct(t["INSTRUCTION_MISSED"], t["INSTRUCTION_COVERED"]),
                     "lines_covered": t["LINE_COVERED"],
                     "lines_total": t["LINE_MISSED"] + t["LINE_COVERED"],
                     "gate": "PASS" if line is not None and line >= GATE else "FAIL"})
        worst[svc] = sorted(classes, reverse=True)[:15]

    md = ["| Service | Line | Branch | Instruction | Lines covered/total | 80% line gate |",
          "|---|---|---|---|---|---|"]
    md += [f'| {r["service"]} | {fmt(r["line"])} | {fmt(r["branch"])} | {fmt(r["instruction"])} '
           f'| {r["lines_covered"]}/{r["lines_total"]} | {r["gate"]} |' for r in rows]
    if "--classes" in sys.argv:
        for svc, cls in worst.items():
            md += ["", f"### {svc}: most missed lines", "| Missed | Class | Line % |", "|---|---|---|"]
            md += [f"| {m} | {c} | {p} |" for m, c, p in cls if m]
    open(os.path.join(out, "summary.md"), "w").write("\n".join(md) + "\n")
    json.dump(rows, open(os.path.join(out, "summary.json"), "w"), indent=2)
    print("\n".join(md))


if __name__ == "__main__":
    main()
