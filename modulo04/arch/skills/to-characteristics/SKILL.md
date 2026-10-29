---
name: to-characteristics
description: Read an agentic system (brief, ADRs, code, or a two-line description), find its architecture quanta, propose the architecture characteristics that meet the three defining criteria, set a Target for each, find the fitness function that checks it, and write characteristics.md. A characteristic without a fitness function becomes a gap task.
disable-model-invocation: true
---

# To characteristics

Turn what is known about a system into its architecture characteristics: the few things it must
guarantee beyond its domain behavior, each with a measurable Target, a Measure and the fitness
function that checks it. You propose: you read, recommend and ask; the user decides, and every
question comes with your recommendation.

The input is what the user wrote with the command: a path to a project, a short description of the
system ("an agent that answers the customers of an online store, with guardrails on input and
output"), or nothing. With nothing, work from the current repository; if it holds no system and no
brief, ask for a two-line description and start from it.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings. Do not mention the source book in generated files.

Write every file, task and question in the user's language, template headings and table headers
included, following [references/language.md](references/language.md).

Template for the file you write: [references/characteristics.md](references/characteristics.md).

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: characteristics at
  `docs/architecture/characteristics.md`, dossier index at `docs/architecture/README.md`, tasks as
  local files in `.scratch/architecture/issues/`. Do not ask about the tracker. Tell the user in one
  line, and suggest `/arch:setup-architecture` (Claude Code) or `$arch:setup-architecture` (Codex)
  for later. Then go on.
- Read the brief (`docs/architecture/brief.md`, or the location the setup gives) and the ADRs in
  `docs/adr/`, if they exist. Their answers are settled: do not ask them again.
- If there is a project (the path given, or the current repository), read its README, build files
  (`pom.xml`, `build.gradle`, `package.json`, ...), configuration (`application.properties`,
  `application.yaml`, environment files) and tests. Treat what they show as answered, and say where
  you saw it.
- If `characteristics.md` already exists, read it: its rows are the starting point, and you
  propose only changes.

Show the user, in a few lines, what you read and what you did not find. Done when you know where
each file goes and what is already answered.

## 2. Find the quanta

- Count the deployables by build unit: one `pom.xml` or `build.gradle` with its own packaging per
  service, one app per folder in a monorepo. Several Dockerfiles of the same build (for example
  `Dockerfile.jvm` and `Dockerfile.native`) are image variants of one deployable, not more
  deployables. Several endpoints in the same deployable are still one quantum.
- One deployable: one quantum, named after the system. Say so in one line and do not ask.
- More than one deployable is a sign of more than one quantum. Each quantum gets its own section in
  the file, with its own characteristics.
  - With the `per-quantum` layout in the setup, write one `characteristics.md` per quantum folder
    (`docs/architecture/<quantum>/characteristics.md`).
  - Without the setup, ask whether to keep one dossier for the whole system or one per quantum.
    Recommend one dossier unless the quanta clearly need different characteristics (for example, a
    customer chat and a nightly batch job).

Done when every quantum has a name and you know which file holds its section.

## 3. Propose candidates

- List the candidate characteristics for each quantum, each with the evidence that suggested it
  (where in the brief, ADRs or code you saw it). Common ones in an agent: cost per request,
  latency, availability, auditability, confidentiality.
- Apply the three defining criteria to each candidate, and say in one line how it meets or fails
  each: non-domain, influences the structure, critical to success.
- Recommend keeping the smallest number of characteristics: each one costs complexity in the
  design, and wanting all of them leads to an architecture that meets none. For each candidate you
  recommend dropping, name the criterion it fails.
- Ask in rounds: at most three questions per round, numbered, each with your recommendation and
  its reason in one line. The user may answer "ok" to accept a recommendation, or "ok to all" to
  accept every recommendation of the round.
- After the answers, say in one line what they settled.

Done when the user has confirmed the list of characteristics for each quantum.

## 4. Set the Target and the Measure

For each characteristic:

- **Target**: a measurable value, with a number and a unit, and a percentile or a time window when
  it applies ("p95 under 3 s", "at most 3 model calls per request", "99.5% per month"). "Fast",
  "cheap" or "always up" are not Targets.
  - Recommend the Target from what the project shows (numbers in the README, limits in the
    configuration, timings in the tests or logs) and say where you saw it.
  - With no evidence, recommend a value and mark it as an estimate in the file.
  - For latency in an agent, say which one the user feels: TTFT with streaming, the whole response
    without it (an output guardrail can only judge the whole response).
  - For cost in an agent, set a ceiling per request: model calls per request, and tokens when the
    project measures them.
- **Measure**: the family that observes it: Operational (at runtime), Structural (in the code) or
  Process (in how the team works).

Ask in the same rounds as step 3, with a recommendation for each Target. Done when every
characteristic has a Target and a Measure.

## 5. Find the fitness function

For each characteristic, look in the repository for a mechanism that already checks it
objectively: a test, a metric with an alert, a limit in the configuration, a log line that counts
what the Target counts, an architecture rule in the build, a rule in the CI pipeline. Point to the
path (and the test or rule name).

- Found: write the path in the Fitness Function column.
- None found: the characteristic is still only an intention. Write "none" and open a `gap` task
  for it (step 7), with the title "Fitness function for <characteristic>" in the user's language.
  In the task body, say what the check must confirm and the Target. If the plugin you are running
  from has the `to-adr` skill, end the body with
  `Next: /arch:to-adr "<decision the check needs>" (Claude Code) · $arch:to-adr "<decision the check needs>" (Codex)`.

Done when every characteristic has a path or a `gap` task to open.

## 6. Out of Scope

Record in the Out of Scope section every candidate the user dropped, with the criterion it failed
or where it belongs instead. When they come up, always record these two, with the reason:

- **Autonomy of the agent**: what the agent may do on its own is a design principle, not an
  architecture characteristic.
- **Quality of the response**: a guardrail checks it on every request; it stays out of the
  dossier.

A domain requirement that came up as a candidate goes here too: it lives in the specification of
the system.

Done when every dropped candidate is in the section.

## 7. Show the draft, then write

Show the user `characteristics.md` as a draft, with the list of `gap` tasks you will open. Write
only after the user confirms or corrects it.

1. **characteristics.md**: fill the template, one section per quantum. Write only what the user
   confirmed or what the repository shows.
2. **Tasks**: one `gap` task per characteristic without a fitness function. Before creating, look
   for an open task with the same title and reuse it.
   - GitHub tracker: `gh issue create --title "<title>" --label gap --body "<what the check must confirm, the Target, and a link to characteristics.md>"`.
   - Local tracker: a file `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`)
     with a `# <title>` line, a `Labels: gap` line, and the body.
   Put the link or file name of each task in the Fitness Function column.
3. **Dossier index**: in `docs/architecture/README.md` (one per quantum folder with the
   `per-quantum` layout), set the Architecture Characteristics row, linking to `characteristics.md`,
   to `generated` with today's date. If the row is missing, add it. If the file does not exist,
   create it with that row.

Done when every characteristic has a Target, a Measure and a fitness function or a `gap` task,
every dropped candidate is in Out of Scope, and the dossier index shows Architecture
Characteristics as `generated`.

## 8. Report and next step

List the files written and the tasks opened. Name each characteristic in one line with its Target.
Then:

- If the plugin you are running from has the `to-adr` skill, end with
  `Next: /arch:to-adr (Claude Code) · $arch:to-adr (Codex)`.
- Otherwise end with: `Next: review characteristics.md, and mark it reviewed in docs/architecture/README.md.`
