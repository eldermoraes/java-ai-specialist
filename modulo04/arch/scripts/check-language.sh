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
#   - a copy listed in PAIRS of sync-references.sh is out of sync with reference/;
#   - the vocabulary, the characteristics template, a SKILL.md or the README:
#       - lists a configuration limit as a fitness function on its own, or mentions one without
#         "only when a test exercises it" (the vocabulary must state the rule);
#       - drops the quality of the response because a guardrail checks it, or mentions it without
#         "measured by evals" (the vocabulary must state the scope reason);
#       - counts one deployable per Dockerfile (the vocabulary must say that several images of the
#         same build are one deployable);
#       - takes the name of the system from the title of the README;
#   - a SKILL.md names the system without the build file as the source and the question in the
#     round that confirms the list as the fallback, or the characteristics template lost that rule;
#   - the README asks to confirm the quanta without "only when there is more than one deployable";
#   - language.md, the characteristics template, the dossier index template or a SKILL.md that
#     writes characteristics.md no longer keeps the term architecture characteristic as it is (the
#     title "Architecture Characteristics: <system name>" in every language), or a file translates
#     it ("Características de arquitetura");
#   - the vocabulary counts the multiplier in calls ("15 times the calls") instead of tokens;
#   - the vocabulary, the ADR template or a SKILL.md uses `not now` without "the condition that
#     reopens it";
#   - the vocabulary lost a condition of the model gateway, or the condition of the loop circuit
#     breaker (the model chooses the next step), or a file counts retries as that condition;
#   - the vocabulary or a SKILL.md that supersedes an ADR edits more than "only the Status line";
#   - a SKILL.md that closes tasks lacks `Status: closed` for local tasks, or deletes the task file;
#   - a script in skills/*/scripts/ differs from its source in reference/scripts/, or a SKILL.md
#     runs a script its folder does not ship;
#   - the README leaves a skill out of "Its skills are ...", of the Skills table, or of the count
#     that `claude plugin details arch` lists.
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

# Shared rules of the architecture vocabulary that a skill once broke. Text is matched with line
# breaks folded, so wrapping a sentence does not matter.
folded() {
  tr '\n' ' ' < "$1" | tr -s ' '
}
VOCABULARY="$PLUGIN_ROOT/reference/vocabulary.md"
RULE_FILES="$VOCABULARY $PLUGIN_ROOT/reference/templates/characteristics.md $PLUGIN_ROOT/README.md"
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  RULE_FILES="$RULE_FILES $skill_md"
done

# A limit in the configuration implements a characteristic; it is a fitness function only when a
# test exercises it.
if ! folded "$VOCABULARY" | grep -q 'limit in the configuration is not one by itself'; then
  fail "reference/vocabulary.md no longer says that a limit in the configuration is not a fitness function by itself"
fi
for file in $RULE_FILES; do
  text=$(folded "$file")
  if printf '%s\n' "$text" | grep -qiE 'alert, (a )?(limit in the configuration|configuration limit)'; then
    fail "${file#"$PLUGIN_ROOT"/} lists a configuration limit as a fitness function on its own"
  fi
  if printf '%s\n' "$text" | grep -qiE 'limit in the configuration|configuration limit' \
      && ! printf '%s\n' "$text" | grep -q 'exercises it'; then
    fail "${file#"$PLUGIN_ROOT"/} mentions a configuration limit without \"only when a test exercises it\""
  fi
done

# The quality of the response is out of scope because evals measure it, not because a guardrail
# checks it (a guardrail also checks characteristics that stay, such as confidentiality).
if ! folded "$VOCABULARY" | grep -q 'quality of the response\*\* is measured by evals'; then
  fail "reference/vocabulary.md no longer puts the quality of the response out of scope as measured by evals"
fi
for file in $RULE_FILES; do
  text=$(folded "$file")
  if printf '%s\n' "$text" | grep -qiE 'guardrail checks it on every request|checked by a guardrail on every request'; then
    fail "${file#"$PLUGIN_ROOT"/} drops the quality of the response because a guardrail checks it"
  fi
  if printf '%s\n' "$text" | grep -qi 'quality of the response' \
      && ! printf '%s\n' "$text" | grep -q 'measured by evals'; then
    fail "${file#"$PLUGIN_ROOT"/} mentions the quality of the response without \"measured by evals\""
  fi
done

# Deployables are counted by build unit: images of the same build are one deployable.
if ! folded "$VOCABULARY" | grep -q 'images of the same build .* are one deployable'; then
  fail "reference/vocabulary.md no longer says that several images of the same build are one deployable"
fi
for file in $RULE_FILES; do
  if folded "$file" | grep -q 'one `Dockerfile` per service'; then
    fail "${file#"$PLUGIN_ROOT"/} counts one deployable per Dockerfile"
  fi
done

