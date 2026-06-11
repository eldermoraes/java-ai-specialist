# Aula 09 (Blackboard): War-Room de Incidentes de Produção

> **Padrão**: Blackboard
> **Case**: War-room de incidente (especialistas de aplicação, infra e banco colaboram num quadro compartilhado até a causa raiz)
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic (`@PlannerAgent` + `BlackboardPlanner`) · Ollama (`deepseek-v4-pro:cloud` + `deepseek-v4-flash:cloud`)

---

## O que você vai aprender

O **Blackboard** é um dos padrões mais antigos da IA clássica (nasceu no sistema de reconhecimento de fala Hearsay-II, nos anos 70) e ganhou uma releitura agêntica no LangChain4j. A metáfora é literal: imagine uma sala de crise com um **quadro branco no centro**. Cada especialista olha para o quadro, e **quando os dados de que ele precisa estão lá**, ele levanta, escreve a contribuição dele e senta. Ninguém segue um roteiro; ninguém manda em ninguém. A solução **emerge** das contribuições acumuladas.

No LangChain4j, o quadro é o `AgenticScope` e os especialistas são os mesmos `@Agent` que você já conhece. A diferença está no scheduler: o `BlackboardPlanner` inspeciona o quadro após **cada passo** e ativa **exatamente um** agente: aquele cujos argumentos `@V("...")` já existem como estado no scope. Quando um agente escreve seu `outputKey`, todos os que dependem daquela chave são re-armados, o que permite ciclos de refinamento.

Compare com o que você já viu no módulo: no `@SequenceAgent` a ordem é **fixa no código**; no `@SupervisorAgent` a ordem é **decidida por um LLM planner** (caro, não-determinístico); no Blackboard a ordem é **derivada dos dados** por um algoritmo determinístico, sem nenhuma chamada de LLM para orquestrar. Quando vários agentes estão prontos ao mesmo tempo, uma `ConflictResolutionStrategy` desempata (por padrão, ordem de declaração em `subAgents`).

A execução termina quando uma de três condições é atingida: (1) o **goal predicate** é satisfeito (aqui, o relatório de causa raiz está no quadro); (2) **quiescência**, quando nenhum agente consegue mais disparar; (3) o limite de **maxInvocations** é atingido (proteção contra loops infinitos).

```
                        @Inject WarRoomAgent          (interface @PlannerAgent)
                                  │ investigar(sintoma, logs, metricas, bancoDados, warRoomId)
                                  ▼
            ┌─────────────────────────────────────────────────┐
            │  BlackboardPlanner (scheduler em CÓDIGO, 0 LLM)  │
            │  · ativa 1 agente por passo                      │
            │  · ready = todos os @V presentes no quadro       │
            │  · desempate: ConflictResolutionStrategy         │
            │  · para quando: goal | quiescência | maxPassos   │
            └────────────────────┬────────────────────────────┘
                                 │ lê / ativa (oportunista)
   ┌──────────────┬──────────────┼────────────────┬──────────────────────┐
   ▼              ▼              ▼                ▼                      ▼
┌─────────┐  ┌─────────┐  ┌───────────┐  ┌────────────────┐  ┌──────────────────┐
│Analista │  │Analista │  │Analista de│  │Correlacionador │  │Engenheiro de     │
│de Aplic.│  │de Infra │  │Banco Dados│  │de Hipóteses    │  │Causa Raiz        │
│lê: logs │  │lê: metr.│  │lê: banco  │  │lê: 3 evidências│  │lê: sintoma+hipót.│
│(flash)  │  │(flash)  │  │(flash)    │  │(pro)           │  │(pro)             │
└────┬────┘  └────┬────┘  └─────┬─────┘  └───────┬────────┘  └────────┬─────────┘
     │escreve     │escreve      │escreve         │escreve             │escreve
     ▼            ▼             ▼                ▼                    ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                    AgenticScope = o QUADRO (blackboard)                        │
│  sintoma · logs · metricas · bancoDados            ← entradas da UI            │
│  evidenciaAplicacao · evidenciaInfra · evidenciaBanco                          │
│  hipotese · relatorioIncidente                     ← goal: relatorioIncidente  │
└────────────────────────────────────────────────────────────────────────────────┘
```

Para um alerta que cita o banco de dados, a sequência que **emerge** dos dados é:

