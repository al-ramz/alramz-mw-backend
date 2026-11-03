#!/usr/bin/env python3
"""JSON/report helper for .claude/skills/alramz-sonar-remediation/scripts/*.sh. Stdlib only.

  export        --out DIR --task-file report-task.txt   wait for CE task, dump metrics/gate/issues/hotspots
  compare       --before DIR --after DIR --out DIR      fixed / pending / new / changed issues + metric deltas
  build-summary --log FILE --out DIR                    per-module surefire/failsafe totals + jar presence
  final         --reports DIR                           consolidated .claude/modules/sonar-scans/final/FINAL-REPORT.md
"""
import argparse, base64, glob, json, os, re, sys, time, urllib.parse, urllib.request
import xml.etree.ElementTree as ET

HOST = os.environ.get("SONAR_HOST_URL", "http://localhost:9000").rstrip("/")
PROJECT = os.environ.get("SONAR_PROJECT_KEY", "alramzit_alramz-mw-backend")
ORG = {"organization": os.environ["SONAR_ORGANIZATION"]} if os.environ.get("SONAR_ORGANIZATION") else {}
RATING = {"1.0": "A", "2.0": "B", "3.0": "C", "4.0": "D", "5.0": "E"}
# Legacy (Standard Experience) and MQR keys; whichever exist on this server get fetched.
METRICS = [
    "alert_status", "bugs", "vulnerabilities", "code_smells", "security_hotspots",
    "security_hotspots_reviewed", "reliability_rating", "security_rating", "sqale_rating",
    "security_review_rating", "sqale_index", "coverage", "line_coverage", "duplicated_lines_density",
    "ncloc", "violations", "blocker_violations", "critical_violations", "major_violations",
    "minor_violations", "info_violations", "new_violations",
    "software_quality_reliability_rating", "software_quality_security_rating",
    "software_quality_maintainability_rating", "software_quality_reliability_issues",
    "software_quality_security_issues", "software_quality_maintainability_issues",
    "software_quality_blocker_issues", "software_quality_high_issues",
]
RATING_METRICS = {m for m in METRICS if m.endswith("_rating")}


def api(path, **params):
    url = f"{HOST}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url)
    tok = os.environ.get("SONAR_TOKEN")
    if tok:
        req.add_header("Authorization", "Basic " + base64.b64encode(f"{tok}:".encode()).decode())
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def paged(path, key, **params):
    out, p = [], 1
    while True:
        d = api(path, p=p, ps=500, **params)
        out += d.get(key, [])
        total = d.get("paging", {}).get("total", d.get("total", 0))
        if p * 500 >= min(total, 10000):
            if total > 10000:
                print(f"WARN: {path} has {total} results; API caps at 10000", file=sys.stderr)
            return out
        p += 1


def fmt(metric, value):
    if value is None:
        return "n/a"
    return RATING.get(value, value) if metric in RATING_METRICS else value


def wjson(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, sort_keys=True)


def rjson(path, default=None):
    try:
        with open(path) as f:
            return json.load(f)
    except FileNotFoundError:
        return default


# ---------------------------------------------------------------- export
def cmd_export(a):
    task = dict(l.strip().split("=", 1) for l in open(a.task_file) if "=" in l)
    ce = task["ceTaskId"]
    for _ in range(360):  # up to 30 min
        st = api("/api/ce/task", id=ce)["task"]["status"]
        if st in ("SUCCESS", "FAILED", "CANCELED"):
            break
        time.sleep(5)
    if st != "SUCCESS":
        sys.exit(f"Compute Engine task {ce} ended {st}")
    known = {m["key"] for m in paged("/api/metrics/search", "metrics")}
    keys = [m for m in METRICS if m in known]
    comp = api("/api/measures/component", component=PROJECT, metricKeys=",".join(keys))["component"]
    measures = {}
    for m in comp.get("measures", []):
        measures[m["metric"]] = m.get("value", (m.get("period") or {}).get("value"))
    gate = api("/api/qualitygates/project_status", projectKey=PROJECT)["projectStatus"]
    issues = paged("/api/issues/search", "issues", componentKeys=PROJECT, resolved="false", **ORG,
                   additionalFields="_all")
    hotspots = paged("/api/hotspots/search", "hotspots", projectKey=PROJECT)
    analysis = {"ceTaskId": ce, "dashboardUrl": task.get("dashboardUrl"),
                "capturedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")}
    wjson(f"{a.out}/analysis.json", analysis)
    wjson(f"{a.out}/measures.json", measures)
    wjson(f"{a.out}/quality-gate.json", gate)
    wjson(f"{a.out}/issues.json", issues)
    wjson(f"{a.out}/hotspots.json", hotspots)
    write_summary(a.out, measures, gate, issues, hotspots, analysis)


