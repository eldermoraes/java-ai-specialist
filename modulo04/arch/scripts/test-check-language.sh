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

# 12. A limit in the configuration back in the list of fitness functions, with no test to exercise
#     it (the skill would accept max-retries=2 alone and skip the gap task).
fresh_copy config-limit-in-skill
edit 's/a metric with an alert, an architecture$/a metric with an alert, a limit in the configuration, an architecture/' \
  "$COPY/skills/to-characteristics/SKILL.md"
expect_fail config-limit-in-skill

# 13. The template accepts a configuration limit without the test that exercises it.
fresh_copy config-limit-in-template
edit 's/, and a configuration limit only through the test that exercises it//; s/architecture rule or CI rule/configuration limit or CI rule/' \
  "$COPY/reference/templates/characteristics.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail config-limit-in-template

# 14. The vocabulary lost the rule on configuration limits.
fresh_copy config-limit-rule-gone
edit 's/A limit in the configuration is not one by itself either: it implements the/It implements the/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail config-limit-rule-gone

# 15. The quality of the response dropped because a guardrail checks it (that reason would drop
#     confidentiality too).
fresh_copy quality-by-guardrail
edit 's/out of the scope of this dossier: it is measured by evals, not by/a guardrail checks it on every request; it stays out of the dossier, not/' \
  "$COPY/skills/to-characteristics/SKILL.md"
expect_fail quality-by-guardrail

# 16. One deployable per Dockerfile in the setup.
fresh_copy deployable-per-dockerfile
edit 's/one app per folder in a monorepo). Several/one `Dockerfile` per service, one app per folder in a monorepo). Several/' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail deployable-per-dockerfile

# 17. The README promises a quantum question with a single deployable.
fresh_copy readme-quanta-always
edit 's/confirm the quanta (asked only when there is more than one$/confirm the quanta (more than one/' \
  "$COPY/README.md"
expect_fail readme-quanta-always

# 18. No name in the build file and no question: the skill would invent the name or take it from
#     the README.
fresh_copy name-without-question
edit 's/ask the user which name to use$/use the name of the directory/' \
  "$COPY/skills/to-characteristics/SKILL.md"
expect_fail name-without-question

# 19. The README title back as the source of the name.
fresh_copy name-from-readme
edit 's/The system name is the name the build file declares (Maven `<name>`, otherwise/The system name is the title of the README; without one, the name the build file declares (Maven `<name>`, otherwise/' \
  "$COPY/reference/templates/characteristics.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail name-from-readme

# 20. The template title back in Portuguese (a run in Portuguese once wrote "Características de
#     arquitetura: support-desk").
fresh_copy title-in-portuguese
edit '1s/^# Architecture Characteristics:/# Características de arquitetura:/' \
  "$COPY/reference/templates/characteristics.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail title-in-portuguese

# 21. language.md lost the rule that keeps the term: the agent translates the title again.
fresh_copy term-rule-gone
edit '/^- The term \*\*architecture characteristic\*\*/,/^  language\.$/d' "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail term-rule-gone

# 22. The skill tells the agent to translate the title.
fresh_copy skill-translates-title
edit 's/^included, following \[references\/language.md\](references\/language.md). Keep the term architecture$/included, following [references\/language.md](references\/language.md). Translate the term architecture/' \
  "$COPY/skills/to-characteristics/SKILL.md"
expect_fail skill-translates-title

# 23. The dossier index template translates the row label.
fresh_copy dossier-label-translated
edit 's/, and the label "Architecture Characteristics" as it is, in every language//' \
  "$COPY/reference/templates/dossier-readme.md"
edit 's/(language.md). Leave/Leave/' "$COPY/reference/templates/dossier-readme.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail dossier-label-translated

# 24. The multiplier back in calls (the vocabulary said "about 15 times the calls of a single chat
#     interaction"; the number the course uses is tokens).
fresh_copy multiplier-in-calls
edit 's/15 times the tokens of a single-agent chat/15 times the calls of a single chat interaction/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail multiplier-in-calls

# 25. to-adr records not now without the condition that reopens it.
fresh_copy not-now-without-condition
edit 's/reopens it/waits for it/g' "$COPY/skills/to-adr/SKILL.md"
expect_fail not-now-without-condition

# 26. The ADR template lost the condition of each part.
fresh_copy adr-template-without-condition
edit 's/reopens it/waits for it/g' \
  "$COPY/reference/templates/adr.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail adr-template-without-condition

# 27. The model gateway lost one of its three conditions.
fresh_copy gateway-condition-gone
edit 's/Condition: more than one model, per-user quota, or keys kept out of the application/Condition: more than one model or per-user quota/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail gateway-condition-gone

# 28. Retries back as a condition of the loop circuit breaker (a project with max-retries would get
#     a breaker it does not need).
fresh_copy breaker-on-retries
edit 's/Condition: the model chooses the next step (a supervisor, a loop agent, tool calling)\./Condition: the model chooses the next step (a supervisor, a loop agent, tool calling, retries)./' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail breaker-on-retries

# 29. to-adr edits an accepted ADR beyond its Status line.
fresh_copy supersede-edits-old-adr
edit 's/change only the Status line of$/update the old ADR, starting with the Status line of/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail supersede-edits-old-adr

# 30. to-adr deletes the local task instead of closing it.
fresh_copy close-by-delete
edit 's/change its line to$/delete the file instead of changing its line to/; s/`Status: closed` and add a line with the path of the ADR; never delete the task file\./its old status./' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail close-by-delete

# 31. The copy of NextAdrNumber.java in to-adr drifts from the single source.
fresh_copy stale-script
drop_pair "to-adr:NextAdrNumber.java"
edit 's/%04d%n/%03d%n/' "$COPY/skills/to-adr/scripts/NextAdrNumber.java"
expect_fail stale-script

# 32. A skill runs the script without shipping it.
fresh_copy missing-script
drop_pair "to-adr:NextAdrNumber.java"
rm "$COPY/skills/to-adr/scripts/NextAdrNumber.java"
expect_fail missing-script

# 33. The README leaves to-adr out of the skills the agent installs.
fresh_copy readme-without-to-adr
edit 's/`setup-architecture`, `grill-architecture`, `to-characteristics` and `to-adr`\./`setup-architecture`, `grill-architecture` and `to-characteristics`./' \
  "$COPY/README.md"
expect_fail readme-without-to-adr

# 34. The README still says the plugin has three skills.
fresh_copy readme-old-count
edit 's/lists the four skills/lists the three skills/' "$COPY/README.md"
expect_fail readme-old-count

# 35. The 15 times back as a market estimate (the source is a measurement a provider published
#     about its own multi-agent research system).
fresh_copy multiplier-market-estimate
edit 's/a provider measured its own multi-agent research system spending/a common market estimate is that a multi-agent system spends/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail multiplier-market-estimate

# 36. The per-task ceiling without its condition (the model had to guess when the ceiling applies).
fresh_copy ceiling-without-condition
edit '/Condition: any task that calls a model; the ceiling always applies/d' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail ceiling-without-condition

# 37. language.md no longer keeps not now as it is (a run could write "agora não" in the ADR).
fresh_copy not-now-translated
edit '/^- The term `not now`, for a part of a decision postponed on purpose/d' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail not-now-translated

if [ "$STATUS" -eq 0 ]; then
  echo "every mutation caught"
fi
exit "$STATUS"
