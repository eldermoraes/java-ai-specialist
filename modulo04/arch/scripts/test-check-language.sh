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
edit 's/`to-characteristics`, `to-adr`, `to-risk`,$/`to-characteristics`, `to-risk`,/' \
  "$COPY/README.md"
expect_fail readme-without-to-adr

# 34. The README still says the plugin has three skills.
fresh_copy readme-old-count
edit 's/lists the eight skills/lists the three skills/' "$COPY/README.md"
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

# 38. The vocabulary lost the score of an unproven technology (the skill would put a technology the
#     team never used through the matrix).
fresh_copy unproven-score-gone
edit 's/It always scores 9, without the$/It is scored like any other area, with the/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail unproven-score-gone

# 39. A score of 4 back in the high level (the template and the vocabulary would disagree, and a
#     medium risk would open a high-risk task).
fresh_copy four-is-high
edit 's/3–4 medium, 6–9 high/3 medium, 4–9 high/' "$COPY/reference/templates/risk.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail four-is-high

# 40. to-risk lets the model multiply instead of running the script.
fresh_copy score-by-model
edit 's/^  java <this skill.s folder>\/scripts\/RiskScore.java <impact> <likelihood>$/  multiply impact by likelihood/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail score-by-model

# 41. to-risk counts the agent as an assessor.
fresh_copy agent-as-assessor
edit 's/The agent is not an assessor/The agent is an assessor/' "$COPY/skills/to-risk/SKILL.md"
expect_fail agent-as-assessor

# 42. to-risk opens high-risk tasks below 6.
fresh_copy high-risk-from-four
edit 's/risk of 6 or 9 without mitigation in place/risk of 4 or more without mitigation/g' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail high-risk-from-four

# 43. The retry lost its condition on effects (a project with a send tool would get a retry).
fresh_copy retry-without-effect-condition
edit 's/on a call with no effect, or on an idempotent one\./on any call./' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail retry-without-effect-condition

# 44. The Effectful Retry forgets the retry of a model call that already ran tools.
fresh_copy effectful-retry-without-tools
edit 's/retry of a call that ran tools (repeating/retry of a model call (repeating/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail effectful-retry-without-tools

# 45. language.md no longer keeps the risk terms as they are (a run could write "tecnologia não
#     comprovada" or "avaliador único").
fresh_copy risk-terms-translated
edit '/^- The terms risk storming, effectful retry, unproven technology and `single-assessor`, the$/d' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail risk-terms-translated

# 45b. unproven technology dropped from the terms kept as they are.
fresh_copy unproven-translated
edit 's/^- The terms risk storming, effectful retry, unproven technology and/- The terms risk storming, effectful retry and/' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail unproven-translated

# 46. The dossier index template lost the row of the risk assessment.
fresh_copy dossier-without-risk
edit '/^| \[Risk\](risk.md) | pending | |$/d' "$COPY/reference/templates/dossier-readme.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail dossier-without-risk

# 47. The queue lost the rate limit as a condition.
fresh_copy queue-without-rate-limit
edit 's/ or the volume goes past the rate limit of the provider\./. /' "$COPY/reference/vocabulary.md"
edit 's/^  a crash, or the volume goes past the rate limit of the provider\./  a crash./' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail queue-without-rate-limit

# 48. to-adr closes the high-risk task it answers (the risk would leave the board before the
#     mitigation reaches the code).
fresh_copy adr-closes-high-risk
edit 's/keep the high-risk task open/close the high-risk task/' "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-closes-high-risk

# 49. The README still says the plugin has four skills.
fresh_copy readme-four-skills
edit 's/lists the eight skills/lists the four skills/' "$COPY/README.md"
expect_fail readme-four-skills

# 50. The copy of RiskScore.java in to-risk drifts from the single source.
fresh_copy stale-risk-script
drop_pair "to-risk:RiskScore.java"
edit 's/score <= 4/score <= 5/' "$COPY/skills/to-risk/scripts/RiskScore.java"
expect_fail stale-risk-script

