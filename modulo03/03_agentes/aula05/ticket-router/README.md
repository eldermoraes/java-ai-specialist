# Aula 05 — Dynamic Routing: Roteamento de Tickets de TI

> **Padrão**: Dynamic Routing (via `@ConditionalAgent` + `@ActivationCondition`)
> **Case**: Tickets de TI roteados para FAQ/BUG/SECURITY/FEATURE com **troca de modelo LLM em runtime**
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud` + `gpt-oss:20b-cloud`)

---

## O que você vai aprender

`@ConditionalAgent` é a forma idiomática de roteamento declarativo: declaram-se **N sub-agents** e **N `@ActivationCondition`** (uma por sub-agent). O framework avalia as condições, executa o **primeiro com `true`** e armazena o resultado no `outputKey` único.

O ponto pedagógico chave desta aula é a **troca de modelo LLM em runtime**: cada handler tem seu próprio `@RegisterAiService(modelName=...)`. Quando o `@ConditionalAgent` ativa um handler, o modelo certo é usado automaticamente.

```
                  @Inject TicketAgent ticketAgent;
                                │
                                ▼
                    ┌─────────────────────────┐
                    │     @SequenceAgent      │
                    │  Classifier → Handler   │
                    └────────────┬────────────┘
                                 │
                ┌────────────────┴───────────────┐
                ▼                                ▼
       ┌──────────────────┐            ┌──────────────────┐
       │ TicketClassifier │   then ──▶ │   TicketHandler  │
       │  @Agent          │            │ @ConditionalAgent│
       │  (gpt-oss:20b)   │            └────────┬─────────┘
       └──────────────────┘                     │
                                                │
              ┌────────────┬────────────────────┼────────────────────┐
              ▼            ▼                    ▼                    ▼
          ┌──────┐    ┌──────────┐         ┌─────────┐          ┌─────────┐
          │FaqBot│    │ Engineer │         │Security │          │   PM    │
          │ FAQ  │    │  BUG     │         │SECURITY │          │ FEATURE │
          │ 20b  │    │  120b    │         │  120b   │          │   20b   │
          └──────┘    └──────────┘         └─────────┘          └─────────┘
                       (cada um com sua @ActivationCondition)
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
│   ├── TicketClassifier.java     — @Agent: classifica → outputKey="category"
│   ├── FaqBot.java               — @Agent + @RegisterAiService(modelName="smaller")
│   ├── EngineerAgent.java        — @Agent + @RegisterAiService (default = 120b)
│   ├── SecurityOfficer.java      — @Agent + @RegisterAiService (default)
│   ├── ProductManagerAgent.java  — @Agent + @RegisterAiService(modelName="smaller")
│   └── ExampleGenerator.java     — AI Service auxiliar (gera tickets de exemplo)
├── workflow/
│   ├── TicketHandler.java        — @ConditionalAgent + 4 @ActivationCondition static
│   ├── TicketAgent.java          — @SequenceAgent(Classifier, Handler) + @Output static
│   └── TicketRouter.java         — wrapper Multi<TicketEvent>
├── rest/
│   └── ExampleResource.java      — endpoint /api/example/ticket
├── dto/                          — TicketCategory, ModelTier, TicketResponse, TicketEvent
└── TicketWebsocket.java          — endpoint /ws/tickets
```

### Pontos-chave

#### 1. `TicketClassifier` — classifica e escreve no scope

```java
@RegisterAiService(modelName = "smaller")
public interface TicketClassifier {
    @SystemMessage("...categoria em FAQ/BUG/SECURITY/FEATURE...")
    @UserMessage("Ticket: {ticket}")
    @Agent(name = "classifier", outputKey = "category")
    TicketCategory classify(@V("ticket") String ticket);
}
```

O `outputKey = "category"` armazena o enum `TicketCategory` no `AgenticScope`. As `@ActivationCondition` leem essa chave depois.

#### 2. Cada handler — `@Agent(outputKey="answer")` + modelo próprio

```java
@RegisterAiService(modelName = "smaller")  // ou default = gpt-oss:120b-cloud
public interface FaqBot {
    @SystemMessage("...")
    @UserMessage("Ticket: {ticket}")
    @Agent(name = "faq", outputKey = "answer")
    String answer(@V("ticket") String ticket);
}
```

Note: TODOS os 4 handlers usam o mesmo `outputKey = "answer"`. Só **um** vai executar (escolhido pelo `@ConditionalAgent`), então não há conflito.

#### 3. `TicketHandler` — `@ConditionalAgent` + 4 `@ActivationCondition`

```java
public interface TicketHandler {
    @ConditionalAgent(outputKey = "answer", subAgents = {
        FaqBot.class, EngineerAgent.class, SecurityOfficer.class, ProductManagerAgent.class })
    String handle(@V("ticket") String ticket);

