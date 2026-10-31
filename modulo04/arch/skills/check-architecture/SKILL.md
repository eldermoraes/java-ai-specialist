---
name: check-architecture
description: Check the architecture dossier against the system: every Target, every Compliance of an ADR, every required trace attribute and every part a document says is in the code; without a dossier, the non-negotiables of an agent in production. Each item comes out Verified, Violated or Not Verified (needs telemetry), with its type (Operational, Structural, Process) and its evidence. Writes docs/architecture/check-report.md, opens or reuses a gap task for each item that is not Verified or has no fitness function, and states the next command without running it.
disable-model-invocation: true
---

# Check architecture

Check what the architecture dossier says against what the system shows: each item comes out
Verified, Violated or Not Verified (needs telemetry), with its type and its evidence, and each
gap becomes a `gap` task. You check and recommend; the user decides, and every question comes with
your recommendation.

This skill writes no document of the dossier, never closes a task, and never runs the next skill:
it reads, checks, writes the report, opens or reuses `gap` tasks, and states the next command.

The input is what the user wrote with the command: a path to a project, or nothing. With nothing,
work from the current repository.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings, the three results and the three types. Do not mention the source book in generated
files.

Write every file, task and question in the user's language, template headings and table headers
included, following [references/language.md](references/language.md). Keep Verified, Violated,
Not Verified (needs telemetry), Operational, Structural and Process as they are.

Template for the file you write: [references/check-report.md](references/check-report.md).

Fixed sentences in this skill (the `Next:` lines, the Next Command, "none") are written here in
the plugin's language: translate each into the user's language, and keep commands and file names
as they are.

## 1. Read the setup and the dossier

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: dossier in `docs/architecture/`, report at
  `docs/architecture/check-report.md`, ADRs at `docs/adr/`, tasks as local files in
  `.scratch/architecture/issues/`. Do not ask about the tracker. Tell the user in one line, and
  suggest `/arch:setup-architecture` (Claude Code) or `$arch:setup-architecture` (Codex) for later.
  Then go on.
- Read the dossier index (the `README.md` of the dossier folder), if it exists, and every document
  of the dossier that exists:
  - the characteristics (`characteristics.md`): the Target, the Measure and the Fitness Function
    of each characteristic;
  - the ADRs in `docs/adr/` with the status `accepted`: the Compliance of each;
  - the traces (`traces.md`): the In the Code of each required row; what the Trace of a Request
    section says is in the code today (an id the system generates per request and how far it
    reaches, the spans and log lines that exist); and the decision of the Content section;
  - the autonomy (`autonomy.md`): the Approval, Idempotency, Hard Limits and Kill Switch it says are
    in the code;
  - the risk assessment (`risk.md`): the mitigations it says are in the code.
- A document in `pending`, or missing, is not a gap of the report: it goes to the Next Command.
- Without a dossier, check the non-negotiables of an agent in production: hard limits, the log of
  the decision path, and the kill switch.
- Read the open `gap` tasks in the tracker, opened by any skill.
  - GitHub tracker: `gh issue list --label gap --state open`.
  - Local tracker: the files in `.scratch/architecture/issues/` with a `Labels: gap` line and a
    `Status: open` line.
- If there is a project (the path given, or the current repository), read its build files,
  configuration, code, tests, the configuration of the CI (for Process items), and the
  architecture rules in the build (ArchUnit and the like).
- Read telemetry only when the user gives a source (an exported file, a query to a backend). Never
  call a backend on your own.

Show the user, in a few lines, what you read and what you did not find. Done when you know which
documents exist and where the report goes.

## 2. List the items

- Each item with its source (the document and the line: the Target of the characteristic X, the
  Compliance of ADR NNNN, the row "Tokens" of `traces.md`, the Hard Limit of the action Y), or the
  non-negotiable it comes from when there is no dossier.
- Each item with its type, by the rule of the vocabulary: the family of what the item observes,
  not of where its test runs. A test in the build that counts model calls still checks an
  Operational item.
- The items of `traces.md`, in every system:
  - each required row (its condition met) is a Structural item: whether the code records its
    attributes, from its In the Code, checked in the code;
  - each required row that the code records is also an Operational item: whether its attributes
    show in the traces at runtime;
  - each part the Trace of a Request section says is in the code is a Structural item;
  - the decision of the Content section is a Structural item, checked in the configuration that
    logs or exports the content;
  - a row in `not now` is not an item: it stays in `traces.md`, with the condition that reopens it.
