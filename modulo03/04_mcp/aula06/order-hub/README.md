# Aula 6 (MCP): order-hub · expondo e testando o seu MCP server

> - **Bloco**: MCP · **Foco**: MCP *server* no Quarkus (você troca de lado: sai de quem consome, vira quem expõe)
> - **Case**: a **Central de Pedidos da Cloud For You** (uma empresa B2B fictícia de serviços de nuvem) exposta como MCP server: buscar, listar e cancelar pedidos
> - **Stack**: Quarkus 3.35.2 · Java 25 · `quarkus-mcp-server-http` **1.13.1** (Quarkiverse) · no client: LangChain4j via `quarkus-langchain4j-bom` + Ollama

Este é **um único projeto Maven multi-módulo** que constrói o server, dá a ele os superpoderes (structured output + elicitation) e o testa de verdade:

```
order-hub/                    ← POM pai agregador (sem código)
├── order-hub-server/         ← o MCP server (quem expõe as tools; sem LLM)
└── order-hub-client/         ← o client declarativo (quem consome o server; com LLM)
```

---

## O que você vai aprender

Todo o módulo até aqui te colocou de um lado só do história: o de quem **consome** tools MCP. Aqui você passa a ser quem **expõe**. E quem expõe uma capacidade para o mundo tem responsabilidades que quem só consome não tem: dar forma ao resultado e confirmar antes executar algo destrutivo.

- **Os dois `@Tool` de mesmo nome, em bibliotecas opostas.** `io.quarkiverse.mcp.server.Tool` (aqui, quem **expõe**) não é `dev.langchain4j.agent.tool.Tool` (nos agentes, quem **consome**). É fácil importar a classe errada.
- **snake_case exposto × camelCase Java.** O método é `buscarPedido`; o nome de protocolo, no atributo `name`, é `buscar_pedido` (a convenção de fato do ecossistema MCP).
- **O server não tem LLM.** Procure config de modelo no `order-hub-server`: não tem. Quem raciocina é o agente do outro lado. Consequência: server barato (sem inferência, sem GPU, sem token), e você o constrói e testa **sem modelo nenhum**.
- **A descrição é a interface para o modelo.** Quem lê a descrição da tool é o LLM, não um humano. Capriche como na assinatura de um método público.
- **structuredContent + record.** A tool devolve um `record` tipado (`Pedido`), não texto aleatório, e a extensão o serializa como `structuredContent`.
- **Elicitation + tool annotations = "operações destrutivas nunca autônomas".** A annotation `destructiveHint` **avisa**; a elicitation **confirma** no momento da chamada. As duas metades do human-in-the-loop, agora dentro do protocolo.
- **`ToolCallException` → `isError`, não HTTP 500.** O erro de negócio é transportado dentro da resposta da tool; o modelo lê esse erro e se recupera.

---

## Como rodar

Pré-requisitos: **Java 25**, **Maven 3.9+** (ou o wrapper `./mvnw`) e, para o client de verdade, **Ollama** em `localhost:11434` com `deepseek-v4-pro:cloud`.

### O server

```bash
# na raiz do order-hub
./mvnw -pl order-hub-server quarkus:dev
```

Sobe em `http://localhost:8080`, com o endpoint MCP em `http://localhost:8080/mcp`. Sem LLM, sobe num piscar.

### O client, fechando o loop

Em **outro terminal**, com o server já de pé:

```bash
./mvnw -pl order-hub-client quarkus:dev
```

Sobe em `http://localhost:8081`. Abra `http://localhost:8081/` e pergunte "qual o status do pedido PED-1001?". O agente descobre as tools do **seu** server e as chama.

---

## Estrutura

```
order-hub/
├── pom.xml                              # POM pai agregador (sem herança; agregação pura)
├── order-hub-server/                    # ── o MCP server ──
│   ├── pom.xml                          # BOM io.quarkiverse.mcp 1.13.1; sem dependência de LLM
│   └── src/
│       ├── main/java/com/eldermoraes/
│       │   ├── dto/
│       │   │   ├── Pedido.java          # record → structuredContent de buscar_pedido
│       │   │   └── PedidosDoCliente.java# wrapper (topo do structuredContent é sempre objeto)
│       │   ├── dominio/
│       │   │   └── PedidoRepository.java# fake em memória (ConcurrentHashMap = thread-safe)
│       │   └── mcp/
│       │       └── OrderHubMcp.java      # as 3 tools: buscar/listar/cancelar
│       ├── main/resources/
│       │   └── application.properties   # traffic-logging ligado; sem modelo
│       └── test/java/com/eldermoraes/mcp/
│           └── OrderHubMcpTest.java      # JSON-RPC cru contra /mcp (5 verificações)
└── order-hub-client/                    # ── o client declarativo ──
    ├── pom.xml                          # quarkus-langchain4j-mcp + ollama
    └── src/
        ├── main/java/com/eldermoraes/
        │   ├── ai/AssistentePedidos.java# @RegisterAiService + @McpToolBox("central-pedidos")
        │   └── rest/AssistenteResource.java # POST /api/assistente (@RunOnVirtualThread)
        ├── main/resources/
        │   ├── application.properties   # porta 8081; modelos Ollama; bloco mcp.central-pedidos.*
        │   └── META-INF/resources/index.html # chat da Central de Pedidos
        └── test/java/com/eldermoraes/ai/
            └── AssistentePedidosTest.java# smoke de montagem (passa sem Ollama/server) + IT @Disabled
```

---

## Pontos-chave

### 1. Os dois `@Tool`: cuidado pra não confundir