    @ActivationCondition(value = FaqBot.class, description = "categoria == FAQ")
    static boolean isFaq(@V("category") TicketCategory category) {
        return category == TicketCategory.FAQ;
    }

    @ActivationCondition(value = EngineerAgent.class, description = "categoria == BUG")
    static boolean isBug(@V("category") TicketCategory category) { ... }
    // ... isSecurity, isFeature
}
```

Cada `@ActivationCondition` é um método static que recebe `@V("category")` do `AgenticScope` e retorna `boolean`. O framework executa todos em ordem; o **primeiro que retornar `true`** ativa o sub-agente correspondente.

#### 4. `TicketAgent` — `@SequenceAgent` encadeia classifier + handler

```java
public interface TicketAgent {
    @SequenceAgent(outputKey = "ticketResponse",
                   subAgents = { TicketClassifier.class, TicketHandler.class })
    TicketResponse processar(@V("ticket") String ticket);

    @Output
    static TicketResponse assemble(AgenticScope scope) {
        TicketCategory category = (TicketCategory) scope.readState("category");
        String answer = (String) scope.readState("answer");
        ModelTier tier = tierFor(category);
        return new TicketResponse(category, tier, tier.modelId(), agentNameFor(category), answer, 0L);
    }
}
```

O `@Output static assemble` combina o `category` (do classifier) com o `answer` (do handler ativo) e devolve um `TicketResponse` completo, incluindo informações derivadas sobre o modelo usado.

#### 5. Troca de modelo runtime — visível nos logs

| Categoria | Handler | `modelName` | Modelo invocado |
|---|---|---|---|
| FAQ | `FaqBot` | `"smaller"` | `gpt-oss:20b-cloud` |
| BUG | `EngineerAgent` | default | `gpt-oss:120b-cloud` |
| SECURITY | `SecurityOfficer` | default | `gpt-oss:120b-cloud` |
| FEATURE | `ProductManagerAgent` | `"smaller"` | `gpt-oss:20b-cloud` |

O Quarkus extension resolve o `modelName` no momento da injeção do bean — o `@ConditionalAgent` não sabe nada sobre modelos, ele só ativa o sub-agente certo.

## O que observar

| Observação | Explica… |
|---|---|
| Sequência WS: `RECEIVED → CLASSIFICATION → ANSWER` | Orchestrator emite 3 marcos para visualização da pipeline |
| FAQ/FEATURE respondem ~3-7s | Modelo `gpt-oss:20b-cloud` mais rápido |
| BUG/SECURITY respondem ~18-24s | Modelo `gpt-oss:120b-cloud` mais robusto |
| Contagem `chamadas 20b` vs `chamadas 120b` no painel | Cada ticket alimenta uma das contagens, depending do roteamento |

## Para experimentar

- Adicione uma 5ª categoria (ex: `BILLING`): nova `BillingAgent` interface + entrada em `TicketCategory` + nova `@ActivationCondition` em `TicketHandler` + classe no `subAgents` de `@ConditionalAgent`. **Atualize também** o `tierFor` e `agentNameFor` no `TicketAgent`
- Troque o modelo do `EngineerAgent` para `"smaller"` (gpt-oss:20b-cloud) — veja como a análise de bug perde profundidade

## Próxima aula

Aula 06: **HITL** — Aprovação de desconto B2B com `@LoopAgent` + `@HumanInTheLoop` binário (aprovar/rejeitar como exit condition; "contrapor" reinicia o loop).
