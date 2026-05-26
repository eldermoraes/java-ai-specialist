# Aula 02 — Orchestrator-Workers: Triagem Curricular

> **Padrão**: Orchestrator-Workers
> **Case**: Triagem de currículos para vagas de tecnologia
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic (`quarkus-langchain4j-agentic` 1.9.2 + `langchain4j-agentic` 1.13.0-beta) · Ollama (`deepseek-v4-pro:cloud` + `gpt-oss:20b-cloud`)

---

## O que você vai aprender

Esta é a **primeira aula do módulo Agentes** — é onde você sai do mundo "AI Services soltos" para o mundo de **agentes compostos via framework**.

O padrão **Orchestrator-Workers** decompõe uma tarefa complexa em pedaços, **delega** cada pedaço a um agente especializado (worker), executa os workers **em paralelo** e por fim **sintetiza** os resultados num parecer único.

Você vai construir isso usando 4 anotações do LangChain4j Agentic, todas declarativas — o Quarkus extension registra cada interface anotada como um bean `@ApplicationScoped` automaticamente, e você apenas `@Inject` o resultado:

1. **`@Agent`** em cada AI Service worker — declara que aquela interface é um agente, com `name`, `description` e `outputKey` (a chave no `AgenticScope` onde o resultado vai parar)
2. **`@ParallelAgent`** numa interface "marker" — referencia os 4 workers em `subAgents = {...}`, o framework executa todos em paralelo
3. **`@SequenceAgent`** noutra interface — encadeia o parallel + o synthesizer
4. **`@Output`** static method — pós-processa o resultado lendo `AgenticScope` para montar o `TriagemReport` final completo

```
       @Inject TriagemAgent triagemAgent;   ←  Quarkus produz o bean automaticamente
                       │
                       ▼
              ┌─────────────────────┐
              │   @SequenceAgent    │       TriagemAgent.java
              │  (TriagemAgent)     │       ↓ ordena 2 sub-agents
              └──────────┬──────────┘
                         │
        ┌────────────────┴───────────────┐
        ▼                                ▼
┌─────────────────┐              ┌─────────────────┐
│ @ParallelAgent  │              │ @Agent          │
│ (WorkersParallel)│  ── then ─▶ │ ReportSynthesizer│
└────────┬─────────┘              │ (deepseek-v4-pro)│
         │                        └─────────────────┘
   ┌─────┼─────┬─────┐
   ▼     ▼     ▼     ▼
┌────┐┌─────┐┌─────┐┌──────┐
│Skil││Exp- ││Cult-││RedFl-│  ← 4 @Agent workers
│ ls ││erie-││ural ││ ags  │    (gpt-oss:20b-cloud)
└────┘└─────┘└─────┘└──────┘
```

### Por que este padrão importa

- **Decomposição reduz alucinação**: cada agente olha um aspecto bem definido (skills, experiência, fit cultural, red flags) em vez de pedir um veredito holístico
- **Paralelismo encurta latência**: o framework executa os 4 workers em paralelo — tempo total ≈ tempo do mais lento, não soma dos 4
- **Auditabilidade**: o parecer final cita os 4 sub-relatórios — você pode inspecionar a base de cada conclusão
- **Mix de modelos**: workers usam modelo barato (`gpt-oss:20b-cloud`), sintetizador usa modelo robusto (`deepseek-v4-pro:cloud`)
- **Declarativo > programático**: você nunca chama `parallelBuilder()` ou `sequenceBuilder()` manualmente — o framework Quarkus detecta as annotations em build time e gera os SyntheticBeans

---

## Como rodar

Pré-requisito: ter `OLLAMA_HOST` configurado para o Ollama Cloud (deepseek-v4-pro:cloud e gpt-oss:20b-cloud) ou um Ollama local com os modelos baixados.