# 51. to-risk checks unproven technology only when it is the dimension (an availability run would
#     miss the 9 of a beta framework).
fresh_copy unproven-only-as-dimension
edit 's/^- Every technology an area depends on is checked for unproven technology in every run, whatever the$/- A technology may be unproven, when unproven technology is the/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail unproven-only-as-dimension

# 52. A proof of concept gets a Next: line (the mitigation of the 9 would take the place of the
#     architecture decision in the last line).
fresh_copy poc-with-next-line
edit 's/^  is not an architecture decision and gets no Next: line\.$/  gets its own line./' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail poc-with-next-line

# 53. to-adr sends the user straight to to-risk, before the mitigation is in the code.
fresh_copy adr-next-without-code
edit 's/Next: put the mitigation in the code, then \/arch:to-risk to close the high-risk task/Next: \/arch:to-risk/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-next-without-code

# 54. to-risk asks one question per technology (a project with many dependencies would get dozens).
fresh_copy one-question-per-technology
edit 's/^  knows\. Ask one question about it: list only the technologies the build or the README marks as$/  knows. Ask about each technology the build or the README lists, as/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail one-question-per-technology

# 55. to-risk rewrites the whole file on every run (a second dimension would erase the first).
fresh_copy one-block-for-all
edit 's/replaces only the block of that dimension; a new dimension adds a block after the last one, with$/rewrites the whole file, with/' \
  "$COPY/skills/to-risk/SKILL.md"
edit 's/^   replaces only the block of that dimension; a new dimension adds a block after the last one, with$/   rewrites the whole file, with/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail one-block-for-all

# 55b. to-risk matches the previous area by its exact name (every area of a rerun would be new).
fresh_copy area-by-exact-name
edit 's/Match an area of the$/Match an area of the previous block by its exact name; never/' \
  "$COPY/skills/to-risk/SKILL.md"
edit 's/^  previous block by its subject, not by its exact name:/  by its subject:/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail area-by-exact-name

# 56. to-autonomy lost the rule on limits only in the prompt (a ceiling only in the system message
#     would pass for a hard limit).
fresh_copy prompt-limit-gone
edit 's/A limit that only the prompt states is not a hard limit\. //' \
  "$COPY/skills/to-autonomy/SKILL.md"
expect_fail prompt-limit-gone

# 56b. The vocabulary lost the same rule.
fresh_copy prompt-limit-gone-vocabulary
edit 's/A limit that only the prompt states is not a hard limit\. //' "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail prompt-limit-gone-vocabulary

# 57. to-autonomy no longer records who approved (an approval without a name proves nothing).
fresh_copy approver-not-recorded
edit '/^- Who approved is an identity too/d' "$COPY/skills/to-autonomy/SKILL.md"
expect_fail approver-not-recorded

# 57b. to-autonomy assumes who approves instead of asking.
fresh_copy approver-assumed
edit 's/Never assume who approves: ask\. //' "$COPY/skills/to-autonomy/SKILL.md"
expect_fail approver-assumed

# 58. to-autonomy scores risk (the work of to-risk), in the description and in the body.
fresh_copy autonomy-scores-risk
edit 's/[Dd]oes not score risk/score each action with the risk matrix/g' \
  "$COPY/skills/to-autonomy/SKILL.md"
expect_fail autonomy-scores-risk

# 58b. to-autonomy runs the risk matrix script.
fresh_copy autonomy-runs-risk-script
echo "- run scripts/RiskScore.java for each action" >> "$COPY/skills/to-autonomy/SKILL.md"
expect_fail autonomy-runs-risk-script

# 59. The template writes a Permission value differently from the vocabulary.
fresh_copy permission-value-drift
edit 's/`with-approval`/`approval`/g' "$COPY/reference/templates/autonomy.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail permission-value-drift

# 60. to-autonomy recommends autonomous for an effect that cannot be undone.
fresh_copy autonomous-without-undo
edit 's/ and the effect can be undone//' "$COPY/skills/to-autonomy/SKILL.md"
expect_fail autonomous-without-undo

# 60b. The vocabulary lost the same rule.
fresh_copy autonomous-without-undo-vocabulary
edit 's/ and the effect can be undone//' "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail autonomous-without-undo-vocabulary

