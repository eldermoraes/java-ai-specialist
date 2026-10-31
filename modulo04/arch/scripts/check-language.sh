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
#   - the vocabulary counts the multiplier in calls ("15 times the calls") instead of tokens, or
#     calls the 15 times a market estimate instead of a measurement published by a provider;
#   - the vocabulary, the ADR template or a SKILL.md uses `not now` without "the condition that
#     reopens it", or language.md no longer keeps `not now` as it is in every language;
#   - the vocabulary lost a condition of the model gateway, the condition of the per-task ceiling
#     (any task that calls a model), or the condition of the loop circuit breaker (the model
#     chooses the next step), or a file counts retries as that condition;
#   - the vocabulary or a SKILL.md that supersedes an ADR edits more than "only the Status line";
#   - a SKILL.md that closes tasks lacks `Status: closed` for local tasks, or deletes the task file;
#   - a script in skills/*/scripts/ differs from its source in reference/scripts/, or a SKILL.md
#     runs a script its folder does not ship;
#   - the README leaves a skill out of "Its skills are ...", of the Skills table, or of the count
#     that `claude plugin details arch` lists;
#   - the vocabulary lost the score of an unproven technology (9, without the matrix), the levels
#     of the risk matrix (written the same way in the risk template), the condition of a retry (no
#     effect, or idempotent) or a condition of the queue with persisted work;
#   - language.md no longer keeps the risk terms and the Direction values as they are, or the
#     dossier index template lost the row of risk.md;
#   - to-risk lost a contract: the score from RiskScore.java, the agent not being an assessor,
#     high-risk only for 6 or 9 without mitigation in place, the retry of a call that ran tools,
#     unproven technology checked in every run with one question, no Next: line for a proof of
#     concept, one block per dimension, the area matched by its subject, or which tasks it closes;
#   - to-adr closes the high-risk task, or no longer sends the user to the code before to-risk;
#   - to-risk sends the user back to to-adr for a high-risk task an ADR already answers, or to-adr
#     no longer sends the user to the first of risk.md, autonomy.md and traces.md still pending,
#     then to check-architecture;
#   - to-adr sends the user to code a mitigation when the ADR stays `proposed`, instead of the
#     decision still open, or to-risk counts a high-risk task with only a `proposed` ADR as answered;
#   - the vocabulary says a guardrail retry repeats a response to correct it, instead of repeating
#     the call with or without the reason for the rejection;
#   - the vocabulary lost a limit only in the prompt not being a hard limit, the Permission values
#     (written the same way in the autonomy template), the rule for `autonomous`, or the condition
#     of the kill switch through an approval; language.md no longer keeps the autonomy terms and
#     the Permission values as they are; the dossier index template lost the row of autonomy.md;
#   - the vocabulary or to-autonomy lost the exception to a second approval (an action that only
#     delivers what a person already approved in the same request, with a hard limit that checks
#     the content), in the rule for `autonomous` or in the condition of the approval;
#   - to-autonomy lost a contract: the prompt limit, the rule for `autonomous`, who approved as an
#     identity, never assuming who approves, one kill switch per system, the rule of the decision
#     task, the required kill switch with a task, not scoring risk (description and body), or it
#     runs a script or closes a task; to-adr no longer matches the decision task by the subject,
#     or closes the gap task that asked for the ADR instead of reusing it for Compliance;
#   - the vocabulary or to-traces lost the namespace of the application for what gen_ai.* does not
#     name, the cost computed from the tokens, the content out of the telemetry by default, or the
#     durable record of an effectful action that sampling may drop; a file requires an older name
#     of the token attributes; the vocabulary lost the end of the task in the decision path; the
#     dossier index template lost the row of traces.md; language.md no longer keeps the results of
#     an architecture check, the types or the attribute names as they are;
#   - to-traces lost one gap task for the instrumentation, the older names as evidence, or the
#     reuse of a gap task opened by any skill, or it closes a task;
#   - check-architecture lost a contract: no runtime item Verified by reading the code, evidence
#     for Verified and for Violated, a gap task for a Verified item without a fitness function,
#     never running the next command, the reuse of a gap task opened by any skill, the
#     non-negotiables without a dossier, the Next: line only for a decision, never calling a
#     backend, the items of traces.md (each required row Structural, a row the code records also
#     Operational, the Trace of a Request and the Content as Structural items); or it closes a
#     task or writes the dossier index; the vocabulary lost the two checks of a required attribute.
#   - the vocabulary or to-characteristics says an output guardrail can only judge the whole
#     response (an accumulator lets it run on chunks), or lost "waits for the whole response by
#     default";
#   - setup-architecture recommends GitHub Issues without the write permission on the repository
#     (`gh repo view --json viewerPermission` with ADMIN, MAINTAIN or WRITE), or lost "say why"
#     when it recommends local files;
#   - setup-architecture lets the user keep the GitHub tracker without the labels (it creates them
#     only when confirmed, or lost what to do when the user declines them);
#   - language.md lost the rule for a command with no text of the user (the language the host is
#     set to answer in, otherwise one question with a recommendation);
#   - language.md, to-adr, to-risk or check-architecture reads the `Next: /arch:to-adr "..."` line
#     of a task back without matching it by the command (the word Next: is translated);
#   - setup-architecture recommends GitHub Issues with no word on the local tasks already open in
#     `.scratch/architecture/issues/`, or lost the offer to move them to GitHub Issues (and the
#     step that moves them) when the user still picks GitHub Issues;
#   - to-characteristics asks for the layout without the setup (one dossier or one per quantum),
#     instead of keeping one dossier with one section per quantum and suggesting
#     setup-architecture for one dossier per quantum.
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
# The 15 times is a measurement a provider published about its own multi-agent research system,
# not a market estimate: the vocabulary once said "a common market estimate".
if ! folded "$VOCABULARY" | grep -q 'a provider measured its own multi-agent research system'; then
  fail "reference/vocabulary.md no longer says that the 15 times is a measurement a provider published about its own system"
