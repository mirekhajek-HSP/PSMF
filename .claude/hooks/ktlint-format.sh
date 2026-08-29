#!/usr/bin/env bash
# PostToolUse hook: format the Kotlin file that was just edited.
#
# Runs the ktlint CLI rather than `./gradlew ktlintFormat`, which would pay
# Gradle startup on every single edit. The CLI and the Gradle plugin are
# pinned to the same ktlint version and both read .editorconfig, so they
# cannot disagree about style.
#
# Never blocks. A formatter that fails an edit is worse than one that
# quietly does nothing, so every failure path here exits 0.
set -uo pipefail

payload=$(cat)

file=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)

# Nothing to do unless this is a Kotlin file that still exists.
case "$file" in
    *.kt|*.kts) ;;
    *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

if ! command -v ktlint >/dev/null 2>&1; then
    echo "ktlint-format hook: ktlint not on PATH, skipping" >&2
    exit 0
fi

# -F rewrites the file in place. Output is noise on success, and on failure
# it is the unfixable violations, which `./gradlew ktlintCheck` will report
# properly at build time anyway.
ktlint -F --relative "$file" >/dev/null 2>&1

exit 0