```bash
cd modulo03/03_agentes/aula02/triagem-curricular
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── SkillsAnalyzer.java        — @Agent worker: match de skills técnicas
│   ├── ExperienceAnalyzer.java    — @Agent worker: trajetória e gaps
│   ├── CulturalFitAnalyzer.java   — @Agent worker: soft skills, tom
│   ├── RedFlagsAnalyzer.java      — @Agent worker: inconsistências
│   ├── ReportSynthesizer.java     — @Agent sintetizador final
│   └── ExampleGenerator.java      — AI Service auxiliar (gera vagas/CVs de exemplo)
├── workflow/
│   ├── WorkersParallel.java       — interface @ParallelAgent (agrega os 4 workers)
│   ├── TriagemAgent.java          — interface @SequenceAgent + @Output (entrypoint)
│   └── TriagemOrchestrator.java   — wrapper que envolve TriagemAgent em Multi<ProgressUpdate>
├── rest/
│   └── ExampleResource.java       — endpoints /api/example/{vaga,cv}
├── dto/                            — records (input/output)
└── TriagemWebsocket.java          — endpoint /ws/triagem (retorna Multi<ProgressUpdate>)
```

### Pontos-chave para os alunos

#### 1. Workers como `@Agent`

Cada worker é uma `interface` com **duas** anotações principais. `@RegisterAiService(modelName = "smaller")` cria o bean Quarkus apontando para `gpt-oss:20b-cloud`. `@Agent(name, description, outputKey)` declara o método como **agente** dentro do framework agentic — o `outputKey` define onde o resultado fica no `AgenticScope` para outros agents lerem.

```java
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface SkillsAnalyzer {
    @SystemMessage("...JSON schema...")
    @UserMessage("VAGA:\n{vaga}\n\nCV:\n{cv}")
    @Agent(name = "skills",
            description = "Analisa a aderência das skills técnicas do candidato à vaga",
            outputKey = "skills")
    SkillsReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
```

#### 2. `WorkersParallel` — `@ParallelAgent` aglomerando os 4 workers

Uma interface "marker" que só serve para declarar a composição paralela. O framework executa os 4 sub-agents em paralelo, propaga `vaga`/`cv` para todos (via `@V`), e armazena cada resultado no `AgenticScope` sob a chave `outputKey` declarada em cada `@Agent`.

```java
public interface WorkersParallel {
    @ParallelAgent(
        outputKey = "workers",
        subAgents = {
            SkillsAnalyzer.class,
            ExperienceAnalyzer.class,
            CulturalFitAnalyzer.class,
            RedFlagsAnalyzer.class
        }
    )
    Object run(@V("vaga") String vaga, @V("cv") String cv);
}
```

O retorno é `Object` apenas para satisfazer a validação do AI Services (não pode ser `void`). O retorno do método não é usado — os resultados vão para o `AgenticScope` via `outputKey` de cada worker individualmente.

#### 3. `TriagemAgent` — `@SequenceAgent` orquestrando parallel + synthesizer

Encadeia dois agents: primeiro o paralelo, depois o synthesizer. O `@SequenceAgent` propaga o `AgenticScope` entre as etapas — quando o synthesizer é chamado, ele já tem `{vaga, skills, experience, cultural, redFlags}` disponíveis para resolução dos `@V`.

```java
public interface TriagemAgent {
    @SequenceAgent(
        outputKey = "triagemFinal",
        subAgents = { WorkersParallel.class, ReportSynthesizer.class }
    )
    TriagemReport triagem(@V("vaga") String vaga, @V("cv") String cv);

    @Output
    static TriagemReport assemble(AgenticScope scope) {
        TriagemReport parcial = (TriagemReport) scope.readState("triagemFinal");
        return new TriagemReport(
                parcial.scoreFinal(), parcial.recomendacao(), parcial.justificativa(),
                (SkillsReport) scope.readState("skills"),
                (ExperienceReport) scope.readState("experience"),
                (CulturalFitReport) scope.readState("cultural"),
                (RedFlagsReport) scope.readState("redFlags"));
    }
}
```

