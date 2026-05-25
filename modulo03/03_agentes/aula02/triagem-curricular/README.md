# Aula 02 — Orchestrator-Workers: Triagem Curricular

> **Padrão**: Orchestrator-Workers
> **Case**: Triagem de currículos para vagas de tecnologia
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic 1.10.x · Ollama (`gpt-oss:120b-cloud` + `gpt-oss:20b-cloud`)

---

## O que você vai aprender

Este projeto demonstra o padrão **Orchestrator-Workers**: um agente orquestrador **decompõe** uma tarefa complexa, **delega** os pedaços para workers especializados que executam **em paralelo**, e por fim **sintetiza** os resultados em uma resposta consolidada.

```
                       ┌──────────────────┐
                       │    Orquestrador  │
                       │  (Java code +    │
                       │   ExecutorService)│
                       └────────┬─────────┘
                                │
        ┌───────────┬───────────┼───────────┬───────────┐
        ▼           ▼           ▼           ▼           ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
   │ Skills  │ │ Experi- │ │  Fit    │ │  Red    │   ← 4 workers
   │Analyzer │ │  ência  │ │Cultural │ │ Flags   │   em paralelo
   └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   (modelo smaller)
        └───────────┴─────┬─────┴───────────┘
                          ▼
                  ┌──────────────────┐
                  │    Synthesizer   │   ← LLM sintetiza
                  │  (gpt-oss:120b)  │      score + parecer
                  └──────────────────┘
```

### Por que este padrão importa

- **Decomposição reduz alucinação**: cada worker olha um aspecto bem definido (skills, experiência, fit cultural, red flags) em vez de pedir um veredito holístico
- **Paralelismo encurta latência**: 4 chamadas LLM em paralelo terminam no tempo da mais lenta, não na soma das 4
- **Auditabilidade**: o parecer final cita os 4 sub-relatórios — você pode inspecionar a base de cada conclusão
- **Mix de modelos**: workers usam modelo barato (`gpt-oss:20b-cloud`), sintetizador usa modelo robusto (`gpt-oss:120b-cloud`)

---

## Como rodar

Pré-requisito: ter `OLLAMA_HOST` configurado para o Ollama Cloud (gpt-oss:120b-cloud e gpt-oss:20b-cloud) ou um Ollama local com os modelos baixados.

```bash
cd modulo03/03_agentes/aula02/triagem-curricular
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Como usar a UI

1. **Cole uma vaga** no painel esquerdo (ou clique em "usar exemplo")
2. **Cole um CV** no painel direito (ou clique em "usar exemplo")
3. Clique em **Analisar Candidato**
4. Observe o pipeline:
   - Os 4 cards de workers (Skills, Experiência, Fit Cultural, Red Flags) começam com spinner
   - Cada um é preenchido **em momentos diferentes** conforme o LLM responde (paralelismo real)
   - Depois que os 4 terminam, aparece **"Sintetizando…"**
   - Por fim, o **Parecer Final** com score consolidado e recomendação (AVANCAR / REVISAO_HUMANA / NAO_AVANCAR)

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── SkillsAnalyzer.java        — worker: match de skills técnicas
│   ├── ExperienceAnalyzer.java    — worker: trajetória e gaps
│   ├── CulturalFitAnalyzer.java   — worker: soft skills, tom
│   ├── RedFlagsAnalyzer.java      — worker: inconsistências
│   └── ReportSynthesizer.java     — sintetizador final
├── workflow/
│   └── TriagemOrchestrator.java   — orquestrador (CompletableFuture)
├── dto/                            — records (input/output)
└── TriagemWebsocket.java          — endpoint /ws/triagem
```

### Pontos-chave para os alunos

#### 1. Workers como AI Services independentes

Cada worker é uma `interface` anotada com `@RegisterAiService(modelName = "smaller")`. O `modelName` aponta para a configuração `quarkus.langchain4j.ollama.smaller.*` no `application.properties` (modelo `gpt-oss:20b-cloud`, mais rápido e barato).

