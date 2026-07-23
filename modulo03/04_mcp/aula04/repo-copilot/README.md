# Aula 04 (MCP): Repo Copilot · o caminho declarativo (clients MCP no Quarkus)

> - **Módulo**: MCP 
> - **Foco**: clients MCP declarativos + produção
> - **Case**: o mesmo assistente da aula03 (responde sobre arquivos de um projeto usando tools de servidores MCP), reconstruído sem uma linha de código para montar as peças
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j via `quarkus-langchain4j-bom` · Ollama (`deepseek-v4-pro:cloud`)

---

## O que você vai aprender

Na aula03 montamos o MCP **à mão**: um `StdioMcpTransport` e um
`StreamableHttpMcpTransport`, um `DefaultMcpClient` para cada, um `McpToolProvider`
agregando tudo, e um `Supplier<ToolProvider>` que o `@RegisterAiService` recebia. Cada
peça do protocolo ficava visível: esse era o objetivo pedagógico.

Agora fazemos o **mesmo agente** do jeito que você usaria em produção: **a montagem vira
configuração**. Os clients MCP nascem do `application.properties`
(`quarkus.langchain4j.mcp.*`), o agente pede as tools com `@McpToolBox`, e as classes que
ligavam as peças **somem**. E de brinde vêm as peças de produção: Dev UI, health checks,
autenticação client-side, métricas, traces (OpenTelemetry) e Langfuse.

**Mapa de tradução (aula03 → aula04):**

| Aula03 (em código) | Aula04 (declarativo) |
|---|---|
| `StdioMcpTransport` (`.command(...)`) | `quarkus.langchain4j.mcp.<nome>.transport-type=stdio` + `.command=...` |
| `StreamableHttpMcpTransport` (`.url(...)`) | `quarkus.langchain4j.mcp.<nome>.transport-type=streamable-http` + `.url=...` |
| `McpClients` com `@PostConstruct` / `@PreDestroy` | ciclo de vida do client é da **extensão** (abre no boot, fecha no shutdown) |
| `ProjetoToolProviderSupplier` + `toolProviderSupplier=...` | `@McpToolBox({"filesystem","deepwiki"})` no método |
| `try/catch` de resiliência no boot | `quarkus.langchain4j.mcp.<nome>.enabled=false` |

## Como rodar

Pré-requisitos:

- **Ollama** em `localhost:11434` (Ollama Cloud) com o modelo `deepseek-v4-pro:cloud` disponível.
- **Node/npx** no PATH (o servidor de filesystem é um pacote npm que o `npx` baixa e executa).
- Acesso à internet (para o `npx` e para o servidor remoto DeepWiki).
- **Docker/Podman**: necessário para os Dev Services do **Langfuse** (ver notas no fim).
  Se a sua máquina não tiver ou não aguentar, veja o **fallback** logo abaixo.

```bash
cd modulo03/04_mcp/aula04/repo-copilot
./mvnw quarkus:dev
```

Abra <http://localhost:8080/> e pergunte, por exemplo:

- *"Quais arquivos existem neste projeto e o que faz o README?"* → usa as tools de **filesystem**.
- *"No repositório quarkusio/quarkus, o que é o Quarkus?"* → usa a tool remota do **DeepWiki**.

Ou via `curl`:

```bash
curl -s -X POST http://localhost:8080/api/assistente \
  -d 'Quais arquivos .java existem e o que cada um faz?'
```

> Para apontar o assistente para **outro** projeto, mude `assistente.projeto.diretorio`
> no `application.properties` (ou passe `-Dassistente.projeto.diretorio=/caminho/do/seu/projeto`).

## Estrutura do código

```
repo-copilot/
├── pom.xml                                   # + mcp (agora de verdade) + health + micrometer + otel + langfuse
└── src/main/
    ├── java/com/eldermoraes/
    │   ├── ai/
    │   │   └── AssistenteProjeto.java        # @AiService com @McpToolBox, sem toolProviderSupplier
    │   ├── mcp/
    │   │   └── TokenAuthProvider.java        # auth client-side p/ um server protegido (par do client "protegido")
    │   └── rest/
    │       └── AssistenteResource.java       # POST /api/assistente (@RunOnVirtualThread)
    └── resources/
        ├── application.properties            # ⭐ a configuração do MCP fica toda aqui agora (quarkus.langchain4j.mcp.*)
        └── META-INF/resources/index.html     # chat simples
```

Repare no que **desapareceu** em relação à aula03:

- **`McpClients.java`**: não existe mais. Os clients nascem do `application.properties`.
- **`ProjetoToolProviderSupplier.java`**: não existe mais. Quem escolhe as tools é o
  `@McpToolBox` no método do agente.

Essa ausência **é** o conteúdo da aula.

### Pontos-chave

#### 1. O bloco `quarkus.langchain4j.mcp.*`

