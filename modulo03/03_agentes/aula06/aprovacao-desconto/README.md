# Aula 06 — Human-in-the-Loop (HITL): Aprovação de Desconto B2B

> **Padrão**: Human-in-the-Loop (decisão ternária — aprovar/rejeitar/contrapor)
> **Case**: Vendedor solicita desconto → agente comercial propõe % → gerente decide
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`deepseek-v4-pro:cloud` + `gpt-oss:20b-cloud`) · Postgres (Dev Services)

---

## O que você vai aprender

O padrão **Human-in-the-Loop** é único entre os 6 padrões do módulo: ele **bloqueia** a execução do workflow agente até que um humano tome uma decisão. Os outros 5 padrões são totalmente automatizados; HITL **integra autoridade humana** ao fluxo.

Esta aula tem dois agentes (`@Agent`) e um serviço de aprovação Java que gerencia o bloqueio:

```
   ┌─────────────────────────────────────────────────────────────┐
   │                                                             │
   │   Vendedor envia pedido            Gerente decide           │
   │           ▼                              ▲                  │
   │   ┌──────────────────┐         ┌──────────────────┐         │
   │   │  VendedorWS      │         │   GerenteWS      │         │
   │   └────────┬─────────┘         └────────▲─────────┘         │
   │            │                            │                   │
   │            ▼                            │                   │
   │   ┌──────────────────────┐              │                   │
   │   │  DescontoWorkflow    │              │                   │
   │   │   1. ComercialAgent  │  ◀── propõe  │                   │
   │   │      (@Agent)        │              │                   │
   │   │   2. ApprovalService │ ─ broadcast ─┘                   │
   │   │      (CompletableFut)│ ◀── aguarda decisão              │
   │   │   3. RespostaFinal   │                                  │
   │   │      Agent (@Agent)  │                                  │
   │   └──────────────────────┘                                  │
   │            │                                                │
   │            ▼                                                │
   │     Postgres entity                                         │
   │     (ApprovalProposal)                                      │
   └─────────────────────────────────────────────────────────────┘
```

## Como rodar

Pré-requisito: podman rodando (testcontainers usa para Postgres Dev Services).

```bash
cd modulo03/03_agentes/aula06/aprovacao-desconto
./mvnw quarkus:dev
```
Abra <http://localhost:8080/> (interface tem painéis vendedor + gerente lado a lado).

## Estrutura

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── ComercialAgent.java       — @Agent: propõe % de desconto
│   ├── RespostaFinalAgent.java   — @Agent: redige resposta ao vendedor
│   └── ExampleGenerator.java     — AI Service auxiliar (gera pedidos B2B)
├── hitl/
│   ├── ApprovalService.java      — bloqueio via CompletableFuture + Postgres
│   ├── ApprovalProposal.java     — PanacheEntity (persistência)
│   └── ApprovalStatus.java       — enum (PENDENTE/APROVADA/REJEITADA/CONTRAPROPOSTA/EXPIRADA)
├── workflow/
│   └── DescontoWorkflow.java     — orquestra: agente → bloqueio → agente
├── ws/
│   ├── VendedorWebSocket.java    — /ws/vendedor/{vendedorId}
│   └── GerenteWebSocket.java     — /ws/gerente (broadcast bidirecional)
├── rest/
│   └── ExampleResource.java      — /api/example/pedido
└── dto/                          — PropostaDesconto, ApprovalDecision, VendedorEvent, GerenteEvent
```

### Pontos-chave

#### 1. Workers como `@Agent`

```java
@RegisterAiService
public interface ComercialAgent {
    @SystemMessage("...analista comercial sênior B2B...")
    @UserMessage("Pedido do vendedor: {descricao}")
    @Agent(name = "comercial",
            description = "Analista comercial — propõe desconto razoável",
            outputKey = "proposta")
    PropostaDesconto propor(@V("descricao") String descricaoPedido);
}

@RegisterAiService
public interface RespostaFinalAgent {
    @SystemMessage("...redige resposta final ao vendedor...")
    @UserMessage("PEDIDO: {pedido}\n\nPROPOSTA: {propostaResumo}\n\nDECISÃO: {decisaoResumo}")
    @Agent(name = "resposta", outputKey = "respostaFinal")
    String redigir(@V("pedido") String pedido,
                   @V("propostaResumo") String propostaResumo,
                   @V("decisaoResumo") String decisaoResumo);
}
```

#### 2. Bloqueio HITL ternário via `ApprovalService` (Java)

```java
@ApplicationScoped
public class ApprovalService {
    private final ConcurrentMap<Long, CompletableFuture<ApprovalDecision>> waiters = new ConcurrentHashMap<>();

    @Transactional
    public ApprovalProposal criarPendente(String vendedorId, String descricaoPedido, PropostaDesconto proposta) {
        // persiste no Postgres + broadcast para gerentes
        waiters.put(entity.id, new CompletableFuture<>());
        return entity;
    }

