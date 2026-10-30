#!/bin/sh
# Mutation test for scripts/check-language.sh. Copies the plugin to a temporary folder, breaks it
# one way at a time, and expects check-language.sh to fail on every break and pass on the
# untouched copy. Each mutation is a bug a reviewer found once; this keeps it caught.
#
# Usage, from anywhere: scripts/test-check-language.sh
# Maintainer and CI tool. The skills never run it.

set -eu

PLUGIN_ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
STATUS=0

# Portable in-place edit (BSD and GNU sed differ on -i).
edit() {
  expr=$1
  file=$2
  sed "$expr" "$file" > "$file.tmp"
  mv "$file.tmp" "$file"
}

drop_pair() {
  edit "/^$1\$/d" "$COPY/scripts/sync-references.sh"
}

fresh_copy() {
  COPY="$WORK/$1"
  mkdir -p "$COPY"
  cp -R "$PLUGIN_ROOT/." "$COPY"
}

expect_fail() {
  name=$1
  if "$COPY/scripts/check-language.sh" > "$WORK/$name.log" 2>&1; then
    echo "NOT CAUGHT: $name"
    STATUS=1
  else
    echo "caught: $name ($(grep -m1 '^FAIL' "$WORK/$name.log" || echo 'no FAIL line'))"
  fi
}

# Baseline: the untouched plugin passes.
fresh_copy baseline
if "$COPY/scripts/check-language.sh" > "$WORK/baseline.log" 2>&1; then
  echo "baseline passes"
else
  echo "BASELINE FAILS:"
  cat "$WORK/baseline.log"
  STATUS=1
fi

# 1. Stale vocabulary copy, with the skill left out of PAIRS (sync --check no longer sees it).
fresh_copy stale-vocabulary
drop_pair "to-characteristics:vocabulary.md"
echo "- stale line" >> "$COPY/skills/to-characteristics/references/vocabulary.md"
expect_fail stale-vocabulary

# 2. Stale template copy, out of PAIRS.
fresh_copy stale-template
drop_pair "grill-architecture:brief.md"
echo "stale line" >> "$COPY/skills/grill-architecture/references/brief.md"
expect_fail stale-template

# 3. agents/openai.yaml missing.
fresh_copy no-openai-yaml
rm "$COPY/skills/to-characteristics/agents/openai.yaml"
expect_fail no-openai-yaml

# 4. agents/openai.yaml allows implicit invocation.
fresh_copy openai-implicit
edit 's/allow_implicit_invocation: false/allow_implicit_invocation: true/' \
  "$COPY/skills/to-characteristics/agents/openai.yaml"
expect_fail openai-implicit

# 5. Frontmatter without disable-model-invocation.
fresh_copy no-disable-model-invocation
edit '/^disable-model-invocation: true$/d' "$COPY/skills/to-characteristics/SKILL.md"
expect_fail no-disable-model-invocation

# 6. disable-model-invocation only in the body, not in the frontmatter.
fresh_copy disable-in-body
edit '/^disable-model-invocation: true$/d' "$COPY/skills/grill-architecture/SKILL.md"
echo "disable-model-invocation: true" >> "$COPY/skills/grill-architecture/SKILL.md"
expect_fail disable-in-body

# 7. Dossier index template missing from the skill folder (and from PAIRS).
fresh_copy no-dossier-template-copy
drop_pair "to-characteristics:dossier-readme.md"
rm "$COPY/skills/to-characteristics/references/dossier-readme.md"
expect_fail no-dossier-template-copy

# 8. Skill writes the dossier index without citing its template.
fresh_copy no-dossier-template-link
drop_pair "grill-architecture:dossier-readme.md"
rm "$COPY/skills/grill-architecture/references/dossier-readme.md"
edit '/references\/dossier-readme.md/d' "$COPY/skills/grill-architecture/SKILL.md"
expect_fail no-dossier-template-link

# 9. Local task format without the Status line, in a skill that creates tasks.
fresh_copy no-status-in-skill
edit 's/Status: open/state line/g' "$COPY/skills/to-characteristics/SKILL.md"
expect_fail no-status-in-skill

# 10. Tracker template lost the Status line.
fresh_copy no-status-in-template
edit 's/Status: open/state line/g' "$COPY/reference/templates/agents-architecture.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail no-status-in-template

# 11. Fixed "Next:" line without the translation instruction.
fresh_copy next-untranslated
edit 's/as they are/as written/g; s/as$/as written/' "$COPY/skills/grill-architecture/SKILL.md"
expect_fail next-untranslated

if [ "$STATUS" -eq 0 ]; then
  echo "every mutation caught"
fi
exit "$STATUS"