fi
for file in $(find "$PLUGIN_ROOT/reference" "$PLUGIN_ROOT/skills" "$PLUGIN_ROOT/README.md" -type f); do
  if folded "$file" | grep -qi 'market estimate'; then
    fail "${file#"$PLUGIN_ROOT"/} calls the 15 times a market estimate; it is a measurement published by a provider"
  fi
done
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
# `not now` stays as it is in every language: a run could write "agora não" or "adiado" in the ADR.
if ! folded "$PLUGIN_ROOT/reference/language.md" | grep -q 'The term `not now`, for a part of a decision postponed on purpose: write it as it is, in every language'; then
  fail "reference/language.md no longer keeps \`not now\` as it is, in every language"
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
# The per-task ceiling always applies: the vocabulary once gave it no condition, and the model had
# to guess it.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q 'Condition: any task that calls a model; the ceiling always applies'; then
  fail "reference/vocabulary.md lost the condition of the per-task ceiling (\"any task that calls a model; the ceiling always applies\")"
fi
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

# Risk assessment (to-risk). Each rule below is a sentence a reviewer asked for when the skill was
# built; losing one changes what the skill writes.
TO_RISK="$PLUGIN_ROOT/skills/to-risk/SKILL.md"
TO_ADR="$PLUGIN_ROOT/skills/to-adr/SKILL.md"
RISK_TEMPLATE="$PLUGIN_ROOT/reference/templates/risk.md"
LEVELS='1–2 low, 3–4 medium, 6–9 high'
# A technology unknown to the team scores 9 without the matrix.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q 'unknown to the team. It always scores 9, without the matrix'; then
  fail "reference/vocabulary.md no longer says that a technology unknown to the team always scores 9, without the matrix"
fi
# The levels of the risk matrix, written the same way in the vocabulary and in the template.
for file in "$VOCABULARY" "$RISK_TEMPLATE"; do
  if [ ! -f "$file" ] || ! folded "$file" | grep -q "$LEVELS"; then
    fail "${file#"$PLUGIN_ROOT"/} lost the levels of the risk matrix (\"$LEVELS\")"
  fi
done
# The condition of a retry: a call with no effect, or an idempotent one.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q 'on a call with no effect, or on an idempotent one'; then
  fail "reference/vocabulary.md lost the condition of a retry (\"on a call with no effect, or on an idempotent one\")"
fi
# The three conditions of a queue with persisted work.
for condition in 'does not need the answer at once' 'cannot be lost in a crash' 'past the rate limit of the provider'; do
  if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -q "$condition"; then
    fail "reference/vocabulary.md lost a condition of the queue with persisted work (\"$condition\")"
  fi
done
# The risk terms stay as they are in every language.
LANGUAGE_TEXT=$(folded "$PLUGIN_ROOT/reference/language.md")
if ! printf '%s\n' "$LANGUAGE_TEXT" | grep -q 'The terms risk storming, effectful retry, unproven technology and `single-assessor`, the Direction values (`new`, `improving`, `stable`, `worsening`)'; then
  fail "reference/language.md no longer keeps risk storming, effectful retry, unproven technology, single-assessor and the Direction values as they are"
fi
# The dossier index has the row of the risk assessment.
if ! grep -q '^| \[Risk\](risk.md) | pending | |$' "$PLUGIN_ROOT/reference/templates/dossier-readme.md"; then
  fail "reference/templates/dossier-readme.md lost the row that links to risk.md"
