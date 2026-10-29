#!/bin/sh
# Regression check for the language rule: every file a skill writes for the user follows the
# user's language (reference/language.md), even though the skills and templates are in English.
#
# Fails when:
#   - reference/language.md is missing or lost the rule;
#   - a SKILL.md does not point to references/language.md, or its skill folder lacks the copy, or
#     the copy differs from reference/language.md (every skill in skills/ is checked, new ones
#     included, even when missing from the PAIRS list of sync-references.sh);
#   - a SKILL.md links to a references/ file that its skill folder does not ship;
#   - a template in reference/templates/ lost its language instruction;
#   - any skill, reference, template or the README mentions English (for example "in English"),
#     outside language.md itself, which is the only file allowed to name the plugin's own language;
#   - a copy in skills/*/references/ is out of sync with reference/.
#
# Usage, from anywhere: scripts/check-language.sh
# Maintainer and CI tool. The skills never run it.

set -eu

PLUGIN_ROOT=$(cd "$(dirname "$0")/.." && pwd)
STATUS=0

fail() {
  echo "FAIL: $1"
  STATUS=1
}

RULE="user's language"

if [ ! -f "$PLUGIN_ROOT/reference/language.md" ]; then
  fail "reference/language.md is missing"
elif ! grep -q "$RULE" "$PLUGIN_ROOT/reference/language.md"; then
  fail "reference/language.md no longer states the rule (\"$RULE\")"
fi

for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  skill_dir=$(dirname "$skill_md")
  skill=$(basename "$skill_dir")
  if ! grep -q "references/language.md" "$skill_md"; then
    fail "skills/$skill/SKILL.md does not point to references/language.md"
  fi
  if ! grep -q "$RULE" "$skill_md"; then
    fail "skills/$skill/SKILL.md does not tell the agent to write in the $RULE"
  fi
  if [ ! -f "$skill_dir/references/language.md" ]; then
    fail "skills/$skill/references/language.md is missing (run scripts/sync-references.sh)"
  elif ! cmp -s "$PLUGIN_ROOT/reference/language.md" "$skill_dir/references/language.md"; then
    # Compared here too, not only in sync-references.sh --check: a skill left out of the PAIRS
    # list there would keep a stale copy of the rule and still pass.
    fail "skills/$skill/references/language.md differs from reference/language.md (add the skill to PAIRS in scripts/sync-references.sh and run it)"
  fi
  # Every reference the SKILL.md links to must ship in the skill folder.
  for ref in $(grep -o 'references/[A-Za-z0-9_.-]*\.md' "$skill_md" | sort -u); do
    if [ ! -f "$skill_dir/$ref" ]; then
      fail "skills/$skill/SKILL.md links to $ref, which is missing (run scripts/sync-references.sh)"
    fi
  done
done

for template in "$PLUGIN_ROOT"/reference/templates/*.md; do
  if ! grep -q "$RULE" "$template"; then
    fail "reference/templates/$(basename "$template") lost its language instruction (\"$RULE\")"
  fi
done

# Any mention of English outside language.md is how the bug came back last time
# ("Write in the user's words, in English" in the brief template).
for file in $(find "$PLUGIN_ROOT/reference" "$PLUGIN_ROOT/skills" "$PLUGIN_ROOT/README.md" -type f \
    ! -name language.md); do
  if grep -qi "english" "$file"; then
    fail "${file#"$PLUGIN_ROOT"/} mentions English: $(grep -ni "english" "$file" | head -1)"
  fi
done

if ! "$PLUGIN_ROOT/scripts/sync-references.sh" --check > /dev/null; then
  fail "references out of sync with reference/ (run scripts/sync-references.sh)"
fi

if [ "$STATUS" -eq 0 ]; then
  echo "language rule in place"
fi
exit "$STATUS"