def count(items, f):
    c = {}
    for i in items:
        k = f(i)
        c[k] = c.get(k, 0) + 1
    return dict(sorted(c.items(), key=lambda kv: -kv[1]))


def impact_sev(i):
    return ",".join(sorted({x["severity"] for x in i.get("impacts", [])})) or "-"


def write_summary(out, measures, gate, issues, hotspots, analysis):
    L = [f"# SonarQube snapshot — {PROJECT}", "", f"Captured: {analysis['capturedAt']}  ",
         f"Quality gate: **{gate.get('status')}**", "", "| Metric | Value |", "|---|---|"]
    L += [f"| {k} | {fmt(k, v)} |" for k, v in sorted(measures.items())]
    L += ["", "## Quality gate conditions", "", "| Metric | Status | Actual | Threshold |", "|---|---|---|---|"]
    L += [f"| {c['metricKey']} | {c['status']} | {c.get('actualValue','')} | {c.get('comparator','')} {c.get('errorThreshold','')} |"
          for c in gate.get("conditions", [])]
    L += ["", f"## Open issues: {len(issues)}", "", "By type: " + json.dumps(count(issues, lambda i: i.get("type"))),
          "", "By legacy severity: " + json.dumps(count(issues, lambda i: i.get("severity"))),
          "", "By impact severity: " + json.dumps(count(issues, impact_sev)),
          "", "### By rule", "", "| Rule | Count |", "|---|---|"]
    L += [f"| {r} | {n} |" for r, n in count(issues, lambda i: i["rule"]).items()]
    L += ["", "### By file", "", "| File | Count |", "|---|---|"]
    L += [f"| {f.split(':',1)[-1]} | {n} |" for f, n in count(issues, lambda i: i["component"]).items()]
    L += ["", f"## Security hotspots: {len(hotspots)}", "",
          "By status: " + json.dumps(count(hotspots, lambda h: h.get("status"))), "",
          "| Rule | File:Line | Probability | Status |", "|---|---|---|---|"]
    L += [f"| {h.get('ruleKey')} | {h['component'].split(':',1)[-1]}:{h.get('line','')} | {h.get('vulnerabilityProbability')} | {h.get('status')} |"
          for h in hotspots]
    open(f"{out}/summary.md", "w").write("\n".join(L) + "\n")


# ---------------------------------------------------------------- compare
def short(i):
    return {"key": i["key"], "rule": i["rule"], "severity": i.get("severity"), "impacts": impact_sev(i),
            "type": i.get("type"), "file": i["component"].split(":", 1)[-1], "line": i.get("line"),
            "message": i.get("message")}


def cmd_compare(a):
    b_iss = {i["key"]: i for i in rjson(f"{a.before}/issues.json", [])}
    a_iss = {i["key"]: i for i in rjson(f"{a.after}/issues.json", [])}
    fixed = [short(b_iss[k]) for k in b_iss if k not in a_iss]
    new = [short(a_iss[k]) for k in a_iss if k not in b_iss]
    pending, changed = [], []
    for k in b_iss.keys() & a_iss.keys():
        o, n = short(b_iss[k]), short(a_iss[k])
        pending.append(n)
        diff = {f: [o[f], n[f]] for f in ("severity", "impacts", "file", "line", "message") if o[f] != n[f]}
        if diff:
            changed.append({**n, "changes": diff})
    bm, am = rjson(f"{a.before}/measures.json", {}), rjson(f"{a.after}/measures.json", {})
    bg, ag = rjson(f"{a.before}/quality-gate.json", {}), rjson(f"{a.after}/quality-gate.json", {})
    bh, ah = rjson(f"{a.before}/hotspots.json", []), rjson(f"{a.after}/hotspots.json", [])
    res = {"counts": {"before": len(b_iss), "after": len(a_iss), "fixed": len(fixed), "pending": len(pending),
                      "new": len(new), "changed": len(changed),
                      "hotspots_before": len(bh), "hotspots_after": len(ah),
                      "hotspots_to_review_after": sum(h.get("status") == "TO_REVIEW" for h in ah)},
           "gate": {"before": bg.get("status"), "after": ag.get("status")},
           "measures": {k: {"before": bm.get(k), "after": am.get(k)} for k in sorted(set(bm) | set(am))},
           "fixed": fixed, "pending": pending, "new": new, "changed": changed}
    os.makedirs(a.out, exist_ok=True)
    wjson(f"{a.out}/comparison.json", res)
    c = res["counts"]
    L = ["# SonarQube before/after comparison", "",
         f"Quality gate: {res['gate']['before']} → **{res['gate']['after']}**", "",
         "| Issues | Count |", "|---|---|"] + [f"| {k} | {v} |" for k, v in c.items()]
    L += ["", "| Metric | Before | After |", "|---|---|---|"]
    L += [f"| {k} | {fmt(k, v['before'])} | {fmt(k, v['after'])} |" for k, v in res["measures"].items()]
    for name in ("new", "pending", "changed", "fixed"):
        L += ["", f"## {name.capitalize()} ({len(res[name])})", "", "| Rule | Sev | File:Line | Message |", "|---|---|---|---|"]
        L += [f"| {i['rule']} | {i['severity']}/{i['impacts']} | {i['file']}:{i['line']} | {(i['message'] or '').replace('|','/')} |"
              for i in sorted(res[name], key=lambda i: (i["file"], i["line"] or 0))]
    open(f"{a.out}/comparison.md", "w").write("\n".join(L) + "\n")
    print(json.dumps({"gate": res["gate"], **c}))