```
passo 1  AnalistaDeBancoDeDados     lê bancoDados          → evidenciaBanco       (promovido pela ConflictResolutionStrategy
                                                                                   quando o sintoma menciona banco/conexão/query)
passo 2  AnalistaDeAplicacao        lê logs                → evidenciaAplicacao   (declaration order entre os prontos)
passo 3  AnalistaDeInfra            lê metricas            → evidenciaInfra
passo 4  CorrelacionadorDeHipoteses lê as 3 evidências     → hipotese             (SÓ AGORA ficou ready)
passo 5  EngenheiroDeCausaRaiz      lê sintoma + hipotese  → relatorioIncidente   → GOAL atingido, fim
```

São **5 chamadas LLM por investigação** (3× `deepseek-v4-flash` + 2× `deepseek-v4-pro`) e **zero chamadas LLM de orquestração**: contraste direto com o Supervisor da aula 04, onde cada decisão de roteamento custa uma chamada ao planner LLM (e por isso lá o modelo default precisa de `format=json`; aqui o planner é código, e não precisa).

## Como rodar

```bash
cd modulo03/03_agentes/aula09/war-room-incidentes
./mvnw quarkus:dev
```

Abra <http://localhost:8080/>.

Pré-requisito: Ollama em `localhost:11434` (Ollama Cloud) com os modelos `deepseek-v4-pro:cloud` e `deepseek-v4-flash:cloud` disponíveis.

## Estrutura do código

```
war-room-incidentes/
├── pom.xml                                  # deps padrão + langchain4j-agentic-patterns (BlackboardPlanner)
└── src/main/
    ├── java/com/eldermoraes/
    │   ├── WarRoomWebsocket.java            # @WebSocket /ws/warroom; @OnTextMessage retorna Multi<WarRoomEvent>
    │   ├── ai/
    │   │   ├── AnalistaDeAplicacao.java     # lê logs            → escreve evidenciaAplicacao (flash)
    │   │   ├── AnalistaDeInfra.java         # lê metricas        → escreve evidenciaInfra (flash)
    │   │   ├── AnalistaDeBancoDeDados.java  # lê bancoDados      → escreve evidenciaBanco (flash)
    │   │   ├── CorrelacionadorDeHipoteses.java # lê as 3 evidências → escreve hipotese (pro)
    │   │   ├── EngenheiroDeCausaRaiz.java   # lê sintoma+hipotese → escreve relatorioIncidente (pro)
    │   │   └── ExampleGenerator.java        # gera Incidente de exemplo (flash)
    │   ├── workflow/
    │   │   ├── WarRoomAgent.java            # @PlannerAgent + @PlannerSupplier + @Output (o coração da aula)
    │   │   ├── ObservablePlanner.java       # decorator do SPI Planner: publica cada passo do quadro
    │   │   ├── QuadroEventBus.java          # roteia eventos do quadro por warRoomId
    │   │   └── WarRoomOrchestrator.java     # wrapper Multi + virtual thread (padrão do módulo)
    │   ├── dto/
    │   │   ├── Incidente.java               # input do WS e do gerador de exemplos
    │   │   ├── Severidade.java              # enum SEV1/SEV2/SEV3
    │   │   ├── RelatorioIncidente.java      # saída tipada do incident commander
    │   │   ├── QuadroFinal.java             # foto final do quadro (montada pelo @Output)
    │   │   └── WarRoomEvent.java            # eventos WS: ABERTO/CONTRIBUICAO/RELATORIO/ERROR
    │   └── rest/
    │       └── ExampleResource.java         # GET /api/example/incidente (@RunOnVirtualThread)
    └── resources/
        ├── application.properties           # modelos + warroom.max-passos
        └── META-INF/resources/index.html    # quadro da war-room em tempo real
```

### Pontos-chave

#### 1. Knowledge sources: cada especialista declara o que LÊ (`@V`) e o que ESCREVE (`outputKey`)

```java
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface AnalistaDeBancoDeDados {

    @SystemMessage("""
            Você é um DBA sênior participando de uma war-room de incidente de produção.
            ...
            - Nunca retorne uma resposta vazia: se o diagnóstico não mostrar nada relevante, escreva
              exatamente "- Sem evidências relevantes no banco de dados".
            """)
    @UserMessage("""
            DIAGNÓSTICO DO BANCO DE DADOS:
            {bancoDados}
            """)
    @Agent(name = "banco",
            description = "Analisa o diagnóstico do banco de dados (queries lentas, locks, pool de conexões) e publica evidências",
            outputKey = "evidenciaBanco")
    @ModelName("smaller")
    String analisar(@V("bancoDados") String bancoDados);
}
```

