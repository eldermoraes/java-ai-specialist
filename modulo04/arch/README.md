# arch: agentic architecture skills

Skills that write the architecture documentation of an agentic system. Your agent asks, writes and
keeps the files; you make the decisions. Works in Claude Code and in Codex.

## Install

Ask your agent: *Install the arch plugin following the "For your agent" section of
github.com/eldermoraes/java-ai-specialist/tree/main/modulo04/arch.*

## For your agent

You are installing the `arch` plugin for your user. Run only the commands below, in a terminal, for
the host you are. Do not run any script from this repository. The commands need network access and
change your host's plugin configuration: ask the user to approve them when your host asks.

The plugin lives in the marketplace `java-ai-specialist` (the catalog file
`.claude-plugin/marketplace.json` at the root of the repository). Its skills are
`setup-architecture`, `grill-architecture`, `to-characteristics` and `to-adr`.

### If you are Claude Code

1. Run `claude plugin marketplace add eldermoraes/java-ai-specialist`.
   If it says the marketplace already exists, run
   `claude plugin marketplace update java-ai-specialist` instead.
   If cloning over SSH fails, run the same command again with
   `CLAUDE_CODE_PLUGIN_PREFER_HTTPS=1` set in its environment.
2. Run `claude plugin install arch@java-ai-specialist`.
3. Verify: `claude plugin list` shows `arch@java-ai-specialist` enabled, and
   `claude plugin details arch` lists the four skills.
4. Tell the user to run `/reload-plugins` (or start a new session), and finish with:
   `Next: /arch:setup-architecture`

### If you are Codex

1. Run `codex plugin marketplace add eldermoraes/java-ai-specialist`.
   If it says the marketplace already exists, run
   `codex plugin marketplace upgrade java-ai-specialist` instead.
2. Run `codex plugin add arch@java-ai-specialist`.
   If the `plugin add` command does not exist, tell the user to update Codex and stop.
3. Verify: `codex plugin list --marketplace java-ai-specialist` shows `arch@java-ai-specialist`
   installed and enabled.
4. Tell the user to start a new Codex session, and finish with:
   `Next: $arch:setup-architecture`

### Update

- Claude Code: `claude plugin marketplace update java-ai-specialist`, then
  `claude plugin update arch@java-ai-specialist`.
- Codex: `codex plugin marketplace upgrade java-ai-specialist`, then
  `codex plugin add arch@java-ai-specialist`.

Updating never touches the files the skills generated in the user's project (`docs/architecture/`,
`docs/adr/`, `docs/agents/architecture.md`).

### Uninstall

- Claude Code: `claude plugin uninstall arch@java-ai-specialist`.
- Codex: `codex plugin remove arch@java-ai-specialist`.

## Skills

| Skill | Claude Code | Codex | Writes |
|---|---|---|---|
| `setup-architecture` | `/arch:setup-architecture` | `$arch:setup-architecture` | `docs/architecture/README.md`, `docs/adr/`, `docs/agents/architecture.md`, the tracker labels |
| `grill-architecture` | `/arch:grill-architecture` | `$arch:grill-architecture` | `docs/architecture/brief.md`, `docs/adr/NNNN-agent-workflow-or-multi-agent.md`, one `decision` task per open decision |
| `to-characteristics` | `/arch:to-characteristics` | `$arch:to-characteristics` | `docs/architecture/characteristics.md` (Target, Measure and fitness function per characteristic, one section per quantum, Out of Scope), one `gap` task per characteristic without a fitness function |
| `to-adr` | `/arch:to-adr` | `$arch:to-adr` | `docs/adr/NNNN-slug.md` (one decision per run, each part with the condition it needs, `not now` with the condition that reopens it), closes the `decision` task it answers, one `gap` task when Compliance has no fitness function |

The skills run only when you call them: the agent never starts them on its own
(`disable-model-invocation: true` for Claude Code, `agents/openai.yaml` with
`allow_implicit_invocation: false` for Codex).

Each skill works on its own. Without the setup, `grill-architecture`, `to-characteristics` and
`to-adr` write to the default locations and keep their tasks as local files in
`.scratch/architecture/issues/`. `to-characteristics` needs no brief and no ADR either: it reads
the code of a project, or starts from a two-line description. `to-adr` needs no brief either: it
reads the code where the decision lives.

The skills write in your language: the files, tasks and questions follow the language you use in
the conversation, unless you ask for another one (rule in `reference/language.md`).

## Typical run

1. `/arch:setup-architecture`: choose the tracker (GitHub Issues or local files) and the layout.
2. `/arch:grill-architecture "an agent that answers the customers of a telecom and can open a ticket in the support system"`:
   answer the rounds of questions; each comes with a recommendation.
3. Read `docs/architecture/brief.md` and `docs/adr/0001-agent-workflow-or-multi-agent.md`.
4. `/arch:to-characteristics`: confirm the quanta (asked only when there is more than one
   deployable) and, in one round, the characteristics kept and dropped; then the Targets, up to three per round. Each question comes with a recommendation:
   "ok" accepts it, and "ok to all" accepts the whole round.
5. Read `docs/architecture/characteristics.md` and the `gap` tasks for the characteristics that
   have no fitness function yet.
6. `/arch:to-adr "per-task ceiling and loop circuit breaker"`: one decision per run; answer the
   round, read the draft, and confirm. Read the new ADR in `docs/adr/` and its `gap` task, if
   Compliance has no fitness function yet. Run it again for each new decision.

On a project that already has code, `to-characteristics` also runs alone, from the project root
(`/arch:to-characteristics`) or with a path (`/arch:to-characteristics path/to/project`), and so
does `to-adr`.

## Layout

```
.claude-plugin/plugin.json      plugin manifest (name "arch")
reference/vocabulary.md         single source of the architecture vocabulary
reference/language.md           single source of the language rule for what the skills write
reference/templates/            single source of the document templates
reference/scripts/              single source of the scripts the skills ship (NextAdrNumber.java)
scripts/sync-references.sh      copies reference/ into each skill (maintainer tool)
scripts/check-language.sh       checks the language rule and the skill contracts (maintainer and CI tool)
scripts/test-check-language.sh  mutation test: each break must make check-language.sh fail (CI tool)
skills/<skill>/SKILL.md         instructions to the agent
skills/<skill>/agents/openai.yaml   Codex policy: explicit invocation only
skills/<skill>/references/      copies of the vocabulary, language rule and templates the skill uses
skills/<skill>/scripts/         copies of the scripts the skill runs: NextAdrNumber.java gives the next free ADR number (grill-architecture, to-adr)
```

After editing anything in `reference/`, run `scripts/sync-references.sh`, and check with
`scripts/sync-references.sh --check`. Before a commit, run `scripts/check-language.sh`, and
`scripts/test-check-language.sh` after changing it; the CI runs both.

## Requirements

- Claude Code or Codex with plugin support.
- Java 11 or later, for the ADR numbering script.
- `gh`, authenticated, only if you choose GitHub Issues as the tracker.
