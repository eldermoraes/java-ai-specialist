# arch: agentic architecture skills

Skills that write the architecture documentation of an agentic system. Your agent asks, writes and
keeps the files; you make the decisions. Works in Claude Code and in Codex.

This folder is the plugin as of Module 4, lesson 1.

## Install

Ask your agent: *Install the arch plugin from github.com/eldermoraes/java-ai-specialist, following
the "For your agent" section of its README.* The instructions your agent follows are in the
[repository README](../../../README.md#for-your-agent).

## Skills

| Skill | Claude Code | Codex | Writes |
|---|---|---|---|
| `setup-architecture` | `/arch:setup-architecture` | `$arch:setup-architecture` | `docs/architecture/README.md`, `docs/adr/`, `docs/agents/architecture.md`, the tracker labels |
| `grill-architecture` | `/arch:grill-architecture` | `$arch:grill-architecture` | `docs/architecture/brief.md`, `docs/adr/NNNN-agent-workflow-or-multi-agent.md`, one `decision` task per open decision |

Both skills run only when you call them: the agent never starts them on its own
(`disable-model-invocation: true` for Claude Code, `agents/openai.yaml` with
`allow_implicit_invocation: false` for Codex).

Each skill works on its own. `grill-architecture` without the setup writes to the default
locations and keeps its tasks as local files in `.scratch/architecture/issues/`.

## Typical run

1. `/arch:setup-architecture`: choose the tracker (GitHub Issues or local files) and the layout.
2. `/arch:grill-architecture "an agent that answers the customers of a telecom and can open a ticket in the support system"`:
   answer the rounds of questions; each comes with a recommendation.
3. Read `docs/architecture/brief.md` and `docs/adr/0001-agent-workflow-or-multi-agent.md`.

## Layout

```
.claude-plugin/plugin.json      plugin manifest (name "arch")
reference/vocabulary.md         single source of the architecture vocabulary
reference/templates/            single source of the document templates
scripts/sync-references.sh      copies reference/ into each skill (maintainer tool)
skills/<skill>/SKILL.md         instructions to the agent
skills/<skill>/agents/openai.yaml   Codex policy: explicit invocation only
skills/<skill>/references/      copies of the vocabulary and templates the skill uses
skills/grill-architecture/scripts/NextAdrNumber.java   next free ADR number (java NextAdrNumber.java docs/adr)
```

After editing anything in `reference/`, run `scripts/sync-references.sh`, and check with
`scripts/sync-references.sh --check`.

## Requirements

- Claude Code or Codex with plugin support.
- Java 11 or later, for the ADR numbering script.
- `gh`, authenticated, only if you choose GitHub Issues as the tracker.
