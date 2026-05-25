# Aula 05 — Dynamic Routing: Ticket Router com Troca de Modelo

> **Padrão**: Dynamic Routing
> **Case**: Roteamento de tickets de TI corporativo com escolha do modelo LLM em runtime
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud` + `gpt-oss:20b-cloud`)

---

## O que você vai aprender

Este projeto demonstra o padrão **Dynamic Routing**: um classificador determina a categoria da mensagem em runtime, e o sistema **escolhe diferentes agentes E diferentes MODELOS** conforme a categoria.

A novidade vs. a aula04 (Supervisor): aqui não há revisão — é roteamento puro, mas com **escolha consciente do modelo LLM** baseada em custo/latência/qualidade.

```
                    Ticket de TI (texto livre)
                            │
                            ▼
                ┌──────────────────────────┐
                │   TicketClassifier (LLM) │  ← modelo barato (smaller)
                │   modelo: gpt-oss:20b    │     decide FAQ/BUG/SECURITY/FEATURE
                └──────────────┬───────────┘
                               │
              ┌────────────────┼────────────────┬──────────────┐
              ▼                ▼                ▼              ▼
        ┌──────────┐    ┌────────────┐    ┌──────────┐    ┌──────────┐
        │ FaqBot   │    │ Engineer   │    │ Security │    │ Product  │
        │ 20b 🟢   │    │ 120b 🟣   │    │ 120b 🟣  │    │ 20b 🟢   │
        └──────────┘    └────────────┘    └──────────┘    └──────────┘
           FAQ              BUG              SECURITY        FEATURE
        (FAST tier)      (ROBUST tier)    (ROBUST tier)   (FAST tier)
```

### Por que este padrão importa

Em produção, nem todo agente precisa do modelo mais caro:

| Categoria | Por que escolhemos o modelo |
|---|---|
| **FAQ** (reset senha, VPN) | Resposta direta de base estática → modelo barato suficiente |
| **FEATURE** (pedido) | Texto curto, registro padronizado → modelo barato suficiente |
| **BUG** (falha técnica) | Raciocínio passo-a-passo → modelo robusto compensa custo |
| **SECURITY** (incidente) | Domínio sensível, custo de erro alto → modelo robusto sempre |

Economia real: ~80% das requisições de uma central N1 são FAQ. Rotear pelo modelo barato pode reduzir custo de inferência em **5-10x** sem perder qualidade onde importa.

---

## Como rodar

```bash
cd modulo03/03_agentes/aula05/ticket-router
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Como usar a UI

1. **Cole um ticket** ou clique em um dos 4 botões de exemplo (FAQ/BUG/SECURITY/FEATURE)
2. Clique em **Abrir Ticket**
3. Observe o pipeline horizontal:
   - **① Ticket Recebido** — preview do texto
   - **② Categoria Detectada** — badge colorido + agente que vai responder
   - **③ Modelo Selecionado** — **badge grande** com o `modelId` real (`gpt-oss:20b-cloud` em verde ou `gpt-oss:120b-cloud` em violeta) + tier FAST/ROBUST
4. Resposta do agente aparece abaixo com **tempo de resposta** medido
5. Painel lateral mostra contadores acumulados (quantas chamadas em cada modelo) + tempo da última resposta

