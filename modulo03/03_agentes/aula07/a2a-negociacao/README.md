# Aula 07: Agent-to-Agent (A2A): Negociação Comprador × Vendedor

> **Padrão**: Agent-to-Agent (A2A), dois agentes em **apps separados** dialogando pelo **protocolo A2A 1.0** (SDK Java oficial)
> **Case**: Negociação B2B autônoma, comprador (orçamento + critérios) ↔ vendedor (catálogo + margem)
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · A2A Java SDK 1.0.0.Final (`org.a2aproject.sdk`) · Ollama (`deepseek-v4-pro:cloud`) · Maven multi-módulo

---

## O que você vai aprender

O padrão **Agent-to-Agent (A2A)** é arquiteturalmente diferente dos outros do módulo: aqui há **dois processos Quarkus separados**, cada um com seu próprio agente LLM, conversando pelo **protocolo A2A** — o padrão aberto (criado pelo Google, hoje na Linux Foundation) para interoperabilidade entre agentes. Nada de endpoint REST proprietário: o vendedor **publica um Agent Card**, o comprador **descobre** o agente por ele e envia mensagens **JSON-RPC 2.0** que viram **tasks** com ciclo de vida padronizado.

```
   comprador-app (porta 8080)                    vendedor-app (porta 8081)
   ┌────────────────────────────┐                ┌─────────────────────────────┐
   │  @Inject CompradorAgent    │   discovery    │  @Produces @PublicAgentCard │
   │  (avalia proposta)         │  ────────────▶ │  AgentCard                  │
   │           ▼                │  GET /.well-   │  (skill "negociar")         │
   │  NegociacaoCoordinator     │  known/agent-  │                             │
   │  (loop manual 5 rodadas)   │  card.json     │  @Produces AgentExecutor    │
   │           ▼                │                │   submit → startWork        │
   │  VendedorA2AClient         │   JSON-RPC 2.0 │   → NegociacaoService       │
   │  (Client do SDK oficial,   │  ────────────▶ │      (VendedorAgent LLM)    │
   │   DataPart tipado,         │  POST /        │   → addArtifact(DataPart)   │
   │   contextId=compradorId)   │  "SendMessage" │   → complete()              │
   │                            │  ◀──────────── │           ▼                 │
   │                            │  Task+artifact │  VendedorDashboard (WS)     │
   └────────────────────────────┘                └─────────────────────────────┘
```

Como nas demais aulas do módulo, cada lado tem um **agente real** (`@RegisterAiService` + `@Agent` com `description`). O `CompradorAgent` decide ACEITAR/CONTRAPOR/DESISTIR; o `VendedorAgent` propõe preço respeitando o preço mínimo do catálogo. A novidade desta aula é o **transporte entre os agentes**: os dois apps não conversam por uma API proprietária, e sim pelo protocolo A2A (SDK Java oficial **1.0.0.Final**, groupId `org.a2aproject.sdk`, alinhado à spec A2A 1.0.0).

## Por que um protocolo, e não um endpoint REST qualquer?

Dois apps poderiam trocar JSON por um endpoint REST inventado por nós — mas aí só *esses dois* apps se entenderiam. O ponto do A2A é ser um **padrão aberto de interoperabilidade**: qualquer agente que fale o protocolo conversa com o vendedor desta aula sem ler o código dele. O que o protocolo padroniza:

| Aspecto | Como o A2A resolve |
|---|---|
| **Discovery** | Card público em `GET /.well-known/agent-card.json` descreve skills, transportes e formatos — nada de URL+path combinados por e-mail |
| **Contrato** | O **Agent Card** documenta a skill `negociar` e seu payload |
| **Ciclo de vida** | Cada mensagem vira uma **Task** com id e estados (`submitted → working → completed/failed`) |
| **Multi-turno** | **`contextId`** padronizado: as N rodadas de uma negociação compartilham o mesmo contexto |
| **Envelope** | **JSON-RPC 2.0** (`method: "SendMessage"`), igual para qualquer agente A2A do planeta |

O contrato de **negócio** continua sendo nosso: os records `MensagemNegociacao` e `RespostaVendedor` viajam como JSON estruturado dentro de um `DataPart` — o protocolo padroniza o envelope e o ciclo de vida, não o domínio.

## Como rodar

Em 2 terminais (vendedor primeiro, por causa do discovery):

