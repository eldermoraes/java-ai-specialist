# Language of what you write

Shared rule for every skill in the `arch` plugin. The skills, templates and vocabulary are written
in English; that says nothing about the language of what you write for the user.

## The rule

Write every file and every task for the user in **the user's language**: the language the user is
writing in, in this conversation. When the user asks for another language, use that one.

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
  (`decision`, `high-risk`, `gap`), the `Tracker:`, `Layout:` and `Labels:` lines with their
  values, file and folder names, commands.
- The headings `## Agent skills` and `### Architecture` of the architecture block, which other
  skill sets look for.
- Established technical terms, inside the translated text, when the user's language has no
  established translation for them (for example ADR, Target, fitness function, architecture
  quantum, harness). Keep their meaning from the vocabulary.

## Existing files

When a file you add to already exists in a language other than the user's, ask the user once which
language to use for that file, with your recommendation (keep the file's language), and follow the
answer.
