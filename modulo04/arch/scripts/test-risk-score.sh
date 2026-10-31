#!/bin/sh
# Test for reference/scripts/RiskScore.java, the risk matrix script that to-risk runs: the nine
# combinations of impact and likelihood with the score and level expected, "unproven" as 9 high,
# and every invalid input (0, 4, a letter, a missing argument) exiting with code 2.
#
# Usage, from anywhere: scripts/test-risk-score.sh (requires Java 11 or later)
# Maintainer and CI tool. The skills never run it.

set -eu

PLUGIN_ROOT=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT="$PLUGIN_ROOT/reference/scripts/RiskScore.java"
STATUS=0

expect() {
  want=$1
  shift
  if got=$(java "$SCRIPT" "$@" 2>/dev/null) && [ "$got" = "$want" ]; then
    echo "ok: $* -> $got"
  else
    echo "FAIL: $* -> '${got:-}' (expected '$want')"
    STATUS=1
  fi
}

expect_usage() {
  if java "$SCRIPT" "$@" > /dev/null 2>&1; then
    echo "FAIL: '$*' was accepted (expected exit code 2)"
    STATUS=1
  else
    code=$?
    if [ "$code" -eq 2 ]; then
      echo "ok: '$*' rejected with code 2"
    else
      echo "FAIL: '$*' exited with code $code (expected 2)"
      STATUS=1
    fi
  fi
}

expect "1 low" 1 1
expect "2 low" 1 2
expect "3 medium" 1 3
expect "2 low" 2 1
expect "4 medium" 2 2
expect "6 high" 2 3
expect "3 medium" 3 1
expect "6 high" 3 2
expect "9 high" 3 3
expect "9 high" unproven

expect_usage 0 1
expect_usage 1 0
expect_usage 4 1
expect_usage 1 4
expect_usage x 1
expect_usage 1
expect_usage
expect_usage 1 2 3

if [ "$STATUS" -eq 0 ]; then
  echo "risk matrix script in place"
fi
exit "$STATUS"
