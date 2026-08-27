#!/usr/bin/env bash
# scan-vendored-assets.sh
#
# Finds files that a Maven-tree-based license/SBOM scan (Eclipse Dash, CycloneDX
# maven plugin) CANNOT see, because they never go through Maven dependency
# resolution:
#
#   1. Committed .jar files (checked straight into git, not resolved by Maven)
#   2. Maven `system`-scope / `systemPath` dependencies (bypass resolution too)
#   3. Vendored front-end JS/CSS/font libraries checked into static resource
#      dirs (no npm/webpack in this repo, so third-party JS/CSS arrives as
#      committed files, typically under a `lib/` folder or named `*.min.*`)
#
# Modes:
#   ./fc-tools/scan-vendored-assets.sh            human-readable report (default)
#   ./fc-tools/scan-vendored-assets.sh --list      deterministic sorted listing,
#                                                   one "<category><TAB><path>"
#                                                   line per finding — the
#                                                   format committed as
#                                                   fc-tools/vendored-assets-baseline.txt
#   ./fc-tools/scan-vendored-assets.sh --check     regenerate the --list output,
#                                                   diff it against the baseline,
#                                                   and verify every baselined
#                                                   path has a matching row in
#                                                   fc-tools/oss-inventory-vendored-assets.csv;
#                                                   exit 1 on either kind of drift.
#                                                   Wired into CI by
#                                                   .github/workflows/vendored-assets-scan.yml
#
# CI runs --check as a PR gate (reads and diffs, never writes) and the default
# report mode at release time (informational artifact upload). Regenerating
# the baseline after a reviewed vendored-asset change, and updating
# fc-tools/oss-inventory-vendored-assets.csv with the
# licence findings for that change, is a manual step for whoever makes it:
#
#   ./fc-tools/scan-vendored-assets.sh --list > fc-tools/vendored-assets-baseline.txt
#
# This complements, it does not replace, the Eclipse Dash / SBOM Maven scans
# already run by .github/workflows/eclipse-dash.yml and sbom.yml — those cover
# a different open question (NF-4b: release-artefact location for the Dash
# scan/SBOM), still open with QA as of the 2026-08-17 response.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

BASELINE="fc-tools/vendored-assets-baseline.txt"
CSV="fc-tools/oss-inventory-vendored-assets.csv"

MODE="report"
case "${1:-}" in
  --check) MODE="check" ;;
  --list)  MODE="list" ;;
  "") ;;
  *) echo "Usage: $0 [--check|--list]" >&2; exit 2 ;;
esac

hr() { printf '%.0s-' {1..78}; echo; }

# --- gather the three categories, shared by every mode ----------------------

jars=$(git ls-files -- '*.jar' | LC_ALL=C sort)