**`@Output` static method** é o "pós-processamento" do agentic system. Sem ele, o método retornaria apenas o output do último sub-agent (o synthesizer), com os campos `skills`/`experience`/`cultural`/`redFlags` do `TriagemReport` ficando `null` (porque o synthesizer não os preenche — só o `AgenticScope` os tem, vindos dos workers).

#### 4. `TriagemOrchestrator` — wrapper Multi para WebSocket

Quarkus extension produz o bean `TriagemAgent` automaticamente. O orchestrator apenas `@Inject` ele e o envolve em `Multi<ProgressUpdate>` para o frontend.

```java
@ApplicationScoped
public class TriagemOrchestrator {
    @Inject TriagemAgent triagemAgent;

    public Multi<ProgressUpdate> triagem(String vaga, String cv) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> {
                    try {
                        emitter.emit(ProgressUpdate.started());
                        TriagemReport result = triagemAgent.triagem(vaga, cv);
                        emitter.emit(ProgressUpdate.done(result));
                        emitter.complete();
                    } catch (Exception e) { emitter.fail(e); }
                }));
    }
}
```

---

## Trade-off: declarativo vs progresso ao vivo

A versão declarativa **não emite progresso por worker** ao vivo — você vê `STARTED → DONE` no WebSocket. Internamente os 4 workers rodam em paralelo, mas o framework não expõe callbacks per-request (apenas via `@AgentListenerSupplier` global, que não é adequado para uma conexão WS específica sem complexidade adicional).

**O que você ganha em troca**:
- ~90% menos código no orchestrator (de ~70 para ~25 linhas)
- Nenhuma referência a `AgenticServices.parallelBuilder`/`sequenceBuilder` ou `ClientProxy.unwrap` — tudo via annotations + `@Inject`
- Validação build-time pelo Quarkus extension (`AgenticProcessor`) — alinhamento de tipos/outputKeys verificado antes do app subir

**Como o frontend continua mostrando 4 cards**: ao receber `DONE`, o `TriagemReport` traz os 4 sub-reports embutidos (graças ao `@Output assemble`). O JS renderiza todos de uma vez. Você não vê os workers terminando em ordens diferentes (paralelismo visível), mas o paralelismo ainda existe — só não é observável ao vivo do lado do cliente.

Veja `pattern_lc4j_agentic_programmatic` (memória) se quiser a versão programática alternativa que preserva progresso por worker via `AgentListener` custom — paga-se em código extra e em `ClientProxy.unwrap()`.

---

## O que observar

| Observação | Explica… |
|---|---|
| Sequência WS: `STARTED → DONE` apenas (sem WORKER_DONE×4) | Framework declarativo executa tudo opaque-to-client |
| Tempo total ≈ tempo do worker mais lento | Paralelismo interno do framework (Executor próprio) |
| O `TriagemReport.skills/experience/cultural/redFlags` vem preenchido | `@Output assemble()` montou a partir do `AgenticScope` |
| Logs do servidor mostram 4 chamadas ao `gpt-oss:20b-cloud` + 1 ao `deepseek-v4-pro:cloud` | 4 workers + synthesizer, em paralelo + sequence |

---

## Para experimentar

- **Edite os system prompts** dos workers (`src/main/java/com/eldermoraes/ai/*Analyzer.java`) — veja como a qualidade muda
- **Adicione um quinto worker** (ex: `LanguagesAnalyzer`): basta criar a interface com `@Agent`, incluir em `WorkersParallel.subAgents` e adicionar `@V("languages")` no `ReportSynthesizer`. Atualize `TriagemReport` + `@Output assemble()` para preservar no resultado final
- **Substitua `@SequenceAgent` por `@LoopAgent`** com `@ExitCondition` para experimentar iteração até convergência

---

## Próxima aula

Aula 03: **Parallel** — mesmo agente invocado N vezes em paralelo (tradução de comunicado em 5 idiomas). Lá você vai ver `StructuredTaskScope` (JDK 25 preview, JEP 505) como alternativa para paralelismo "puro" — quando você NÃO quer compor agentes via framework, só rodar a mesma função em paralelo com controle fino de progresso.
