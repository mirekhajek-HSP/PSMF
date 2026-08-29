#!/usr/bin/env bash
# PostToolUse guard: shared/src/commonTest must not use JUnit or MockK.
#
# commonTest compiles for every target, including iOS. `org.junit` and
# `io.mockk` are JVM-only, so importing either produces an unresolved
# reference much later, in a Kotlin/Native compile whose error message
# points nowhere near the actual mistake.
#
# The rule (CLAUDE.md, docs/TECH_STACK.md section 2): shared tests use
# kotlin.test, and test doubles are hand-written fakes. Repositories sit
# behind interfaces precisely so that fakes are cheap.
#
# This is the KMP-adapted descendant of golblok's JUnit 4 guard.
#
# Exit 2 is the blocking signal: stderr is fed back to the model so it can
# correct the file it has just written.
set -uo pipefail

payload=$(cat)
file=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)

[ -n "$file" ] || exit 0
[ -f "$file" ] || exit 0

# Only guard shared common tests. Android-target tests may use both.
case "$file" in
    */shared/src/commonTest/*) ;;
    *) exit 0 ;;
esac

found=""
grep -q 'org\.junit'  "$file" && found="org.junit"
grep -q 'io\.mockk'   "$file" && found="${found:+$found and }io.mockk"

[ -n "$found" ] || exit 0

cat >&2 <<EOF
BLOCKED: $file uses $found.

shared/src/commonTest compiles for every target, including iOS. JUnit and
MockK are JVM-only, so this will fail later in a Kotlin/Native compile with
an error that points nowhere near this file.

Use instead:
  - kotlin.test           @Test, assertEquals, assertTrue, assertFailsWith
  - a hand-written fake   e.g. FakeMatchRepository implementing the
                          repository interface, not a mock

Android-target tests under composeApp/src/androidUnitTest may use JUnit and
MockK. Shared tests may not. See CLAUDE.md, "Testing".
EOF
exit 2
