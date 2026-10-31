---
name: to-autonomy
description: Decide what an agent may do on its own, one effectful action at a time. Read the system, list every effectful action with its agent identity and blast radius, check the three excesses, recommend a permission level for each action, settle approval, idempotency, hard limits and the kill switch in rounds, and write docs/architecture/autonomy.md. Opens a decision task for each effectful action whose autonomy is not settled in the code or in an accepted ADR. Does not score risk.
disable-model-invocation: true
---

# To autonomy

Decide, action by action, what the agent may do without a person: the effectful actions of the
system, the identity each one runs with, its blast radius, the three excesses, the permission level
of each action, who approves, how the action stays idempotent, the hard limits in the code, and the
kill switch of the system. You propose: you read, recommend and ask; the user decides, and every
question comes with your recommendation.

This skill decides what the agent may do. It does not score risk: what can fail and how much it
hurts is the risk assessment, which you read when it exists and never recalculate.

The input is what the user wrote with the command: one action in quotes ("refund a payment"), a
path to a project, or nothing. With a path or nothing, work from that project or the current
repository, with every effectful action.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings, its permission levels and the condition of each part. Do not mention the source book in
generated files.

Write every file, task and question in the user's language, template headings and table headers
included, following [references/language.md](references/language.md). Keep blast radius, kill
switch, least agency, `not now` and the Permission values as they are.

Templates for the files you write:
- [references/autonomy.md](references/autonomy.md) for the autonomy of the system;
- [references/dossier-readme.md](references/dossier-readme.md) for the dossier index, when it does
  not exist yet.

Fixed sentences in this skill (the `Next:` lines, the title of the `decision` task) are written
here in the plugin's language: translate each into the user's language, and keep commands and file
names as they are.

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: autonomy at
  `docs/architecture/autonomy.md`, dossier index at `docs/architecture/README.md`, ADRs at
  `docs/adr/`, tasks as local files in `.scratch/architecture/issues/`. Do not ask about the
  tracker. Tell the user in one line, and suggest `/arch:setup-architecture` (Claude Code) or
  `$arch:setup-architecture` (Codex) for later. Then go on.
- Read `docs/architecture/autonomy.md`, if it exists: the action blocks already decided, and the
  name of the system in its title.
- Read the risk assessment (`docs/architecture/risk.md`, or the location the setup gives), if it
  exists: its Effectful Retry lists effectful actions already found, and the notes of its areas are
  evidence for the blast radius. Never recalculate its scores.
- Read the brief (`docs/architecture/brief.md`) and the characteristics
  (`docs/architecture/characteristics.md`), or the locations the setup gives, if they exist: the
  trust boundary (which data leaves, which actions have an effect) and characteristics such as
  auditability.
- Read the ADRs in `docs/adr/`: an `accepted` ADR that already decides the autonomy of an action is
  evidence, cited by its number.
- Read the open `decision` tasks in the tracker, so you do not open them twice.
  - GitHub tracker: `gh issue list --label decision --state open`.
  - Local tracker: the files in `.scratch/architecture/issues/` with a `Labels: decision` line and
    a `Status: open` line.
- If there is a project (the path given, or the current repository), read its README, build files
  (`pom.xml`, `build.gradle`, `package.json`, ...), configuration (`application.properties`,
  `application.yaml`, environment files), tests, and the code where the actions live: the tools,
  the MCP clients, the writes to a database or to another system, the messages, the payments, the
  approval points, the authentication of the endpoints, the credentials in the configuration, the
  checks on what the model returns, the limits, and any switch that turns a feature off. Treat
  what they show as answered, and say where you saw it.

Show the user, in a few lines, what you read and what you did not find. Done when you know where
each file goes and what is already answered.

## 2. List the effectful actions

- With one action in the input, work on that action only. Do not list the others.
- Without it, list every effectful action: each action whose effect leaves the system (opens a
  ticket, sends a message, writes to another system, moves money). For each, the place where you
  saw it and what triggers it: the model, through a tool; or the code, from an output of the model.
- An action named in the words of the domain is clearer than the name of the method: name it by
  its effect.
- Model calls and reads with no effect outside the system go in one line, as actions without
  effect.
- What the Effectful Retry of `risk.md` already lists is evidence: start from it.
- The name of the system is the name the build file declares: in Maven, `<name>` when it exists,
  otherwise `<artifactId>`; in Gradle, `rootProject.name` in `settings.gradle`; in other builds,
  the project name field (`name` in `package.json`). The README title is not a source for the
  name. When no build file declares a name, ask the user which name to use in the round that
  confirms the list of actions (the first round of step 5), not in a round of its own, with your
  recommendation. With an existing `autonomy.md`, keep its title as it is.

Done when every effectful action has the place where it shows and what triggers it.

## 3. Agent identity and blast radius

- For each action, the identity it runs with: its own, the user's, or the whole application's. On
  behalf of whom it acts, and whether the record keeps both. An agent never does more than the user
  it acts for may do.
- Who approved is an identity too: an approval that does not record who approved proves nothing.
- For each action, the blast radius by the three questions of the vocabulary: what it reaches (one
  record, one customer, the whole base), what the error costs, and whether it can be undone. Give
  the evidence of each answer.

Done when every action has its identity and its blast radius, with the evidence.

## 4. Check the three excesses