```bash
# Terminal 1
cd modulo03/03_agentes/aula07/a2a-negociacao/vendedor-app
./mvnw quarkus:dev

# Terminal 2
cd modulo03/03_agentes/aula07/a2a-negociacao/comprador-app
./mvnw quarkus:dev
```

Abra <http://localhost:8080/> (comprador) e <http://localhost:8081/> (dashboard vendedor). A resolução do Agent Card é **lazy**: acontece na primeira negociação e fica cacheada — se o vendedor estiver fora do ar, o comprador emite um `ERROR` claro ("Vendedor A2A indisponível…") em vez de travar.

## Estrutura

```
aula07/a2a-negociacao/
├── pom.xml                          # parent: BOM a2a-java-sdk-bom:1.0.0.Final
├── comprador-app/                   # porta 8080 — lado CLIENTE A2A
│   └── src/main/java/com/eldermoraes/comprador/
│       ├── ai/CompradorAgent.java        # @Agent (decide ACEITAR/CONTRAPOR/DESISTIR)
│       ├── ai/ExampleGenerator.java      # AI Service (gera EntradaCompra de exemplo)
│       ├── a2a/VendedorA2AClient.java    # Client do SDK: discovery + sendMessage síncrono
│       ├── a2a/NegociacaoPayloadMapper.java # record ↔ Map do DataPart (Jackson)
│       ├── negociacao/NegociacaoCoordinator.java # loop manual 5 rodadas
│       ├── ws/CompradorWebSocket.java
│       └── rest/ExampleResource.java     # /api/example/compra
└── vendedor-app/                    # porta 8081 — lado SERVIDOR A2A
    └── src/main/java/com/eldermoraes/vendedor/
        ├── ai/VendedorAgent.java         # @Agent (propõe preço respeitando margem)
        ├── a2a/VendedorAgentCardProducer.java   # @Produces @PublicAgentCard
        ├── a2a/VendedorAgentExecutorProducer.java # @Produces AgentExecutor
        ├── a2a/NegociacaoPayloadMapper.java     # Map do DataPart ↔ record
        ├── negociacao/NegociacaoService.java    # lógica da rodada (catálogo + LLM + WS)
        ├── catalogo/CatalogoService.java # catálogo estático em memória
        ├── rest/CatalogoEndpoint.java    # GET /api/catalogo (frontend)
        └── ws/VendedorDashboard.java     # broadcast read-only
```

As dependências A2A (versões governadas pelo BOM no pom pai):

```xml
<!-- vendedor-app: servidor A2A com transporte JSON-RPC (rotas auto-registradas) -->
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-sdk-reference-jsonrpc</artifactId>
</dependency>

<!-- comprador-app: cliente A2A + transporte JSON-RPC -->
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-sdk-client</artifactId>
</dependency>
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-sdk-client-transport-jsonrpc</artifactId>
</dependency>
```

### Pontos-chave

#### 1. O vendedor publica um Agent Card (`@PublicAgentCard`)

O "cartão de visita" do protocolo: basta produzir o bean e o SDK serve o card em `GET /.well-known/agent-card.json` e registra o endpoint JSON-RPC em `POST /` — zero rotas manuais.

```java
@Produces
@PublicAgentCard
public AgentCard agentCard(@ConfigProperty(name = "vendedor.a2a.public-url") String publicUrl) {
    return AgentCard.builder()
            .name("Vendedor B2B")
            .version("1.0.0")
            .defaultInputModes(List.of("application/json"))
            .skills(List.of(AgentSkill.builder()
                    .id("negociar")
                    .description("Recebe DataPart com MensagemNegociacao {...} e responde RespostaVendedor {...}")
                    .build()))
            .supportedInterfaces(List.of(
                    new AgentInterface(TransportProtocol.JSONRPC.asString(), publicUrl)))
            .build();
}
```

#### 2. O `AgentExecutor` é o lado servidor do protocolo

Cada `SendMessage` vira uma chamada a `execute()`. O `AgentEmitter` controla o ciclo de vida da task — e o erro vira `fail(...)`, que o cliente recebe como `TASK_STATE_FAILED`:

