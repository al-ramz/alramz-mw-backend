#!/usr/bin/env bash
# Diff .claude/modules/sonar-scans/before vs .claude/modules/sonar-scans/after -> .claude/modules/sonar-scans/comparison/ (fixed, pending, new, changed issues + metrics).
source "$(dirname "$0")/_lib.sh"
B="${1:-$REPORTS_DIR/before}"; A="${2:-$REPORTS_DIR/after}"
[[ -f "$B/issues.json" && -f "$A/issues.json" ]] || die "Need $B/issues.json and $A/issues.json"
$PY compare --before "$B" --after "$A" --out "$REPORTS_DIR/comparison"
log "Comparison: $REPORTS_DIR/comparison/comparison.md"