# 61. One kill switch per action (every action would open the same question).
fresh_copy kill-switch-per-action
edit '/^- One kill switch per system: record it once/d' "$COPY/skills/to-autonomy/SKILL.md"
expect_fail kill-switch-per-action

# 62. to-autonomy opens a decision task for an action an accepted ADR already decides.
fresh_copy decision-despite-adr
edit 's/ without an accepted ADR whose/ whose/' "$COPY/skills/to-autonomy/SKILL.md"
expect_fail decision-despite-adr

# 63. language.md no longer keeps least agency as it is.
fresh_copy least-agency-translated
edit 's/kill switch and least agency, and the Permission/kill switch, and the Permission/' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail least-agency-translated

# 64. The dossier index template lost the row of the autonomy file.
fresh_copy dossier-without-autonomy
edit '/^| \[Autonomy\](autonomy.md) | pending | |$/d' "$COPY/reference/templates/dossier-readme.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail dossier-without-autonomy

# 65. The kill switch lost its condition through an approval (a workflow with an approval would
#     get no kill switch).
fresh_copy kill-switch-without-approval-condition
edit 's/, even through an approval\./. /' "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail kill-switch-without-approval-condition

# 66. The README still says the plugin has five skills.
fresh_copy readme-five-skills
edit 's/lists the eight skills/lists the five skills/' "$COPY/README.md"
expect_fail readme-five-skills

# 67. to-autonomy closes the decision task (it closes in to-adr, when the ADR is accepted).
fresh_copy autonomy-closes-task
echo "   - GitHub tracker: gh issue close <number>" >> "$COPY/skills/to-autonomy/SKILL.md"
expect_fail autonomy-closes-task

# 68. A required kill switch without a task, when Approval, Idempotency and Hard Limits are already
#     in the code.
fresh_copy kill-switch-without-task
edit 's/^   opened for it when there is none\.$/   when that task exists./' \
  "$COPY/skills/to-autonomy/SKILL.md"
expect_fail kill-switch-without-task

# 69. to-adr matches the decision task by the exact text (the decision typed in other words would
#     leave the task open for ever).
fresh_copy decision-by-exact-text
edit 's/holds the same decision, matched by the subject and not by the exact text\./holds the same decision./' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail decision-by-exact-text

# 70. to-traces lost the namespace of the application (the skill would invent gen_ai.approver).
fresh_copy traces-namespace-gone
edit 's/^  these go in the namespace of the application\. Use the current names/  Use the current names/' \
  "$COPY/skills/to-traces/SKILL.md"
expect_fail traces-namespace-gone

# 70b. The vocabulary lost the same rule.
fresh_copy vocabulary-namespace-gone
edit 's/^  step: these go in the namespace of the application (a/  step (a/' "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail vocabulary-namespace-gone

# 71. The template requires the older name of the tokens (the stack writes it; the conventions
#     moved on).
fresh_copy older-token-name
edit 's/gen_ai\.usage\.input_tokens/gen_ai.usage.prompt_tokens/' "$COPY/reference/templates/traces.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail older-token-name

# 72. to-traces records the cost as an attribute (the price changes, and the old number lies).
fresh_copy cost-recorded
edit 's/^  price, never recorded as the attribute of cost\. A cost/  price. A cost/' \
  "$COPY/skills/to-traces/SKILL.md"
expect_fail cost-recorded

# 73. to-traces lost the content out of the telemetry by default (prompts would reach the backend).
fresh_copy content-in-telemetry
edit 's/^  content stays out of the telemetry unless the user decides otherwise, with/  content goes to the telemetry, with/' \
  "$COPY/skills/to-traces/SKILL.md"
expect_fail content-in-telemetry

# 73b. The vocabulary lost the same rule.
fresh_copy content-in-telemetry-vocabulary
edit 's/content stays out of the telemetry unless the user decides$/content goes to the telemetry unless the user decides/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail content-in-telemetry-vocabulary

# 74. Sampling takes the place of the durable record (a dropped trace would lose the action).
fresh_copy sampling-without-record
edit 's/^  action also goes to a durable record, tied to the trace by its trace id\. Without/  action is kept. Without/' \
  "$COPY/skills/to-traces/SKILL.md"