Três clients declarados por configuração: `filesystem` (stdio), `deepwiki`
(streamable-http) e `protegido` (desligado). O `application.properties` está **todo
comentado** mapeando cada chave para a peça equivalente da aula03. Leia-o de cima a baixo:
é o coração da aula.

#### 2. `@McpToolBox` e a seleção por método

No `AssistenteProjeto`, o método pede `@McpToolBox({"filesystem", "deepwiki"})`. A seleção
é **por método**: outro método poderia pedir só `@McpToolBox("filesystem")` e receber
menos tools no request. Prompt mais enxuto, menos token por chamada.

#### 3. `TokenAuthProvider` + client `protegido` + a disciplina do `${MCP_TOKEN:}`

O `TokenAuthProvider` (`@McpClientName("protegido")`) mostra como o agente se **autentica**
num server protegido: devolve um `Bearer <token>`. O client `protegido` está
`enabled=false` (só demonstra o par). E o token vem de `assistente.mcp.token=${MCP_TOKEN:}`,
com **default vazio** porque `MCP_TOKEN` normalmente não existe na máquina do aluno. O
provider injeta esse token como `Optional<String>` justamente por isso: o SmallRye Config
trata string vazia como ausência de valor (um `String` obrigatório receberia `null` e a
aplicação nem subiria). O valor real **nunca** é hardcoded nem comitado.

#### 4. Server MCP é dependência de infraestrutura

Cada client MCP declarado entra no **readiness** (`/q/health/ready`), via
`McpClientHealthCheck` (que usa o ping do próprio protocolo). Ou seja: um server MCP passa
a ser tratado como qualquer outra dependência de infra (banco, fila): se ele está fora,
seu readiness reflete isso.

## O que observar

Um **roteiro de observação, em ordem**:

| # | Onde | O que observar |
|---|---|---|
| 1 | **Dev UI**: <http://localhost:8080/q/dev-ui> | Painel de **MCP clients**: inspecione as **tools descobertas** (é o `tools/list` do protocolo renderizado na tela) e **experimente uma tool** direto no painel. Veja também a tela de **chat** do quarkus-langchain4j. |
| 2 | **/q/health**: <http://localhost:8080/q/health> | O **readiness** lista os clients MCP (o `McpClientHealthCheck` usa o ping do protocolo). Teste: **derrube o deepwiki** com `enabled=false`, reinicie, e veja o readiness mudar. |
| 3 | **/q/metrics**: <http://localhost:8080/q/metrics> | Métricas **Micrometer** das chamadas de tool: **quantas** chamadas, **quanto tempo** levaram, **quantas falharam**. Faça algumas perguntas antes de abrir. |
| 4 | **Langfuse** | **Traces** da conversa: uma **árvore navegável** com prompt, resposta, tool calls e latência de cada passo. |

## Para experimentar

- **Aponte para o seu projeto:** mude `assistente.projeto.diretorio` para o caminho de um
  repositório seu e pergunte sobre o código dele.
- **Um método só de filesystem:** crie um segundo método no `AssistenteProjeto` com
  `@McpToolBox("filesystem")` e compare, no log, o tamanho da lista de tools no request.
- **Derrube o DeepWiki:** ponha `quarkus.langchain4j.mcp.deepwiki.enabled=false`, reinicie
  e olhe o `/q/health`.
- **Abra o `/q/metrics`** depois de algumas perguntas e ache os contadores das tools.
- **Troque o filtro de spans para `all`** e veja o Langfuse capturar mais do que só os
  spans de IA.

---

### Notas importantes

- **(a) Langfuse Dev Services exige Docker/Podman.** Em dev, o `quarkus-langfuse` sobe o
  stack completo do Langfuse, **6 containers**: web, worker, PostgreSQL, ClickHouse,
  Redis e MinIO. O **primeiro boot demora alguns minutos** baixando as imagens.

- **(b) Fallback.** Se a máquina não aguentar o stack, desligue-o com
  `quarkus.langfuse.devservices.enabled=false` no `application.properties` (a linha já está
  lá, comentada). Todo o resto (agente, MCP, health, métricas e traces OTel) roda sem ele.

- **(c) Governança de dados (OWASP MCP08: Insecure Logging).** O Langfuse **armazena**
  prompts, respostas e payloads das tools. Mover o payload do *log de arquivo* para uma
  *plataforma de traces* **não elimina** o risco de vazamento: apenas o **realoca**. Em
  produção isso vira decisão de governança: **onde** o Langfuse roda, **quem** acessa e
  qual a **retenção** dos dados.

- **(d) Transporte legado.** Existe um valor `transport-type=http` (o antigo **HTTP+SSE**),
  mas ele está sendo depreciado: **não use em projeto novo**. Para HTTP, use sempre
  `streamable-http`.