- **Excessive functionality**: the agent reaches functions the use case does not need.
- **Excessive permission**: the identity can do more than the action needs.
- **Excessive autonomy**: a high-impact action without a person checking.
- For each excess: found or not, with the evidence.
- Least agency is decided per tool: what the tool does, how often, and how far it reaches. Without
  a tool, say so: the model only writes text, and the code acts on it.

Done when each excess has its answer and its evidence.

## 5. Ask in rounds

- Ask only what the code does not show. What the code shows (the actions, the identity, the
  authentication, the limits, the timeouts, the checks before the effect) is evidence: say it, do
  not ask it.
- At most three questions per round, numbered, each with your recommendation and its reason in one
  line. The user may answer "ok" to accept one recommendation, or "ok to all" to accept the whole
  round.
- First round:
  - the design principle of the system, in one sentence;
  - the permission level of each action, one of `forbidden`, `propose`, `with-approval`,
    `autonomous`, recommended by the rule of the vocabulary: `forbidden` when the use case does not
    need the action; `propose` or `with-approval` when the blast radius is large or the effect
    cannot be undone (`propose` when a person or the code acts after the yes, `with-approval` when
    the agent acts after the yes). Recommend `autonomous` only when the blast radius is small and the effect can be undone.
  - who approves, when the level asks for an approval. Never assume who approves: ask. The agent
    is never the one who approves.
- Second round:
  - the hard limits of each action: value, frequency and reach of the action; steps, time and
    tokens of the execution. A limit that only the prompt states is not a hard limit. A limit that
    only the configuration declares is evidence of where the limit lives: say where it is and go on.
  - the idempotency of each action: the idempotency key, the state checked before the effect, and
    the deadline of the approval;
  - the kill switch of the system: who may trigger it, what happens to the work in progress, and
    the path without the agent.
- After the answers, say in one line what they settled.

Done when every action has its level, its approval, its idempotency and its hard limits, and the
kill switch is settled, or the user left a part open.

## 6. Check the condition of each part

- Each part has the condition of the vocabulary: approval (a large blast radius or an effect that
  cannot be undone), idempotency (every effectful action that can run twice: a resent request, a
  retry, two approvals), hard limits (every effectful action; always), kill switch (the model
  chooses the next step, or a model output leads to an effectful action, even through an approval).
- Say whether the system meets each condition, and where you saw it. A part whose condition the
  system does not meet is `not now`, recorded with the condition that reopens it.
- One kill switch per system: record it once, and say in each action whether it stops that action.

Done when every part has its condition, met or not, with the evidence.

## 7. Show the draft, then write

Show the user `autonomy.md` as a draft, with the tasks you will open. Write only after the user
confirms or corrects it.

1. **autonomy.md**: fill the template, in the user's language, with one block per action. With one
   action in the input, replace only the block of that action, matched by its subject and not by
   its exact name (the name is translated and rewritten in every run); a new action adds a block
   after the last one. Without an action in the input, redo the file. Design Principle, Kill
   Switch, Excess Check and Actions Without Effect cover the whole file.
2. **Tasks**: Open one decision task per effectful action without an accepted ADR whose Approval, Idempotency or Hard Limits are not in the code.
   A required kill switch that is not in the code goes to the decision task of the action with the largest blast radius,
   opened for it when there is none.
   Before creating, look for an open task with the same label and subject and reuse it. Match by
   the label and the subject, not by the exact title: an earlier run may have translated the title
   differently. The title is "Autonomy of <action>" (translated); the body has what was decided in
   the conversation, what is missing in the code, and the line
   `Next: /arch:to-adr "<the autonomy of the action, with its parts>"`, with the decision already
   written and its parts included (for example, "propose, with the manager identified, a ceiling in
   the code and a deadline that expires the request").
   - GitHub tracker: find with `gh issue list --label decision --state open --search "<subject>"`;
     create with `gh issue create --title "<title>" --label decision --body "<decided, missing in the code, Next: line>"`.
   - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: decision`
     line, a `Status: open` line and the subject in its `#` title. Create a file
     `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
     already translated, in kebab-case) with a `# <title>` line, a `Labels: decision` line, a
     `Status: open` line, and the same body as on GitHub.
   - This skill never closes a task: the `decision` task closes when `to-adr` records its ADR as
     `accepted`.
   Put the link or file name of each task in the ADR field of its action.
3. **Dossier index**: in `docs/architecture/README.md`, set the row that links to `autonomy.md` to
   `generated` with today's date. Find the row by its link, never by its label, which is
   translated into the user's language. If the row is missing, add it. If the file does not exist,
   create it from [the dossier template](references/dossier-readme.md), translated, with that row
   as `generated` and the other rows as `pending`.

Done when `autonomy.md` has a block for every action of this run, every task is opened as above,
and the dossier index shows Autonomy as `generated`.

## 8. Report and next step

List the files written and the tasks opened. State the action with the largest blast radius in one
line, and each part that is `not now` with the condition that reopens it. Then end with one of
these lines, translated into the user's language, with commands and file names as they are:

- If a `decision` task opened by this run has a `Next: /arch:to-adr "..."` line, end with the line
  of the action with the largest blast radius; on a tie, the first in the list.
- Otherwise, if the plugin you are running from has the `to-traces` skill, end with
  `Next: /arch:to-traces (Claude Code) · $arch:to-traces (Codex)`.
- Otherwise end with: `Next: review docs/architecture/autonomy.md, and mark it reviewed in docs/architecture/README.md.`