# ---------------------------------------------------------------- build summary
def cmd_build_summary(a):
    log = open(a.log, errors="replace").read()
    reactor = re.findall(r"\[INFO\] (\S.*?) \.+ (SUCCESS|FAILURE|SKIPPED)", log)
    rows = []
    for pom in sorted(glob.glob("services/*/pom.xml")):
        mod = os.path.dirname(pom)
        t = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        for x in glob.glob(f"{mod}/target/surefire-reports/TEST-*.xml") + glob.glob(f"{mod}/target/failsafe-reports/TEST-*.xml"):
            r = ET.parse(x).getroot()
            for k in t:
                t[k] += int(r.get(k, 0))
        jars = [os.path.basename(j) for j in glob.glob(f"{mod}/target/*.jar") if not j.endswith(("-plain.jar", "-sources.jar"))]
        boot = any(_is_boot_jar(j) for j in glob.glob(f"{mod}/target/*.jar"))
        rows.append({"module": os.path.basename(mod), **t, "jars": jars, "bootJar": boot})
    res = {"reactor": [{"name": n, "result": r} for n, r in reactor], "modules": rows,
           "buildSuccess": "BUILD SUCCESS" in log}
    os.makedirs(a.out, exist_ok=True)
    wjson(f"{a.out}/build-summary.json", res)
    L = ["# Build summary", "", f"Overall: **{'BUILD SUCCESS' if res['buildSuccess'] else 'BUILD FAILURE'}**", "",
         "## Reactor", "", "| Module | Result |", "|---|---|"] + [f"| {r['name']} | {r['result']} |" for r in res["reactor"]]
    L += ["", "## Tests & packaging", "", "| Module | Tests | Failures | Errors | Skipped | Jar | Boot jar |", "|---|---|---|---|---|---|---|"]
    L += [f"| {r['module']} | {r['tests']} | {r['failures']} | {r['errors']} | {r['skipped']} | {', '.join(r['jars']) or '-'} | {'yes' if r['bootJar'] else 'no'} |" for r in rows]
    open(f"{a.out}/build-summary.md", "w").write("\n".join(L) + "\n")
    print(open(f"{a.out}/build-summary.md").read())


def _is_boot_jar(path):
    import zipfile
    try:
        with zipfile.ZipFile(path) as z:
            return "BOOT-INF/" in {n[:9] for n in z.namelist()}
    except Exception:
        return False


