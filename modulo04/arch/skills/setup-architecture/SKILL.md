---
name: setup-architecture
description: Prepare a repository for the architecture skills. Chooses the task tracker, the labels and the dossier layout, then creates docs/architecture/, docs/adr/ and docs/agents/architecture.md.
disable-model-invocation: true
---

# Setup architecture

Prepare this repository so the architecture skills have a shared place to read and write: the
dossier in `docs/architecture/`, the ADRs in `docs/adr/`, and the choices every skill follows in
`docs/agents/architecture.md`. Run once per repository; running it again updates the choices.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings. Do not mention the source book in generated files.

Write every file and every question in the user's language, template headings included, following
[references/language.md](references/language.md).

Templates for the files you write:
- [references/dossier-readme.md](references/dossier-readme.md) for `docs/architecture/README.md`;
- [references/agents-architecture.md](references/agents-architecture.md) for
  `docs/agents/architecture.md`.

## 1. Explore

Look before you ask. Check each item and keep a note of what you found:

- `git remote -v`: is the remote on GitHub? Is `gh` installed and authenticated (`gh auth status`)?
  Can the user write to that repository (`gh repo view --json viewerPermission`)?
- `CLAUDE.md` and `AGENTS.md` at the repository root: which exist?
- `docs/adr/`, `docs/architecture/` and `.scratch/`: which exist, and what is inside? In
  `.scratch/architecture/issues/`, how many tasks say `Status: open`?
- `docs/agents/architecture.md`: if it exists, this is a re-run; its current values are the
  defaults for every question.
- `docs/agents/issue-tracker.md`: if it exists, another skill set already chose the tracker. Reuse
  that choice and skip question A.
- Deployables: count the independently deployable units by build unit (one `pom.xml` or
  `build.gradle` with its own packaging per service, one app per folder in a monorepo). Several
  images of the same build (for example `Dockerfile.jvm` and `Dockerfile.native`) are one
  deployable, not more. More than one deployable is a sign of more than one architecture quantum.

Done when every item above has an answer.

## 2. Show what you found

Show the user a short summary of the exploration, one line per item, before the first question.

## 3. Ask, one question at a time

Ask each question on its own, with your recommendation and the reason in one line. Wait for the
answer before the next question. Skip a question whose answer the exploration already settled, and
say why you skipped it.

- **A. Tracker.** Where do architecture tasks live: GitHub Issues or local files in
  `.scratch/architecture/issues/`? Recommend GitHub Issues when the remote is GitHub, `gh` is
  authenticated and `gh repo view --json viewerPermission` gives ADMIN, MAINTAIN or WRITE;
  otherwise recommend local files, and say why. When `.scratch/architecture/issues/` already holds
  tasks, say how many are open, and recommend keeping local files: the dossier links them and every
  skill reads only the chosen tracker. If the user still picks GitHub Issues, offer once, with your
  recommendation (yes), to move each open task to a GitHub Issue with the same title, label and
  body, mark the local file `Status: closed` with a line linking the new issue, and update the
  links in the dossier documents; list the moves in the draft of step 4.
- **B. Labels** (GitHub only). Create the labels `decision`, `high-risk` and `gap`? Recommend yes:
  the other architecture skills open tasks with these labels. If the user declines, say in one
  line that every architecture skill opens GitHub Issues with these labels and cannot do it
  without them, and recommend the local tracker instead; ask again question A with that
  recommendation.
- **C. Layout.** Ask only when the exploration found more than one deployable: one dossier at
  `docs/architecture/` for the whole repository, or one dossier per quantum at
  `docs/architecture/<quantum>/`? Recommend one dossier unless the deployables have clearly
  different characteristics (for example, one public API and one batch job). With a single
  deployable, use one dossier without asking.
- **D. Agent instructions file.** Ask only when neither `CLAUDE.md` nor `AGENTS.md` exists: create
  one to hold the architecture block? Recommend the file your host reads (`CLAUDE.md` for Claude
  Code, `AGENTS.md` for Codex).

Done when every question was answered or skipped with a reason.

## 4. Show the draft, then write

Show the user the full list of files and labels you will create or change, with the content of
`docs/agents/architecture.md` and of the architecture block. Write only after the user confirms.

Create:

1. `docs/architecture/README.md` from the dossier template (one per quantum with the per-quantum
   layout). If it exists, keep its rows and add the missing ones.
2. `docs/adr/`, if it does not exist, with a `.gitkeep` file so git keeps the empty folder. Keep
   any ADR already there: the architecture skills continue its numbering.
3. `docs/agents/architecture.md` from its template, with the answers filled in.
4. In the existing `CLAUDE.md` or `AGENTS.md` (or both, when both exist), this block, added once:

   ```markdown
   ## Agent skills

   ### Architecture

   Architecture skills read and write `docs/architecture/` and `docs/adr/`, and follow the
   tracker, labels and layout in `docs/agents/architecture.md`.
   ```

   If a `## Agent skills` heading already exists, add only the `### Architecture` part under it.
5. With the GitHub tracker, the labels are always created. Run for each label:

   ```
   gh label create decision --color 1D76DB --description "Open architecture decision that needs an ADR" --force
   gh label create high-risk --color B60205 --description "Architecture risk scored 6 or 9 without mitigation" --force
   gh label create gap --color FBCA04 --description "Architecture requirement not in place yet" --force
   ```

6. With the local tracker, create `.scratch/architecture/issues/` with a `.gitkeep` file. Each
   task the skills open later is a file `NN-slug.md` in that folder, numbered from `01` (`slug`
   is the title already translated, in kebab-case), whose first lines are a `# Title`, a
   `Labels: <label>` line and a `Status: open` line. Closing the task means changing that line to
   `Status: closed`.
7. When the user accepted the move of the local tasks (question A), after the labels: for each
   open task, `gh issue create` with its title, label and body; then mark the local file
   `Status: closed` with a line linking the new issue, and point the links in the dossier
   documents to the new issue.

Done when every file in the draft exists with the confirmed content, and, with the GitHub tracker,
`gh label list` shows the three labels, and every moved task has its GitHub Issue.

## 5. Report and next step

List what was created and what was left as it was. End with this line, translated into the
user's language, with commands and file names as they are:

`Next: /arch:grill-architecture (Claude Code) · $arch:grill-architecture (Codex)`
