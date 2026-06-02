# Aula 05: Dynamic Routing, Roteamento de Tickets de TI

> **Padrão**: Dynamic Routing (troca de modelo LLM em runtime)
> **Case**: Tickets de TI atendidos por um único agente que escolhe o modelo conforme a categoria
> **Stack**: Quarkus 3.35.2, Java 25, LangChain4j Agentic, Ollama (`deepseek-v4-pro:cloud` para casos complexos, `gpt-oss:20b-cloud` para casos simples)

---

## O que você vai aprender

O Dynamic Routing é o pattern em que **um único agente troca o `ChatModel` em tempo de execução** com base em uma decisão dinâmica. Aqui essa decisão é a categoria do ticket. Tickets curtos e operacionais (FAQ, FEATURE) usam um modelo barato e rápido; tickets complexos (BUG, SECURITY) usam um modelo robusto.

A composição da aula é simples:

```
                 @Inject TicketClassifier classifier;
                 @Inject @ModelName("smaller") ChatModel modeloRapido;
                 @Inject                        ChatModel modeloRobusto;
                                  │
                                  ▼
                       ┌────────────────────────┐
                       │     TicketRouter       │
                       │     (orchestrator)     │
                       └────────────┬───────────┘
                                    │
                  ┌─────────────────┼─────────────────┐
                  ▼                                   ▼
        ┌────────────────────┐              ┌────────────────────┐
        │ TicketClassifier   │              │   TicketHandler    │
        │ @RegisterAiService │              │     (interface)    │
        │ (smaller)          │              │ AgenticServices    │
        │ classifica em      │              │ .agentBuilder()    │
        │ FAQ/BUG/SEC/FEAT   │              │ .chatModel(modelo) │
        │                    │              │ .build()           │
        └────────────────────┘              └────────────────────┘
                                                     │
                            ┌────────────────────────┴────────────────────────┐
                            ▼                                                 ▼
                  modeloRapido (FAQ/FEATURE)                       modeloRobusto (BUG/SECURITY)
                  gpt-oss:20b-cloud                                deepseek-v4-pro:cloud
```

## Como rodar

```bash
cd modulo03/03_agentes/aula05/ticket-router
./mvnw quarkus:dev
```

Abra <http://localhost:8080/>.

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── TicketClassifier.java   classifica em FAQ, BUG, SECURITY ou FEATURE (modelName="smaller")
│   ├── TicketHandler.java      AI Service único; responde adaptando estrutura à categoria
│   └── ExampleGenerator.java   AI Service auxiliar; gera tickets de exemplo
├── workflow/
│   └── TicketRouter.java       orchestrator: classifica, escolhe modelo, constrói handler via builder
├── rest/
│   └── ExampleResource.java    endpoint /api/example/ticket
├── dto/                         TicketCategory, ModelTier, TicketResponse, TicketEvent, TicketInput
└── TicketWebsocket.java         endpoint /ws/tickets
```

### Pontos-chave

#### 1. `TicketClassifier` define a categoria

```java
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface TicketClassifier {
    @SystemMessage("Categorize em FAQ, BUG, SECURITY ou FEATURE...")
    @UserMessage("Ticket: {ticket}")
    @Agent(name = "classifier",
           description = "Classifica um ticket de TI em FAQ, BUG, SECURITY ou FEATURE",
           outputKey = "category")
    TicketCategory classify(@V("ticket") String ticket);
}
```

#### 2. `TicketHandler` é uma interface "pura"

A interface declara um único método de resposta. Note que ela **não usa `@RegisterAiService`**. A instância é construída pelo orchestrator a cada requisição, via `AgenticServices.agentBuilder(TicketHandler.class)`, fornecendo o `ChatModel` específico daquela chamada.

```java
public interface TicketHandler {
    @SystemMessage("Adapte tom e estrutura à categoria...")
    @UserMessage("Categoria: {category}\n\nTicket: {ticket}")
    @Agent(name = "handler",
           description = "Responde tickets de TI adaptando estrutura à categoria",
           outputKey = "answer")
    String responder(@V("category") TicketCategory category, @V("ticket") String ticket);
}
```

#### 3. `TicketRouter` injeta dois `ChatModel` e troca em runtime

```java
@Inject
@ModelName("smaller")
ChatModel modeloRapido;        // gpt-oss:20b-cloud