fi
# One block per dimension in the template.
if [ -f "$RISK_TEMPLATE" ] && ! folded "$RISK_TEMPLATE" | grep -q 'replaces only the block of that dimension'; then
  fail "reference/templates/risk.md lost \"replaces only the block of that dimension\""
fi
if [ -f "$TO_RISK" ]; then
  TO_RISK_TEXT=$(folded "$TO_RISK")
  for phrase in \
      'scripts/RiskScore.java <impact> <likelihood>' \
      'is not an assessor' \
      '6 or 9 without mitigation in place' \
      'a call that ran tools' \
      'Every technology an area depends on is checked for unproven technology in every run, whatever the dimension' \
      'A proof of concept or spike is not an architecture decision and gets no Next: line' \
      'Ask one question about it: list only the technologies the build or the README marks as beta or pre-release' \
      'replaces only the block of that dimension' \
      'Match an area of the previous block by its subject' \
      'Close only high-risk tasks of the dimension assessed in this run'; do
    if ! printf '%s\n' "$TO_RISK_TEXT" | grep -qF "$phrase"; then
      fail "skills/to-risk/SKILL.md lost \"$phrase\""
    fi
  done
  if printf '%s\n' "$TO_RISK_TEXT" | grep -qiE '(4|four) or more'; then
    fail "skills/to-risk/SKILL.md opens high-risk tasks below 6"
  fi
fi
# to-adr keeps the high-risk task open, and sends the user to the code before to-risk.
TO_ADR_TEXT=$(folded "$TO_ADR")
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -q 'keep the high-risk task open'; then
  fail "skills/to-adr/SKILL.md no longer says \"keep the high-risk task open\""
fi
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -q 'put the mitigation in the code, then /arch:to-risk'; then
  fail "skills/to-adr/SKILL.md no longer ends with \"put the mitigation in the code, then /arch:to-risk\" when the ADR answers a high-risk task"
fi
# The chain moves on after an ADR. to-risk does not send the user back to to-adr for a high-risk
# task that an ADR already answers (the loop between to-risk and to-adr), and to-adr sends the user
# to the first document still pending, then to check-architecture (a fixed return to to-risk).
if [ -f "$TO_RISK" ] && ! printf '%s\n' "$TO_RISK_TEXT" | grep -qF 'is answered: its `Next:` line does not count'; then
  fail "skills/to-risk/SKILL.md lost \"is answered: its \`Next:\` line does not count\" (the loop between to-risk and to-adr comes back)"
fi
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -qF 'the first of `risk.md`, `autonomy.md` and `traces.md`, in this order, that is `pending` or missing in the dossier index'; then
  fail "skills/to-adr/SKILL.md no longer sends the user to the first of risk.md, autonomy.md and traces.md still pending"
fi
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -qF 'end with `Next: /arch:check-architecture'; then
  fail "skills/to-adr/SKILL.md no longer ends with \"Next: /arch:check-architecture\" when the dossier documents are written"
fi
# An ADR that stays `proposed` decided no mitigation. to-adr sends the user to the decision still
# open, never to the code; to-risk counts as answered only a high-risk task with an `accepted` ADR,
# and a task with only a `proposed` ADR neither sends the user back to to-adr (the loop) nor
# disappears from the chain (validation of 31/10, aula 7: the student who answers "later").
for phrase in \
    'If the ADR stays `proposed`, end with the decision task and what is missing to decide' \
    '`Next: decide <what is missing> (<decision task>), then /arch:to-adr "<decision>"' \
    'If the ADR is `accepted` and answers a `high-risk` task, end with'; do
  if ! printf '%s\n' "$TO_ADR_TEXT" | grep -qF "$phrase"; then
    fail "skills/to-adr/SKILL.md lost \"$phrase\" (a proposed ADR sends the user to code a mitigation nobody decided)"
  fi
done
if printf '%s\n' "$TO_ADR_TEXT" | grep -qF -- '- If the ADR answers a `high-risk` task, end with'; then
  fail "skills/to-adr/SKILL.md sends the user to the code for any ADR that answers a high-risk task, proposed included"
fi
if [ -f "$TO_RISK" ]; then
  for phrase in \
      'A `high-risk` task that already links an `accepted` ADR' \
      'A `high-risk` task that links only a `proposed` ADR is not answered, and its `Next:` line does not count either' \
      'say in one line that its decision is still pending'; do
    if ! printf '%s\n' "$TO_RISK_TEXT" | grep -qF "$phrase"; then
      fail "skills/to-risk/SKILL.md lost \"$phrase\" (a proposed ADR counts as an answer, or the loop with to-adr comes back)"
    fi
  done
  if printf '%s\n' "$TO_RISK_TEXT" | grep -qF 'A `high-risk` task that already links an ADR'; then
    fail "skills/to-risk/SKILL.md counts a high-risk task with any ADR as answered, proposed included"
  fi