expect_fail sampling-without-record

# 75. The decision path without the end of the task (a limit of steps reached would look like a
#     success).
fresh_copy decision-path-without-end
edit 's/in order, why each ran, and why the task ended\./in order, and why each ran./' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail decision-path-without-end

# 76. One gap task per attribute in a system with no trace at all (nine tasks with one cause).
fresh_copy one-task-per-attribute
edit 's/When the system emits no trace at all, open one gap task for the instrumentation, with$/When the system emits no trace at all, open the same tasks, with/' \
  "$COPY/skills/to-traces/SKILL.md"
expect_fail one-task-per-attribute

# 77. check-architecture marks a runtime item Verified by reading the code.
fresh_copy runtime-verified-by-code
edit 's/^- Never mark a runtime item Verified by reading the code: without telemetry, it is Not Verified$/- A runtime item may be Verified by reading the code; without telemetry, it may be Not Verified/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail runtime-verified-by-code

# 78. Violated without evidence (an item could be marked Violated on a guess).
fresh_copy violated-without-evidence
edit '/^- Violated needs evidence too/d' "$COPY/skills/check-architecture/SKILL.md"
expect_fail violated-without-evidence

# 79. A Verified item without a fitness function gets no gap task (it breaks in the next commit).
fresh_copy verified-without-fitness-function
edit 's/A Verified item without a fitness function gets a gap task, so$/A Verified item is done, so/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail verified-without-fitness-function

# 80. check-architecture runs the next skill instead of stating it.
fresh_copy check-runs-next
edit 's/State the next command; never run it\./Run the next command./' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-runs-next

# 80b. check-architecture closes a task (the team closes it, from Tasks That Can Be Closed).
fresh_copy check-closes-task
echo "   - GitHub tracker: gh issue close <number>" >> "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-closes-task

# 81. check-architecture reuses only its own gap tasks (the task of to-traces would be opened twice).
fresh_copy check-reuses-own-only
edit 's/and the same subject, whichever skill opened it, and reuse it\./and the same subject that this skill opened, and reuse it./' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-reuses-own-only

# 82. Without a dossier, nothing to check (the skill would stop on an old project).
fresh_copy check-without-dossier
edit 's/^- Without a dossier, check the non-negotiables of an agent in production: hard limits, the log of$/- Without a dossier, stop and suggest the other skills: hard limits, the log of/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-without-dossier

# 83. language.md no longer keeps Not Verified (needs telemetry) as it is (a run could write
#     "Não verificado").
fresh_copy result-translated
edit 's/, `Not Verified (needs telemetry)`)/)/' "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail result-translated

# 84. The dossier index template lost the row of the traces.
fresh_copy dossier-without-traces
edit '/^| \[Traces\](traces.md) | pending | |$/d' "$COPY/reference/templates/dossier-readme.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail dossier-without-traces

# 85. The README still says the plugin has six skills.
fresh_copy readme-six-skills
edit 's/lists the eight skills/lists the six skills/' "$COPY/README.md"
expect_fail readme-six-skills

# 86. Every missing test becomes a request for an ADR.
fresh_copy next-adr-for-every-gap
edit 's/^   line only when closing the gap needs an architecture decision; a missing test does not\. The$/   line to every task. The/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail next-adr-for-every-gap

# 86b. check-architecture calls a telemetry backend on its own.
fresh_copy check-calls-backend
edit 's/^  call a backend on your own\.$/  hesitate to query the backend./' "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-calls-backend

# 86c. to-traces closes a task.
fresh_copy traces-closes-task
echo "   - GitHub tracker: gh issue close <number>" >> "$COPY/skills/to-traces/SKILL.md"
expect_fail traces-closes-task

# 87. check-architecture reads only the In the Code of the required rows (the id a request carries,
#     in the Trace of a Request, never becomes an item, and a system without traces has no Verified).
fresh_copy check-skips-trace-of-request
edit 's/^  - each part the Trace of a Request section says is in the code is a Structural item;$/  - the Trace of a Request section is context, not an item;/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-skips-trace-of-request

