# Aula 03 — Parallel: Tradução de Comunicado Corporativo

> **Padrão**: Parallel
> **Case**: Tradução simultânea de um comunicado interno para 5 idiomas com adaptação cultural
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic 1.10.x · Ollama (`gpt-oss:120b-cloud`)

---

## O que você vai aprender

Este projeto demonstra o padrão **Parallel**: o mesmo agente é invocado **N vezes em paralelo** com parâmetros diferentes, e os resultados são consolidados. Não há orquestrador inteligente — apenas fan-out e fan-in.

```
                  ┌──────────────────┐
                  │   Comunicado PT  │
                  └────────┬─────────┘
                           │
        ┌───────┬──────────┼──────────┬──────────┐
        ▼       ▼          ▼          ▼          ▼
     🇺🇸 EN   🇪🇸 ES      🇫🇷 FR      🇩🇪 DE      🇨🇳 ZH
        (mesmo `CulturalTranslator` invocado 5x em paralelo)

                Resultados chegam fora de ordem
```

### Diferença vs. Orchestrator-Workers

| | Orchestrator-Workers (aula02) | Parallel (aula03) |
|---|---|---|
| **Workers** | Diferentes (cada um faz coisa distinta) | Mesmo agente, parâmetros diferentes |
| **Síntese** | LLM final pondera resultados | Sem síntese — resultados independentes |
| **Quando usar** | Tarefas decomponíveis em sub-tarefas heterogêneas | N versões/variações homogêneas da mesma operação |

### Casos de uso reais

- **Tradução multi-idioma** (este projeto)
- **Geração de variantes de copy** (LinkedIn, Tweet, Email, slogan a partir do mesmo briefing)
- **Análise multi-canal** (mesmo produto avaliado em SAC, Twitter, App Store, Zendesk)
- **Comparação de modelos** (mesma prompt em Opus, GPT-5, Gemini para benchmarking)

---

## Como rodar

```bash
cd modulo03/03_agentes/aula03/traducao-paralela
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Como usar a UI

1. **Cole um comunicado em português** (ou clique em "usar comunicado de exemplo")
2. Clique em **Traduzir em paralelo**
3. Observe os 5 cards:
   - Todos começam com spinner ao mesmo tempo
   - Vão sendo preenchidos **em ordens diferentes** (paralelismo real — o que terminar primeiro aparece primeiro)
   - Cada card mostra o tempo individual (`4.2s`, `6.1s`, etc.)
   - Cada tradução tem um expand **"Notas de adaptação cultural"** mostrando as decisões do modelo

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   └── CulturalTranslator.java     — único AI Service, parametrizado por idioma
├── workflow/
│   └── TraducaoOrchestrator.java   — fan-out + fan-in via CompletableFuture
├── dto/                             — records
└── TraducaoWebsocket.java          — endpoint /ws/traducao
```

### Pontos-chave para os alunos

#### 1. Um único AI Service, parametrizado

O `CulturalTranslator` é uma única interface — não criamos 5 agentes diferentes. O idioma, código e país-alvo entram como `@V("...")`:

```java
@RegisterAiService
public interface CulturalTranslator {
    @SystemMessage("Você é um tradutor para {idiomaNome} ({idiomaCodigo})...")
    @UserMessage("Comunicado: {comunicado}")
    Traducao traduzir(
        @V("idiomaNome") String nome,
        @V("idiomaCodigo") String codigo,
        @V("paisAlvo") String pais,
        @V("comunicado") String comunicado);
}
```

#### 2. Fan-out com `CompletableFuture.supplyAsync`

```java
ExecutorService executor = Executors.newFixedThreadPool(5);
List<CompletableFuture<Void>> futures = idiomas.stream()
    .map(idioma -> CompletableFuture
        .supplyAsync(() -> translator.traduzir(...), executor)
        .orTimeout(150, TimeUnit.SECONDS)
        .handle((traducao, throwable) -> { ... }))
    .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

#### 3. Falhas isoladas

Se 1 dos 5 idiomas falhar (timeout, modelo retornando texto inválido), os outros 4 **não são afetados**. O `handle((result, throwable) -> ...)` captura a falha e emite `LANG_ERROR` para aquele card específico, mantendo o painel funcional.

#### 4. WebSocket emite por idioma

Para cada idioma que termina (sucesso ou erro), o orquestrador emite `LANG_DONE` ou `LANG_ERROR` via `connection.sendText(...)`. O frontend renderiza o card daquele idioma imediatamente — sem esperar os outros.

---

## Alternativa declarativa

A versão declarativa com `@ParallelAgent` ficaria assim (5 sub-agentes nomeados explicitamente):

```java
public interface TraducaoAgent {
    @ParallelAgent(outputKey = "traducoes", subAgents = {
        @SubAgent(type = TranslatorEN.class, outputKey = "en"),
        @SubAgent(type = TranslatorES.class, outputKey = "es"),
        @SubAgent(type = TranslatorFR.class, outputKey = "fr"),
        @SubAgent(type = TranslatorDE.class, outputKey = "de"),
        @SubAgent(type = TranslatorZH.class, outputKey = "zh")
    })
    Map<String,Traducao> traduzir(@V("comunicado") String comunicado);
}
```

**Trade-off**: 5 interfaces separadas (uma por idioma) e perda do fan-out dinâmico. A versão programática reusa **um** serviço e roda em loop sobre uma lista — naturalmente extensível para 50 idiomas.

---

## O que observar no frontend

| Observação | Explica… |
|---|---|
| Os 5 cards começam com spinner ao mesmo tempo | Fan-out simultâneo |
| Os cards terminam em ordens **diferentes** a cada execução | Paralelismo real (latência varia) |
| O tempo total ≈ tempo do idioma mais lento | Não soma das 5 chamadas |
| Cada card tem seu próprio timer individual | Visibilidade de latência por chamada |
| Notas de adaptação cultural | O modelo justifica suas escolhas (não é tradução literal) |

---

## Para experimentar

- **Adicione mais idiomas** em `Idioma.alvos()` (ex: Japonês, Italiano, Português Europeu) — o `Executors.newFixedThreadPool(idiomas.size())` se ajusta automaticamente
- **Mude o `@SystemMessage`** para pedir tradução **literal** em vez de adaptação cultural — compare resultados
- **Compare custo** com versão sequencial: mude o `parallelism` para 1 e meça o tempo total
- **Saturação**: tente 20 idiomas — quando o provider Ollama começa a serializar/throttling?

---

## Próxima aula

Aula 04: **Supervisor** — agente LLM que decide qual especialista chamar e valida a resposta (triagem médica hospitalar).
