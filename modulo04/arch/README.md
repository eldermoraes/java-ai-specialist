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
`setup-architecture`, `grill-architecture`, `to-characteristics`, `to-adr`, `to-risk`,
`to-autonomy`, `to-traces` and `check-architecture`.

### If you are Claude Code

1. Run `claude plugin marketplace add eldermoraes/java-ai-specialist`.
   If it says the marketplace already exists, run
   `claude plugin marketplace update java-ai-specialist` instead.
   If cloning over SSH fails, run the same command again with
   `CLAUDE_CODE_PLUGIN_PREFER_HTTPS=1` set in its environment.
2. Run `claude plugin install arch@java-ai-specialist`.
3. Verify: `claude plugin list` shows `arch@java-ai-specialist` enabled, and
   `claude plugin details arch` lists the eight skills.
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
| `to-risk` | `/arch:to-risk` | `$arch:to-risk` | `docs/architecture/risk.md` (one block per dimension: areas, impact and likelihood, score from the risk matrix script, assessors, Direction; Effectful Retry for the whole system), one `high-risk` task per risk of 6 or 9 without mitigation in place |
| `to-autonomy` | `/arch:to-autonomy` | `$arch:to-autonomy` | `docs/architecture/autonomy.md` (one block per effectful action: effect, blast radius, agent identity, permission level, approval, idempotency, hard limits; the kill switch of the system; the three excesses), one `decision` task per effectful action whose autonomy is not settled in the code or in an accepted ADR |
| `to-traces` | `/arch:to-traces` | `$arch:to-traces` | `docs/architecture/traces.md` (the trace of a request; each required attribute with its convention, `gen_ai.*` or the namespace of the application, its condition and its place in the code; cost computed from the tokens; content; sampling; Stack Reference), one `gap` task per required attribute the code does not record, or one for the instrumentation when the system emits no trace |
| `check-architecture` | `/arch:check-architecture` | `$arch:check-architecture` | `docs/architecture/check-report.md` (each item of the dossier Verified, Violated or Not Verified (needs telemetry), with its type and evidence; without a dossier, the non-negotiables of an agent in production; the next command), one `gap` task per item that is not Verified or has no fitness function, reusing the open ones |

The skills run only when you call them: the agent never starts them on its own
(`disable-model-invocation: true` for Claude Code, `agents/openai.yaml` with
`allow_implicit_invocation: false` for Codex).

Each skill works on its own. Without the setup, `grill-architecture`, `to-characteristics`,
`to-adr`, `to-risk`, `to-autonomy`, `to-traces` and `check-architecture` write to the default
locations and keep their tasks as local files in `.scratch/architecture/issues/`. `to-characteristics` needs no brief and no ADR either: it
reads the code of a project, or starts from a two-line description. `to-adr` needs no brief either:
it reads the code where the decision lives. `to-risk` needs no other document: it reads the code
where the external dependencies live. `to-autonomy` needs no other document: it reads the code
where the actions live. `to-traces` needs no other document: it reads the code where a request
starts and where the model is called. `check-architecture` checks whatever the dossier has, and
without a dossier the non-negotiables of an agent in production.

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
7. `/arch:to-risk "availability when the provider fails"`: one dimension per run; answer the round
   (the scores recommended for each area, the technologies the team does not know yet, who
   assesses), read the draft, and confirm. Read `docs/architecture/risk.md` and the `high-risk`
   tasks, and follow the `Next:` line.
8. `/arch:to-autonomy`: answer the two rounds (the design principle, the permission level of each
   action and who approves; then the hard limits, the idempotency and the kill switch), read the
   draft, and confirm. Read `docs/architecture/autonomy.md` and the `decision` tasks, and follow
   the `Next:` line.
9. `/arch:to-traces`: answer the round (the content of prompts and responses, the namespace of the
   application, the sampling), read the draft, and confirm. Read `docs/architecture/traces.md` and
   the `gap` tasks, and follow the `Next:` line.
10. `/arch:check-architecture`: answer the round (the telemetry to read, if any), read the draft,
    and confirm. Read `docs/architecture/check-report.md` and the `gap` tasks, and follow the Next
    Command.

On a project that already has code, `to-characteristics` also runs alone, from the project root
(`/arch:to-characteristics`) or with a path (`/arch:to-characteristics path/to/project`), and so
does `to-adr`.

## Layout

```
.claude-plugin/plugin.json      plugin manifest (name "arch")
reference/vocabulary.md         single source of the architecture vocabulary
reference/language.md           single source of the language rule for what the skills write
reference/templates/            single source of the document templates
reference/scripts/              single source of the scripts the skills ship (NextAdrNumber.java, RiskScore.java)
scripts/sync-references.sh      copies reference/ into each skill (maintainer tool)
scripts/check-language.sh       checks the language rule and the skill contracts (maintainer and CI tool)
scripts/test-check-language.sh  mutation test: each break must make check-language.sh fail (CI tool)
scripts/test-risk-score.sh      checks the scores and levels RiskScore.java prints (CI tool)
skills/<skill>/SKILL.md         instructions to the agent
skills/<skill>/agents/openai.yaml   Codex policy: explicit invocation only
skills/<skill>/references/      copies of the vocabulary, language rule and templates the skill uses
skills/<skill>/scripts/         copies of the scripts the skill runs: NextAdrNumber.java gives the next free ADR number (grill-architecture, to-adr); RiskScore.java scores a risk on the risk matrix (to-risk)
```

After editing anything in `reference/`, run `scripts/sync-references.sh`, and check with
`scripts/sync-references.sh --check`. Before a commit, run `scripts/check-language.sh`, and
`scripts/test-check-language.sh` after changing it, and `scripts/test-risk-score.sh` after
changing the risk matrix script; the CI runs them.

## Requirements

- Claude Code or Codex with plugin support.
- Java 11 or later, for the ADR numbering script and the risk matrix script.
- `gh`, authenticated, only if you choose GitHub Issues as the tracker.