# The name of the system comes from the build file; without one, the user is asked in the round
# that confirms the list. The README title is not a source (it often names something else).
for file in $RULE_FILES; do
  if folded "$file" | grep -qiE '(name of the system|system name) is the title of the README'; then
    fail "${file#"$PLUGIN_ROOT"/} takes the name of the system from the title of the README"
  fi
done
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  text=$(folded "$skill_md")
  if printf '%s\n' "$text" | grep -q 'The name of the system is' \
      && ! { printf '%s\n' "$text" | grep -q 'the name the build file declares' \
             && printf '%s\n' "$text" | grep -q 'ask the user which name to use in the round that confirms the list'; }; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md names the system without \"the name the build file declares\" and the fallback \"ask the user which name to use in the round that confirms the list\""
  fi
done
TEMPLATE_TEXT=$(folded "$PLUGIN_ROOT/reference/templates/characteristics.md")
if ! { printf '%s\n' "$TEMPLATE_TEXT" | grep -q 'the name the build file declares' \
       && printf '%s\n' "$TEMPLATE_TEXT" | grep -q 'the name the user gave'; }; then
  fail "reference/templates/characteristics.md lost the rule for the system name (build file, otherwise the name the user gave)"
fi

# The term architecture characteristic stays as it is in every language, like Target and fitness
# function: a run in Portuguese once titled the file "Características de arquitetura".
KEEP_TERM='Keep the term architecture characteristic as it is, in every language'
if ! folded "$PLUGIN_ROOT/reference/language.md" \
    | grep -q 'The title of the characteristics file is `Architecture Characteristics: <system name>` in every language'; then
  fail "reference/language.md no longer keeps the term architecture characteristic (and the title of the characteristics file) as it is"
fi
if [ "$(head -1 "$PLUGIN_ROOT/reference/templates/characteristics.md")" != '# Architecture Characteristics: {{system name}}' ]; then
  fail "reference/templates/characteristics.md lost the title \"# Architecture Characteristics: {{system name}}\""
fi
if ! printf '%s\n' "$TEMPLATE_TEXT" | grep -q "$KEEP_TERM"; then
  fail "reference/templates/characteristics.md does not say \"$KEEP_TERM\""
fi
if ! folded "$PLUGIN_ROOT/reference/templates/dossier-readme.md" \
    | grep -q 'the label "Architecture Characteristics" as it is, in every language'; then
  fail "reference/templates/dossier-readme.md does not keep the label \"Architecture Characteristics\" as it is"
fi
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  if grep -q '(references/characteristics.md)' "$skill_md" \
      && ! folded "$skill_md" | grep -q "$KEEP_TERM"; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md writes characteristics.md without \"$KEEP_TERM\""
  fi
done
for file in $(find "$PLUGIN_ROOT/reference" "$PLUGIN_ROOT/skills" "$PLUGIN_ROOT/README.md" -type f); do
  if grep -qi 'caracter.sticas de arquitetura' "$file"; then
    fail "${file#"$PLUGIN_ROOT"/} translates the term architecture characteristic: $(grep -ni 'caracter.sticas de arquitetura' "$file" | head -1)"
  fi
done

# With a single deployable the quantum is not asked.
if folded "$PLUGIN_ROOT/README.md" | grep -q 'confirm the quanta' \
    && ! folded "$PLUGIN_ROOT/README.md" | grep -q 'confirm the quanta (asked only when there is more than one deployable)'; then
  fail "README.md asks to confirm the quanta without \"only when there is more than one deployable\""
fi

# The multiplier of a multi-agent system is about 15 times the tokens of a single-agent chat, not
# the calls: the vocabulary once said "15 times the calls".
if ! folded "$VOCABULARY" | grep -q 'about 15 times the tokens of a single-agent chat'; then
  fail "reference/vocabulary.md no longer says that a multi-agent system spends about 15 times the tokens of a single-agent chat"
fi
for file in $(find "$PLUGIN_ROOT/reference" "$PLUGIN_ROOT/skills" "$PLUGIN_ROOT/README.md" -type f); do
  if folded "$file" | grep -qi '15 times the calls'; then
    fail "${file#"$PLUGIN_ROOT"/} counts the multiplier in calls (\"15 times the calls\"); it is about 15 times the tokens"
  fi
done

# A part of a decision without its condition is `not now`, always with the condition that reopens
# it; `not now` is a decision, never a status.
if ! folded "$VOCABULARY" | grep -q '\*\*Not now\*\*: a decision postponed on purpose, recorded with the condition that reopens it'; then
  fail "reference/vocabulary.md no longer defines not now with the condition that reopens it"
fi
if ! folded "$PLUGIN_ROOT/reference/templates/adr.md" | grep -q '`not now`, with the condition that reopens it'; then
  fail "reference/templates/adr.md no longer asks for each part with its condition (\`not now\`, with the condition that reopens it)"
fi
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  text=$(folded "$skill_md")
  if printf '%s\n' "$text" | grep -qi 'not now' \
      && ! printf '%s\n' "$text" | grep -q 'the condition that reopens it'; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md uses not now without \"the condition that reopens it\""
  fi
