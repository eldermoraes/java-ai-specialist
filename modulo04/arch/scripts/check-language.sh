#!/bin/sh
# Regression check for the plugin's skills: the language rule (reference/language.md), the
# references each skill ships, and the contracts the skills share. Every skill in skills/ is
# checked, new ones included, even when missing from the PAIRS list of sync-references.sh.
#
# Fails when:
#   - reference/language.md is missing or lost the rule;
#   - a SKILL.md does not point to references/language.md or does not tell the agent to write in
#     the user's language;
#   - a SKILL.md links to a references/*.md file that its skill folder does not ship, that has no
#     source in reference/ or reference/templates/, or whose copy differs from that source (cmp -s;
#     this catches a stale copy of any reference, not only language.md);
#   - a skill folder lacks agents/openai.yaml, or that file does not say
#     "allow_implicit_invocation: false";
#   - the frontmatter of a SKILL.md lacks "disable-model-invocation: true";
#   - a SKILL.md writes the dossier index (docs/architecture/README.md) without linking its
#     template, references/dossier-readme.md;
#   - a SKILL.md defines local tasks (a "Labels:" line) without the "Status: open" line, or the
#     tracker template or language.md lost the Status line;
#   - a SKILL.md has a "Next:" line without telling the agent to translate it and keep commands and
#     file names as they are;
#   - a template in reference/templates/ lost its language instruction;
#   - any skill, reference, template or the README mentions English (for example "in English"),
#     outside language.md itself, which is the only file allowed to name the plugin's own language;
#   - a copy listed in PAIRS of sync-references.sh is out of sync with reference/.
#
# What it cannot check is the wording of each SKILL.md beyond these phrases.
#
# Usage, from anywhere: scripts/check-language.sh
# Maintainer and CI tool. The skills never run it. scripts/test-check-language.sh proves, with
# mutations in temporary copies, that each check above fails when it should.

set -eu

PLUGIN_ROOT=$(cd "$(dirname "$0")/.." && pwd)
STATUS=0

fail() {
  echo "FAIL: $1"
  STATUS=1
}

RULE="user's language"
NEXT_RULE="commands and file names as they are"

if [ ! -f "$PLUGIN_ROOT/reference/language.md" ]; then
  fail "reference/language.md is missing"
else
  if ! grep -q "$RULE" "$PLUGIN_ROOT/reference/language.md"; then
    fail "reference/language.md no longer states the rule (\"$RULE\")"
  fi
  if ! grep -q '`Status:`' "$PLUGIN_ROOT/reference/language.md"; then
    fail "reference/language.md no longer keeps the \`Status:\` line of local tasks as it is"
  fi
fi

if ! grep -q 'Status: open' "$PLUGIN_ROOT/reference/templates/agents-architecture.md"; then
  fail "reference/templates/agents-architecture.md no longer defines the \`Status: open\` line of local tasks"
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
  fi

  # Every reference the SKILL.md links to must ship in the skill folder, identical to its source.
  # Compared here, not only in sync-references.sh --check: a skill left out of the PAIRS list
  # there would keep a stale copy and still pass.
  for ref in $(grep -o 'references/[A-Za-z0-9_.-]*\.md' "$skill_md" | sort -u); do
    name=${ref#references/}
    if [ -f "$PLUGIN_ROOT/reference/$name" ]; then
      source="$PLUGIN_ROOT/reference/$name"
    elif [ -f "$PLUGIN_ROOT/reference/templates/$name" ]; then
      source="$PLUGIN_ROOT/reference/templates/$name"
    else
      fail "skills/$skill/SKILL.md links to $ref, which has no source in reference/ or reference/templates/"
      continue
    fi
    if [ ! -f "$skill_dir/$ref" ]; then
      fail "skills/$skill/SKILL.md links to $ref, which is missing (add the pair to PAIRS in scripts/sync-references.sh and run it)"
    elif ! cmp -s "$source" "$skill_dir/$ref"; then
      fail "skills/$skill/$ref differs from ${source#"$PLUGIN_ROOT"/} (add the pair to PAIRS in scripts/sync-references.sh and run it)"
    fi
  done

  # Explicit invocation only, in both hosts.
  openai="$skill_dir/agents/openai.yaml"
  if [ ! -f "$openai" ]; then
    fail "skills/$skill/agents/openai.yaml is missing"
  elif ! grep -q '^ *allow_implicit_invocation: false *$' "$openai"; then
    fail "skills/$skill/agents/openai.yaml does not say \"allow_implicit_invocation: false\""
  fi
  frontmatter=$(awk 'NR == 1 { if ($0 != "---") exit; next } /^---$/ { exit } { print }' "$skill_md")
  if ! printf '%s\n' "$frontmatter" | grep -q '^disable-model-invocation: true *$'; then
    fail "skills/$skill/SKILL.md frontmatter lacks \"disable-model-invocation: true\""
  fi

  # A skill that writes the dossier index creates it from the template when it is missing.
  if grep -q 'docs/architecture/README.md' "$skill_md" \
      && ! grep -q '(references/dossier-readme.md)' "$skill_md"; then
    fail "skills/$skill/SKILL.md writes docs/architecture/README.md without linking references/dossier-readme.md"
  fi

  # Local tasks carry their state.
  if grep -q 'Labels: ' "$skill_md" && ! grep -q 'Status: open' "$skill_md"; then
    fail "skills/$skill/SKILL.md defines local tasks without the \`Status: open\` line"
  fi

  # Fixed "Next:" sentences are translated.
  # Matched on the text with line breaks folded, so wrapping the sentence does not matter.
  if grep -q 'Next: ' "$skill_md" \
      && ! tr '\n' ' ' < "$skill_md" | tr -s ' ' | grep -q "$NEXT_RULE"; then
    fail "skills/$skill/SKILL.md has a \"Next:\" line without \"translate ... keep $NEXT_RULE\""
  fi
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
  echo "language rule and skill contracts in place"
fi
exit "$STATUS"