fi
# A guardrail retry repeats the call, with or without the reason for the rejection (retry and
# reprompt, the canonical pair of the guardrails module); it never "repeats a response to correct
# it" (validation of 31/10, aula 4).
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'A **guardrail retry** repeats the call after a response came and was rejected, with or without the reason for the rejection'; then
  fail "reference/vocabulary.md lost \"a guardrail retry repeats the call ... with or without the reason for the rejection\""
fi
if printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'repeats a response that came and was rejected, to correct it'; then
  fail "reference/vocabulary.md says a guardrail retry repeats a response to correct it (the guardrails module: a retry without the reason corrects nothing)"
fi

# Autonomy (to-autonomy). Each rule below is a sentence a reviewer asked for when the skill was
# built; losing one changes what the skill decides or writes.
TO_AUTONOMY="$PLUGIN_ROOT/skills/to-autonomy/SKILL.md"
AUTONOMY_TEMPLATE="$PLUGIN_ROOT/reference/templates/autonomy.md"
PERMISSION_VALUES='`forbidden`, `propose`, `with-approval`, `autonomous`'
PROMPT_LIMIT='A limit that only the prompt states is not a hard limit'
AUTONOMOUS_RULE='Recommend `autonomous` only when the blast radius is small and the effect can be undone'
# A limit only in the prompt is not a hard limit (a ceiling only in the system message would pass).
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF "$PROMPT_LIMIT"; then
  fail "reference/vocabulary.md lost \"$PROMPT_LIMIT\""
fi
# The four Permission values, written the same way in the vocabulary and in the template.
for file in "$VOCABULARY" "$AUTONOMY_TEMPLATE"; do
  if [ ! -f "$file" ] || ! folded "$file" | grep -qF "$PERMISSION_VALUES"; then
    fail "${file#"$PLUGIN_ROOT"/} lost the Permission values (\"$PERMISSION_VALUES\")"
  fi
done
# autonomous only with a small blast radius and an effect that can be undone.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF "$AUTONOMOUS_RULE"; then
  fail "reference/vocabulary.md lost \"$AUTONOMOUS_RULE\""
fi
# The one exception: an action that only delivers what a person already approved in the same
# request, with a hard limit that checks the content against the approval. Without it, the skill
# asks a second approval of the same decision (the rubber stamp the vocabulary condemns; the reply
# to the seller in aprovacao-desconto came out propose; validation of 31/10, round 7). Without the
# hard limit, the exception would let the model change what was approved.
RUBBER_STAMP_RULE='when the action only delivers to the person who made the request what a person already approved in the same request, and a hard limit in the code checks that the content matches the approval, recommend `autonomous` within that hard limit and say why: a second approval of the same decision becomes a rubber stamp'
APPROVAL_EXCEPTION='an effect that cannot be undone, unless the action only delivers what a person already approved in the same request'
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF "$RUBBER_STAMP_RULE"; then
  fail "reference/vocabulary.md lost the exception to a second approval (\"$RUBBER_STAMP_RULE\")"
fi
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF "$APPROVAL_EXCEPTION"; then
  fail "reference/vocabulary.md lost the exception in the condition of the approval (\"$APPROVAL_EXCEPTION\")"
fi
# The kill switch also holds when a model output leads to an effectful action through an approval
# (a workflow with an approval would get no kill switch).
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'or a model output leads to an effectful action, even through an approval'; then
  fail "reference/vocabulary.md lost the condition of the kill switch (\"a model output leads to an effectful action, even through an approval\")"
fi
# The autonomy terms and the Permission values stay as they are in every language.
if ! printf '%s\n' "$LANGUAGE_TEXT" | grep -qF "The terms blast radius, kill switch and least agency, and the Permission values ($PERMISSION_VALUES)"; then
  fail "reference/language.md no longer keeps blast radius, kill switch, least agency and the Permission values as they are"
fi
# The dossier index has the row of the autonomy file.
if ! grep -q '^| \[Autonomy\](autonomy.md) | pending | |$' "$PLUGIN_ROOT/reference/templates/dossier-readme.md"; then
  fail "reference/templates/dossier-readme.md lost the row that links to autonomy.md"