```java
@Produces
public AgentExecutor agentExecutor(NegociacaoService negociacao, NegociacaoPayloadMapper payload) {
    return new AgentExecutor() {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) {
            emitter.submit();
            emitter.startWork();
            try {
                MensagemNegociacao mensagem = payload.toMensagem(extrairDataPart(context.getMessage()));
                RespostaVendedor resposta = negociacao.negociar(mensagem);  // chamada LLM blocking
                emitter.addArtifact(List.of(new DataPart(payload.toMap(resposta))));
                emitter.complete();
            } catch (Exception e) {
                emitter.fail(emitter.newAgentMessage(List.of(new TextPart("Erro: " + e.getMessage())), null));
            }
        }
        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) { emitter.cancel(); }
    };
}
```

O `execute()` roda em worker thread do SDK (`executor-thread-N`) — chamada LLM blocking é segura.

#### 3. Payload tipado via `DataPart` (e um gotcha do SDK)

O contrato `MensagemNegociacao`/`RespostaVendedor` viaja como JSON estruturado num `DataPart` — não como texto livre. A conversão record ↔ `Map` usa Jackson (`convertValue`), com dois cuidados:

```java
private final ObjectMapper mapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)        // ① omite campos null
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true); // ② preserva valores monetários
```

① é obrigatório: o conversor `Struct → Map` do SDK 1.0.0.Final (`A2ACommonFieldMapper.structToMap`) usa `Collectors.toMap`, que **lança NPE com valores null** — e o servidor responde um `Internal Error` opaco e sem `id`, que quebra o parse no cliente. Na rodada 1, `ultimoValorProposto` é null: omitido do payload, o vendedor o trata como "use o preço de tabela" (campo ausente vira null no record ao desserializar — o contrato se mantém).

#### 4. Ponte síncrona no cliente: `Client` + consumers por chamada

O `Client` do SDK entrega respostas via **consumers** (estilo callback). O loop do coordinator precisa de request→response síncrono por rodada — a ponte é um `CompletableFuture` completado por consumers registrados **por chamada** (negociações concorrentes não se misturam):

```java
// criação (uma vez, lazy): discovery + transporte JSON-RPC em modo blocking
AgentCard card = A2A.getAgentCard(vendedorUrl);   // GET /.well-known/agent-card.json
Client client = Client.builder(card)
        .clientConfig(new ClientConfig.Builder().setStreaming(false).build())  // "SendMessage" blocking
        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
        .build();

// por rodada: Message com DataPart + contextId, future completado pelo consumer
Message a2aMessage = Message.builder()
        .role(Message.Role.ROLE_USER)
        .messageId(UUID.randomUUID().toString())
        .contextId(mensagem.compradorId())     // multi-turno A2A: 1 contexto = 1 negociação
        .parts(new DataPart(payload.toMap(mensagem)))
        .build();
CompletableFuture<RespostaVendedor> futuro = new CompletableFuture<>();
client.sendMessage(a2aMessage,
        List.of((event, agentCard) -> completar(event, futuro)),  // consumers SÓ desta chamada
        futuro::completeExceptionally, null);
return futuro.get(timeoutSegundos, TimeUnit.SECONDS);
```

O consumer extrai o `DataPart` dos artifacts da `Task` (`TaskEvent → task.artifacts() → parts`) e converte de volta para `RespostaVendedor`.

#### 5. O loop de negociação continua no orchestrator Java

A2A é entre **apps**, não dentro de um único JVM — o `@LoopAgent` declarativo orquestra sub-agents do mesmo processo. Aqui o loop é código Java imperativo, e o `NegociacaoCoordinator` não sabe nada de protocolo: ele injeta o `VendedorA2AClient` e chama um método síncrono comum:

```java
@Inject
VendedorA2AClient vendedorRemoto;

// dentro do loop de rodadas:
RespostaVendedor resp = vendedorRemoto.negociar(new MensagemNegociacao(...));
```

Todo o A2A (discovery, JSON-RPC, task, DataPart) fica **isolado no pacote `a2a/`** — domínio e orquestração não dependem do transporte.

#### 6. `ExampleGenerator` retorna `EntradaCompra` tipada via LLM

```java
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {
    @SystemMessage("...gere cenário de compra B2B variando produto/orçamento/prazo...")
    EntradaCompra entradaExemplo();
}
```

Frontend chama `/api/example/compra` e preenche os 4 campos do formulário a partir do JSON.

## Timeouts em cadeia

Cada rodada atravessa três camadas com timeout próprio — cada uma precisa exceder a anterior:

