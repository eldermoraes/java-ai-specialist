---
name: to-adr
description: Record one architecture decision as an ADR. Read the system, check that the decision is architecturally significant and that each part of it has the condition it needs, ask what is open in rounds with recommendations, and write docs/adr/NNNN-slug.md with Compliance pointing to a fitness function or a gap task. Closes the decision task it answers.
disable-model-invocation: true
---

# To ADR

Turn one architecture decision into an ADR: the forces at play, each part of the decision with the
condition it needs, the options, the decision and its why, the consequences, and the fitness
function that checks it. You propose: you read, recommend and ask; the user decides, and every
question comes with your recommendation.

The input is what the user wrote with the command: the decision in quotes ("per-task ceiling and
loop circuit breaker"), a path to a project, or nothing. With a path or nothing, work from that
project or the current repository, and pick the decision in step 2.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings. Do not mention the source book in generated files.

Write every file, task and question in the user's language, template headings included, following
[references/language.md](references/language.md).

Templates for the files you write:
- [references/adr.md](references/adr.md) for the ADR;
- [references/dossier-readme.md](references/dossier-readme.md) for the dossier index, when it does
  not exist yet.

Fixed sentences in this skill (the `Next:` lines, the title of the `gap` task) are written here in
the plugin's language: translate each into the user's language, and keep commands and file names
as they are.

One decision per run. Another decision is another run of this skill.

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: ADRs at `docs/adr/`, dossier index at
  `docs/architecture/README.md`, tasks as local files in `.scratch/architecture/issues/`. Do not
  ask about the tracker. Tell the user in one line, and suggest `/arch:setup-architecture` (Claude
  Code) or `$arch:setup-architecture` (Codex) for later. Then go on.
- Read every ADR in `docs/adr/`: number, title and status. You need them to number the new one, to
  find an ADR on the same subject, and to supersede it.
- Read the brief (`docs/architecture/brief.md`) and the characteristics
  (`docs/architecture/characteristics.md`), or the locations the setup gives, if they exist: their
  Open Decisions, Targets and fitness functions are settled, do not ask them again.
- Read the risk assessment (`docs/architecture/risk.md`, or the location the setup gives), if it
  exists: its scores and mitigations are evidence, do not ask them again.
- Read the open `decision`, `gap` and `high-risk` tasks in the tracker. A `gap` or `high-risk`
  task whose body has a `Next: /arch:to-adr "..."` line is a request for an ADR.
  - GitHub tracker: `gh issue list --label decision --state open`,
    `gh issue list --label gap --state open` and `gh issue list --label high-risk --state open`.
  - Local tracker: the files in `.scratch/architecture/issues/` with a `Labels: decision`,
    `Labels: gap` or `Labels: high-risk` line and a `Status: open` line.
- If there is a project (the path given, or the current repository), read its README, build files
  (`pom.xml`, `build.gradle`, `package.json`, ...), configuration (`application.properties`,
  `application.yaml`, environment files), tests, and the code where the decision lives. For a
  decision about cost: where the model is called, how agents are composed, the token limits, the
  retries, and every loop. For a decision about resilience: every call over the network with its
  timeout, retries and fallback, how the work runs (in the request, through an emitter, through a
  queue), the state kept between requests, and the effectful actions. Treat what they show as
  answered, and say where you saw it.

Show the user, in a few lines, what you read and what you did not find. Done when you know where
each file goes and what is already answered.

## 2. Pick the decision

- With the decision in the input, use it. Do not list other candidates.
- Without it, list the candidates: open `decision` tasks, `gap` and `high-risk` tasks with a
  `Next: /arch:to-adr "..."` line, and the Open Decisions of the brief. Recommend one, with its
  reason in one line, and ask in one round. With no candidate, ask the user for the decision in
  one line.

Done when there is one decision to record.

## 3. Check that it is architecturally significant

- A decision deserves an ADR when it affects the structure, the architecture characteristics, the
  dependencies, the interfaces, or the construction techniques of the system (vocabulary). Say in
  one line which of these it affects.
- If it affects none, say which criterion is missing, recommend not writing an ADR, and stop,
  unless the user insists.
- If an ADR on the same subject exists: when it is `proposed`, recommend completing that file
  instead of writing a new one; when it is `accepted`, recommend a new ADR that supersedes it.

Done when the decision is significant (or the user insisted) and you know whether it is a new ADR,
the completion of a `proposed` one, or a new ADR that supersedes an `accepted` one.

## 4. Check the condition of each part

- Split the decision into the parts it holds ("a per-task ceiling and a loop circuit breaker" has
  two parts).
- For each part, say the condition under which it makes sense, whether the system meets it, and
  where you saw it. The vocabulary gives the condition of the known parts: the per-task ceiling,
  the loop circuit breaker, the router by cost and the model gateway, and the parts of resilience:
  timeout, retry, fallback, the circuit breaker of a dependency, the queue with persisted work, the
  saga and the state scoped by user or tenant, and the parts of autonomy: the approval, the
  idempotency, the hard limits and the kill switch. For any other part, reason out its condition
  and state it.
- A part whose condition the system does not meet: recommend `not now`, recorded with the
  condition that reopens it. Do not invent what the code does not show: no loop in the code means
  no loop for the loop circuit breaker to stop.
- For a decision about cost, count from the code what the vocabulary asks: the model calls of one
  task, path by path, the models involved, and the token limits per call; the ceiling is counted
  in the worst case (retries, tool results and reasoning tokens included). A retry is not a loop:
  it counts in the worst case of the ceiling, not as the condition of the loop circuit breaker.

Done when every part has its condition, met or not, with the evidence.

## 5. Ask in rounds

- Ask only what the code does not show. What the code shows (the calls, the models, the limits in
  the configuration) is evidence: say it, do not ask it.
- At most three questions per round, numbered, each with your recommendation and its reason in one
  line. The user may answer "ok" to accept one recommendation, or "ok to all" to accept the whole
  round.
- For a decision about cost, recommend from the vocabulary: a per-task ceiling in model calls and
  in tokens, counted in the worst case, and `not now` for each part without its condition.
- An answer of "later" or "I don't know" on the decision itself leaves the ADR `proposed`, and the
  `decision` task stays open (or is opened, in step 7) with what is missing to decide.
- After the answers, say in one line what they settled.

Done when the decision and each part of it are settled, or the user left the decision open.

## 6. Consequences and Compliance

- **Consequences**: what gets better, what gets worse, and the trade-off accepted. If nothing gets
  worse, look again: everything in architecture is a trade-off (First Law).
- **Compliance**: look in the repository for a fitness function that checks the decision and
  fails when it stops holding: a test, a metric with an alert, an architecture rule in the build,
  or a rule in the CI pipeline. Point to the path (and the test or rule name).
  - A log line counts only when a test or an alert reads it and fails.
  - A limit in the configuration implements the decision and does not check it: it counts only
    when a test exercises it and fails when the limit stops holding. Point to that test.
  - None found: Compliance points to a `gap` task titled "Fitness function for ADR NNNN: <title>"
    (translated), whose body says what the check must confirm and links the ADR.

Done when the ADR has its Consequences, with at least one thing that gets worse, and a Compliance
with a path or a `gap` task.

## 7. Show the draft, then write

Show the user the ADR as a draft, with the tasks you will close, update and open. Write only after the
user confirms or corrects it.

1. **ADR**: get the number by running the script that ships in this skill's folder (the folder
   that holds this `SKILL.md`), from the project root, with the ADR folder as the argument:

   ```
   java <this skill's folder>/scripts/NextAdrNumber.java docs/adr
   ```

   (In Claude Code, this skill's folder is `${CLAUDE_SKILL_DIR}`. Requires Java 11 or later.)
   It prints the next free number (`0001` in a repository without ADRs). When you complete a
   `proposed` ADR, keep its number and file. Write `docs/adr/<number>-<slug>.md` from the ADR
   template, where `slug` is the title already translated, in kebab-case. In Context, each part of
   the decision with its condition and the options; in Decision, the decision with its why, and
   each part that is `not now` with the condition that reopens it. Status is `accepted` when the
   user confirmed the decision, otherwise `proposed`. `not now` is never a status.
2. **Superseding**: when the new ADR supersedes an `accepted` one, change only the Status line of
   the old ADR, to `superseded by ADR-<new number>`, and link the old ADR in the Notes of the new
   one. No other line of an accepted ADR is ever edited.
3. **Tasks**:
   - Close the `decision` task the ADR answers, when the ADR is `accepted`. GitHub tracker:
     `gh issue close <number> --comment "<link to the ADR>"`. Local tracker: change its line to
     `Status: closed` and add a line with the path of the ADR; never delete the task file. With the
     decision in the input, the task the ADR answers is the open `decision` task whose
     `Next: /arch:to-adr "..."` line holds the same decision, matched by the subject and not by the exact text.
   - A `gap` task that asked for this ADR with a `Next: /arch:to-adr` line stays open and becomes
     the `gap` task of Compliance, when there is no fitness function: the subject is the same, now
     checked against the ADR. Never close it and open another task for the same check. Update its
     body with what the check must confirm and the link to the ADR, without the `Next:` line:
     GitHub tracker, `gh issue edit <number> --body "<what the check must confirm, and a link to the ADR>"`;
     local tracker, the body of the file, keeping its `#` title, `Labels:` and `Status: open`
     lines. When Compliance points to a fitness function, close it the same way as the `decision`
     task. When the ADR stays `proposed`, leave it as it is. It is matched like the `decision`
     task: by the subject of its `Next: /arch:to-adr "..."` line.
   - When the ADR answers a `high-risk` task, keep the high-risk task open: the risk falls when the mitigation
     reaches the code, and `to-risk` closes it then. Add the link to the ADR to it: a comment on
     GitHub (`gh issue comment <number> --body "<link to the ADR>"`), a line with the path of the
     ADR in the local file. With the decision in the input, the task the ADR answers is the open
     `high-risk` task whose `Next: /arch:to-adr "..."` line holds the same decision, matched by the
     subject and not by the exact text (the user types it in their own words). With no task that
     matches, the ADR answers no `high-risk` task.
   - When the ADR stays `proposed`, keep the `decision` task open, or open one with what is
     missing to decide.
   - Open the `gap` task of Compliance, when there is no fitness function and no `gap` task asked
     for this ADR (that task is the one, as above). Before creating, look
     for an open task with the same label and subject and reuse it. Match by the label and the
     subject, not by the exact title: an earlier run may have translated the title differently,
     and a `gap` task for the same check opened by another skill serves.
     - GitHub tracker: find with `gh issue list --label <label> --state open --search "<subject>"`;
       create with `gh issue create --title "<title>" --label <label> --body "<what the check must confirm, and a link to the ADR>"`.
     - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: <label>`
       line, a `Status: open` line and the subject in its `#` title. Create a file
       `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
       already translated, in kebab-case) with a `# <title>` line, a `Labels: <label>` line, a
       `Status: open` line, and the same body as on GitHub.
   Put the link or file name of each task in the Compliance or the Notes of the ADR.
4. **Dossier index**: in `docs/architecture/README.md`, set the row that links to `../adr/` to
   `generated` with today's date. Find the row by its link, never by its label, which is
   translated into the user's language. If the row is missing, add it. If the file does not
   exist, create it from [references/dossier-readme.md](references/dossier-readme.md), translated,
   with that row as `generated` and the other rows as `pending`.

Done when the ADR has all seven sections, every task is closed, updated or opened as above, and the dossier
index shows the ADRs as `generated`.

## 8. Report and next step

List the files written and the tasks closed, updated and opened. State the decision in one line, and each
part that is `not now` with the condition that reopens it. Say that another decision is another
run of this skill. Then end with one of these lines, translated into the user's language, with
commands and file names as they are:

- If the ADR answers a `high-risk` task, end with
  `Next: put the mitigation in the code, then /arch:to-risk to close the high-risk task (Claude Code) · $arch:to-risk (Codex)`.
- Otherwise, take the first of `risk.md`, `autonomy.md` and `traces.md`, in this order, that is
  `pending` or missing in the dossier index. If the plugin you are running from has the skill that
  writes it (`to-risk`, `to-autonomy`, `to-traces`), end with that skill, in the form
  `Next: /arch:to-risk (Claude Code) · $arch:to-risk (Codex)`.
- Otherwise, when the three are `generated` or `reviewed` and the plugin you are running from has
  the `check-architecture` skill, end with
  `Next: /arch:check-architecture (Claude Code) · $arch:check-architecture (Codex)`.
- Otherwise end with: `Next: review ADR <number>, and mark it reviewed in docs/architecture/README.md.`
