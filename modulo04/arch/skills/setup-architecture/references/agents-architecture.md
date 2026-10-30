# Architecture skills

<!-- Write this file in the user's language, headings included (language.md). Keep the
     `Tracker:` and `Layout:` lines, the `Labels:` and `Status:` names, and their values as they
     are. Leave this comment out of the
     file. -->

Configuration read by every architecture skill. Written by `setup-architecture`; edit it by hand
or run the setup again to change a choice.

## Tracker

<!-- github | local -->
Tracker: {{tracker}}

- `github`: tasks are GitHub Issues in `{{owner/repo}}`, created with `gh issue create`.
- `local`: tasks are Markdown files in `.scratch/architecture/issues/NN-slug.md`, each with a
  `# Title` line, a `Labels:` line and a `Status: open` line; a closed task says
  `Status: closed`.

## Labels

| Label | Meaning |
|---|---|
| `decision` | An open decision that still needs an ADR |
| `high-risk` | A risk scored 6 or 9 without mitigation |
| `gap` | Something the architecture requires that is not in place yet |

## Layout

<!-- single | per-quantum -->
Layout: {{layout}}

{{quanta: one line per quantum, with its folder under docs/architecture/, only for per-quantum}}

## Artifact locations

| Artifact | Location |
|---|---|
| Dossier index | `docs/architecture/README.md` |
| Brief | `docs/architecture/brief.md` |
| ADRs | `docs/adr/NNNN-slug.md` |
| Architecture characteristics | `docs/architecture/characteristics.md` |
| Local tasks | `.scratch/architecture/issues/` (only with the `local` tracker) |