# ---------------------------------------------------------------- final
def cmd_final(a):
    R = a.reports
    cmp_ = rjson(f"{R}/comparison/comparison.json")
    build = rjson(f"{R}/build/build-summary.json")
    start = rjson(f"{R}/startup/startup-summary.json")
    L = [f"# SonarQube remediation — final report ({PROJECT})", "",
         f"Generated {time.strftime('%Y-%m-%d %H:%M')}. Every number below is read from files under `.claude/modules/sonar-scans/`; nothing is typed in by hand.", ""]
    L += ["## A. SonarQube comparison", ""]
    if cmp_:
        m = cmp_["measures"]
        pick = lambda *ks: next((k for k in ks if k in m), ks[0])
        rows = [("Reliability", pick("reliability_rating", "software_quality_reliability_rating")),
                ("Security", pick("security_rating", "software_quality_security_rating")),
                ("Maintainability", pick("sqale_rating", "software_quality_maintainability_rating")),
                ("Hotspots Review", "security_review_rating"), ("Bugs", "bugs"),
                ("Vulnerabilities", "vulnerabilities"), ("Code Smells", "code_smells"),
                ("Security Hotspots", "security_hotspots"), ("Blocker issues", "blocker_violations"),
                ("Critical issues", "critical_violations"), ("Open issues (total)", "violations"),
                ("Technical debt (min)", "sqale_index"), ("Coverage %", "coverage"),
                ("Duplications %", "duplicated_lines_density")]
        L += ["| Metric | Before | After | Status |", "|---|---|---|---|"]
        for label, k in rows:
            v = m.get(k, {})
            b, af = fmt(k, v.get("before")), fmt(k, v.get("after"))
            status = ("A ✅" if af == "A" else f"{af} ❌") if k in RATING_METRICS else _delta(v.get("before"), v.get("after"), k)
            L.append(f"| {label} | {b} | {af} | {status} |")
        g = cmp_["gate"]
        L.append(f"| Quality Gate | {g['before']} | {g['after']} | {'✅' if g['after'] == 'OK' else '❌'} |")
        c = cmp_["counts"]
        L += ["", "## B. Remediation summary", "",
              f"- Issues identified (baseline): **{c['before']}**",
              f"- Fixed (closed by SonarQube on re-analysis): **{c['fixed']}**",
              f"- Pending (still open): **{c['pending']}**",
              f"- Changed (open, but moved or re-rated): **{c['changed']}**",
              f"- New issues introduced: **{c['new']}**",
              f"- Hotspots: {c['hotspots_before']} → {c['hotspots_after']} ({c['hotspots_to_review_after']} still TO_REVIEW)",
              "", "### Pending issues by rule", "", "| Rule | Count | Files |", "|---|---|---|"]
        by_rule = {}
        for i in cmp_["pending"]:
            by_rule.setdefault(i["rule"], []).append(i["file"].split("/src/")[-1].split("/")[-1])
        L += [f"| {r} | {len(f)} | {', '.join(sorted(set(f)))} |" for r, f in sorted(by_rule.items(), key=lambda kv: -len(kv[1]))]
        reasons = f"{R}/remediation/pending-reasons.md"
        if os.path.exists(reasons):
            L += ["", open(reasons).read().strip()]
        L += ["", "Full lists: `.claude/modules/sonar-scans/comparison/comparison.md` (fixed, pending, new, changed) and `.claude/modules/sonar-scans/remediation/`."]
    else:
        L += ["_No comparison yet: run sonar-compare.sh._"]
    L += ["", "## C. Change log", "", "See `CHANGELOG-SONAR-REMEDIATION.md` (one row per modified file: problem, fix, rule, tests)."]
    L += ["", "## D. Build report", ""]
    if build:
        mods = build["modules"]
        L += [f"- Overall: **{'SUCCESS' if build['buildSuccess'] else 'FAILURE'}**",
              f"- Reactor modules: {len(build['reactor'])}; succeeded: {sum(r['result']=='SUCCESS' for r in build['reactor'])}; "
              f"failed: {sum(r['result']=='FAILURE' for r in build['reactor'])}; skipped: {sum(r['result']=='SKIPPED' for r in build['reactor'])}",
              f"- Tests: {sum(r['tests'] for r in mods)} run, {sum(r['failures'] for r in mods)} failures, "
              f"{sum(r['errors'] for r in mods)} errors, {sum(r['skipped'] for r in mods)} skipped",
              f"- Spring Boot executable jars: {sum(r['bootJar'] for r in mods)}", "",
              "Per module: `.claude/modules/sonar-scans/build/build-summary.md`."]
    else:
        L += ["_No build summary: run build-all.sh._"]
    L += ["", "## E. Startup report", ""]
    if start:
        L += ["| Service | Port | Result | Health | Duration (s) | Detail |", "|---|---|---|---|---|---|"]
        L += [f"| {s['service']} | {s['port']} | {s['result']} | {s['health']} | {s['seconds']} | {s['detail'].replace('|','/')} |" for s in start]
    else:
        L += ["_No startup results: run startup-check.sh._"]
    os.makedirs(f"{R}/final", exist_ok=True)
    open(f"{R}/final/FINAL-REPORT.md", "w").write("\n".join(L) + "\n")
    print(f"{R}/final/FINAL-REPORT.md")


def _delta(b, a, k):
    try:
        b, a = float(b), float(a)
    except (TypeError, ValueError):
        return "-"
    if a == b:
        return "="
    better = a > b if k == "coverage" else a < b
    return ("▼ " if a < b else "▲ ") + ("better" if better else "worse")


def main():
    p = argparse.ArgumentParser()
    s = p.add_subparsers(dest="cmd", required=True)
    e = s.add_parser("export"); e.add_argument("--out", required=True); e.add_argument("--task-file", required=True)
    c = s.add_parser("compare"); c.add_argument("--before", required=True); c.add_argument("--after", required=True); c.add_argument("--out", required=True)
    b = s.add_parser("build-summary"); b.add_argument("--log", required=True); b.add_argument("--out", required=True)
    f = s.add_parser("final"); f.add_argument("--reports", required=True)
    a = p.parse_args()
    {"export": cmd_export, "compare": cmd_compare, "build-summary": cmd_build_summary, "final": cmd_final}[a.cmd](a)


if __name__ == "__main__":
    main()