```java
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface SkillsAnalyzer {
    @SystemMessage("...JSON schema...")
    @UserMessage("VAGA:\n{vaga}\n\nCV:\n{cv}")
    SkillsReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
```

#### 2. Orquestrador com paralelismo explícito

A composição é feita em Java puro — `CompletableFuture.supplyAsync(...)` para cada worker, `CompletableFuture.allOf(...).join()` para sincronizar, depois `synthesizer.synthesize(...)`. Cada worker tem `.exceptionally(...)` para que falha em 1 não derrube o lote.

```java
var skillsFuture = CompletableFuture.supplyAsync(() -> skillsAnalyzer.analyze(vaga, cv), executor)
    .orTimeout(120, SECONDS)
    .exceptionally(t -> SkillsReport.empty(t.getMessage()))
    .whenComplete((r, t) -> progress.accept(ProgressUpdate.workerDone("skills", r)));
```

#### 3. WebSocket com progresso incremental

O `TriagemWebsocket` injeta `WebSocketConnection` e emite mensagens conforme cada worker termina. O frontend renderiza cada card no momento exato em que chega — visualmente fica claro que estão executando **em paralelo** (terminam em ordens diferentes a cada execução).

---

## Alternativa declarativa (com `@SequenceAgent` + `@ParallelAgent`)

O LangChain4j Agentic também oferece **composição declarativa** via anotações. Equivalente conceitual ao código deste projeto seria:

```java
public interface TriagemAgent {
    @SequenceAgent(outputKey = "triagemReport", subAgents = {
        @SubAgent(type = WorkersParallel.class, outputKey = "workersDone"),
        @SubAgent(type = ReportSynthesizer.class, outputKey = "triagemReport")
    })
    TriagemReport triagem(@V("vaga") String vaga, @V("cv") String cv);
}

interface WorkersParallel {
    @ParallelAgent(outputKey = "workersDone", subAgents = {
        @SubAgent(type = SkillsAnalyzer.class, outputKey = "skills"),
        @SubAgent(type = ExperienceAnalyzer.class, outputKey = "experience"),
        @SubAgent(type = CulturalFitAnalyzer.class, outputKey = "cultural"),
        @SubAgent(type = RedFlagsAnalyzer.class, outputKey = "redFlags")
    })
    void run(@V("vaga") String vaga, @V("cv") String cv);
}
```

**Trade-off**: a versão declarativa é mais sucinta, mas não permite emitir progress updates por worker no WebSocket (a resposta vem em um único bloco no final). Para a aula, escolhemos a versão programática justamente para **mostrar visualmente** o paralelismo.

---

## O que observar no frontend

| Observação | Explica… |
|---|---|
| Os 4 cards terminam em **ordens diferentes** | Paralelismo real — workers rápidos chegam antes |
| Tempo total ≈ tempo do worker mais lento | Não soma das 4 chamadas |
| O parecer final só aparece depois dos 4 cards | Sincronização (`allOf().join()`) |
| Se um worker falhar, o card fica vermelho mas o pipeline continua | `.exceptionally(...)` evita cascata |

---

## Para experimentar

- **Edite os system prompts** dos workers (`src/main/java/com/eldermoraes/ai/*Analyzer.java`) — veja como a qualidade muda
- **Mude `modelName = "smaller"` para o default** (remova o parâmetro) em um worker — compare latência e qualidade
- **Adicione um quinto worker** (ex: `LanguagesAnalyzer`) e atualize o orquestrador + frontend
- **Troque para a versão declarativa** com `@SequenceAgent`/`@ParallelAgent` — veja o que perde de visibilidade

---

## Próxima aula

Aula 03: **Parallel** — mesmo agente invocado N vezes em paralelo (tradução de comunicado em 5 idiomas).