fi
if [ -f "$TO_AUTONOMY" ]; then
  TO_AUTONOMY_TEXT=$(folded "$TO_AUTONOMY")
  for phrase in \
      "$PROMPT_LIMIT" \
      "$AUTONOMOUS_RULE" \
      "$RUBBER_STAMP_RULE" \
      "$APPROVAL_EXCEPTION" \
      'Who approved is an identity too: an approval that does not record who approved proves nothing' \
      'Never assume who approves: ask' \
      'One kill switch per system: record it once, and say in each action whether it stops that action' \
      'Open one decision task per effectful action without an accepted ADR whose Approval, Idempotency or Hard Limits are not in the code' \
      'A required kill switch that is not in the code goes to the decision task of the action with the largest blast radius, opened for it when there is none' \
      'It does not score risk'; do
    if ! printf '%s\n' "$TO_AUTONOMY_TEXT" | grep -qF "$phrase"; then
      fail "skills/to-autonomy/SKILL.md lost \"$phrase\""
    fi
  done
  if ! awk 'NR == 1 { next } /^---$/ { exit } { print }' "$TO_AUTONOMY" | grep -q '^description: .*Does not score risk\.$'; then
    fail "skills/to-autonomy/SKILL.md description lost \"Does not score risk.\""
  fi
  # to-autonomy decides what the agent may do; it never scores risk with the risk matrix script.
  if grep -q 'RiskScore' "$TO_AUTONOMY" || grep -q 'scripts/[A-Za-z0-9_.-]*\.java' "$TO_AUTONOMY"; then
    fail "skills/to-autonomy/SKILL.md runs a script (\"RiskScore\" or scripts/*.java); it does not score risk"
  fi
  # The decision task closes in to-adr, when its ADR is accepted.
  if grep -q 'gh issue close' "$TO_AUTONOMY"; then
    fail "skills/to-autonomy/SKILL.md closes a task; the decision task closes in to-adr"
  fi
fi
# to-adr matches the decision task by its Next: line, by the subject (the decision typed in other
# words would leave the task open for ever).
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -qF 'the open `decision` task whose `Next: /arch:to-adr "..."` line holds the same decision, matched by the subject and not by the exact text'; then
  fail "skills/to-adr/SKILL.md no longer matches the decision task by its Next: line, by the subject"
fi
# to-adr reuses the gap task that asked for the ADR as the gap task of Compliance (closing it and
# opening another for the same check leaves the subject open under a new task).
if ! printf '%s\n' "$TO_ADR_TEXT" | grep -qF 'A `gap` task that asked for this ADR with a `Next: /arch:to-adr` line stays open and becomes the `gap` task of Compliance'; then
  fail "skills/to-adr/SKILL.md no longer reuses the gap task that asked for the ADR as the gap task of Compliance"
fi
if printf '%s\n' "$TO_ADR_TEXT" | grep -qF 'asked for this ADR with a `Next: /arch:to-adr` line is closed'; then
  fail "skills/to-adr/SKILL.md closes the gap task that asked for the ADR and opens another for the same check"
fi

# Traceability (to-traces) and the architecture check (check-architecture). Each rule below is a
# sentence a reviewer asked for when the skills were built; losing one changes what the skill
# records or how it judges an item.
TO_TRACES="$PLUGIN_ROOT/skills/to-traces/SKILL.md"
CHECK_ARCH="$PLUGIN_ROOT/skills/check-architecture/SKILL.md"
TRACES_TEMPLATE="$PLUGIN_ROOT/reference/templates/traces.md"
NAMESPACE_RULE='The `gen_ai.*` conventions do not name the user, the tenant, the approver or the reason of a step: these go in the namespace of the application'
COST_RULE='the cost is computed from them and the price, never recorded as the attribute of cost'
CONTENT_RULE='Prompt and response content stays out of the telemetry unless the user decides otherwise'
SAMPLING_RULE='Sampling may drop a trace: an effectful action also goes to a durable record, tied to the trace by its trace id'
REUSE_RULE='look for an open one with the gap label and the same subject, whichever skill opened it, and reuse it'
# The vocabulary and to-traces carry the rules on namespace, cost, content and sampling.
for rule in "$NAMESPACE_RULE" "$COST_RULE" "$CONTENT_RULE" "$SAMPLING_RULE"; do
  for file in "$VOCABULARY" "$TO_TRACES"; do
    if [ ! -f "$file" ] || ! folded "$file" | grep -qiF "$rule"; then
      fail "${file#"$PLUGIN_ROOT"/} lost \"$rule\""
    fi
  done
done
# The current names of the token attributes. The older names (prompt_tokens, completion_tokens)
# are what some stacks still write: evidence for the Stack Reference, never the name to require.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF '`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`'; then
  fail "reference/vocabulary.md lost the current names of the token attributes (gen_ai.usage.input_tokens, gen_ai.usage.output_tokens)"
fi
if [ ! -f "$TRACES_TEMPLATE" ] || ! grep -qF '`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`' "$TRACES_TEMPLATE"; then
  fail "reference/templates/traces.md lost the current names of the token attributes"
fi
for file in $(find "$PLUGIN_ROOT/reference" "$PLUGIN_ROOT/skills" "$PLUGIN_ROOT/README.md" -type f); do
  if grep -qE 'gen_ai\.usage\.(prompt|completion)_tokens' "$file"; then
    fail "${file#"$PLUGIN_ROOT"/} requires an older name of the token attributes: $(grep -nE 'gen_ai\.usage\.(prompt|completion)_tokens' "$file" | head -1)"
  fi
