# Aula 03 — Parallel: Tradução de Comunicado Corporativo

> **Padrão**: Parallel (1 agente × N inputs em paralelo)
> **Case**: Tradução simultânea de um comunicado interno para 5 idiomas com adaptação cultural
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic (`quarkus-langchain4j-agentic` 1.9.2 + `langchain4j-agentic` 1.13.0-beta) · Ollama (`deepseek-v4-pro:cloud` + `gpt-oss:20b-cloud`)

---

## O que você vai aprender

A diferença entre **Orchestrator-Workers** (aula02) e **Parallel** está no número de classes de agente:

- **Aula02**: 4 agentes **distintos** rodando em paralelo (skills, experience, cultural, redFlags) — cada um com seu próprio prompt — agregados por um synthesizer
- **Aula03**: 1 único agente (`CulturalTranslator`) invocado **N vezes em paralelo** com inputs diferentes (5 idiomas)

A annotation declarativa apropriada é **`@ParallelMapperAgent`** — semântica de `map` funcional: aplica o mesmo agente a cada item de uma lista, paralelamente.

```
       @Inject TraducaoAgent traducaoAgent;
                       │
                       ▼
              ┌────────────────────────┐
              │  @ParallelMapperAgent  │       TraducaoAgent.java
              │   subAgent = Translator│       ↓ map sobre a lista de idiomas
              └────────────┬───────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼      ▼      ▼     ▼      ▼      ▼
     ┌─────┐┌─────┐┌─────┐┌─────┐┌─────┐
     │ EN  ││ ES  ││ FR  ││ DE  ││ ZH  │  ← mesma classe CulturalTranslator
     │     ││     ││     ││     ││     │    invocada 5 vezes em paralelo
     └─────┘└─────┘└─────┘└─────┘└─────┘    (gpt-oss:20b-cloud)
```

## Como rodar

```bash
cd modulo03/03_agentes/aula03/traducao-paralela
./mvnw quarkus:dev
```
Abra <http://localhost:8080/>.

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── CulturalTranslator.java   — @Agent worker (1 método, invocado N vezes)
│   └── ExampleGenerator.java     — AI Service auxiliar (gera comunicados de exemplo)
├── workflow/
│   ├── TraducaoAgent.java        — interface @ParallelMapperAgent (entrypoint)
│   └── TraducaoOrchestrator.java — wrapper Multi<ProgressUpdate>
├── rest/
│   └── ExampleResource.java      — endpoint /api/example/comunicado
├── dto/                          — Idioma, Traducao, ComunicadoInput, ProgressUpdate
└── TraducaoWebsocket.java        — endpoint /ws/traducao
```

### Pontos-chave

#### 1. `CulturalTranslator` — agente único + `@V("idioma")` singular

```java
@RegisterAiService(modelName = "smaller")
public interface CulturalTranslator {
    @SystemMessage("...{idioma.nome} ({idioma.codigo}) País-alvo: {idioma.paisAlvo}...")
    @UserMessage("...{comunicado}...")
    @Agent(name = "translator", outputKey = "traducao")
    Traducao traduzir(@V("idioma") Idioma idioma, @V("comunicado") String comunicado);
}
```

Note que o template `@SystemMessage` acessa propriedades do record `Idioma` via Qute (`{idioma.nome}`, `{idioma.codigo}`, `{idioma.paisAlvo}`).

#### 2. `TraducaoAgent` — `@ParallelMapperAgent` com `List<Idioma>` no método composto

```java
public interface TraducaoAgent {
    @ParallelMapperAgent(subAgent = CulturalTranslator.class, outputKey = "traducoes")
    List<Traducao> traduzir(@V("idiomas") List<Idioma> idiomas, @V("comunicado") String comunicado);
}
```

**Pegadinha didática**: o `@V` no método composto é **plural** (`"idiomas"` com `List<Idioma>`), o `@V` no sub-agente é **singular** (`"idioma"` com `Idioma`). O framework "destructura" automaticamente: passa cada elemento da lista para o sub-agente, em paralelo.

Se você usar o MESMO nome em ambos (ex: `@V("idiomas")` no sub-agente), o framework lança `AgenticSystemConfigurationException: Conflicting types for key 'idiomas': java.util.List and com.eldermoraes.dto.Idioma`.

#### 3. `TraducaoOrchestrator` — wrapper trivial

```java
@Inject TraducaoAgent traducaoAgent;

private void runTraducao(String comunicado, MultiEmitter<...> emitter) {
    List<Idioma> idiomas = Idioma.alvos();
    emitter.emit(ProgressUpdate.started(...));
    List<Traducao> traducoes = traducaoAgent.traduzir(idiomas, comunicado);
    emitter.emit(ProgressUpdate.allDone(traducoes));
    emitter.complete();
}
```

Sem `StructuredTaskScope`, sem `CompletableFuture`. O framework cuida do paralelismo (Executor próprio, configurável via `@ParallelExecutor` static).

#### 4. `ExampleGenerator` — exemplos via LLM, não hardcoded

```java
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {
    @SystemMessage("Gere um comunicado corporativo realista em PT-BR...")
    @UserMessage("Gere um novo comunicado de exemplo agora.")
    String comunicadoExemplo();
}
```

Servido via REST em `/api/example/comunicado`; frontend chama com fetch quando o aluno clica em "↳ usar comunicado de exemplo".

## Trade-off: feedback ao vivo

A versão declarativa não emite `LANG_DONE` por idioma — a sequência WS é `STARTED → ALL_DONE` (com lista completa de traduções). Internamente os 5 workers rodam em paralelo (tempo total ≈ 10s, não 50s), mas isso não é observável no cliente.

O frontend reage ao `ALL_DONE` preenchendo os 5 cards de uma vez. Para observability granular per-agente seria necessário `@AgentListenerSupplier` global — vale a pena estudar quando tema/complexidade pedir.

## O que observar

| Observação | Explica… |
|---|---|
| 5 chamadas HTTP ao `gpt-oss:20b-cloud` em paralelo nos logs | Framework executa em paralelo via Executor próprio |
| `ALL_DONE` traz `traducoes: List<Traducao>` (5 items) | `@ParallelMapperAgent` retorna a lista no `outputKey` |
| Cada `Traducao` tem `notasAdaptacao` com 5+ itens | Worker faz adaptação cultural (não tradução literal) |

## Para experimentar

- Adicione um sexto idioma em `Idioma.alvos()` (ex: japonês) — o `@ParallelMapperAgent` se ajusta automaticamente
- Customize o `@ParallelExecutor` adicionando um static method na interface `TraducaoAgent` para controlar pool de threads
- Faça o `CulturalTranslator` usar o modelo default (120b) para um idioma específico — compare qualidade vs latência

## Próxima aula

Aula 04: **Supervisor** — LLM-planner que escolhe especialista (Cardio/Neuro/Ortopedia/GI) e roteia. Usa `@SupervisorAgent` declarativo + `responseStrategy`.
