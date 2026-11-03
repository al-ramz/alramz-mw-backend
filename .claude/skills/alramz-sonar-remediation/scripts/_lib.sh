# shellcheck shell=bash
# Shared config + helpers for .claude/skills/alramz-sonar-remediation/scripts/*.sh. Sourced, never executed.
# Every value is overridable from the environment.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# scripts live in .claude/skills/alramz-sonar-remediation/scripts/ -> repo root is four levels up
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
REPORTS_DIR="${REPORTS_DIR:-$REPO_ROOT/.claude/modules/sonar-scans}"
# Defaults target the private SonarCloud project. For a local server:
#   SONAR_HOST_URL=http://localhost:9000 SONAR_ORGANIZATION= SONAR_PROJECT_KEY=alramz-mw-oss
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonarcloud.io}"
SONAR_ORGANIZATION="${SONAR_ORGANIZATION-alramzit}"
SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY:-alramzit_alramz-mw-backend}"
SONAR_PLUGIN="${SONAR_PLUGIN:-org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751}"
SONAR_TOKEN_FILE="${SONAR_TOKEN_FILE:-$HOME/.sonar/alramz-cloud-user-token}"
PY="python3 -I $SCRIPT_DIR/sonar_report.py"

cd "$REPO_ROOT"

# Maven wrapper if present, else system mvn.
if [[ -x "$REPO_ROOT/mvnw" ]]; then MVN="$REPO_ROOT/mvnw"; else MVN="${MVN:-mvn}"; fi

# mvn clean verify with one retry. An IDE compiler (VS Code JDT LS) writing into target/ inside OneDrive
# can race `clean` and fail a module transiently. The first attempt's log is kept as <log>.attempt1.
# Usage: maven_verify <log> [extra mvn args...]; returns the final exit code.
maven_verify() {
  local logf="$1"; shift
  local rc
  set +e
  "$MVN" -B -fae clean verify "$@" > "$logf" 2>&1; rc=$?
  if [[ $rc -ne 0 && "${BUILD_RETRY:-1}" == 1 ]]; then
    log "build failed (exit $rc); retrying once in case it was the IDE/OneDrive race -> $logf"
    mv "$logf" "$logf.attempt1"
    "$MVN" -B -fae clean verify "$@" > "$logf" 2>&1; rc=$?
  fi
  set -e
  return $rc
}

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

# Token: $SONAR_TOKEN, else a chmod-600 file outside the repo. Never stored in the repo.
load_token() {
  if [[ -z "${SONAR_TOKEN:-}" && -f "$SONAR_TOKEN_FILE" ]]; then
    SONAR_TOKEN="$(tr -d '[:space:]' < "$SONAR_TOKEN_FILE")"
  fi
  [[ -n "${SONAR_TOKEN:-}" ]] || die "No token. Export SONAR_TOKEN or write one to $SONAR_TOKEN_FILE (chmod 600)."
  export SONAR_TOKEN SONAR_HOST_URL SONAR_PROJECT_KEY SONAR_ORGANIZATION
}

# Server reachable, token valid, and (SonarCloud) the project exists and is PRIVATE — never upload to a public one.
require_sonar() {
  curl -fsS -m 15 -u "$SONAR_TOKEN:" "$SONAR_HOST_URL/api/authentication/validate" | grep -q '"valid":true' \
    || die "Token rejected or server unreachable at $SONAR_HOST_URL"
  if [[ -n "$SONAR_ORGANIZATION" ]]; then
    curl -fsS -m 15 -u "$SONAR_TOKEN:" \
      "$SONAR_HOST_URL/api/projects/search?organization=$SONAR_ORGANIZATION&projects=$SONAR_PROJECT_KEY" \
      | grep -q '"visibility":"private"' \
      || die "$SONAR_PROJECT_KEY missing or not private in org $SONAR_ORGANIZATION; refusing to upload."
  fi
}

# Build + test (JaCoCo XML for coverage) + analyse + wait for server-side processing, then export.
# $1 = output dir. Build failures don't stop analysis of what compiled (-fae).
scan_and_export() {
  local out="$1"; mkdir -p "$out"
  load_token; require_sonar
  log "mvn clean verify -> $out/build.log"
  local rc=0
  maven_verify "$out/build.log" || rc=$?
  set +e
  log "sonar analysis -> $out/scan.log"
  # Separate run so analysis covers every module that compiled, even if another module's tests failed.
  "$MVN" -B -fae "$SONAR_PLUGIN:sonar" \
    -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" \
    -Dsonar.projectKey="$SONAR_PROJECT_KEY" ${SONAR_ORGANIZATION:+-Dsonar.organization="$SONAR_ORGANIZATION"} \
    ${SONAR_EXTRA_ARGS:-} > "$out/scan.log" 2>&1
  local src=$?
  set -e
  printf 'build=%s\nsonar=%s\n' "$rc" "$src" > "$out/maven-exit-codes.txt"
  local task; task="$(find "$REPO_ROOT/target/sonar" "$REPO_ROOT"/services/*/target/sonar -name report-task.txt 2>/dev/null | head -1 || true)"
  [[ -n "$task" ]] || die "No report-task.txt; analysis did not upload. See $out/scan.log (maven exit $rc)."
  cp "$task" "$out/report-task.txt"
  $PY export --out "$out" --task-file "$out/report-task.txt"
  log "build exit=$rc sonar exit=$src; exported to $out"
}
