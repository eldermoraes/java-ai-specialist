# Aula 05: Dynamic Routing, Roteamento de Tickets de TI

> **Padrão**: Dynamic Routing (troca de modelo LLM em runtime)
> **Case**: Tickets de TI atendidos por um único agente que escolhe o `ChatModel` conforme a categoria
> **Stack**: Quarkus 3.35.2, Java 25, LangChain4j 1.15.1, Quarkiverse LangChain4j 1.11.0.CR1, Ollama (`deepseek-v4-pro:cloud` e `gpt-oss:20b-cloud`)

---

## O que você vai aprender

O Dynamic Routing é o pattern em que **um único agente troca o `ChatModel` em tempo de execução** com base em uma decisão dinâmica. A aula demonstra a forma canônica introduzida na LangChain4j 1.15.0 (PR #5158) e disponível na Quarkiverse LangChain4j 1.11.0.CR1: a annotation `@ChatModelSupplier` aplicada a um método static que recebe valores do `AgenticScope` via `@V` e devolve o `ChatModel` apropriado a cada chamada do agente.

```
                  @Inject TicketAgent ticketAgent;
                                │
                                ▼
                  ┌────────────────────────┐
                  │     @SequenceAgent     │   TicketAgent.java
                  │   (TicketClassifier,   │   1) classifica
                  │    TicketHandler)      │   2) responde
                  └────────────┬───────────┘
                               │
                ┌──────────────┴──────────────┐
                ▼                             ▼
       ┌────────────────────┐       ┌────────────────────┐
       │ TicketClassifier   │       │   TicketHandler    │
       │ @RegisterAiService │       │  (interface pura)  │
       │   (modelo smaller) │       │ @ChatModelSupplier │
       │ classifica em      │       │ static method      │
       │ FAQ/BUG/SEC/FEAT   │       │ escolhe o modelo   │
       └────────────────────┘       └─────────┬──────────┘
                                              │
                            ┌─────────────────┼─────────────────┐
                            ▼                                   ▼
                  ChatModel @ModelName("smaller")       ChatModel @Default
                  gpt-oss:20b-cloud                     deepseek-v4-pro:cloud
                  (categorias FAQ, FEATURE)             (categorias BUG, SECURITY)
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
│   ├── TicketHandler.java      agente único; @ChatModelSupplier escolhe o modelo
│   └── ExampleGenerator.java   AI Service auxiliar (gera tickets de exemplo)
├── workflow/
│   ├── TicketAgent.java        @SequenceAgent compõe classifier + handler + @Output
│   └── TicketRouter.java       wrapper Multi<TicketEvent>
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

#### 2. `TicketHandler` declara o agente e o supplier de modelo

A interface tem o método `@Agent` que responde o ticket e um método `static @ChatModelSupplier` que recebe `@V("category") TicketCategory` do `AgenticScope` e devolve o `ChatModel` apropriado a cada chamada. **Não há `@RegisterAiService`**: a Quarkus extension reconhece a interface por causa do `@Agent` e respeita o supplier ao construir o agente dentro da composição.

```java
public interface TicketHandler {

    @SystemMessage("Adapte tom e estrutura à categoria...")
    @UserMessage("Categoria: {category}\n\nTicket: {ticket}")
    @Agent(name = "handler",
           description = "Responde tickets de TI adaptando estrutura à categoria",
           outputKey = "answer")
    String responder(@V("category") TicketCategory category, @V("ticket") String ticket);

    @ChatModelSupplier
    static ChatModel chatModel(@V("category") TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> CDI.current()
                    .select(ChatModel.class, ModelName.Literal.of("smaller"))
                    .get();
            case BUG, SECURITY -> CDI.current()
                    .select(ChatModel.class)
                    .get();
        };
    }
}
```

O static method `chatModel(@V("category") ...)` é **invocado pelo framework a cada chamada** do agente. Como ele faz lookup via `CDI.current().select(ChatModel.class, ModelName.Literal.of("smaller"))`, os `ChatModel` beans usados são exatamente os configurados em `application.properties`. Sem duplicação de configuração.

#### 3. `TicketAgent` compõe classifier + handler em sequência

A composição declarativa é o que ativa o supplier. Em invocação standalone (`@Inject TicketHandler handler; handler.responder(...)`) o supplier não é honrado: a feature exige composição agentic.

```java
public interface TicketAgent {

    @SequenceAgent(
            outputKey = "ticketResponse",
            subAgents = { TicketClassifier.class, TicketHandler.class })
    TicketResponse processar(@V("ticket") String ticket);