    public ApprovalDecision aguardarDecisao(Long propostaId) throws TimeoutException {
        return waiters.get(propostaId).get(timeoutMinutes, TimeUnit.MINUTES);  // BLOQUEIA
    }

    @Transactional
    public ApprovalProposal decidir(ApprovalDecision decision) {
        // persiste status + completa o future → libera o aguardar
        waiters.get(decision.propostaId()).complete(decision);
    }
}
```

A virtual thread spawnada pelo `DescontoWorkflow.processarPedido(...)` chama `aguardarDecisao(id)` e **bloqueia até 10 minutos** esperando o gerente. Quando o gerente envia decisão via `GerenteWebSocket`, o `decidir(...)` completa o `CompletableFuture` e a virtual thread retoma.

## Trade-off: `@HumanInTheLoop` declarativo

O LangChain4j Agentic tem **`@HumanInTheLoop`** static method que substituiria o `ApprovalService` em parte:

```java
public interface ApprovalGate {
    @HumanInTheLoop(description = "Aguardar decisão do gerente", outputKey = "decisao", async = true)
    static String askManager(AgenticScope scope, @V("proposta") PropostaDesconto proposta) {
        // bloqueia em CompletableFuture até gerente decidir
        // mas precisa CDI.current() para acessar serviço de decisão
    }
}
```

**Por que não usamos**:
- `@HumanInTheLoop` é **binário** (aprovar/rejeitar). A decisão real é **ternária** (aprovar/rejeitar/**contrapor com % diferente**) — perderíamos uma feature do caso corporativo
- O static method não tem `@Inject` direto — precisaria `CDI.current().select(...).get()` (cerimônia)
- A persistência no Postgres e multi-gerente broadcast já existem no `ApprovalService` — duplicar via `@HumanInTheLoop` seria custo sem ganho
- **Para HITL com decisão ternária ou estado persistente, Java explícito é a forma idiomática**. `@HumanInTheLoop` é melhor para fluxos binários simples (aprovar/rejeitar uma proposta de email, p.ex.)

Para casos binários puros sem persistência, a versão declarativa funcionaria:

```java
public interface ApprovalAgent {
    @LoopAgent(maxIterations = 3, subAgents = {ComercialAgent.class, ApprovalGate.class})
    String processar(@V("descricao") String desc);

    @ExitCondition
    static boolean aprovado(@V("decisao") String decisao) {
        return "APROVADO".equals(decisao);
    }
}
```

## O que observar

| Observação | Explica… |
|---|---|
| Vendedor: `RECEBIDO → PROPOSTA_PREPARADA` em ~10s | `ComercialAgent.propor(...)` chamou o LLM |
| Painel do gerente recebe a proposta em tempo real | `ApprovalService.broadcastGerentes(...)` via `OpenConnections` |
| Vendedor fica em loading até gerente decidir | Virtual thread bloqueada em `future.get(timeout)` |
| Após decisão: `DECIDIDA` chega ao vendedor | `RespostaFinalAgent.redigir(...)` formatou resposta personalizada |
| Postgres mantém ApprovalProposal entity | Histórico persistente; `listarTodas()` recupera após restart |

## Recuperação pós-restart

O `ApprovalService.@PostConstruct` lê `findByStatus(PENDENTE)` no Postgres. Propostas pendentes antes do restart aparecem no painel do gerente — mas as virtual threads originais foram perdidas. Quando o gerente decide nessas propostas pós-restart, o `decidir(...)` ainda persiste o status no Postgres mas não há `waiter` (futureMap está vazio). Em produção isso seria mitigado por sticky sessions ou um broker de eventos persistente.

## Para experimentar

- Adicione uma 5ª categoria de decisão (ex: `ENCAMINHADA_DIRETOR` para pedidos > R$ 500k): novo enum + novo handler no `decidir(...)` + novo botão no painel do gerente
- Habilite log SQL (`quarkus.hibernate-orm.log.sql=true`) — veja todas as transações
- Force timeout reduzido (`hitl.approval.timeout.minutes=1`) — veja a EXPIRADA acontecer
- Abra 2 abas do gerente: ambas recebem broadcast de propostas pendentes; apenas a primeira a decidir "ganha" (race condition controlada pelo Postgres + future)

## Conclusão do módulo

Você passou pelos 6 padrões clássicos de agentes em produção. O módulo demonstrou que o framework Quarkus + LangChain4j Agentic resolve o vocabulário declarativo (`@Agent`, composições `@SequenceAgent`/`@ParallelAgent`/`@ParallelMapperAgent`/`@SequenceAgent`/`@SupervisorAgent`/`@ConditionalAgent`/`@LoopAgent`/`@HumanInTheLoop`/`@A2AClientAgent`) e quando recorrer a Java imperativo (HITL ternário com persistência, A2A REST entre apps separados).
