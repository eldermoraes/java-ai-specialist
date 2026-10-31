---
name: grill-architecture
description: Interview the user about an agentic system, one round of questions at a time, and write the kata brief plus ADR 0001 (agent, workflow or multi-agent). Works from a short idea or from an existing project.
disable-model-invocation: true
---

# Grill architecture

Turn an idea or an existing project into two files: the brief of the system, in kata format, and
the first ADR, which decides between agent, workflow and multi-agent. You grill: you ask, the user
decides, and every question comes with your recommendation.

The input is what the user wrote with the command: a short idea ("an agent that answers the
customers of a telecom and can open a ticket in the support system"), a path to a project, or
nothing. With nothing, ask for a two-line description of the system and start from it.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings. Do not mention the source book in generated files.

Write every file, task and question in the user's language, template headings included, following
[references/language.md](references/language.md).

Templates for the files you write:
- [references/brief.md](references/brief.md) for the brief;
- [references/adr.md](references/adr.md) for the ADR;
- [references/dossier-readme.md](references/dossier-readme.md) for the dossier index, when it does
  not exist yet.

Fixed sentences in this skill (the `Next:` lines) are written here in the plugin's language:
translate each into the user's language, and keep commands and file names as they are.

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- Without it, the setup has not run. Use the defaults: brief at `docs/architecture/brief.md`, ADRs
  at `docs/adr/`, tasks as local files in `.scratch/architecture/issues/`. Tell the user in one
  line, and suggest `/arch:setup-architecture` (Claude Code) or `$arch:setup-architecture` (Codex) for
  later. Then go on.
- With the per-quantum layout, ask which quantum this brief is for, and write under its folder.
- If a brief already exists, read it: its answers are settled, and you grill only what it leaves
  open. If the input is a project path, read its README, build files and main source folders
  first, and treat what the code shows as answered (say where you saw it).

Done when you know where each file goes and what is already answered.

## 2. Build the decision tree

Build, for yourself, the tree of decisions this system needs. A node is a question; a node depends
on the nodes whose answers it needs. Start from these nodes and add the ones the input suggests:

1. **Trust boundary** (no prerequisites; always the first round):
   - Which data does the system touch?
   - May that data leave the company (to a model provider, to a third-party tool)?
   - Which actions have an effect outside the system?
2. **Users and load** (no prerequisites): who uses it, through which channel, at what volume.
3. **Systems reached** (after 1): which existing systems the agent reads from and writes to.
4. **Path** (after 1 and 3): is the sequence of steps known ahead of time, or does it depend on
   what the model finds along the way? Are there contexts that must stay isolated from each other?
5. **Agent, workflow or multi-agent** (after 4): the decision of ADR 0001.
6. **For each effectful action** (after 1 and 5): who approves it, and what is the hard limit.
   Record the answer if the user has one; otherwise it becomes an Open Decision.
7. **Where the model runs** (after 1): only when the trust boundary says some data may not leave.

The **frontier** is the set of open nodes whose prerequisites are all answered.

## 3. Grill in rounds along the frontier

Each round:

- Ask only frontier questions, at most three per round, numbered.
- Give each question your recommendation and its reason in one line, built from the answers so
  far. The user may answer "ok" to accept a recommendation.
- An answer of "I don't know" or "later" closes the node as an **Open Decision**, with what is
  missing to decide it. That is a valid outcome.
- After the answers, update the tree, recompute the frontier, and say in one line what the answers
  settled.

Settle node 5 with these criteria, and say which ones applied:

- **Workflow** when the path is known ahead of time; cost and latency stay predictable.
- **Agent** when the path depends on what the model discovers; it then requires hard limits, a log
  of the decision path, and a kill switch.
- **Multi-agent** when contexts must stay isolated from each other; model calls multiply.

Recommend the simplest option that meets the requirements: a workflow, unless the path cannot be
known ahead of time.

Done when every node is answered or recorded as an Open Decision, and node 5 has an answer. If the
user leaves node 5 open, the ADR is written with status `proposed`.

## 4. Show the draft, then write

Show the user the brief and the ADR as drafts. Write only after the user confirms or corrects them.

1. **Brief**: fill every section of the brief template. Write only what the user said or what the
   repository shows. Effectful actions go in the Trust Boundary section; every Open Decision goes in
   its table.
2. **ADR**: get the number by running the script that ships in this skill's folder (the folder
   that holds this `SKILL.md`), from the project root, with the ADR folder as the argument:

   ```
   java <this skill's folder>/scripts/NextAdrNumber.java docs/adr
   ```

   (In Claude Code, this skill's folder is `${CLAUDE_SKILL_DIR}`. Requires Java 11 or later.)
   It prints the next free number (`0001` in a repository without ADRs). Write
   `docs/adr/<number>-agent-workflow-or-multi-agent.md` from the ADR template: the options in
   Context, the decision and the criteria that applied in Decision, the trade-offs in
   Consequences. In Compliance, name the check that would show the decision still holds (for an
   agent: the hard limits and the kill switch exist and are tested); when it does not exist yet,
   write that a `gap` task tracks it. Status is `accepted` when the user confirmed node 5, otherwise
   `proposed`.
3. **Tasks**: one `decision` task per Open Decision, plus one `gap` task when Compliance points to
   a check that does not exist. Before creating, look for an open task with the same label that
   names the same decision or check, and reuse it. Match by the label and the subject, not by the
   exact title: an earlier run may have translated the title differently.
   - GitHub tracker: find with `gh issue list --label <label> --state open --search "<subject>"`;
     create with `gh issue create --title "<title>" --label <label> --body "<what is missing to decide, and a link to the brief>"`.
   - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: <label>`
     line, a `Status: open` line and the subject in its `#` title. Create a file
     `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
     already translated, in kebab-case) with a `# <title>` line, a `Labels: <label>` line, a
     `Status: open` line, and the same body as on GitHub.
   Put the link or file name of each task in the Open Decisions table of the brief.
4. **Dossier index**: in `docs/architecture/README.md` (the `README.md` of the quantum folder,
   with the per-quantum layout, where the ADR row links to `../../adr/`), set the rows that link
   to `brief.md` and to `../adr/` to `generated` with today's date. Find each row by its link, never by its label,
   which is translated into the user's language. If a row is missing, add it. If the file does not
   exist, create it from [references/dossier-readme.md](references/dossier-readme.md), translated,
   with those two rows as `generated` and the other rows as `pending`.

Done when the brief has every section filled, the ADR has all seven sections, every Open Decision
has a task, and the dossier index shows both as `generated`.

## 5. Report and next step

List the files written and the tasks opened. Name the Open Decisions in one line each. Then end
with one of these lines, translated into the user's language, with commands and file names as
they are:

- If the plugin you are running from has the `to-characteristics` skill, end with
  `Next: /arch:to-characteristics (Claude Code) · $arch:to-characteristics (Codex)`.
- Otherwise end with: `Next: review the brief and ADR 0001, and mark them reviewed in docs/architecture/README.md.`