A **regra de ativação** do Blackboard é toda declarativa: o agente fica "ready" quando **todos** os seus `@V` existem como estado **não-blank** no quadro. O `AnalistaDeBancoDeDados` só precisa de `bancoDados` (entrada da UI), então está pronto desde o passo 1. Já o `CorrelacionadorDeHipoteses` declara `@V("evidenciaAplicacao")`, `@V("evidenciaInfra")` e `@V("evidenciaBanco")`: ele **não pode** disparar antes dos três analistas, e ninguém precisou programar essa ordem.

Dois detalhes importam aqui:

- O prompt **proíbe resposta vazia**. String blank no `outputKey` não conta como "estado presente": se um analista devolvesse `""`, o correlacionador nunca ficaria ready e a investigação morreria por quiescência.
- O `@ModelName("smaller")` no método `@Agent` define qual `ChatModel` o agentic usa para **este** knowledge source dentro da composição: os três analistas extraem evidência com o modelo barato (flash); correlação e causa raiz, que exigem raciocínio de síntese, ficam no modelo robusto (pro, o default). O `modelName = "smaller"` do `@RegisterAiService` cobre o outro caminho (a interface usada como AI service comum, injetada com `@Inject`); por isso os dois aparecem juntos e devem ficar alinhados.

#### 2. `@PlannerAgent` + `@PlannerSupplier`: o quadro tem um scheduler, não um roteiro

```java
public interface WarRoomAgent {

    @PlannerAgent(name = "warRoom",
            description = "Conduz a war-room de incidente: especialistas colaboram num quadro compartilhado até a causa raiz",
            outputKey = "quadroFinal",
            subAgents = {
                    AnalistaDeAplicacao.class,
                    AnalistaDeInfra.class,
                    AnalistaDeBancoDeDados.class,
                    CorrelacionadorDeHipoteses.class,
                    EngenheiroDeCausaRaiz.class
            })
    QuadroFinal investigar(@V("sintoma") String sintoma,
                           @V("logs") String logs,
                           @V("metricas") String metricas,
                           @V("bancoDados") String bancoDados,
                           @V("warRoomId") String warRoomId);

    @PlannerSupplier
    static Planner planner() {
        ...
        return new ObservablePlanner(new BlackboardPlanner(objetivo, maxPassos, prioridade), bus);
    }
}
```

O `@PlannerAgent` é o irmão "plugável" das composições que você já usou: em vez de uma estratégia fixa (`@SequenceAgent`, `@ParallelAgent`), ele delega o agendamento a um `Planner` que **você** entrega no método static `@PlannerSupplier`. Os argumentos do método viram as primeiras escritas no quadro (`sintoma`, `logs`, `metricas`, `bancoDados`, mais `warRoomId`, chave técnica que nenhum agente declara, usada só para rotear eventos de observabilidade). A interface não leva `@RegisterAiService`; a extensão do Quarkus produz o bean sintético e você injeta `WarRoomAgent` direto.

Repare que o supplier constrói um `BlackboardPlanner` **novo a cada investigação**: o planner é stateful (contador de passos, quais agentes já dispararam). Nunca cacheie a instância.

E o argumento didático central: **não há nenhuma chamada LLM de orquestração**. Ative `quarkus.langchain4j.log-requests=true` e conte: são exatamente 5 requests por investigação, todos de especialistas.

#### 3. `ConflictResolutionStrategy`: quem fala primeiro na war-room

No passo 1, os TRÊS analistas estão prontos ao mesmo tempo (cada um depende só da própria entrada). Alguém precisa desempatar:

```java
ConflictResolutionStrategy prioridade = ConflictResolutionStrategy
        .agentOfType(AnalistaDeBancoDeDados.class, scope -> {
            String sintoma = String.valueOf(scope.readState("sintoma", "")).toLowerCase();
            return sintoma.contains("banco") || sintoma.contains("conex")
                    || sintoma.contains("query") || sintoma.contains("sql")
                    || sintoma.contains("pool");
        })
        .or(ConflictResolutionStrategy.declarationOrder());
```

