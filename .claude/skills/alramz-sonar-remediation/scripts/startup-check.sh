#!/usr/bin/env bash
# Start each Spring Boot service jar (one at a time) with a local-only profile, health-check it, stop it.
# Requires build-all.sh first. Results -> .claude/modules/sonar-scans/startup/.
#
# Config (env):
#   STARTUP_PROFILE   (dev)   Spring profile to run
#   STARTUP_TIMEOUT   (150)   seconds to wait for "Started ..." or exit
#   SERVICES          (all)   space-separated subset, e.g. "utility-service onboarding-service"
#   STARTUP_FREE_PORT (0)     1 = always run on a free port >= 18080 instead of the configured one. Use when an
#                             IDE/dev instance of the same service may be running: it shares target/, so a
#                             rebuild makes spring-boot-devtools restart it, freeing and re-taking the port
#                             mid-check (the "in use" test then races).
#   STARTUP_SMOKE     (0)     1 = after "Started", also GET /api/v1/info (expect 200) and POST /api/auth/login
#                             with a non-existent consumer (expect 4xx). Read-only (writes no user); proves the
#                             JWT filter chain, the starter's JPA user lookup and the live DB/jwt_* tables. It does
#                             NOT exercise a transactional write (derived finders skip the `transactionManager`
#                             lookup that `save` needs) - cover bean wiring like that in the service's tests.
#                             Any other outcome -> STARTED_BUT_SMOKE_FAILED.
#
# Safety: only PIDs started here are ever killed. Every outbound endpoint the dev profile points at an
# internal/QA host (Oracle BROK, OTLP collector, eTrade QA, DFM test) is overridden to localhost below.
source "$(dirname "$0")/_lib.sh"
OUT="$REPORTS_DIR/startup"; mkdir -p "$OUT"
PROFILE="${STARTUP_PROFILE:-dev}"; TIMEOUT="${STARTUP_TIMEOUT:-150}"

export SPRING_PROFILES_ACTIVE="$PROFILE"
export JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"   # throwaway, >=256-bit; never persisted
export TRACING_OTLP_ENDPOINT="http://localhost:4318/v1/traces"
export LOGS_OTLP_ENDPOINT="http://localhost:3100/otlp/v1/logs"
export COMPANY_DATASOURCE_BROK_URL="jdbc:oracle:thin:@//localhost:1/LOCAL_ONLY"
export ETRADE_BASE_URL="http://localhost:1"
export DFM_MEMBER_API_BASE_URL="http://localhost:1"

DEP_RX='Connection refused|UnknownHostException|Communications link failure|Unable to obtain|Network Adapter could not|RedisConnectionFailure|Unable to connect to Redis|PSQLException|password authentication failed|Failed to obtain JDBC Connection'

PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do [[ -n "$p" ]] && kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

# lsof alone has missed a live listener here; also try connecting on IPv4 and IPv6 (a process on *:PORT IPv6
# does not stop a 0.0.0.0:PORT IPv4 bind on macOS, so both stacks must be checked).
port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1 || nc -z -G 1 127.0.0.1 "$1" >/dev/null 2>&1 || nc -z -G 1 ::1 "$1" >/dev/null 2>&1; }
free_port() { local p=18080; while port_busy "$p"; do p=$((p+1)); done; echo "$p"; }

