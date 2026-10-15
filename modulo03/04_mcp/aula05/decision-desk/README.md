# Aula 05 (módulo MCP): Decision Desk · um toolbox MCP por agente

> - **Padrão**: workflow agêntico sequencial + human-in-the-loop binário, com um toolbox MCP por agente
> - **Case**: pergunta de engenharia entra → Agente Repo lê o repositório local → Agente Ecossistema confronta com a doc externa → gate humano (sim/não) → se "sim", grava um mini-ADR em `decisions/`
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic + MCP · Ollama (`deepseek-v4-pro:cloud` + `gemma4:31b-cloud`) · MCP servers: filesystem (stdio/npx) + DeepWiki (streamable-http)

---

## O que você vai aprender

Você já sabe montar equipes de agentes (módulo de Agentes) e já sabe plugar ferramentas externas via MCP (aulas anteriores deste módulo). Esta aula é o encontro dos dois mundos.

O desenho do time de agentes (usando `@SequenceAgent`) é o mesmo de sempre. A novidade da aula inteira cabe no `@McpToolBox` de cada sub-agente: cada membro recebe o seu toolbox, e nada além: **menor privilégio por agente**. E repare: **não existe um único `@Tool` local neste projeto**; todas as capacidades dos agentes vêm de fora, via MCP.

```
   PERGUNTA
      │
      ▼
 ┌─────────────────────────────┐
 │ ① AgenteRepo                │  @McpToolBox("filesystem")
 │    lê o repositório local   │  → list_directory, read_file, directory_tree, write_file…
 └──────────────┬──────────────┘
                ▼
 ┌─────────────────────────────┐
 │ ② AgenteEcossistema         │  @McpToolBox("deepwiki")
 │    confronta com a doc      │  → ask_question (e só)
 └──────────────┬──────────────┘
                ▼
 ┌─────────────────────────────┐
 │ ③ GateAprovacao (humano)    │  @HumanInTheLoop: grava ou não? (sim/não)
 └──────────────┬──────────────┘
         "sim"  │  "não" → nada é gravado (gate duro)
                ▼
 ┌─────────────────────────────┐
 │ ④ EscritorAdr               │  @McpToolBox("filesystem")  ← a mesma caixa do ①
 │    grava decisions/adr-…md  │  → list_allowed_directories, create_directory, write_file
 └─────────────────────────────┘
```

Duas caixas MCP diferentes, uma por função: **filesystem** para quem lê o repositório e para quem grava o ADR; **deepwiki** para quem confronta com o ecossistema. Quem lê é quem escreve, porque leitura e escrita chegam juntas no mesmo server. Por isso o gate humano vem antes da gravação.

## Como rodar

Pré-requisitos:

- **Ollama** ativo (local ou Cloud) com os modelos `deepseek-v4-pro:cloud` e `gemma4:31b-cloud`.
- **node/npx** disponível: o server MCP de filesystem sobe via `npx -y @modelcontextprotocol/server-filesystem`.
- Acesso à internet para o **DeepWiki** (`https://mcp.deepwiki.com/mcp`).

```bash
cd modulo03/04_mcp/aula05/decision-desk
./mvnw quarkus:dev
```

Abra <http://localhost:8080/>, gere (ou escreva) uma pergunta e leve-a ao desk.

> **Aponte para um repositório seu**: por padrão `desk.repo.diretorio=${user.dir}` faz o server de filesystem enxergar a pasta do próprio projeto. Troque essa propriedade para o caminho de um repositório seu e faça uma pergunta sobre o seu código de verdade: a ferramenta é genérica, o domínio é o seu.

## Estrutura

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── AgenteRepo.java          # @Agent + @McpToolBox("filesystem"): lê o repositório local
│   ├── AgenteEcossistema.java   # @Agent + @McpToolBox("deepwiki"): confronta com a doc externa
│   ├── EscritorAdr.java         # @Agent + @McpToolBox("filesystem"): grava o ADR (mesma caixa do AgenteRepo)
│   └── ExampleGenerator.java    # AI service auxiliar: gera perguntas de exemplo
├── workflow/
│   ├── DecisionDeskAgent.java   # @SequenceAgent de 4 passos + @Output monta a DecisaoDesk
│   ├── GateAprovacao.java       # @HumanInTheLoop binário (sim/não) → ApprovalService
│   ├── RegistroDecisao.java     # @ConditionalAgent + @ActivationCondition: gate duro
│   └── DeskWorkflow.java        # ponte: virtual thread + Multi<DeskEvent> para a UI
├── hitl/
│   └── ApprovalService.java     # bloqueio via CompletableFuture + broadcast (OpenConnections)
├── rest/
│   └── ExampleResource.java     # GET /api/example/pergunta
├── dto/
│   ├── DeskEvent.java           # evento por fase (RECEBIDO/AGUARDANDO_APROVACAO/…)
│   └── DecisaoDesk.java         # resultado final montado pelo @Output
└── DeskWebsocket.java           # /ws/desk: nova pergunta OU decisão do gate
```

## Pontos-chave

### 1. Um `@McpToolBox` por agente = menor privilégio

Cada agente declara a sua caixa e só enxerga as tools daquele server:

```java
@Agent(name = "agenteRepo", outputKey = "analiseRepo")
@McpToolBox("filesystem")
String analisarRepositorio(@V("pergunta") String pergunta);