| Camada | Config | Valor | Onde |
|---|---|---|---|
| LLM (Ollama) | `quarkus.langchain4j.timeout` | 120s | ambos os apps |
| Servidor A2A (espera do executor em modo blocking) | `a2a.blocking.agent.timeout.seconds` | 150s | vendedor-app (default do SDK é **30s** — estouraria com LLM lento!) |
| Cliente A2A (espera fim-a-fim da rodada) | `vendedor.a2a.timeout-segundos` | 180s | comprador-app (config própria, usada no `futuro.get`) |

## Trade-off: `@A2AClientAgent` declarativo

O LangChain4j Agentic tem **`@A2AClientAgent(a2aServerUrl = "...")`**, que substituiria o `VendedorA2AClient` imperativo por uma anotação. Por que ainda não:

- O `langchain4j-agentic-a2a` **lançado** ainda depende do SDK antigo (`io.github.a2asdk` 0.3.x), que fala o protocolo v0.3 — **incompatível** com um servidor 1.0 (métodos JSON-RPC diferentes: `message/send` → `SendMessage`).
- O `main` do LangChain4j já aponta para `org.a2aproject.sdk:1.0.0.CR1`, mas sem release; o quarkus-langchain4j ainda não expõe.

Quando o quarkus-langchain4j atualizar, a troca imperativo → declarativo fica confinada ao `VendedorA2AClient` — o coordinator e o lado servidor (`@PublicAgentCard` + `AgentExecutor`) não mudam.

## O que observar

| Observação | Explica… |
|---|---|
| `curl http://localhost:8081/.well-known/agent-card.json` | Discovery: card com skill `negociar`, `supportedInterfaces` JSONRPC e `protocolVersion: "1.0"` |
| Sequência WS comprador: `INICIADA → RODADA×N → ACORDO/IMPASSE` | Loop emite eventos por rodada |
| Log do vendedor: `A2A task=<uuid> contextId=comp-xxx — rodada N` | Cada rodada é uma **task nova** no **mesmo contextId** (multi-turno A2A) |
| Log do comprador: `Agent Card resolvido: Vendedor B2B v1.0.0` | Resolução lazy + cache do card |
| Executor roda em `executor-thread-N` | Worker pool do SDK — LLM blocking seguro |
| Dashboard vendedor mostra cada rodada em tempo real | Broadcast WebSocket read-only |
| 2 chamadas LLM por rodada (comprador + vendedor) | Cada agente roda no seu app |

Para ver o protocolo cru (dispara 1 chamada LLM real):

```bash
curl -s -X POST http://localhost:8081/ -H 'Content-Type: application/json' -d '{
  "jsonrpc": "2.0", "id": "curl-1", "method": "SendMessage",
  "params": {"message": {"messageId": "m-1", "contextId": "comp-curl", "role": "ROLE_USER",
    "parts": [{"data": {"compradorId": "comp-curl", "rodada": 1,
      "produto": "licenca software", "mensagem": "Procuro licença, orçamento R$ 2200"}}]}}}'
```

A resposta é uma `Task` completa: `status.state: TASK_STATE_COMPLETED` e o artifact com a `RespostaVendedor`.

## Para experimentar

- Aumente o orçamento do comprador acima do preço mínimo do vendedor: acordo em 1 rodada
- Diminua orçamento abaixo do preço mínimo: impasse com `limiteAtingido=true` do vendedor
- Mude `negociacao.max-rodadas=3`: força impasse mais rápido
- Derrube o vendedor e tente negociar: `ERROR` imediato com mensagem clara (resolução/conexão)
- Adicione um terceiro app (terceiro agente intermediário): com A2A real, ele descobre os dois pelo Agent Card — clássico em supply chains

## Conclusão do módulo

Você passou por 6 padrões em 6 aulas, todos usando `@Agent` + composições declarativas (`@ParallelAgent`, `@ParallelMapperAgent`, `@SequenceAgent`, `@SupervisorAgent`, `@ConditionalAgent`, `@LoopAgent`, `@HumanInTheLoop`) ou comunicação inter-app — nesta aula, o **protocolo A2A 1.0 com o SDK Java oficial**. Os 3 conceitos centrais (`@Agent` em workers + composição declarativa em interfaces marker + `@Inject` direto da interface composta) formam o vocabulário canônico do framework agentic em Quarkus; o A2A acrescenta o quarto: **interoperabilidade entre agentes de processos (e fornecedores) diferentes via protocolo aberto**.