`OrderHubMcp.java` importa `io.quarkiverse.mcp.server.Tool`, o do lado de quem **expõe**. Nos agentes você usou `dev.langchain4j.agent.tool.Tool`, o do lado de quem **consome**. Mesma palavra, bibliotecas diferentes, trabalhos opostos. Confira sempre o import.

### 2. snake_case × camelCase

```java
@Tool(name = "buscar_pedido", ...)     // nome de protocolo: snake_case
public Pedido buscarPedido(...) { ... } // método Java: camelCase
```

### 3. O server não tem LLM (por isso é barato)

Nenhuma chave de modelo no `application.properties` do server. Ele é uma casca de lógica de negócio publicada pelo protocolo; quem pensa é o client. Serviço HTTP comum: sem inferência, sem GPU, sem token.

### 4. A descrição é a interface para o modelo

As descrições das três tools são caprichadas de propósito: dizem **o que** a tool faz, **quando** usá-la e **o que** espera de cada argumento. Uma descrição vaga produz chamadas erradas; uma precisa, chamadas certas.

### 5. structuredContent + record

`buscar_pedido` tem `structuredContent = true` e retorna um `Pedido`. A extensão gera até um `outputSchema` a partir do record. `listar_pedidos_por_cliente` devolve o wrapper `PedidosDoCliente` porque **o topo do structuredContent é sempre um objeto**, nunca um array solto.

### 6. Elicitation + tool annotations = destrutivo nunca autônomo

`cancelar_pedido` carrega `destructiveHint = true` (o **aviso** declarativo) e usa **elicitation** (a **confirmação** no ato). Fluxo real do código:

1. Pedido não existe? -> `ToolCallException` (vira `isError`).
2. Client **não** suporta elicitation? -> **recusa clara, sem cancelar**. (Para clients stateless existe o padrão MRTR, de confirmação em duas fases, fora do escopo.)
3. Suporta? -> `requestBuilder().setMessage("Confirma o cancelamento do pedido X (cliente Y, valor Z)?").addSchemaProperty("motivo", ...).build().sendAndAwait()`.
4. `actionAccepted()` → cancela e devolve "Pedido X cancelado. Motivo: ...". Senão -> "Cancelamento abortado pelo usuário."

Guardrail da spec: elicitation é para **confirmação e contexto**, **nunca** para credencial (senha, token, cartão).

### 7. `ToolCallException` → `isError` que o modelo lê

Chame `buscar_pedido` com um id que não existe: o server **não** devolve HTTP 500. Devolve um resultado de tool com `isError: true` e o texto do erro. O modelo lê isso e reage (tenta outro id, avisa o usuário). Um 500 mataria a conversa; um `isError` vira contexto para o modelo se recuperar.

---

## O que observar no log

Ligue o traffic logging (já ligado: `quarkus.mcp.server.traffic-logging.enabled=true`) e leia o JSON-RPC do lado de quem **responde**:

| Mensagem JSON-RPC | O que significa                                                                                                                                 |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `initialize` → resposta com `capabilities` e `Mcp-Session-Id` | O handshake: o client abre a sessão e negocia capabilities (inclusive se suporta **elicitation**)                                               |
| `notifications/initialized` | O client avisa que terminou de inicializar (o server responde `202`)                                                                            |
| `tools/list` → `result.tools[]` | O server publica suas tools (nomes snake_case, descrições, `annotations`, `inputSchema`, `outputSchema`)                                        |
| `tools/call` (`buscar_pedido`) → `result.structuredContent` | A tool foi invocada e devolveu **dados tipados** (não texto)                                                                                    |
| `tools/call` com id inexistente → `result.isError: true` | O erro de negócio foi transportado dentro da resposta (não como HTTP 500): o modelo lê e se recupera                                            |
| `elicitation/create` → resposta do usuário | No cancelamento com client compatível: o server **pausa e pergunta**; a resposta traz `action` (ACCEPT/DECLINE/CANCEL) e o `content` (o motivo) |

---

## Roteiro de teste manual

1. **Dev UI → página de MCP server.** Com `quarkus:dev` no server, abra `http://localhost:8080/q/dev-ui/` e ache a página da extensão MCP: inspecione as tools publicadas e chame-as do navegador, sem escrever cliente nenhum.
2. **MCP Inspector, o "Postman do MCP".**
   ```bash
   npx @modelcontextprotocol/inspector
   ```
   Aponte para `http://localhost:8080/mcp` (transporte Streamable HTTP), liste as tools, dispare chamadas com os argumentos que quiser, veja a resposta estruturada voltar. É o protocolo puro, sem agente nem modelo.
3. **Quebre de propósito.** Chame `buscar_pedido` com `PED-9999` (não existe) e veja, no log, o resultado com `isError`, não um HTTP 500.
4. **Feche o loop com o client.** Suba o `order-hub-client` (porta 8081) e converse: ele consome o **seu** server pelo caminho declarativo.
5. **Plugue num agente de verdade.**
   ```bash
   claude mcp add --transport http central-pedidos http://localhost:8080/mcp
   ```
   De dentro do Claude Code/Codex/Copilot/etc: "qual o status do pedido PED-1001?", "cancele o pedido PED-1003" (dispara a elicitation pedindo sua confirmação). O server que você escreveu do zero respondendo a uma ferramenta comercial, sem uma linha de integração dedicada.

---

## Para experimentar

**Troque a Central de Pedidos pelo seu domínio.** A estrutura é a mesma para qualquer negócio: troque `Pedido`/`PedidoRepository` pelo **estoque da sua loja**, pela **sua coleção**, pelo **seu sistema de chamados**. As tools viram `buscar_produto`/`listar_por_categoria`/`baixar_estoque`, a elicitation confirma a operação destrutiva do seu domínio, e o mesmo client passa a conversar com ele. O protocolo é aberto: qualquer agente compatível fala com o seu server.

---