@Agent(name = "agenteEcossistema", outputKey = "visaoEcossistema")
@McpToolBox("deepwiki")
String consultarEcossistema(@V("pergunta") String pergunta, @V("analiseRepo") String analiseRepo);
```

O AgenteRepo nunca enxerga o DeepWiki; o AgenteEcossistema nunca enxerga o filesystem. É a promessa "Secure & Isolated" da Parte 1 virando prática concreta.

### 2. A granularidade é o server, não a tool: leitura e escrita vêm juntas

`@McpToolBox("filesystem")` entrega **todas** as tools do server de filesystem: as de leitura e as de escrita, juntas. Não dá para pegar só `read_file` sem `write_file` no caminho declarativo. Por isso o mesmo AgenteRepo que lê o repositório é quem, no passo ④ (via `EscritorAdr`, mesma caixa), chama o `write_file`. Não há um segundo agente "de escrita" a quem delegar o risco: o que separa a leitura da escrita é o gate, não a arquitetura.

### 3. Gate humano binário: `@HumanInTheLoop` declarativo + fallback

Na aprovação de desconto (módulo de Agentes) a decisão era **ternária** (aprovar/rejeitar/contrapor) e exigiu Java puro. Aqui a decisão é **binária** (grava ou não grava): exatamente o caso que a anotação `@HumanInTheLoop` foi feita para atender. Por baixo, o mesmo mecanismo: um `CompletableFuture` no `ApprovalService` que bloqueia a virtual thread até o "sim/não" chegar pelo WebSocket.

### 4. Gate duro via `@ConditionalAgent` / `@ActivationCondition`

Negou? O `EscritorAdr` **nem é invocado**, sem nenhum request ao modelo nem tool call de escrita:

```java
@ConditionalAgent(outputKey = "registro", subAgents = { EscritorAdr.class })
String registrar(@V("aprovacao") String aprovacao);

@ActivationCondition(EscritorAdr.class)
static boolean aprovado(@V("aprovacao") String aprovacao) {
    return "sim".equalsIgnoreCase(aprovacao == null ? "" : aprovacao.strip());
}
```

A fronteira é código Java determinístico, não a obediência do modelo.

## O que observar

| Observação no log | Explica…                                                                      |
|---|-------------------------------------------------------------------------------|
| Request do **AgenteRepo** lista `list_directory`, `read_file`, `directory_tree`, `write_file`… | A toolbox `filesystem` inteira: todas as tools do server                      |
| Request do **AgenteEcossistema** lista **só** `ask_question` (e afins do DeepWiki) | A toolbox `deepwiki`, e apenas ela; duas listas diferentes = menor privilégio |
| `read_file` acontece antes da resposta do AgenteRepo | Sequência definida pela tool (pedido→resultado→resposta)            |
| `write_file` só aparece **depois** do seu "sim" | O gate segura a ferramenta externa destrutiva                                 |
| Ao **negar**, **nenhum** request do EscritorAdr acontece | Gate duro do `@ConditionalAgent`: o agente de escrita nem é invocado          |
| Um arquivo `decisions/adr-AAAAMMDD-….md` aparece no disco após o "sim" | O `write_file` do server de filesystem, autorizado por você                   |

## Para experimentar

- **Teste de resiliência (o principal)**: derrube o DeepWiki (tire a internet ou troque `quarkus.langchain4j.mcp.deepwiki.url` por uma URL inválida) e rode de novo. O fluxo deve seguir com o que o **AgenteRepo** levantou do código local, em vez de travar a decisão inteira por causa de um server externo. É o eco do `failIfOneServerFails(false)` que você viu na parte do client: um server que cai não derruba a equipe.
- **Troque o DeepWiki pelo Context7**: `quarkus.langchain4j.mcp.deepwiki.url=https://mcp.context7.com/mcp` (também sem auth).
- **Aponte `desk.repo.diretorio` para outro repositório** e faça uma pergunta sobre ele.
- **Mude o timeout do gate** (`desk.aprovacao.timeout.minutos=1`) e deixe estourar: sem resposta humana, o desk nega por segurança e nada é gravado.
