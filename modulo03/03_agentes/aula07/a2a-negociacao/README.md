# Aula 07 — Agent-to-Agent (A2A): Negociação Comprador × Vendedor

> **Padrão**: Agent-to-Agent (A2A) — dois agentes em **apps separados** dialogando via REST
> **Case**: Negociação B2B autônoma — comprador (orçamento + critérios) ↔ vendedor (catálogo + margem)
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud`) · Maven multi-módulo

---

## O que você vai aprender

O padrão **Agent-to-Agent (A2A)** é arquiteturalmente diferente dos outros do módulo: aqui há **dois processos Quarkus separados**, cada um expondo seu próprio agente, dialogando via HTTP. A negociação acontece num loop iterativo até acordo, impasse ou limite de rodadas.

```
   comprador-app (porta 8080)              vendedor-app (porta 8081)
   ┌───────────────────────────┐           ┌───────────────────────────┐
   │  @Inject CompradorAgent   │           │   NegociacaoEndpoint      │
   │  (avalia proposta)        │  REST     │   @POST /a2a/negociacao   │
   │           ▼               │  ───────▶ │           ▼               │
   │  NegociacaoCoordinator    │  ◀─────── │  @Inject VendedorAgent    │
   │  (loop manual 5 rodadas)  │           │  (gera proposta)          │
   │           ▼               │           │           ▼               │
   │  @RegisterRestClient      │           │  VendedorDashboard (WS)   │
   │  VendedorRemote           │           │  (broadcast read-only)    │
   └───────────────────────────┘           └───────────────────────────┘
```

Cada lado tem um **agente real** (`@RegisterAiService` + `@Agent` com `description`). O `CompradorAgent` decide ACEITAR/CONTRAPOR/DESISTIR; o `VendedorAgent` propõe preço respeitando o preço mínimo do catálogo.

## Como rodar

Em 2 terminais:

```bash
# Terminal 1
cd modulo03/03_agentes/aula07/a2a-negociacao/vendedor-app
./mvnw quarkus:dev

# Terminal 2
cd modulo03/03_agentes/aula07/a2a-negociacao/comprador-app
./mvnw quarkus:dev
```
Abra <http://localhost:8080/> (comprador) e <http://localhost:8081/> (dashboard vendedor).

## Estrutura

```
aula07/a2a-negociacao/
├── pom.xml                          — multi-módulo Maven parent
├── comprador-app/                   — porta 8080
│   └── src/main/java/com/eldermoraes/comprador/
│       ├── ai/CompradorAgent.java       — @Agent (decide ACEITAR/CONTRAPOR/DESISTIR)
│       ├── ai/ExampleGenerator.java     — AI Service (gera EntradaCompra de exemplo)
│       ├── negociacao/VendedorRemote.java — @RegisterRestClient
│       ├── negociacao/NegociacaoCoordinator.java — loop manual 5 rodadas
│       ├── ws/CompradorWebSocket.java
│       └── rest/ExampleResource.java     — /api/example/compra
└── vendedor-app/                    — porta 8081
    └── src/main/java/com/eldermoraes/vendedor/
        ├── ai/VendedorAgent.java         — @Agent (propõe preço respeitando margem)
        ├── catalogo/CatalogoService.java — catálogo estático em memória
        ├── rest/NegociacaoEndpoint.java  — @POST /a2a/negociacao
        └── ws/VendedorDashboard.java     — broadcast read-only
```

### Pontos-chave

#### 1. Cada lado tem um agente declarativo (`@Agent`)

```java
@RegisterAiService
public interface CompradorAgent {
    @SystemMessage("...estratégia: anchoring -20%, ceder até orçamento, ACEITAR/CONTRAPOR/DESISTIR...")
    @Agent(name = "comprador",
           description = "Agente comprador B2B — avalia proposta e decide acao",
           outputKey = "decisaoComprador")
    DecisaoComprador avaliar(@V("produto") String produto, @V("orcamentoMax") BigDecimal orc, ...);
}
```

#### 2. Loop de negociação fica no orchestrator Java (não em `@LoopAgent`)

A2A é entre **apps**, não dentro de um único processo. O `@LoopAgent` declarativo orquestra sub-agents **do mesmo JVM**. Em A2A, o loop é entre apps via HTTP — necessariamente código Java imperativo, com `@RegisterRestClient` para invocar o vendedor remoto.

```java
@ApplicationScoped
public class NegociacaoCoordinator {
    @Inject CompradorAgent comprador;
    @Inject @RestClient VendedorRemote vendedorRemoto;

