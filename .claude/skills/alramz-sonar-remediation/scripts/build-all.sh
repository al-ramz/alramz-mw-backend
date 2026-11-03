#!/usr/bin/env bash
# Build every reactor module, run unit + integration tests, package -> .claude/modules/sonar-scans/build/.
# MAVEN_ARGS: extra args (e.g. "-o" for offline). Exit code = Maven's.
source "$(dirname "$0")/_lib.sh"
OUT="$REPORTS_DIR/build"; mkdir -p "$OUT"
log "$MVN -B -fae clean verify ${MAVEN_ARGS:-} -> $OUT/build.log"
rc=0
# shellcheck disable=SC2086
maven_verify "$OUT/build.log" ${MAVEN_ARGS:-} || rc=$?
echo "$rc" > "$OUT/maven-exit-code.txt"
$PY build-summary --log "$OUT/build.log" --out "$OUT"
exit "$rc"
