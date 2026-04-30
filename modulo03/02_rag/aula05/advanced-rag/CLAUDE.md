# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

This is `aula05` (lesson 5) of `modulo03/02_rag` from a Java AI Specialist course — a teaching example demonstrating an "advanced RAG" pattern in Quarkus + LangChain4j. The code and prompts are in Portuguese (Brazilian).

The parent directory contains other lessons; this module is self-contained with its own `pom.xml` and should be operated on as an independent Maven project (run commands from this directory, not the repo root).

## Common commands

```bash
./mvnw quarkus:dev                          # Live-reload dev mode; Dev UI at http://localhost:8080/q/dev/
./mvnw test                                 # Run unit tests (JUnit 5 + RestAssured)
./mvnw test -Dtest=ClassName#methodName     # Run a single test
./mvnw package                              # Build target/quarkus-app/quarkus-run.jar
./mvnw package -Dnative                     # Build native executable (requires GraalVM)
./mvnw package -Dnative -Dquarkus.native.container-build=true   # Native build via container
./mvnw verify -Dnative                      # Run integration tests against native image (skipITs=true otherwise)
```

Java 25 is required (`maven.compiler.release=25`). Quarkus platform is `3.34.6`.

Try the chat endpoint:

```bash
curl -X POST -H "Content-Type: text/plain" \
  --data "Quantos dias de férias eu tenho?" \
  http://localhost:8080/chat/session-1
```

## Runtime dependency: Ollama

The app uses `quarkus-langchain4j-ollama` for both chat and embedding models. A reachable Ollama instance is required at runtime — Ollama Dev Services are explicitly **disabled** (`quarkus.langchain4j.ollama.devservices.enabled=false`) and preload is off (`quarkus.langchain4j.devservices.preload=false`).

Two named chat models are configured in `src/main/resources/application.properties` and selected via `@ModelName(...)`:

- `assistant` — `gpt-oss:120b-cloud`, temperature 0.9, 120s timeout. Used by `AssistantService` (final answer generation).
- `smaller` — `gpt-oss:20b-cloud`, temperature 0, 60s timeout, `num-predict=512`. Used for everything that needs deterministic, cheap calls: query rewriting, intent classification, contextual situating, and LLM-as-a-judge reranking.

Embeddings: `nomic-embed-text` (default unnamed embedding model bean).

## Architecture: the "advanced RAG" pattern

The whole point of this lesson is the wiring in `RagConfig.java`. The `RetrievalAugmentor` producer composes five techniques:

1. **Query rewriting** — `CompressingQueryTransformer(chatModel)` (uses the `smaller` model) rewrites the user's question against chat history so follow-ups like "and for managers?" become standalone queries before retrieval.

2. **Semantic routing via a typed AI service** — `IntentClassifierService` is a `@RegisterAiService(modelName = "smaller")` interface whose `classify(String)` method returns a `UserIntent` enum (`RH`, `TI`, `DESCONHECIDO`). LangChain4j parses the LLM's response directly into the enum. The `QueryRouter` lambda calls this classifier on each (rewritten) query and uses a Java `switch` on the enum to pick which retrievers to invoke. `DESCONHECIDO` returns an empty retriever list, short-circuiting search to save tokens. The classifier itself disables the global `RetrievalAugmentor` (`NoRetrievalAugmentorSupplier`) to avoid recursion with the router.

3. **Hybrid search per domain** — for each domain there are TWO retrievers used together:
   - `EmbeddingStoreContentRetriever` over an `InMemoryEmbeddingStore<TextSegment>` (dense / vector).
   - `InMemoryBM25Retriever` (sparse / lexical), a hand-rolled BM25 implementation in `InMemoryBM25Retriever.java` (k1=1.2, b=0.75, top-K=3).

   The router returns `List.of(rhRetriever, rhBm25)` or `List.of(itRetriever, itBm25)` so both run and their results are merged downstream.

4. **LLM-as-a-judge reranking** — `LlmAsScoringModel` implements `ScoringModel` by prompting the `smaller` model to score each `(query, segment)` pair on a 0–10 scale, then normalizes to 0–1. It is wired as a `ReRankingContentAggregator` with `maxResults=3`, so the merged hybrid candidates get re-scored and trimmed before injection.

5. **Contextual retrieval at ingestion** — `ContextService.situate(documento, trecho)` (also `smaller`, also with `NoRetrievalAugmentorSupplier` to avoid RAG-on-itself during boot) generates a short situational context for each chunk. `LoadDataService` prepends that context to the chunk before embedding/indexing, improving recall on both the dense and sparse sides.

6. **Content injection** — `DefaultContentInjector` is configured to include the `source` metadata key in the prompt so the LLM can cite which document each fact came from.

### Beans and qualifiers

Per-domain stores and BM25 indexes are produced as `@Named` beans in `RagConfig`:

| Domain | Embedding store (`@Named`) | BM25 retriever (`@Named`) |
|--------|----------------------------|----------------------------|
| HR     | `rhStore`                  | `rhBm25`                   |
| IT     | `itStore`                  | `itBm25`                   |

`LoadDataService` injects all four by name and `RagConfig.advancedRetrievalAugmentor(...)` does the same.

### Ingestion

`LoadDataService` is `@ApplicationScoped` with a `@Startup` `init()` method that populates both stores at boot from two hard-coded Portuguese documents (Acme Corp HR manual and IT infrastructure policy). For each `\n\n`-separated chunk it:

1. calls `ContextService.situate(...)` to generate a situational prefix;
2. concatenates `context + "\n\n" + chunk` into a `TextSegment` with `Metadata.from("source", <doc name>)`;
3. embeds it and adds to the corresponding `EmbeddingStore`;
4. adds the same segment to the corresponding `InMemoryBM25Retriever`.

To add a new domain you must: add a `UserIntent` enum value, update the classifier system message so the LLM knows the new label, add `@Named` producers for store + BM25 in `RagConfig`, add a retriever pair and a `case` in the router switch, and add an `ingest(...)` call in `LoadDataService.init()`.

### REST surface

`ChatResource` exposes `POST /chat/{sessionId}` (text/plain in, text/plain out). It delegates to `AssistantService.message(@MemoryId String sessionId, @UserMessage String message)`. The path's `sessionId` is the LangChain4j `@MemoryId`, so per-conversation chat memory is keyed by it — different IDs get different histories, which feeds the query rewriter.

`AssistantService` is `@RegisterAiService(modelName = "assistant")` and uses the global `RetrievalAugmentor` produced in `RagConfig`. It is the only AI service that should trigger RAG; the `smaller`-model services explicitly opt out via `NoRetrievalAugmentorSupplier`.

## Conventions

- Packages: `com.eldermoraes` (config, REST, BM25, ingestion, enum) and `com.eldermoraes.ai` (typed AI services + scoring model).
- Prompts and log messages are in Portuguese; preserve the language when editing existing strings unless asked otherwise.
- Diagnostic `System.out.println` calls are intentional for the lesson (they show intent classification, BM25 scores, and reranker scores live in the dev console). Don't strip them when refactoring unless asked.
- `skipITs=true` by default; the `native` Maven profile flips it to false so `verify` runs `*IT` tests against the built native image.