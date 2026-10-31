# Language of what you write

Shared rule for every skill in the `arch` plugin. The skills, templates and vocabulary are written
in English; that says nothing about the language of what you write for the user.

## The rule

Write every file and every task for the user in **the user's language**: the language the user is
writing in, in this conversation. When the user asks for another language, use that one.
When the user has written nothing yet but the command (alone, or with a path), use the language
the host is set to answer in; with no such setting, ask once which language to use, with your
recommendation (the language of the project's README), before the first summary.

This covers everything a skill writes for the user:

- the files (brief, ADRs, dossier index, `docs/agents/architecture.md`, characteristics, and any
  other file a skill creates), including the title, the section headings, the table headers, the
  table rows and the placeholder text taken from the templates: translate them all;
- the tasks (GitHub Issues or local task files): title and body;
- the text a skill adds to an existing file (the architecture block in `CLAUDE.md` or `AGENTS.md`,
  rows in the dossier index), and the label descriptions;
- the questions, recommendations, drafts and reports you show the user.

## What stays as it is

- Values that a skill or a tool reads back, written in backticks in the templates and skills:
  status values (`proposed`, `accepted`, `pending`, `generated`, `reviewed`), label names
  (`decision`, `high-risk`, `gap`), the `Tracker:`, `Layout:`, `Labels:` and `Status:` lines
  with their values (`open` and `closed` in a local task), file and folder names, commands.
- The `Next:` line in the body of a task (`Next: /arch:to-adr "<decision>"`) is a fixed sentence:
  the word `Next:` is translated, and the command stays as it is. A skill that reads the line back
  (for example `Próximo: /arch:to-adr "..."`) must match it by the command, not by the word before it.
- The term `not now`, for a part of a decision postponed on purpose: write it as it is, in every
  language, never translated.
- The terms risk storming, effectful retry, unproven technology and `single-assessor`, the
  Direction values (`new`, `improving`, `stable`, `worsening`) and the scores: write them as they
  are, in every language, like architecture characteristic. The headings Identification,
  Consensus, Mitigation and Risk Matrix are translated with the other headings, and so is the
  level of a risk (low, medium, high), which no skill reads back.
- The terms blast radius, kill switch and least agency, and the Permission values (`forbidden`, `propose`, `with-approval`, `autonomous`):
  write them as they are, in every language. The headings Design Principle, Actions, Kill Switch,
  Excess Check and Actions Without Effect, and the field names of each action, are translated
  with the other headings.
- The names of attributes and spans (`gen_ai.*`, `enduser.id`, the names in the namespace of the
  application, `chat`, `invoke_agent`, `execute_tool`), the results of an architecture check (`Verified`, `Violated`, `Not Verified (needs telemetry)`) and the types Operational, Structural and Process:
  write them as they are, in every language. The three results are headings and values at once,
  and stay as they are in both places. The headings Trace of a Request, Required Attributes,
  Content, Sampling, Stack Reference, Tasks That Can Be Closed and Next Command, and the table
  headers, are translated with the other headings.
- The headings `## Agent skills` and `### Architecture` of the architecture block, which other
  skill sets look for.
- Established technical terms, inside the translated text, when the user's language has no
  established translation for them (for example ADR, Target, fitness function, architecture
  quantum, harness). Keep their meaning from the vocabulary.
- The term **architecture characteristic** (plural architecture characteristics; characteristic
  when it is short for it), in every language and always, like Target and fitness function: in the
  title, the section headings, the table headers, the row of the dossier index and the text. The
  title of the characteristics file is `Architecture Characteristics: <system name>` in every
  language.

## Existing files

When a file you add to already exists in a language other than the user's, ask the user once which
language to use for that file, with your recommendation (keep the file's language), and follow the
answer.
