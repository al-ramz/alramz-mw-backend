#!/usr/bin/env bash
# Baseline: build, analyse, export to .claude/modules/sonar-scans/before/. Immutable: refuses to overwrite an existing baseline.
source "$(dirname "$0")/_lib.sh"
OUT="$REPORTS_DIR/before"
if [[ -f "$OUT/issues.json" && "${FORCE:-0}" != 1 ]]; then
  die "Baseline already exists at $OUT (immutable). Move it aside or set FORCE=1."
fi
[[ -d "$OUT" ]] && chmod -R u+w "$OUT"
scan_and_export "$OUT"
chmod -R a-w "$OUT"   # immutable baseline
log "Baseline: $OUT/summary.md"
