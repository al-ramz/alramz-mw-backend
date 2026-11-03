#!/usr/bin/env bash
# Consolidate comparison/build/startup results -> .claude/modules/sonar-scans/final/FINAL-REPORT.md
source "$(dirname "$0")/_lib.sh"
$PY final --reports "$REPORTS_DIR"