    private void runNegociacao(EntradaCompra entrada, MultiEmitter<...> emitter) {
        for (int rodada = 1; rodada <= maxRodadas; rodada++) {
            RespostaVendedor resp = vendedorRemoto.negociar(new MensagemNegociacao(...));
            DecisaoComprador dec = comprador.avaliar(...);
            emitter.emit(NegociacaoEvent.rodadaConcluida(...));
            if (dec.acao() == ACEITAR) { /* acordo */ break; }
            if (dec.acao() == DESISTIR) { /* impasse */ break; }
        }
    }
}
```

#### 3. `ExampleGenerator` retorna `EntradaCompra` tipada via LLM

```java
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {
    @SystemMessage("...gere cenário de compra B2B variando produto/orçamento/prazo...")
    EntradaCompra entradaExemplo();
}
```

Frontend chama `/api/example/compra` e preenche os 4 campos do formulário a partir do JSON.

## Trade-off: `@A2AClientAgent` declarativo

O LangChain4j Agentic tem **`@A2AClientAgent(a2aServerUrl = "...")`** que substituiria `@RegisterRestClient`:

```java
public interface VendedorRemote {
    @A2AClientAgent(a2aServerUrl = "${vendedor.a2a.url:http://localhost:8081}",
                    outputKey = "resposta",
                    description = "...")
    RespostaVendedor negociar(@V("produto") String p, @V("rodada") int r, ...);
}
```

Mas a versão server-side requer o SDK **`io.github.a2asdk:a2a-java-sdk-server-quarkus:0.2.3.Beta1`** (Beta) no `vendedor-app`, com `@Produces @PublicAgentCard AgentCard` + `@Produces AgentExecutor`. Como o SDK está em Beta1 e o protocolo A2A está migrando para 1.0, mantivemos `@RegisterRestClient` por estabilidade. Quando o SDK A2A estabilizar, a migração é localizada em `VendedorRemote` (1 anotação) + `vendedor-app` (2 producers).

## O que observar

| Observação | Explica… |
|---|---|
| Sequência WS comprador: `INICIADA → RODADA×N → ACORDO/IMPASSE` | Loop emite eventos por rodada |
| Dashboard vendedor mostra cada rodada em tempo real | Broadcast WebSocket read-only |
| 2 chamadas LLM por rodada (comprador + vendedor) | Cada agente roda no seu app |
| Limite de 5 rodadas é configurável | `negociacao.max-rodadas` em application.properties |

## Para experimentar

- Aumente o orçamento do comprador acima do preço mínimo do vendedor — acordo em 1 rodada
- Diminua orçamento abaixo do preço mínimo — impasse com `limiteAtingido=true` do vendedor
- Mude `negociacao.max-rodadas=3` — força impasse mais rápido
- Adicione um terceiro app (terceiro agente intermediário) — clássico em supply chains

## Conclusão do módulo

Você passou por 6 padrões em 6 aulas, todos usando `@Agent` + composições declarativas (`@ParallelAgent`, `@ParallelMapperAgent`, `@SequenceAgent`, `@SupervisorAgent`, `@ConditionalAgent`, `@LoopAgent`, `@HumanInTheLoop`) ou comunicação inter-app (REST + future `@A2AClientAgent`). Os 3 conceitos centrais — `@Agent` em workers + composição declarativa em interfaces marker + `@Inject` direto da interface composta — formam o vocabulário canônico do framework agentic em Quarkus.
