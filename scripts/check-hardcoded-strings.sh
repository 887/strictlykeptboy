#!/usr/bin/env bash
# TR-A.1 — Hardcoded user-facing string lint guardrail.
#
# Greps `app/src/main/java/.../ui/` for Compose / Kotlin patterns that
# embed a non-empty user-facing literal:
#
#   Text("...")
#   Text(text = "...")
#   text = "..."
#
# in non-test files, with an allowlist for obvious non-i18n cases
# (test tags / preview tooling / log strings / developer-only content
# descriptions / wire-format `.label` strings on enums).
#
# Canonical English copy lives in `app/src/main/res/values/strings.xml`;
# UI references it via `stringResource(R.string.X)` /
# `Context.getString(R.string.X)`. See `docs/plans/translations.md`.
#
# Exit codes:
#   0  no findings (clean)
#   1  one or more potential hardcoded strings detected
#
# Invocation:
#   scripts/check-hardcoded-strings.sh                # repo-wide
#   scripts/check-hardcoded-strings.sh --files <list> # space-separated list

set -u

cd "$(dirname "$0")/.."

ROOT="app/src/main/java/com/eight87/strictlykeptboy/ui"

if [[ "${1:-}" == "--files" ]]; then
    shift
    FILES=("$@")
else
    mapfile -t FILES < <(find "$ROOT" -type f -name '*.kt' \
        ! -path '*/test/*' \
        ! -name '*Test.kt' \
        ! -name '*Preview*.kt' \
        ! -name '*Previews.kt')
fi

# Patterns we treat as user-facing literals (PCRE):
#   - Text("literal")             — direct Text composable with literal
#   - Text(text = "literal")      — Text(text = "...") named-arg form
#   - text = "literal"            — `text = "..."` named-arg in any call
# Empty string ("") is always permitted.
PATTERN='(\bText\s*\(\s*"[^"]+"|\bText\s*\(\s*text\s*=\s*"[^"]+"|^\s*text\s*=\s*"[^"]+")'

# Allowlist substrings — lines containing any of these are ignored.
ALLOW=(
    # Compose previews / tooling-only strings
    '@Preview'
    'Preview('
    'tools:'
    # Log strings — developer-only
    'Log.d('
    'Log.v('
    'Log.i('
    'Log.w('
    'Log.e('
    'android.util.Log'
    # Test tags — not user-facing
    'testTag('
    'Modifier.testTag'
    # contentDescription = null (a11y intentional null)
    'contentDescription = null'
    # Wire-format enum .label strings are not UI chrome (T-4)
    '.label,'
    '.label)'
    # stringResource path — already routed correctly
    'stringResource('
    # Resource references — already routed
    'R.string.'
    # Body / id-style values that are passed-through user-supplied content
    'placeholder ='
    # Markdown-format helpers — developer-facing
    'TAG ='
)

FINDINGS=0
declare -a HITS

for f in "${FILES[@]}"; do
    [[ -f "$f" ]] || continue
    # Grep with line numbers; PCRE for the alternation.
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        skip=0
        for needle in "${ALLOW[@]}"; do
            if [[ "$line" == *"$needle"* ]]; then
                skip=1
                break
            fi
        done
        [[ $skip -eq 1 ]] && continue
        HITS+=("$f:$line")
        FINDINGS=$((FINDINGS + 1))
    done < <(grep -nP "$PATTERN" "$f" 2>/dev/null || true)
done

if [[ $FINDINGS -gt 0 ]]; then
    echo "check-hardcoded-strings: found $FINDINGS potential hardcoded user-facing string(s):" >&2
    for h in "${HITS[@]}"; do
        echo "  $h" >&2
    done
    echo >&2
    echo "Move user-facing copy into app/src/main/res/values/strings.xml" >&2
    echo "and reference via stringResource(R.string.X) /" >&2
    echo "Context.getString(R.string.X). See docs/plans/translations.md." >&2
    exit 1
fi

echo "check-hardcoded-strings: clean (${#FILES[@]} files scanned)."
exit 0