boot_jar() {  # the repackaged executable jar, not -plain/-sources
  for j in "$1"/target/*.jar; do
    [[ "$j" == *-plain.jar || "$j" == *-sources.jar ]] && continue
    # no pipe: `unzip | grep -q` dies of SIGPIPE under pipefail and set -e silently ends the script
    if unzip -l "$j" 'BOOT-INF/*' >/dev/null 2>&1; then echo "$j"; return 0; fi
  done
  return 0
}

configured_port() {
  local y="$1/src/main/resources"
  for f in "$y/application-$PROFILE.yml" "$y/application.yml"; do
    [[ -f "$f" ]] || continue
    local p; p="$(awk '/^server:/{s=1;next} /^[^ #]/{s=0} s&&/^  port:/{gsub(/[^0-9]/,"",$2);print $2;exit}' "$f")"
    [[ -n "$p" ]] && { echo "$p"; return; }
  done
  echo 8080
}

: > "$OUT/startup-results.jsonl"
for dir in services/*/; do
  svc="$(basename "$dir")"
  [[ -n "${SERVICES:-}" && " $SERVICES " != *" $svc "* ]] && continue
  grep -q 'spring-boot-maven-plugin' "$dir/pom.xml" || continue      # libraries / BOM: not runnable
  jar="$(boot_jar "$dir")"; log_f="$OUT/$svc.log"
  port="$(configured_port "$dir")"; note=""
  if [[ "${STARTUP_FREE_PORT:-0}" == 1 ]]; then port="$(free_port)"; note="STARTUP_FREE_PORT: ran on $port"
  elif port_busy "$port"; then note="configured port $port in use by another process; ran on "; port="$(free_port)"; note+="$port"; fi
  result=NOT_RUN; health=n/a; secs=0; detail="$note"
  if [[ -z "$jar" ]]; then
    detail="no executable jar in $dir/target (run build-all.sh)"
  else
    log "$svc: java -jar $(basename "$jar") on :$port ($PROFILE)"
    start=$(date +%s)
    java -jar "$jar" --server.port="$port" > "$log_f" 2>&1 &
    pid=$!; PIDS+=("$pid")
    while :; do
      secs=$(( $(date +%s) - start ))
      if ! kill -0 "$pid" 2>/dev/null; then
        if grep -Eq "$DEP_RX" "$log_f"; then result=BLOCKED_BY_DEPENDENCY; else result=FAILED_TO_START; fi
        # deepest root cause: last "Caused by:" line, else the Spring failure description
        cause="$(grep -E '^Caused by:' "$log_f" | grep -v 'ConnectException: Connection refused' | tail -1 | cut -c1-200 || true)"
        # Spring's failure report: first non-blank line after "Description:" (the line right after the banner is blank)
        [[ -z "$cause" ]] && cause="$(grep -A4 '^Description:' "$log_f" | grep -v -e '^Description:' -e '^[[:space:]]*$' | head -1 | cut -c1-200 || true)"
        detail="${detail:+$detail; }${cause}"
        break
      fi
      if grep -Eq 'Started [A-Za-z0-9]+ in [0-9.]+ seconds' "$log_f"; then
        code="$(curl -s -m 10 -o "$OUT/$svc-health.json" -w '%{http_code}' "http://127.0.0.1:$port/actuator/health" || true)"
        health="HTTP $code $(grep -o '"status":"[A-Z_]*"' "$OUT/$svc-health.json" 2>/dev/null | head -1 || true)"
        if [[ "$code" == 200 ]] && grep -q '"status":"UP"' "$OUT/$svc-health.json"; then
          result=STARTED_AND_HEALTHY
        else
          result=STARTED_BUT_HEALTHCHECK_FAILED
          if [[ "$code" == 401 || "$code" == 403 ]]; then
            detail="${detail:+$detail; }context started; /actuator/health is not in the JWT permit-all list (secured)"
          else
            detail="${detail:+$detail; }$(head -c 220 "$OUT/$svc-health.json" 2>/dev/null || true)"
          fi
        fi
        if [[ "${STARTUP_SMOKE:-0}" == 1 ]]; then
          info="$(curl -s -m 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/v1/info" || true)"
          login="$(curl -s -m 20 -o "$OUT/$svc-smoke-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
                   -d '{"consumer":"startup-smoke-no-such-user","consumerPassword":"x"}' \
                   "http://127.0.0.1:$port/api/auth/login" || true)"
          detail="${detail:+$detail; }smoke: /api/v1/info $info, /api/auth/login(unknown user) $login"
          if [[ "$info" != 200 || ! "$login" =~ ^4[0-9][0-9]$ ]]; then
            result=STARTED_BUT_SMOKE_FAILED
            cause="$(grep -E 'NoSuchBeanDefinitionException|Caused by:' "$log_f" | tail -1 | cut -c1-200 || true)"
            [[ -n "$cause" ]] && detail+="; $cause"
          fi
        fi
        break
      fi
      if (( secs >= TIMEOUT )); then result=FAILED_TO_START; detail="${detail:+$detail; }no 'Started' line within ${TIMEOUT}s"; break; fi
      sleep 2
    done
    kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true
  fi
  log "$svc: $result ($health) ${secs}s"
  jq -nc --arg s "$svc" --arg p "$port" --arg r "$result" --arg h "$health" --argjson t "$secs" --arg d "$detail" \
     '{service:$s,port:$p,result:$r,health:$h,seconds:$t,detail:$d}' >> "$OUT/startup-results.jsonl"
done

jq -s . "$OUT/startup-results.jsonl" > "$OUT/startup-summary.json"
{ echo "# Startup check (profile: $PROFILE)"; echo; echo "| Service | Port | Result | Health | Seconds | Detail |"; echo "|---|---|---|---|---|---|"
  jq -r '.[] | "| \(.service) | \(.port) | \(.result) | \(.health) | \(.seconds) | \(.detail|gsub("\\|";"/")) |"' "$OUT/startup-summary.json"; } > "$OUT/startup-summary.md"
cat "$OUT/startup-summary.md"