Se o texto do alerta cita o banco ("pool de conexões", "query lenta"...), o DBA **fura a fila**; senão, vale a ordem de declaração em `subAgents` (aplicação → infra → banco). A condição lê o **próprio quadro** (`scope.readState`); a prioridade também é data-driven. Na UI, isso aparece nos badges de ordem (1º, 2º, 3º…) de cada card do quadro.

#### 4. As três condições de convergência

```java
// (1) goal explícito: o relatório final está no quadro
Predicate<AgenticScope> objetivo = scope -> scope.hasState("relatorioIncidente");

// (3) teto de passos, vindo do application.properties
int maxPassos = ConfigProvider.getConfig()
        .getOptionalValue("warroom.max-passos", Integer.class).orElse(12);

new BlackboardPlanner(objetivo, maxPassos, prioridade);
```

1. **Goal predicate**: assim que o `EngenheiroDeCausaRaiz` escreve `relatorioIncidente`, a investigação acaba, sem um passo a mais.
2. **Quiescência**: se nenhum agente consegue mais disparar, o planner desiste. É o que aconteceria se uma das 4 entradas chegasse vazia (estado blank não habilita ninguém); por isso o `WarRoomOrchestrator` valida os campos **antes** de abrir a war-room e devolve um `ERROR` claro, sem queimar nenhuma chamada LLM.
3. **`maxInvocations`** (`warroom.max-passos=12`): proteção contra ciclos infinitos de re-armamento.

#### 5. `ObservablePlanner`: assistindo o quadro ser preenchido (SPI `Planner`)

A chamada `warRoomAgent.investigar(...)` é bloqueante: sem instrumentação, o frontend só veria o resultado final. Como o `Planner` é uma interface, dá para **decorar** o `BlackboardPlanner` e observar cada passo:

```java
@Override
public Action nextAction(PlanningContext context) {
    publicarContribuicao(context);          // context.previousAgentInvocation() = quem ACABOU de executar
    return delegate.nextAction(context);    // a decisão continua 100% com o BlackboardPlanner
}
```

O `nextAction` recebe um `PlanningContext` com o `AgenticScope` e a invocação anterior (`agentName()` + `output()`); o mapeamento agente→`outputKey` é capturado uma vez no `init()`, a partir dos `AgentInstance` dos sub-agentes. O evento `CONTRIBUICAO` é publicado no `QuadroEventBus` usando o `warRoomId` lido do próprio quadro, e o `WarRoomOrchestrator` repassa ao `Multi` da conexão WebSocket. Falha de observabilidade nunca derruba a investigação (try/catch isolado); e o frontend é tolerante: se nenhuma `CONTRIBUICAO` chegar, ele preenche o quadro inteiro a partir do `RELATORIO` final.

## O que observar

| Observação | Explica… |
|---|---|
| Os 3 analistas atuam **um por vez**, e a ordem muda conforme o texto do sintoma | O `BlackboardPlanner` ativa exatamente 1 agente por passo; o desempate é da `ConflictResolutionStrategy` |
| Sintoma citando "pool de conexões" faz o card **Banco de Dados** receber o badge **1º** | `agentOfType(AnalistaDeBancoDeDados.class, condition)` com condição sobre o `scope` |
| Sintoma sem menção a banco → card **Aplicação** recebe o **1º** | Fallback `or(declarationOrder())`: vale a ordem de `subAgents` |
| O card **Correlação** só preenche depois dos três analistas | ready = todas as `@V` presentes no quadro (`evidenciaAplicacao` + `evidenciaInfra` + `evidenciaBanco`) |
| 5 chamadas LLM por investigação (3× flash + 2× pro) e **nenhuma** de orquestração | O planner é algoritmo, não LLM; confira com `quarkus.langchain4j.log-requests=true` |
| Linha `>> quadro[...]: X escreveu 'Y'` no log a cada passo | `ObservablePlanner.nextAction` + `context.previousAgentInvocation()` |
| A investigação termina exatamente quando `relatorioIncidente` aparece no quadro | Goal predicate `scope.hasState("relatorioIncidente")` (condição de parada nº 1) |
| Campo vazio → evento `ERROR` imediato, zero chamadas LLM | Validação no `WarRoomOrchestrator`: estado blank causaria quiescência silenciosa |
| Investigação que para por quiescência ou teto de passos → `ERROR` listando as **pendências do quadro** | Condições de parada 2 e 3 encerram sem o goal; o `WarRoomOrchestrator` detecta `relatorio() == null` em vez de assumir sucesso |