# 88. A required row the code records gets no Operational item (the report never says which
#     telemetry is missing).
fresh_copy check-no-runtime-item
edit 's/^  - each required row that the code records is also an Operational item: whether its attributes$/  - each required row that the code records is done: whether its attributes/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-no-runtime-item

# 89. The decision on content is not checked (the prompt logged in every environment goes unseen).
fresh_copy check-skips-content
edit 's/^  - the decision of the Content section is a Structural item, checked in the configuration that$/  - the Content section is a note, checked in the configuration that/' \
  "$COPY/skills/check-architecture/SKILL.md"
expect_fail check-skips-content

# 90. The vocabulary lost the runtime check of a required attribute.
fresh_copy vocabulary-one-check
edit 's/^- A required attribute is checked twice: in the code, whether the code records it (Structural);$/- A required attribute is checked in the code, whether the code records it (Structural);/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail vocabulary-one-check

# 91. to-risk counts the Next: line of a high-risk task an ADR already answers (the user who follows
#     the Next: lines goes back and forth between to-risk and to-adr, and never reaches to-autonomy).
fresh_copy risk-adr-loop
edit 's/^  answers, is answered: its `Next:` line does not count\. Say in one line that its mitigation is$/  answers, is open./' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail risk-adr-loop

# 92. to-adr always returns to to-risk (an ADR asked by to-autonomy or check-architecture sends the
#     user back to a dimension already assessed).
fresh_copy adr-fixed-return
edit 's/^- Otherwise, take the first of `risk.md`, `autonomy.md` and `traces.md`, in this order, that is$/- Otherwise, if the plugin you are running from has the `to-risk` skill, end with `Next: \/arch:to-risk`; skip/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-fixed-return

# 93. to-adr never sends the user to check-architecture (the chain stops before the check).
fresh_copy adr-no-check
edit '/^  `Next: \/arch:check-architecture (Claude Code) /d' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-no-check

# 94. to-adr closes the gap task that asked for the ADR and opens another for the same check (the
#     subject stays open under a new task; ticket-router run of 31/10).
fresh_copy adr-gap-not-reused
edit 's/^   - A `gap` task that asked for this ADR with a `Next: \/arch:to-adr` line stays open and becomes$/   - A `gap` task that asked for this ADR with a `Next: \/arch:to-adr` line is closed and replaced by/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-gap-not-reused

# 94b. The old sentence comes back next to the new one (two rules, the agent follows the closing one).
fresh_copy adr-gap-closed-again
echo "     task that asked for this ADR with a \`Next: /arch:to-adr\` line is closed the same way." \
  >> "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-gap-closed-again

# 95. The vocabulary says an output guardrail can only judge the whole response (false with an
#     accumulator, which runs the guardrails on each chunk; validation of 31/10, aula 2).
fresh_copy guardrail-only-whole
edit 's/^  without it, the user waits for the whole response (an output guardrail waits for the whole$/  without it, the user waits for the whole response (an output guardrail can only judge the whole/' \
  "$COPY/reference/vocabulary.md"
edit 's/^  response by default)\.$/  response)./' "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail guardrail-only-whole

# 96. to-characteristics says the same (the sentence lands in the student's characteristics.md).
fresh_copy characteristics-guardrail-only-whole
edit 's/(an output guardrail waits for the whole response by default)/(an output guardrail can only judge the whole response)/' \
  "$COPY/skills/to-characteristics/SKILL.md"
expect_fail characteristics-guardrail-only-whole

# 97. The setup recommends GitHub Issues without the write permission (in a clone of the course
#     repository, labels and tasks fail, or land in someone else's repository; validation of 31/10,
#     aula 7).
fresh_copy setup-issues-without-permission
edit 's/^  authenticated and `gh repo view --json viewerPermission` gives ADMIN, MAINTAIN or WRITE;$/  authenticated;/' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-issues-without-permission

# 98. The setup recommends local files without saying why (the user answers "ok" to a choice he
#     does not understand).
fresh_copy setup-local-without-reason
edit 's/^  otherwise recommend local files, and say why\. When/  otherwise recommend local files. When/' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-local-without-reason

