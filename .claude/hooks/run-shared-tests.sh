#!/usr/bin/env bash
# Stop hook: run the shared test suite before the session finishes.
#
# `:shared:jvmTest` and not `allTests`: the JVM target is the fast loop and
# covers every line of common code. `allTests` additionally runs the
# Android unit-test variants, which triples the time and re-tests the same
# common sources.
#
# Runtime is measured and recorded in docs/BUILD_TIMES.md. If it climbs
# past roughly a minute, gate it on whether any .kt file changed rather
# than paying it every time -- see GATE_ON_KOTLIN_CHANGES below.
set -uo pipefail

payload=$(cat)

# A Stop hook that blocks can be re-entered. Claude Code sets this flag on
# the second pass; without the guard the session cannot ever finish.
if [ "$(printf '%s' "$payload" | jq -r '.stop_hook_active // false' 2>/dev/null)" = "true" ]; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Flip to 1 if the suite gets slow. Cheap insurance, off while it is fast.
GATE_ON_KOTLIN_CHANGES=0
if [ "$GATE_ON_KOTLIN_CHANGES" = "1" ]; then
    if git diff --quiet HEAD -- '*.kt' '*.kts' '*.sq' 2>/dev/null; then
        exit 0
    fi
fi

# Not --quiet: that suppresses the per-test output, and which test failed
# is the single most useful thing this hook can report.
output=$(./gradlew :shared:jvmTest --console=plain 2>&1)
status=$?

[ $status -eq 0 ] && exit 0

# Name the failing tests rather than dumping the whole Gradle log.
failures=$(printf '%s' "$output" | grep -E '^e: |Test.* > .* FAILED|^\s+[A-Za-z.]+ FAILED' | head -20)
[ -z "$failures" ] && failures=$(printf '%s' "$output" | grep -E 'FAILED' | head -20)

cat >&2 <<EOF
The shared test suite is failing, so the work is not finished.

$failures

Run ./gradlew :shared:jvmTest to see the detail. Every test in shared
guards a rule from docs/LEAGUE_APP_ANALYSIS.md, so a failure here usually
means a domain rule was broken rather than that a test needs updating.
EOF
exit 2
