#!/usr/bin/env bash
# Fresh analysis after remediation -> .claude/modules/sonar-scans/after/. A previous after/ is kept as after-<timestamp>/.
source "$(dirname "$0")/_lib.sh"
OUT="$REPORTS_DIR/after"
[[ -d "$OUT" ]] && mv "$OUT" "$REPORTS_DIR/after-$(date +%Y%m%d-%H%M%S)"
scan_and_export "$OUT"
log "After: $OUT/summary.md"