# 99. Next do to-adr com ADR proposed manda pôr a mitigação no código: the student answers "later"
#     to the decision, the ADR stays proposed, and the skill still says "put the mitigation in the
#     code, then /arch:to-risk" (no mitigation was decided; validation of 31/10, aula 7).
fresh_copy adr-proposed-sends-to-code
edit '/^- If the ADR stays `proposed`, end with the decision task and what is missing to decide, in the form$/,/^  No mitigation was decided yet, so never send the user to the code\.$/d' \
  "$COPY/skills/to-adr/SKILL.md"
edit 's/^- If the ADR is `accepted` and answers a `high-risk` task, end with$/- If the ADR answers a `high-risk` task, end with/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-proposed-sends-to-code

# 99b. Only the accepted condition goes away (the proposed line stays, and the high-risk line,
#      checked first by the agent, still catches the proposed ADR).
fresh_copy adr-high-risk-any-status
edit 's/^- If the ADR is `accepted` and answers a `high-risk` task, end with$/- If the ADR answers a `high-risk` task, end with/' \
  "$COPY/skills/to-adr/SKILL.md"
expect_fail adr-high-risk-any-status

# 100. to-risk counts a high-risk task that links only a proposed ADR as answered (the decision the
#      student postponed disappears from the chain of Next: lines).
fresh_copy risk-proposed-answered
edit 's/^  in the list\. A `high-risk` task that already links an `accepted` ADR, or that an `accepted` ADR$/  in the list. A `high-risk` task that already links an ADR, or that an `accepted` ADR/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail risk-proposed-answered

# 101. to-risk sends the user back to to-adr for a high-risk task with only a proposed ADR (the loop
#      fixed in PR #21 comes back through the proposed ADR).
fresh_copy risk-proposed-loop
edit 's/^  links only a `proposed` ADR is not answered, and its `Next:` line does not count either: say in$/  links only a `proposed` ADR is not answered, and its `Next:` line counts: say in/' \
  "$COPY/skills/to-risk/SKILL.md"
expect_fail risk-proposed-loop

# 102. The vocabulary says a guardrail retry repeats a response to correct it (the guardrails module
#      teaches that a retry without the reason corrects nothing; the reprompt carries the reason;
#      validation of 31/10, aula 4).
fresh_copy vocabulary-guardrail-retry-corrects
edit 's/^  - A \*\*guardrail retry\*\* repeats the call after a response came and was rejected, with or$/  - A **guardrail retry** repeats a response that came and was rejected, to correct it; or/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail vocabulary-guardrail-retry-corrects

# 103. The setup creates the labels only when the user confirms them (GitHub tracker without the
#      labels: every gh issue create --label of the other skills fails; validation of 31/10,
#      round 3).
fresh_copy setup-labels-only-confirmed
edit 's/^5\. With the GitHub tracker, the labels are always created\. Run for each label:$/5. With the GitHub tracker and labels confirmed, run for each label:/' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-labels-only-confirmed

# 104. The setup lost what to do when the user declines the labels (the user keeps the GitHub
#      tracker with no labels, and the first task of any skill fails or lands without a label).
fresh_copy setup-labels-declined
edit 's/^  the other architecture skills open tasks with these labels\. If the user declines, say in one$/  the other architecture skills open tasks with these labels./' \
  "$COPY/skills/setup-architecture/SKILL.md"
edit '/^  line that every architecture skill opens GitHub Issues with these labels and cannot do it$/,/^  recommendation\.$/d' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-labels-declined

# 105. language.md lost the rule for a command with no text of the user (a student with no
#      language setting who types only /arch:to-traces gets the summary, the questions and the
#      artifact in the language of the SKILL.md; validation of 31/10, round 3).
fresh_copy language-command-alone
edit '/^When the user has written nothing yet but the command (alone, or with a path), use the language$/,/^recommendation (the language of the project.s README), before the first summary\.$/d' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail language-command-alone

