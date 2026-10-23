# java-ai-specialist

Código das aulas do Método Java AI Specialist, de Elder Moraes. Cada pasta
`moduloNN/<submódulo>/aulaNN/<projeto>` é um projeto autossuficiente, com README próprio.

## Plugins para o seu agente

Este repositório também é um catálogo de plugins para Claude Code e Codex (o arquivo
`.claude-plugin/marketplace.json`, na raiz). Para instalar, peça ao seu agente:

> Install the arch plugin from github.com/eldermoraes/java-ai-specialist, following the "For your
> agent" section of its README.

| Plugin | O que faz | Pasta |
|---|---|---|
| `arch` | Skills de arquitetura agêntica: o agente escreve o dossiê de arquitetura, você decide | [`modulo04/aula01/arch`](modulo04/aula01/arch) |

Quem tem o Atlas conectado tem o conteúdo do curso e as skills lado a lado no mesmo agente.

## For your agent

You are installing a plugin from this repository for your user. Run only the commands below, in a
terminal, for the host you are. Do not run any script from this repository. The commands need
network access and change your host's plugin configuration: ask the user to approve them when your
host asks.

Plugins in this catalog (marketplace `java-ai-specialist`):

| Plugin | Skills |
|---|---|
| `arch` | `setup-architecture`, `grill-architecture` |

### If you are Claude Code

1. Run `claude plugin marketplace add eldermoraes/java-ai-specialist`.
   If it says the marketplace already exists, run
   `claude plugin marketplace update java-ai-specialist` instead.
   If cloning over SSH fails, run the same command again with
   `CLAUDE_CODE_PLUGIN_PREFER_HTTPS=1` set in its environment.
2. Run `claude plugin install arch@java-ai-specialist`.
3. Verify: `claude plugin list` shows `arch@java-ai-specialist` enabled, and
   `claude plugin details arch` lists every skill in the table above for `arch`.
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
