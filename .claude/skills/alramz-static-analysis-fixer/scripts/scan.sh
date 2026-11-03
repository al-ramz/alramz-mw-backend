#!/usr/bin/env bash
# Run Checkstyle + PMD + SpotBugs over the whole reactor on an isolated snapshot, then collect results.
# Usage: scan.sh <out-dir>          e.g. scan.sh .claude/modules/static-analysis-scans/<run>/before
# Env:   SNAP   snapshot dir (default: $TMPDIR/alramz-lint-snapshot)  MAVEN_ARGS  extra mvn args
#        RUN_TESTS=1  also run unit tests (default: analysis only, -DskipTests)
# Exit code = Maven's. The snapshot avoids the VS Code JDT LS / OneDrive race on target/ (see SKILL.md).
set -euo pipefail
OUT="${1:?usage: scan.sh <out-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
SNAP="${SNAP:-${TMPDIR:-/tmp}/alramz-lint-snapshot}"
mkdir -p "$OUT"; OUT="$(cd "$OUT" && pwd)"

rsync -a --delete --exclude target/ --exclude /logs/ --exclude /.claude/modules/ --exclude .git/ "$REPO/" "$SNAP/"
cd "$SNAP"
TESTS=(-DskipTests -Djacoco.skip=true); [[ "${RUN_TESTS:-0}" == 1 ]] && TESTS=()
# BOM first: services resolve the parent from ~/.m2 (<relativePath/>), so an edited BOM must be installed.
mvn -B -q -pl services/alramz-common-bom install > "$OUT/bom-install.log" 2>&1
rc=0
# shellcheck disable=SC2086
mvn -B -fae clean verify "${TESTS[@]}" -Dpmd.skip=false -Dspotbugs.skip=false ${MAVEN_ARGS:-} > "$OUT/build.log" 2>&1 || rc=$?
echo "$rc" > "$OUT/maven-exit-code.txt"
grep -E '^\[INFO\] .* (SUCCESS|FAILURE|SKIPPED)' "$OUT/build.log" | sed 's/^\[INFO\] //' > "$OUT/reactor-summary.txt" || true
python3 -I "$HERE/lint_report.py" collect --repo "$SNAP" --out "$OUT"
echo "maven exit=$rc; reactor: $OUT/reactor-summary.txt"
exit "$rc"