# 106 to 109. A skill reads the Next: /arch:to-adr line back by the word "Next:" (in a conversation
#      in Portuguese the task says "Próximo:"; to-adr did not list it, to-risk skipped the ADR of
#      the mitigation; validation of 31/10, round 4).
fresh_copy next-match-language
edit 's/ (for example `Próximo: \/arch:to-adr "..."`) must match it by the command, not by the word before it\./ (for example `Próximo: \/arch:to-adr "..."`)./' \
  "$COPY/reference/language.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail next-match-language

for skill in to-adr to-risk check-architecture; do
  fresh_copy "next-match-$skill"
  edit 's/: match it by the command, not by the word before it\././' \
    "$COPY/skills/$skill/SKILL.md"
  expect_fail "next-match-$skill"
done

# 110 to 112. The setup switches to GitHub Issues with local tasks already open and says nothing:
#      every skill then reads only GitHub, the open gap, decision and high-risk tasks drop out of
#      the chain, check-architecture opens duplicates and the dossier links point to files no
#      skill reads (validation of 31/10, round 6).
fresh_copy setup-local-tasks-ignored
edit 's/^  tasks, say how many are open, and recommend keeping local files: the dossier links them and every$/  tasks, recommend GitHub Issues anyway, and every/' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-local-tasks-ignored

fresh_copy setup-local-tasks-no-move
edit 's/^  skill reads only the chosen tracker\. If the user still picks GitHub Issues, offer once, with your$/  skill reads only the chosen tracker./' \
  "$COPY/skills/setup-architecture/SKILL.md"
edit '/^  recommendation (yes), to move each open task to a GitHub Issue with the same title, label and$/,/^  links in the dossier documents; list the moves in the draft of step 4\.$/d' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-local-tasks-no-move

fresh_copy setup-local-tasks-move-step
edit '/^7\. When the user accepted the move of the local tasks (question A), after the labels: for each$/,/^   documents to the new issue\.$/d' \
  "$COPY/skills/setup-architecture/SKILL.md"
expect_fail setup-local-tasks-move-step

# 113 to 117. to-autonomy asks a second approval of a decision a person already approved in the
#      same request (aprovacao-desconto: the manager approves the discount, then checks again the
#      text of the reply that only delivers it; the rubber stamp the vocabulary condemns;
#      validation of 31/10, round 7). 114: the exception without the hard limit that checks the
#      content would let the model change what was approved.
fresh_copy rubber-stamp-vocabulary
edit '/^  - The one exception: when the action only delivers to the person who made the request what a$/,/^    it delivers\.$/d' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail rubber-stamp-vocabulary

fresh_copy rubber-stamp-without-hard-limit
edit 's/^    person already approved in the same request, and a hard limit in the code checks that the$/    person already approved in the same request,/' \
  "$COPY/reference/vocabulary.md"
edit 's/^    content matches the approval, recommend `autonomous` within that hard limit and say why: a$/    recommend `autonomous` and say why: a/' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail rubber-stamp-without-hard-limit

fresh_copy rubber-stamp-approval-condition
edit 's/^  - Condition of the approval: a large blast radius or an effect that cannot be undone, unless the$/  - Condition of the approval: a large blast radius or an effect that cannot be undone./' \
  "$COPY/reference/vocabulary.md"
edit '/^    action only delivers what a person already approved in the same request\.$/d' \
  "$COPY/reference/vocabulary.md"
"$COPY/scripts/sync-references.sh" > /dev/null
expect_fail rubber-stamp-approval-condition

fresh_copy rubber-stamp-to-autonomy
edit '/^    The one exception, from the vocabulary: when the action only delivers to the person who made$/,/^    not in the code yet, recommend it as a hard limit of the action in the second round\.$/d' \
  "$COPY/skills/to-autonomy/SKILL.md"
expect_fail rubber-stamp-to-autonomy

fresh_copy rubber-stamp-to-autonomy-condition
edit 's/^  cannot be undone, unless the action only delivers what a person already approved in the same$/  cannot be undone/' \
  "$COPY/skills/to-autonomy/SKILL.md"
edit 's/^  request), idempotency/  ), idempotency/' \
  "$COPY/skills/to-autonomy/SKILL.md"
expect_fail rubber-stamp-to-autonomy-condition

if [ "$STATUS" -eq 0 ]; then
  echo "every mutation caught"
fi
exit "$STATUS"