done
# The decision path includes why the task ended: a limit of steps reached would look like a success.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'the end of the task, and why it ended'; then
  fail "reference/vocabulary.md lost the end of the task in what a trace must carry (\"the end of the task, and why it ended\")"
fi
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'why each ran, and why the task ended'; then
  fail "reference/vocabulary.md lost the end of the task in the decision path (\"why each ran, and why the task ended\")"
fi
# A required attribute is checked in the code (Structural) and, when the code records it, at
# runtime (Operational): without the second, a check of traces never says which telemetry is missing.
if ! printf '%s\n' "$VOCABULARY_TEXT" | grep -qF 'A required attribute is checked twice: in the code, whether the code records it (Structural); and, when the code records it, at runtime, whether it shows in the traces (Operational'; then
  fail "reference/vocabulary.md lost the two checks of a required attribute (Structural in the code, Operational at runtime)"
fi
# The dossier index has the row of the traces.
if ! grep -q '^| \[Traces\](traces.md) | pending | |$' "$PLUGIN_ROOT/reference/templates/dossier-readme.md"; then
  fail "reference/templates/dossier-readme.md lost the row that links to traces.md"
fi
# The results, the attribute names and the types stay as they are in every language.
if ! printf '%s\n' "$LANGUAGE_TEXT" | grep -qF 'the results of an architecture check (`Verified`, `Violated`, `Not Verified (needs telemetry)`) and the types Operational, Structural and Process'; then
  fail "reference/language.md no longer keeps the results of an architecture check and the types as they are"
fi
if ! printf '%s\n' "$LANGUAGE_TEXT" | grep -qF 'The names of attributes and spans (`gen_ai.*`, `enduser.id`'; then
  fail "reference/language.md no longer keeps the names of attributes and spans as they are"
fi
if [ -f "$TO_TRACES" ]; then
  TO_TRACES_TEXT=$(folded "$TO_TRACES")
  for phrase in \
      'When the system emits no trace at all, open one gap task for the instrumentation, with every required attribute in its body, not one task per attribute' \
      'a name the stack still writes in an older form is evidence, recorded in the Stack Reference' \
      "$REUSE_RULE"; do
    if ! printf '%s\n' "$TO_TRACES_TEXT" | grep -qF "$phrase"; then
      fail "skills/to-traces/SKILL.md lost \"$phrase\""
    fi
  done
  if grep -q 'gh issue close' "$TO_TRACES"; then
    fail "skills/to-traces/SKILL.md closes a task; it only opens or reuses gap tasks"
  fi
fi
if [ -f "$CHECK_ARCH" ]; then
  CHECK_ARCH_TEXT=$(folded "$CHECK_ARCH")
  for phrase in \
      'Never mark a runtime item Verified by reading the code: without telemetry, it is Not Verified (needs telemetry)' \
      'Violated needs evidence too' \
      'Verified needs evidence' \
      'A Verified item without a fitness function gets a gap task' \
      'State the next command; never run it' \
      "$REUSE_RULE" \
      'Without a dossier, check the non-negotiables of an agent in production: hard limits, the log of the decision path, and the kill switch' \
      'Add a `Next: /arch:to-adr` line only when closing the gap needs an architecture decision; a missing test does not' \
      'Never call a backend on your own' \
      'what the Trace of a Request section says is in the code today' \
      'each part the Trace of a Request section says is in the code is a Structural item' \
      'each required row (its condition met) is a Structural item' \
      'each required row that the code records is also an Operational item' \
      'the decision of the Content section is a Structural item'; do
    if ! printf '%s\n' "$CHECK_ARCH_TEXT" | grep -qF "$phrase"; then
      fail "skills/check-architecture/SKILL.md lost \"$phrase\""
    fi
  done
  # The check never closes a task: the team closes it, from Tasks That Can Be Closed.
  if grep -q 'gh issue close' "$CHECK_ARCH" || grep -q 'Status: closed' "$CHECK_ARCH"; then
    fail "skills/check-architecture/SKILL.md closes a task; it lists them in Tasks That Can Be Closed"
  fi
  # The check report is not a deliverable of the dossier: the skill never writes the index.
  if grep -q 'references/dossier-readme.md' "$CHECK_ARCH"; then
    fail "skills/check-architecture/SKILL.md writes the dossier index; the check report stays out of it"
  fi
fi