@Inject
ChatModel modeloRobusto;       // deepseek-v4-pro:cloud (default)
```

O qualifier `@ModelName("smaller")` do Quarkus aponta para a configuração nomeada `quarkus.langchain4j.ollama.smaller.*`. Sem qualifier, o `ChatModel` injetado é o configurado em `quarkus.langchain4j.ollama.chat-model.*`. Os dois beans coexistem.

Para cada ticket, o orchestrator decide qual modelo usar e constrói o handler com `AgenticServices.agentBuilder(...)`:

```java
TicketCategory categoria = classifier.classify(ticket);
ChatModel selecionado = tierFor(categoria) == ModelTier.FAST ? modeloRapido : modeloRobusto;

TicketHandler handler = AgenticServices.agentBuilder(TicketHandler.class)
        .chatModel(selecionado)
        .build();
String resposta = handler.responder(categoria, ticket);
```

A função `tierFor(...)` é local e simples:

```java
static ModelTier tierFor(TicketCategory c) {
    return switch (c) {
        case FAQ, FEATURE -> ModelTier.FAST;
        case BUG, SECURITY -> ModelTier.ROBUST;
    };
}
```

#### 4. Configuração em `application.properties`

```properties
quarkus.langchain4j.ollama.chat-model.model-id=deepseek-v4-pro:cloud
quarkus.langchain4j.ollama.chat-model.temperature=0.4

quarkus.langchain4j.ollama.smaller.chat-model.model-id=gpt-oss:20b-cloud
quarkus.langchain4j.ollama.smaller.chat-model.temperature=0
```

O Quarkus produz dois beans `ChatModel` a partir dessas duas seções: o `default` e o nomeado `smaller`.

## Mapa de roteamento

| Categoria | Tier | Modelo | Latência típica |
|---|---|---|---|
| FAQ | FAST | `gpt-oss:20b-cloud` | 1 a 4 s |
| FEATURE | FAST | `gpt-oss:20b-cloud` | 1 a 4 s |
| BUG | ROBUST | `deepseek-v4-pro:cloud` | 20 a 50 s |
| SECURITY | ROBUST | `deepseek-v4-pro:cloud` | 20 a 50 s |

## O que observar

| Observação | Explica |
|---|---|
| Sequência WS: `RECEIVED`, `CLASSIFICATION`, `ANSWER` | O orchestrator emite 3 marcos para a UI |
| Logs do servidor mostram o modelo usado por categoria | A linha `>> categoria=... tier=... modelo=...` no log do `TicketRouter` |
| Resposta a FAQ chega rapido; a BUG demora mais | O orchestrator escolheu o `modeloRapido` ou o `modeloRobusto` conforme o caso |
| O nome do agent reportado na UI é sempre `TicketHandler` | É o mesmo agente para todas as categorias; o que muda é o `ChatModel` |

## Para experimentar

* Edite o `System Message` do `TicketHandler` para dar instruções diferentes por categoria. Veja o impacto da clareza do prompt na qualidade da resposta.
* Adicione uma 5ª categoria (por exemplo `BILLING`): basta atualizar `TicketCategory`, `tierFor(...)`, o classificador e o frontend.
* Adicione um terceiro `ChatModel` (por exemplo um modelo local sem Cloud) via nova seção `quarkus.langchain4j.ollama.<nome>.*` e amplie a lógica de roteamento.
* Capture o tempo de resposta e o custo (token) por categoria para comparar tiers.

## Próxima aula

Aula 06: Human-in-the-Loop, aprovação de desconto B2B com `@LoopAgent` e `@HumanInTheLoop` binário.
