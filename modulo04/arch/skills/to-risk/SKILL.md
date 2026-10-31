---
name: to-risk
description: Assess the architecture risk of a system in one dimension, as a risk storming. Read the system, list the areas at risk with evidence, recommend an impact and a likelihood for each, score them with the risk matrix script, check every effectful retry and every unproven technology, and write docs/architecture/risk.md. Opens a high-risk task for each risk of 6 or 9 without mitigation in place. Does not decide permissions.
disable-model-invocation: true
---

# To risk

Assess what can fail in a system and how much it hurts, one dimension per run, as a risk storming:
the areas where the dimension can hurt, an impact and a likelihood for each, the score from the risk
matrix, the consensus when more than one person scored, the mitigation of each high risk, and the
direction of each risk against the previous assessment. You prepare and recommend; people score.
The agent is not an assessor: your scores are recommendations, and every question comes with one.

This skill scores what can go wrong and how much it hurts. It does not decide what the agent may do
on its own (permissions).

The input is what the user wrote with the command: the dimension in quotes ("availability when the
provider fails"), a path to a project, or nothing. With a path or nothing, work from that project or
the current repository, and pick the dimension in step 2.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings, its scale for impact and likelihood, and its levels. Do not mention the source book in
generated files.

Write every file, task and question in the user's language, template headings and table headers
included, following [references/language.md](references/language.md). Keep risk storming,
effectful retry, unproven technology, `single-assessor` and the Direction values as they are.

Templates for the files you write:
- [references/risk.md](references/risk.md) for the risk assessment;
- [references/dossier-readme.md](references/dossier-readme.md) for the dossier index, when it does
  not exist yet.

Fixed sentences in this skill (the `Next:` lines, the title of the `high-risk` task) are written
here in the plugin's language: translate each into the user's language, and keep commands and file
names as they are.

One dimension per run. Another dimension is another run of this skill.

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: risk assessment at
  `docs/architecture/risk.md`, dossier index at `docs/architecture/README.md`, ADRs at `docs/adr/`,
  tasks as local files in `.scratch/architecture/issues/`. Do not ask about the tracker. Tell the
  user in one line, and suggest `/arch:setup-architecture` (Claude Code) or
  `$arch:setup-architecture` (Codex) for later. Then go on.
- Read `docs/architecture/risk.md`, if it exists: one block per dimension already assessed (the
  block of the dimension of this run is the previous assessment, for the Direction), its assessors,
  the mitigations recorded, and the name of the system in its title.
- Read the brief (`docs/architecture/brief.md`) and the characteristics
  (`docs/architecture/characteristics.md`), or the locations the setup gives, if they exist: the
  trust boundary, the Targets (the impact of a failure is measured against them) and the fitness
  functions.
- Read the ADRs in `docs/adr/`: an `accepted` ADR that already decides a mitigation is evidence,
  cited by its number.
- Read the open `high-risk` tasks in the tracker, so you do not open them twice and for the
  Direction.
  - GitHub tracker: `gh issue list --label high-risk --state open`.
  - Local tracker: the files in `.scratch/architecture/issues/` with a `Labels: high-risk` line and
    a `Status: open` line.
- If there is a project (the path given, or the current repository), read its README, build files
  (`pom.xml`, `build.gradle`, `package.json`, ...), configuration (`application.properties`,
  `application.yaml`, environment files), tests, and the code where the external dependencies live:
  the model calls, the timeouts, the retries, the fallbacks, the circuit breakers, the queues, how
  agents are composed, the tools and other effectful actions, and the state kept between requests.
  Treat what they show as answered, and say where you saw it.

Show the user, in a few lines, what you read and what you did not find. Done when you know where
each file goes and what is already answered.

## 2. Pick the dimension

- With the dimension in the input, use it. Do not list other candidates.
- Without it, list the candidate dimensions from the vocabulary (performance, scalability,
  availability with its transitive dependencies, data loss, single point of failure, security).
  Recommend one from what the code shows, with its reason in one line, and ask in one round.
- Unproven technology (step 4) and the Effectful Retry (step 6) are checked in every run, whatever
  the dimension, so they are not among the candidates.
- A dimension that already has a block in `risk.md` is matched by its subject ("availability when
  the provider fails" and "availability" are the same dimension). When in doubt, say which block
  you matched, with your recommendation, in the round of step 4.

Done when there is one dimension to assess.

## 3. List the areas

- List the parts of the architecture where the dimension can hurt: components, agent compositions,
  external dependencies (the provider, the models, tools, MCP servers, databases, queues) and each
  technology they depend on. Give each area the place where you saw it. This is the diagram of the
  risk storming, as a list.
- What belongs to another dimension goes to Other Dimensions Seen, unscored, with the suggestion of
  another run. A dimension that already has a block in `risk.md` does not go there: suggest
  running it again only when the reading changed. The exception is unproven technology (step 4),
  which stays in the list in every run, as an area of the block of this dimension.
- The name of the system is the name the build file declares: in Maven, `<name>` when it exists,
  otherwise `<artifactId>`; in Gradle, `rootProject.name` in `settings.gradle`; in other builds,
  the project name field (`name` in `package.json`). The README title is not a source for the
  name. When no build file declares a name, ask the user which name to use in the round that
  confirms the list of areas (step 4), not in a round of its own, with your recommendation. With an
  existing `risk.md`, keep its title as it is.

Done when every area has the place where it shows.

## 4. Identification

- For each area, recommend first the impact and then the likelihood, from 1 to 3 each, with the
  evidence and the scale of the vocabulary. What the code shows is evidence: say it, do not ask it.
- Every technology an area depends on is checked for unproven technology in every run, whatever the
  dimension; an unproven one scores 9 in that area. A technology unknown to the team scores 9
  without the matrix, because nobody can score what they do not know. Never assume what the team
  knows. Ask one question about it: list only the technologies the build or the README marks as
  beta or pre-release, and any the user names, each with a recommendation; treat the rest as known.
  Recommend treating each technology you list as unproven technology.
- One question of the round is who assesses (names). Recommend only the people in this
  conversation, with `single-assessor` on every risk.
- Ask in rounds: at most three questions per round, numbered, each with your recommendation and its
  reason in one line. Your recommended scores for every area go in the round as one question. The
  user may answer "ok" to accept one recommendation, or "ok to all" to accept the whole round.
- After the answers, say in one line what they settled.

Done when every area has an impact and a likelihood, or is unproven, and you know who assessed.

## 5. Score with the script, then Consensus

- Every score comes from the script that ships in this skill's folder (the folder that holds this
  `SKILL.md`), never from the model:

  ```
  java <this skill's folder>/scripts/RiskScore.java <impact> <likelihood>
  java <this skill's folder>/scripts/RiskScore.java unproven
  ```

  (In Claude Code, this skill's folder is `${CLAUDE_SKILL_DIR}`. Requires Java 11 or later.) It
  prints the score and the level (`6 high`).
- With one person, every risk is `single-assessor`.
- With more than one (the scores the user brings from other people, each scored alone, or the
  assessors of the previous block), list the scores that diverge and the scores only one person
  gave, ask the consensus score of each with your recommendation, and record the divergence and how
  it was settled. A score only one person gave is discussed too: that person may have seen what
  nobody saw.

Done when every area has its score and level from the script, and the assessors of each.

## 6. Effectful Retry

- Checked in every run, whatever the dimension, for the whole system.
- List every effectful action (a tool, an MCP call, a write to another system, a message, a
  payment) and, for each, what can repeat it: the resilience retry, the guardrail retry, and the
  retry of a call that ran tools (repeating a model call repeats the tools it already ran).
- Say whether each action is idempotent, with the evidence, and score the risk of a duplicated
  effect with the script.
- With no effectful action: write that there is none, that every retry is safe, and what reopens
  the analysis (the first effectful action).

Done when every effectful action has what repeats it and its score, or the absence is recorded.

## 7. Mitigation

- For each risk of 6 or 9: the mitigation proposed, and whether it is already in the code (with the
  evidence) or in an `accepted` ADR (with its number).
- A mitigation that is an architecture decision gets the line
  `Next: /arch:to-adr "<decision>"` with the decision already written and its parts included (for
  example, "sync or async, with timeout, retry and fallback").
- Unproven technology is mitigated with a proof of concept or spike. A proof of concept or spike
  is not an architecture decision and gets no Next: line.
- Every mitigation costs: whoever pays decides whether the risk is worth the cost (the
  Consequences of the ADR).

Done when every risk of 6 or 9 has its mitigation and you know whether it is in place.

## 8. Direction

- With a previous block of the same dimension in `risk.md`, compare each area with it: a lower
  score is `improving`, the same score `stable`, a higher score `worsening`. Match an area of the
  previous block by its subject, not by its exact name: the name is translated and rewritten in
  every run, the same rule as the tasks in step 9.
- An area with no match in the previous block, or a dimension with no previous block: `new`.
- Blocks of other dimensions do not enter the count.
- When a fitness function measures the area, cite its measurement.

Done when every area has its Direction.

## 9. Show the draft, then write

Show the user `risk.md` as a draft, with the tasks you will open and close. Write only after the
user confirms or corrects it.

1. **risk.md**: fill the template, in the user's language, with one block per dimension. A run
   replaces only the block of that dimension; a new dimension adds a block after the last one, with
   Direction new. The previous block of the same dimension stays in the history of git, and the
   Assessors of the new block give the date of both assessments. Effectful Retry and Other
   Dimensions Seen cover the whole file and are redone in every run.
2. **Tasks**: one `high-risk` task per risk of 6 or 9 without mitigation in place. Before creating,
   look for an open task with the same label and subject and reuse it. Match by the label and the
   subject, not by the exact title: an earlier run may have translated the title differently. The
   title is "High risk: <area> (<score>)" (translated); the body has the score, the evidence, the
   mitigation proposed and, when there is one, the `Next: /arch:to-adr "..."` line.
   - GitHub tracker: find with `gh issue list --label high-risk --state open --search "<subject>"`;
     create with `gh issue create --title "<title>" --label high-risk --body "<score, evidence, mitigation, Next: line>"`.
   - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: high-risk`
     line, a `Status: open` line and the subject in its `#` title. Create a file
     `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
     already translated, in kebab-case) with a `# <title>` line, a `Labels: high-risk` line, a
     `Status: open` line, and the same body as on GitHub.
   - A risk that fell below 6, or whose mitigation reached the code: close its task. GitHub
     tracker: `gh issue close <number> --comment "<the reason>"`. Local tracker: change its line to
     `Status: closed` and add a line with the reason; never delete the task file.
   - Close only high-risk tasks of the dimension assessed in this run, of the Effectful Retry, or of
     a technology the team now knows. The question about unproven technology is asked in every run,
     so its answer holds for any block; a task of an area of another dimension stays as it is.
   Put the link or file name of each task in the Mitigation table.
3. **Dossier index**: in `docs/architecture/README.md`, set the row that links to `risk.md` to
   `generated` with today's date. Find the row by its link, never by its label, which is
   translated into the user's language. If the row is missing, add it. If the file does not exist,
   create it from [the dossier template](references/dossier-readme.md), translated, with that row
   as `generated` and the other rows as `pending`.

Done when `risk.md` has the block of this dimension, every task is opened or closed as above, and
the dossier index shows Risk as `generated`.

## 10. Report and next step

List the files written and the tasks opened and closed. State the highest risk in one line, and
the other dimensions seen. Then end with one of these lines, translated into the user's language,
with commands and file names as they are:

- If an open `high-risk` task has a `Next: /arch:to-adr "..."` line, end with that line. The line
  is translated in the user's language (for example `Próximo: /arch:to-adr "..."`): match it by the command, not by the word before it. The choice
  is always among the tasks that have the line: the one with the highest risk; on a tie, the first
  in the list. A `high-risk` task that already links an `accepted` ADR, or that an `accepted` ADR
  answers, is answered: its `Next:` line does not count. Say in one line that its mitigation is
  still missing in the code. Read the Status line of each ADR a task links. A `high-risk` task that
  links only a `proposed` ADR is not answered, and its `Next:` line does not count either: say in
  one line that its decision is still pending, in the open `decision` task, and go on.
- Otherwise, if the plugin you are running from has the `to-autonomy` skill, end with
  `Next: /arch:to-autonomy (Claude Code) · $arch:to-autonomy (Codex)`.
- Otherwise end with: `Next: review docs/architecture/risk.md, and mark it reviewed in docs/architecture/README.md.`