**Teste em sequência**: rode FAQ depois BUG — você vai ver o tempo dobrar/triplicar (modelo robusto é mais lento). Esse é o ponto pedagógico.

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── TicketClassifier.java       — classificador (modelName="smaller")
│   ├── FaqBot.java                 — agente FAQ (modelName="smaller")
│   ├── EngineerAgent.java          — agente BUG (modelo default = 120b)
│   ├── SecurityOfficer.java        — agente SECURITY (modelo default = 120b)
│   └── ProductManagerAgent.java    — agente FEATURE (modelName="smaller")
├── workflow/
│   └── TicketRouter.java           — switch Java explícito sobre TicketCategory
├── dto/                             — records + enums TicketCategory, ModelTier
└── TicketWebsocket.java            — endpoint /ws/tickets
```

### Pontos-chave para os alunos

#### 1. `modelName` aponta para configuração nomeada

```java
@RegisterAiService(modelName = "smaller")
public interface FaqBot { ... }
```

E no `application.properties`:

```properties
quarkus.langchain4j.ollama.chat-model.model-id=gpt-oss:120b-cloud
quarkus.langchain4j.ollama.smaller.chat-model.model-id=gpt-oss:20b-cloud
```

**Mesma anotação `@RegisterAiService`, modelo diferente em runtime** — o segredo é o parâmetro `modelName`.

#### 2. Router como `switch` Java explícito

O `TicketRouter` usa um `switch` clássico que torna a decisão de modelo **visível no código**:

```java
return switch (category) {
    case FAQ      -> new TicketResponse(category, ModelTier.FAST,   "gpt-oss:20b-cloud",  ...);
    case BUG      -> new TicketResponse(category, ModelTier.ROBUST, "gpt-oss:120b-cloud", ...);
    case SECURITY -> new TicketResponse(category, ModelTier.ROBUST, "gpt-oss:120b-cloud", ...);
    case FEATURE  -> new TicketResponse(category, ModelTier.FAST,   "gpt-oss:20b-cloud",  ...);
};
```

Poderíamos usar `@ConditionalAgent` + `@ActivationCondition` do agentic module, mas o `switch` deixa **muito mais óbvio** para o aluno que estamos trocando o modelo.

#### 3. WebSocket emite a classificação ANTES da resposta

O endpoint emite 3 eventos sequenciais:
- `RECEIVED` — eco do ticket recebido
- `CLASSIFICATION` — categoria + modelo escolhido (chega em ~500ms — modelo smaller)
- `ANSWER` — resposta do agente especializado (chega em ~2-15s dependendo do modelo)

O usuário vê o "cérebro decidindo" antes de a resposta começar a chegar.

#### 4. Logging no console

O `TicketRouter` loga cada decisão:
```
INFO [com.eld.wor.TicketRouter] >> Categoria: BUG | Modelo: gpt-oss:120b-cloud
```

Útil em demonstrações ao vivo para confirmar a troca real do modelo.

---

## Alternativa declarativa (`@ConditionalAgent`)

Equivalente do agentic module:

```java
public interface TicketWorkflow {
    @ConditionalAgent(outputKey = "answer", subAgents = {
        FaqBot.class, EngineerAgent.class, SecurityOfficer.class, ProductManagerAgent.class
    })
    String handle(@V("ticket") String ticket, @V("category") TicketCategory category);

    @ActivationCondition(FaqBot.class)
    static boolean activateFaq(@V("category") TicketCategory c) { return c == TicketCategory.FAQ; }
    // ... outras 3 ActivationCondition
}
```

**Trade-offs**: a declarativa é elegante para o roteamento, mas a **escolha do modelo continua sendo no `@RegisterAiService(modelName=...)` de cada agente**. A versão `switch` torna o **mapeamento categoria↔modelo** mais visível em um único lugar.

---

## O que observar no frontend

| Observação | Explica… |
|---|---|
| Classificação aparece em **~500ms** | Modelo smaller é rápido |
| Badge do modelo muda de cor (verde vs violeta) | Escolha de modelo é visual |
| FAQ/FEATURE respondem em ~2-3s | Modelo 20b processa rápido |
| BUG/SECURITY respondem em ~8-15s | Modelo 120b leva mais tempo (mais qualidade) |
| Contador lateral acumula | Visibilidade de mix ao longo da sessão |

---

## Para experimentar

- **Mude `EngineerAgent` para `modelName="smaller"`** — compare qualidade de análise de bug com modelo menor
- **Adicione uma 5ª categoria** (ex: `BILLING`) — atualize enum, classifier, switch, frontend
- **Implemente fallback**: se classificador retornar categoria inesperada, rotear para `FaqBot` por default
- **Roteamento por tamanho do texto**: tickets curtos → modelo small; tickets longos (>500 chars) → modelo 120b independente da categoria
- **Adicione SLA-aware routing**: tickets vindos com header `priority=P1` sempre vão para 120b

---

## Próxima aula

Aula 06: **Human-in-the-Loop (HITL)** — agente prepara uma proposta, sistema **pausa** aguardando aprovação humana, e segue conforme decisão (aprovação de desconto B2B).