assets=$(git ls-files -- '*.js' '*.css' '*.woff' '*.woff2' '*.ttf' '*.eot' \
  | grep -E '(^|/)(static|webapp|public)/' \
  | grep -viE '\.(java|py|md)$' \
  | while IFS= read -r f; do
      if [[ "$f" == */lib/* ]] || [[ "$f" == *.min.js ]] || [[ "$f" == *.min.css ]]; then
        echo "$f"
      fi
    done | LC_ALL=C sort)

img_matches=""
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  dir=$(dirname "$f")
  case "$f" in
    *dataTables*|*jquery-ui*)
      sibling_dir="$(dirname "$(dirname "$dir")")/images"
      if [[ -d "$sibling_dir" ]]; then
        hits=$(git ls-files -- "$sibling_dir/*.png" "$sibling_dir/*.gif" 2>/dev/null)
        [[ -n "$hits" ]] && img_matches="$img_matches
$hits"
      fi
      ;;
  esac
done <<< "$assets"
img_matches=$(echo "$img_matches" | sed '/^$/d' | LC_ALL=C sort -u)

# --- --list / --check: deterministic "<category><TAB><path>" lines ---------
# No absolute paths, no banner sniffing, no inline counts — anything that
# would make the same repo state diff differently on another machine.

emit_list() {
  echo "$jars" | sed '/^$/d' | while IFS= read -r f; do printf 'jar\t%s\n' "$f"; done
  for pom in $(git ls-files -- '*pom.xml' | LC_ALL=C sort); do
    grep -noE '<scope>[[:space:]]*system[[:space:]]*</scope>|systemPath' "$pom" 2>/dev/null \
      | cut -d: -f1 \
      | while IFS= read -r lineno; do printf 'system-scope\t%s:%s\n' "$pom" "$lineno"; done
  done
  echo "$assets" | sed '/^$/d' | while IFS= read -r f; do printf 'frontend\t%s\n' "$f"; done
  echo "$img_matches" | sed '/^$/d' | while IFS= read -r f; do printf 'image\t%s\n' "$f"; done
}

if [[ "$MODE" == "list" ]]; then
  emit_list | LC_ALL=C sort
  exit 0
fi

if [[ "$MODE" == "check" ]]; then
  if [[ ! -f "$BASELINE" ]]; then
    echo "Missing baseline: $BASELINE" >&2
    echo "Run './$(basename "$0") --list > $BASELINE' and commit it." >&2
    exit 1
  fi
  current="$(emit_list | LC_ALL=C sort)"
  baseline_content="$(cat "$BASELINE")"
  if [[ "$current" != "$baseline_content" ]]; then
    echo "Vendored-asset drift detected against $BASELINE:" >&2
    diff -u "$BASELINE" <(echo "$current") >&2
    echo >&2
    echo "A committed jar, system-scope dependency, or vendored front-end asset was added, removed, or moved." >&2
    echo "Review its licence, update $CSV," >&2
    echo "then regenerate the baseline: ./$(basename "$0") --list > $BASELINE" >&2
    exit 1
  fi

  # The baseline only tracks paths, not licences - a CSV row can be deleted
  # without changing it. Every baselined path must have a matching CSV row.
  baseline_paths=$(cut -f2 "$BASELINE" | LC_ALL=C sort -u)
  csv_paths=$(tail -n +2 "$CSV" | cut -d, -f1 | LC_ALL=C sort -u)
  missing=$(comm -23 <(echo "$baseline_paths") <(echo "$csv_paths"))
  if [[ -n "$missing" ]]; then
    echo "Licence-inventory drift: baselined path(s) missing from $CSV:" >&2
    echo "$missing" | sed 's/^/  /' >&2
    echo >&2
    echo "Add a licence-inventory row for each path, or explain the removal in the PR." >&2
    exit 1
  fi

  echo "OK: vendored-asset inventory matches $BASELINE and $CSV covers every entry"
  exit 0
fi

# --- report mode: human-readable, unchanged shape ---------------------------

echo "Vendored-asset scan for: $REPO_ROOT"
echo "(files outside the Maven dependency tree - not covered by Eclipse Dash / SBOM scans)"
hr

echo "## Committed .jar files"
if [[ -z "$jars" ]]; then
  echo "(none found)"
else
  echo "$jars" | while IFS= read -r j; do
    echo "  $j"
  done
  echo "Count: $(echo "$jars" | grep -c .)"
fi
hr

echo "## Maven system-scope / systemPath dependencies (pom.xml)"
found_system=0
for pom in $(git ls-files -- '*pom.xml' | LC_ALL=C sort); do
  if grep -qE '<scope>[[:space:]]*system[[:space:]]*</scope>|systemPath' "$pom" 2>/dev/null; then
    echo "  $pom"
    grep -nE '<scope>[[:space:]]*system[[:space:]]*</scope>|systemPath' "$pom" | sed 's/^/    /'
    found_system=1
  fi
done
[[ $found_system -eq 0 ]] && echo "(none found)"
hr

# Heuristic: any committed JS/CSS/font file that is either (a) inside a
# directory literally named "lib" under a static/webapp resource tree, or
# (b) named *.min.js / *.min.css (the project's own hand-written JS/CSS in
# this repo is never minified - only vendored copies are).
echo "## Vendored front-end assets (js/css/fonts checked into static resources)"
if [[ -z "$assets" ]]; then
  echo "(none found)"
else
  echo "$assets" | while IFS= read -r f; do
    # Try to pull a name/version/license banner out of the first 5 lines.
    banner=$(head -5 "$f" 2>/dev/null | grep -oE '[A-Za-z][A-Za-z .+-]* v?[0-9]+\.[0-9]+(\.[0-9]+)?|Licensed under [A-Za-z0-9 .()/:_-]+|Copyright[^*/]*' | head -2 | tr '\n' ' | ')
    printf "  %-70s %s\n" "$f" "${banner:-<no banner found - inspect manually>}"
  done
  echo "Count: $(echo "$assets" | grep -c .)"
fi
hr

# DataTables' default theme ships small PNG sort/detail icons alongside the
# CSS/JS. They carry no text banner, so flag any PNG living next to a
# dataTables*.css/js file for manual attribution.
echo "## Images colocated with vendored front-end libs (need manual attribution)"
if [[ -z "$img_matches" ]]; then
  echo "(none found)"
else
  echo "$img_matches" | while IFS= read -r i; do echo "  $i"; done
fi
hr

echo "Done. Cross-check results against:"
echo "  fc-tools/oss-inventory-vendored-assets.csv"