## Blackboard × Supervisor × Sequence

| | `@SequenceAgent` (aula 02) | `@SupervisorAgent` (aula 04) | Blackboard (esta aula) |
|---|---|---|---|
| Quem decide a ordem | o desenvolvedor, em código | um LLM planner, a cada passo | os DADOS no quadro |
| Custo de orquestração | zero | 1+ chamada LLM por passo | zero (algoritmo determinístico) |
| Determinismo da ordem | total | baixo | alto (dado o mesmo quadro) |
| Flexibilidade | nenhuma | alta | alta |
| Falha típica | etapa desnecessária roda sempre | planner alucina/roteia errado | quiescência se faltar insumo |

## Por que um `Planner` plugável (e não uma annotation própria)?

O Blackboard vive no módulo `dev.langchain4j:langchain4j-agentic-patterns`, junto com outros padrões de coordenação (GOAP, P2P e Voting, este último visto na aula 08). Todos são implementações do **mesmo SPI** `dev.langchain4j.agentic.planner.Planner` do core agentic. Em vez de uma annotation por padrão (`@BlackboardAgent`, `@VotingAgent`…), o framework expõe um ponto de extensão único: `@PlannerAgent` + `@PlannerSupplier`.

A consequência prática é poderosa: **trocar o padrão de orquestração é trocar UMA linha** no supplier. Os knowledge sources, o `@Output`, o WebSocket e o frontend não sabem qual planner está no comando. É o mesmo desenho de SPI que você conhece de JDBC ou JAX-RS: contrato estável no core, estratégias plugáveis na borda; e foi isso que permitiu decorar o planner com observabilidade sem tocar no algoritmo.

## Para experimentar

- Mude o texto do sintoma para citar (ou não) o banco e observe a ordem dos badges 1º/2º/3º mudar: mesma entrada nos outros 3 campos, ordem diferente.
- Deixe um campo vazio e veja a validação agir; depois comente a chamada a `validar()` no `WarRoomOrchestrator` e observe a **quiescência** (a investigação termina sem relatório e o cliente recebe um `ERROR` com as pendências do quadro; é a condição de parada nº 2 em ação).
- Troque a `ConflictResolutionStrategy` por `agentWithName("infra")` incondicional e veja o SRE falar sempre primeiro.
- Adicione um 6º agente `AnalistaDeSeguranca` que lê `logs` e escreve `evidenciaSeguranca`, **sem mexer em mais nada**: o scheduler o encaixa sozinho no fluxo (depois inclua a nova chave no `CorrelacionadorDeHipoteses` para ela pesar na hipótese).
- Reduza `warroom.max-passos` para `3` e observe a condição de parada nº 3 interromper a investigação antes do goal; o mesmo `ERROR` com as pendências chega ao cliente.

## Conclusão do módulo

Esta aula fecha o módulo de Agentes, e vale olhar a jornada inteira por um único eixo: **quem decide a ordem em que os agentes trabalham?**

| Aula | Padrão | Quem decide a ordem |
|---|---|---|
| 02 | Orchestrator-Workers | o **código** (sequência fixa, workers em paralelo) |
| 03 | Parallel (`@ParallelMapperAgent`) | o **código** (mesmo agente, N inputs simultâneos) |
| 04 | Supervisor | um **LLM planner**, a cada passo |
| 05 | Dynamic Routing (`@ChatModelSupplier`) | o **código**, trocando o modelo em runtime |
| 06 | Human-in-the-Loop | um **humano**, no meio do fluxo |
| 07 | Agent-to-Agent (A2A) | **outro agente**, negociando por protocolo |
| 08 | Voting | **ninguém**: todos opinam em paralelo e uma estratégia consolida |
| 09 | Blackboard | os **DADOS** no quadro compartilhado |

O Blackboard é o extremo "emergente" desse espectro: nenhum roteiro, nenhum chefe, nenhum LLM coordenando: a ordem **emerge** do que já se sabe. Na prática, você raramente escolhe um padrão puro: um sistema real pode usar um Blackboard cujos knowledge sources são supervisores, com um humano como knowledge source de aprovação e um agente remoto via A2A publicando evidências. O que este módulo te deu foi o vocabulário e os critérios para compor essas peças com intenção, sabendo o custo, o determinismo e a falha típica de cada uma.