# An output guardrail waits for the whole response by default; with an accumulator it runs on each
# chunk, so "can only judge the whole response" is false (Quarkus LangChain4j, streamed responses).
TO_CHARACTERISTICS="$PLUGIN_ROOT/skills/to-characteristics/SKILL.md"
for file in "$VOCABULARY" "$TO_CHARACTERISTICS"; do
  [ -f "$file" ] || continue
  if ! folded "$file" | grep -qF 'an output guardrail waits for the whole response by default'; then
    fail "${file#"$PLUGIN_ROOT"/} lost \"an output guardrail waits for the whole response by default\""
  fi
done
for file in $RULE_FILES; do
  if folded "$file" | grep -qiF 'can only judge the whole response'; then
    fail "${file#"$PLUGIN_ROOT"/} says an output guardrail can only judge the whole response"
  fi
done
# Without the setup, a project with more than one deployable got the question "one dossier or one
# per quantum", and no rule said where to write one per quantum: the next skills went back to the
# single-dossier defaults, and the index and the documents split (validation of 31/10, round 8).
if [ -f "$TO_CHARACTERISTICS" ]; then
  TO_CHARACTERISTICS_TEXT=$(folded "$TO_CHARACTERISTICS")
  for phrase in \
      'Without the setup, keep one dossier with one section per quantum, without asking: say so in one line, and suggest `/arch:setup-architecture` (Claude Code) or `$arch:setup-architecture` (Codex) for one dossier per quantum.'; do
    if ! printf '%s\n' "$TO_CHARACTERISTICS_TEXT" | grep -qF "$phrase"; then
      fail "skills/to-characteristics/SKILL.md lost \"$phrase\""
    fi
  done
  if printf '%s\n' "$TO_CHARACTERISTICS_TEXT" | grep -qiF 'ask whether to keep one dossier'; then
    fail "skills/to-characteristics/SKILL.md asks for the layout without the setup; it keeps one dossier with one section per quantum"
  fi
fi
# The setup recommends GitHub Issues only when the user can write to the repository: in a clone of
# someone else's public repository, labels and tasks would fail, or land there.
SETUP_ARCH="$PLUGIN_ROOT/skills/setup-architecture/SKILL.md"
if [ -f "$SETUP_ARCH" ]; then
  SETUP_ARCH_TEXT=$(folded "$SETUP_ARCH")
  for phrase in \
      '`gh repo view --json viewerPermission` gives ADMIN, MAINTAIN or WRITE' \
      'otherwise recommend local files, and say why' \
      'If the user declines, say in one line that every architecture skill opens GitHub Issues with these labels and cannot do it without them, and recommend the local tracker instead; ask again question A with that recommendation' \
      'With the GitHub tracker, the labels are always created' \
      'When `.scratch/architecture/issues/` already holds tasks, say how many are open, and recommend keeping local files: the dossier links them and every skill reads only the chosen tracker' \
      'If the user still picks GitHub Issues, offer once, with your recommendation (yes), to move each open task to a GitHub Issue with the same title, label and body, mark the local file `Status: closed` with a line linking the new issue, and update the links in the dossier documents; list the moves in the draft of step 4' \
      'When the user accepted the move of the local tasks (question A), after the labels: for each open task, `gh issue create` with its title, label and body'; do
    if ! printf '%s\n' "$SETUP_ARCH_TEXT" | grep -qF "$phrase"; then
      fail "skills/setup-architecture/SKILL.md lost \"$phrase\""
    fi
  done
  # Local tasks already open when the setup switches to GitHub Issues (a student who followed the
  # lessons without the setup, then runs it as every skill suggests): every skill then reads only
  # GitHub, the gap, decision and high-risk tasks drop out of the chain, check-architecture opens
  # duplicates, and the dossier links point to files no skill reads (validation of 31/10, round 6).
  # GitHub tracker without the labels: every task the skills open with --label fails in gh
  # ("could not add label"), or the agent improvises (validation of 31/10, round 3).
  if printf '%s\n' "$SETUP_ARCH_TEXT" | grep -qF 'With the GitHub tracker and labels confirmed'; then
    fail "skills/setup-architecture/SKILL.md creates the labels only when the user confirms them; with the GitHub tracker they are always created"
  fi
fi
# A command with no text of the user (alone, or with a path) leaves no language to follow but the
# host setting: without the rule, a student with no language setting gets everything in the
# language of the SKILL.md (validation of 31/10, round 3).
if ! printf '%s\n' "$LANGUAGE_TEXT" | grep -qF 'When the user has written nothing yet but the command (alone, or with a path), use the language the host is set to answer in; with no such setting, ask once which language to use, with your recommendation (the language of the project'"'"'s README), before the first summary'; then
  fail "reference/language.md lost the rule for a command with no text of the user (the host setting, otherwise one question with the README language as the recommendation)"