- The name of the system is the name the build file declares: in Maven, `<name>` when it exists,
  otherwise `<artifactId>`; in Gradle, `rootProject.name` in `settings.gradle`; in other builds,
  the project name field (`name` in `package.json`). The README title is not a source for the
  name. When no build file declares a name, ask the user which name to use in the round that
  confirms the list of items (the round of step 4), not in a round of its own, with your
  recommendation.

Done when every item has its source and its type.

## 3. Check each item

- Verified needs evidence: a path, a test, a rule in the build, or telemetry the user gave.
- Violated needs evidence too: the path or the reading that shows the item does not hold.
- Never mark a runtime item Verified by reading the code: without telemetry, it is Not Verified
  (needs telemetry). Not Verified is not Violated: it says which telemetry is missing.
- A limit in the configuration is Structural evidence that the limit exists, not that the Target
  is reached; it is a fitness function only when a test exercises it.
- For each item, its fitness function: the path of the test, of the metric with an alert, or of
  the rule in the build; or none. A Verified item without a fitness function gets a gap task, so
  the check becomes a test.

Done when every item has its result, its evidence and its fitness function or none.

## 4. Ask in one round

- At most three questions, numbered, each with your recommendation and its reason in one line.
  The user may answer "ok" to accept one recommendation, or "ok to all" to accept the whole round.
- Whether there is telemetry to read, and where. Without a source, recommend none: the runtime
  items stay Not Verified (needs telemetry).
- The name of the system, when no build file declares it.
- An item whose source contradicts itself (two documents that disagree), with the reading you
  recommend.
- After the answers, say in one line what they settled.

Done when the telemetry, the name and every contradiction are settled.

## 5. Show the draft, then write

Show the user `check-report.md` as a draft, with the tasks you will open and the ones you will
reuse. Write only after the user confirms or corrects it.

1. **check-report.md**: fill the template, in the user's language, with today's date. Redo the
   whole file in every run.
2. **Tasks**: one `gap` task per item that is Violated, Not Verified (needs telemetry), or Verified
   without a fitness function. Before opening a gap task, look for an open one with the gap label
   and the same subject, whichever skill opened it, and reuse it. Match by the label and the
   subject, not by the exact title: an earlier run may have translated the title differently. The
   body has the item, its source, the evidence and what closes the gap. Add a `Next: /arch:to-adr`
   line only when closing the gap needs an architecture decision; a missing test does not. The
   line is `Next: /arch:to-adr "<the decision, with its parts>"`, with the decision already
   written.
   - GitHub tracker: find with `gh issue list --label gap --state open --search "<subject>"`;
     create with `gh issue create --title "<title>" --label gap --body "<item, source, evidence, what closes it>"`.
   - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: gap` line, a
     `Status: open` line and the subject in its `#` title. Create a file
     `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
     already translated, in kebab-case) with a `# <title>` line, a `Labels: gap` line, a
     `Status: open` line, and the same body as on GitHub.
   - This skill never closes a task: an open `gap` task whose item is now Verified with a fitness
     function goes to Tasks That Can Be Closed, for the team to close.
   Put the link or file name of each task in the row of its item.
3. **Dossier index**: it does not change. The report is redone in every run and nobody marks it
   reviewed: it is not a deliverable of the dossier.

Done when `check-report.md` has every item with its result, and every task is opened or reused
as above.

## 6. Report and next step

List the report and the tasks, in a few lines: how many items are Verified, Violated and Not
Verified (needs telemetry). State the next command; never run it. End with one of these lines,
translated into the user's language, with commands and file names as they are:

- The first Violated item whose task has a `Next: /arch:to-adr "..."` line: end with that line.
  The line is translated in the user's language (for example `Próximo: /arch:to-adr "..."`): match it by the command, not by the word before it.
- Otherwise, the first Violated item: end with what closes it, from its task.
- Otherwise, the first Not Verified (needs telemetry) item: end with the telemetry it needs.
- Otherwise, the first document of the dossier in `pending`: end with
  `Next: /arch:<the skill that writes it> (Claude Code) · $arch:<the skill that writes it> (Codex)`.
- Otherwise end with: `Next: review docs/architecture/check-report.md with the team.`