    @Output
    static TicketResponse assemble(AgenticScope scope) {
        TicketCategory categoria = (TicketCategory) scope.readState("category");
        String resposta = (String) scope.readState("answer");
        ModelTier tier = tierFor(categoria);
        return new TicketResponse(categoria, tier, tier.modelId(), "TicketHandler", resposta, 0L);
    }

    static ModelTier tierFor(TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> ModelTier.FAST;
            case BUG, SECURITY -> ModelTier.ROBUST;
        };
    }
}
```

#### 4. `TicketRouter` injeta a interface composta

O orchestrator é trivial: recebe o texto do ticket pelo WebSocket, delega ao `TicketAgent` e emite os eventos `RECEIVED`, `CLASSIFICATION` e `ANSWER`.

```java
@Inject
TicketAgent ticketAgent;

private void runRouting(String ticket, MultiEmitter<? super TicketEvent> emitter) {
    emitter.emit(TicketEvent.received(preview(ticket)));
    TicketResponse response = ticketAgent.processar(ticket);
    emitter.emit(TicketEvent.classification(
            response.category(), response.tier(), response.modelId(), response.agentName()));
    emitter.emit(TicketEvent.answer(response));
    emitter.complete();
}
```

#### 5. Override de versão no `pom.xml`

A feature `@ChatModelSupplier` com parâmetros `@V` chegou na Quarkiverse LangChain4j 1.11.0.CR1 (langchain4j-agentic 1.15.1-beta25). Como o Quarkus 3.35.2 ainda resolve a versão estável anterior, sobrescrevemos o BOM Quarkiverse direto no projeto:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${quarkus.platform.group-id}</groupId>
            <artifactId>${quarkus.platform.artifact-id}</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkiverse.langchain4j</groupId>
            <artifactId>quarkus-langchain4j-bom</artifactId>
            <version>1.11.0.CR1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 6. Configuração em `application.properties`

```properties
quarkus.langchain4j.ollama.chat-model.model-id=deepseek-v4-pro:cloud
quarkus.langchain4j.ollama.chat-model.temperature=0.4

quarkus.langchain4j.ollama.smaller.chat-model.model-id=gpt-oss:20b-cloud
quarkus.langchain4j.ollama.smaller.chat-model.temperature=0
```

O Quarkus produz dois `ChatModel` beans a partir dessas seções: o default (acessado via `CDI.current().select(ChatModel.class)`) e o nomeado `smaller` (acessado via `ModelName.Literal.of("smaller")`).

## Mapa de roteamento

| Categoria | Tier | Modelo | Latência típica |
|---|---|---|---|
| FAQ | FAST | `gpt-oss:20b-cloud` | 5 a 20 s |
| FEATURE | FAST | `gpt-oss:20b-cloud` | 5 a 15 s |
| BUG | ROBUST | `deepseek-v4-pro:cloud` | 30 a 50 s |
| SECURITY | ROBUST | `deepseek-v4-pro:cloud` | 20 a 40 s |

## O que observar

| Observação | Explica |
|---|---|
| Sequência WS: `RECEIVED`, `CLASSIFICATION`, `ANSWER` | O orchestrator emite 3 marcos para a UI |
| Logs do servidor mostram a categoria, tier e modelo escolhidos | A linha `>> categoria=... tier=... modelo=...` no `TicketRouter` |
| Respostas a FAQ chegam mais rapido que a BUG | O `@ChatModelSupplier` escolheu o modelo `smaller` para FAQ e o default para BUG |
| O agent reportado na UI é sempre `TicketHandler` | É o mesmo agente em todas as categorias; o que muda é o `ChatModel` |

## Para experimentar

* Altere a `@ChatModelSupplier` para usar um terceiro `ChatModel` em uma categoria nova. Configure mais uma seção `quarkus.langchain4j.ollama.<nome>.*` em `application.properties` e selecione via `ModelName.Literal.of("<nome>")`.
* Imprima a categoria no static method (`System.out.println` ou logger) para visualizar a decisão a cada chamada.
* Use o supplier para escolher modelos de provedores diferentes (Ollama, OpenAI, Anthropic) coexistindo no mesmo agente.
* Adicione uma 5ª categoria (por exemplo `BILLING`): basta atualizar `TicketCategory`, `tierFor(...)`, o classificador, o supplier e o frontend.

## Próxima aula

Aula 06: Human-in-the-Loop, aprovação de desconto B2B com `@LoopAgent` e `@HumanInTheLoop` binário.