done

# The conditions of the known parts of a cost decision.
VOCABULARY_TEXT=$(folded "$VOCABULARY")
for condition in 'more than one model' 'per-user quota' 'keys kept out of the application'; do
  if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q "Condition: more than one model, per-user quota, or keys kept out of the application" \
      || ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q "$condition"; then
    fail "reference/vocabulary.md lost a condition of the model gateway (\"$condition\")"
  fi
done
# The loop circuit breaker needs a loop: the model chooses the next step. A retry has its limit in
# the code and counts only in the worst case of the ceiling (decision of 2026-10-31).
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q 'Condition: the model chooses the next step (a supervisor, a loop agent, tool calling)\.'; then
  fail "reference/vocabulary.md lost the condition of the loop circuit breaker (\"the model chooses the next step (a supervisor, a loop agent, tool calling)\")"
fi
for file in $RULE_FILES; do
  if folded "$file" | grep -qiE 'tool calling,( and)? retr(y|ies)\)'; then
    fail "${file#"$PLUGIN_ROOT"/} counts retries as a condition of the loop circuit breaker"
  fi
done

# An accepted ADR is superseded, never edited: only its Status line changes.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q 'only the Status line changes'; then
  fail "reference/vocabulary.md no longer says that only the Status line of a superseded ADR changes"
fi
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  if grep -qi 'supersede' "$skill_md" && ! folded "$skill_md" | grep -q 'only the Status line'; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md supersedes an ADR without \"only the Status line\""
  fi
done

# A skill that closes tasks closes a local task with its Status line, and never deletes it.
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  if grep -q 'gh issue close' "$skill_md" && ! grep -q 'Status: closed' "$skill_md"; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md closes tasks without the \`Status: closed\` line for local tasks"
  fi
  if folded "$skill_md" | grep -qiE '(delete|remove) the (task )?file' \
      && ! folded "$skill_md" | grep -q 'never delete the task file'; then
    fail "skills/$(basename "$(dirname "$skill_md")")/SKILL.md deletes a local task file instead of closing it"
  fi
done

# Every script a skill ships is a copy of its single source in reference/scripts/, and every
# script a SKILL.md runs ships in its folder.
for script in "$PLUGIN_ROOT"/skills/*/scripts/*; do
  [ -f "$script" ] || continue
  rel=${script#"$PLUGIN_ROOT"/}
  source="$PLUGIN_ROOT/reference/scripts/$(basename "$script")"
  if [ ! -f "$source" ]; then
    fail "$rel has no source in reference/scripts/"
  elif ! cmp -s "$source" "$script"; then
    fail "$rel differs from reference/scripts/$(basename "$script") (add the pair to PAIRS in scripts/sync-references.sh and run it)"
  fi
done
for skill_md in "$PLUGIN_ROOT"/skills/*/SKILL.md; do
  skill_dir=$(dirname "$skill_md")
  for ref in $(grep -o 'scripts/[A-Za-z0-9_.-]*\.java' "$skill_md" | sort -u); do
    if [ ! -f "$skill_dir/$ref" ]; then
      fail "skills/$(basename "$skill_dir")/SKILL.md runs $ref, which is missing (add the pair to PAIRS in scripts/sync-references.sh and run it)"
    fi
  done
done

# The README names every skill: in the "For your agent" sentence, in the count the agent verifies,
# and in the Skills table.
README_TEXT=$(folded "$PLUGIN_ROOT/README.md")
SKILLS_SENTENCE=$(printf '%s\n' "$README_TEXT" | grep -o 'Its skills are [^.]*\.' || true)
count=0
for skill_dir in "$PLUGIN_ROOT"/skills/*/; do
  skill=$(basename "$skill_dir")
  count=$((count + 1))
  if ! printf '%s\n' "$SKILLS_SENTENCE" | grep -q "\`$skill\`"; then
    fail "README.md does not list \`$skill\` in \"Its skills are ...\""
  fi
  if ! grep -q "^| \`$skill\` |" "$PLUGIN_ROOT/README.md"; then
    fail "README.md has no row for \`$skill\` in the Skills table"
  fi
done
case $count in
  1) count_word=one ;; 2) count_word=two ;; 3) count_word=three ;; 4) count_word=four ;;
  5) count_word=five ;; 6) count_word=six ;; 7) count_word=seven ;; 8) count_word=eight ;;
  *) count_word=$count ;;
esac
if ! printf '%s\n' "$README_TEXT" | grep -q "lists the $count_word skills"; then
  fail "README.md does not say that \`claude plugin details arch\` lists the $count_word skills"
fi

if ! "$PLUGIN_ROOT/scripts/sync-references.sh" --check > /dev/null; then
  fail "references out of sync with reference/ (run scripts/sync-references.sh)"
fi

if [ "$STATUS" -eq 0 ]; then
  echo "language rule and skill contracts in place"
fi
exit "$STATUS"