fi
# The Next: line of a task is translated with the fixed sentences (Próximo: in Portuguese), and the
# skills that read it back looked for the literal "Next:": to-adr did not list the task as a
# candidate, to-risk skipped the ADR of the mitigation (validation of 31/10, round 4).
NEXT_MATCH='match it by the command, not by the word before it'
for file in "$PLUGIN_ROOT/reference/language.md" "$TO_ADR" "$TO_RISK" "$CHECK_ARCH"; do
  [ -f "$file" ] || continue
  if ! folded "$file" | grep -qF "$NEXT_MATCH"; then
    fail "${file#"$PLUGIN_ROOT"/} reads the Next: /arch:to-adr line back without \"$NEXT_MATCH\" (the line is translated)"
  fi
done

# With the per-quantum layout, only the setup, the grill and to-characteristics knew the quantum
# folders: to-adr, to-risk, to-autonomy, to-traces and check-architecture read and wrote
# docs/architecture/ and created a second index at its root, with the brief and the characteristics
# in pending; check-architecture did not know which dossier to check (order-hub, two deployables;
# validation of 31/10, round 9).
PER_QUANTUM_ASK='With the per-quantum layout, ask which quantum this run is for, in the first round, with your recommendation (the path given, or the deployable the input names), and read and write that quantum'"'"'s folder: `docs/architecture/<quantum>/` for brief, characteristics, risk, autonomy, traces, check-report and its `README.md`; ADRs stay in `docs/adr/` and tasks in the tracker.'
for file in "$TO_ADR" "$TO_RISK" "$TO_AUTONOMY" "$TO_TRACES" "$CHECK_ARCH"; do
  [ -f "$file" ] || continue
  if ! folded "$file" | grep -qF "$PER_QUANTUM_ASK"; then
    fail "${file#"$PLUGIN_ROOT"/} lost the per-quantum rule (ask the quantum, read and write its folder)"
  fi
done
# The per-quantum bullet sits between "Read docs/agents/architecture.md" and "Without it, the setup
# has not run": the nearest antecedent of "it" became the per-quantum layout, and an agent reading
# it literally took "no per-quantum layout" for "no setup", writing the tasks of a student with the
# single layout and GitHub Issues to .scratch (validation of 31/10, round 10). Name the file.
SETUP_MISSING='- Without `docs/agents/architecture.md`, the setup has not run. Use the defaults:'
for file in "$TO_ADR" "$TO_RISK" "$TO_AUTONOMY" "$TO_TRACES" "$CHECK_ARCH"; do
  [ -f "$file" ] || continue
  if ! folded "$file" | grep -qF -- "$SETUP_MISSING"; then
    fail "${file#"$PLUGIN_ROOT"/} does not name docs/agents/architecture.md in \"Without ..., the setup has not run\" (after the per-quantum bullet, \"it\" means the layout)"
  fi
  if folded "$file" | grep -qF 'Without it, the setup has not run'; then
    fail "${file#"$PLUGIN_ROOT"/} still has \"Without it, the setup has not run\" (the antecedent of it is the per-quantum layout)"
  fi
done
GRILL_ARCH="$PLUGIN_ROOT/skills/grill-architecture/SKILL.md"
if [ -f "$GRILL_ARCH" ] && ! folded "$GRILL_ARCH" | grep -qF 'in `docs/architecture/README.md` (the `README.md` of the quantum folder, with the per-quantum layout'; then
  fail "skills/grill-architecture/SKILL.md updates the root index with the per-quantum layout (the index is the README.md of the quantum folder)"
fi
# The grill asked the quantum with no recommendation and outside a round, while the other skills of
# the dossier ask it in the first round with a recommendation, and the grill itself promises a
# recommendation for every question (validation of 31/10, round 10).
GRILL_QUANTUM_ASK='With the per-quantum layout, ask which quantum this brief is for, in the first round, with your recommendation (the path given, or the deployable the input names), and write under its folder; ADRs stay in `docs/adr/` and tasks in the tracker.'
if [ -f "$GRILL_ARCH" ] && ! folded "$GRILL_ARCH" | grep -qF "$GRILL_QUANTUM_ASK"; then
  fail "skills/grill-architecture/SKILL.md asks the quantum without \"in the first round, with your recommendation\""
fi
AGENTS_ARCH="$PLUGIN_ROOT/reference/templates/agents-architecture.md"
if [ -f "$AGENTS_ARCH" ] && ! folded "$AGENTS_ARCH" | grep -qF 'Each quantum folder holds the same documents: README.md, brief.md, characteristics.md, risk.md, autonomy.md, traces.md and check-report.md'; then
  fail "reference/templates/agents-architecture.md does not say what each quantum folder holds"
fi

if ! "$PLUGIN_ROOT/scripts/sync-references.sh" --check > /dev/null; then
  fail "references out of sync with reference/ (run scripts/sync-references.sh)"
fi

if [ "$STATUS" -eq 0 ]; then
  echo "language rule and skill contracts in place"
fi
exit "$STATUS"
