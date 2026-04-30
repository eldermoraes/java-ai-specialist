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

## Runtime dependency: Ollama

The app uses `quarkus-langchain4j-ollama` for both the chat model and embedding model. A reachable Ollama instance is required at runtime. `src/main/resources/application.properties` is currently empty, so all LangChain4j/Ollama settings default to Quarkus extension defaults — when adding model config, do it there (e.g. `quarkus.langchain4j.ollama.chat-model.model-id`, `quarkus.langchain4j.ollama.base-url`).

## Architecture: the "advanced RAG" pattern

The whole point of this lesson is the wiring in `RagConfig.java`. Three CDI producers compose into one `RetrievalAugmentor`:

1. **Query rewriting** — `CompressingQueryTransformer(chatModel)` rewrites the user's question against chat history so follow-ups like "and for managers?" become standalone queries before retrieval.

2. **Semantic routing via a typed AI service** — `IntentClassifier` is a `@RegisterAiService` interface whose `classify(String)` method returns a `UserIntent` enum (`RH`, `TI`, `DESCONHECIDO`). LangChain4j parses the LLM's response directly into the enum. The `QueryRouter` lambda calls this classifier on each (rewritten) query and uses a Java `switch` on the enum to pick which retriever to invoke. `DESCONHECIDO` returns an empty retriever list, short-circuiting vector search to save tokens.

3. **Multiple isolated embedding stores** — `rhStore` (HR) and `itStore` (IT) are separate `InMemoryEmbeddingStore<TextSegment>` beans, distinguished by `@Named` qualifiers and injected by name into the augmentor producer. Each gets its own `EmbeddingStoreContentRetriever`. Adding a new domain means: add an enum value, add a `@Named` store producer, add a retriever, add a `case` in the router switch.

`DefaultRetrievalAugmentor.builder()` chains transformer → router. There is no explicit `ContentAggregator` or `ContentInjector` — defaults apply.

### Why this matters for changes

- The stores are **in-memory** and never populated in this codebase. Any working demo needs an ingestion path (e.g. a `@Startup` bean, REST endpoint, or test fixture) that calls `embeddingStore.add(embedding, segment)` for each store.
- There is **no REST resource yet** — `quarkus-rest` and `quarkus-smallrye-openapi` are on the classpath but no `@Path` class exists. Wiring an endpoint that consumes the augmentor (typically via another `@RegisterAiService` chat assistant interface that references the `RetrievalAugmentor` bean) is an expected next step.
- The classifier prompt and `UserIntent` enum values must stay in sync. If you add/rename an enum value, update the system message in `IntentClassifier` so the LLM knows the new label.

## Conventions

- Package: `com.eldermoraes` (flat — no sub-packages yet).
- Prompts and log messages are in Portuguese; preserve the language when editing existing strings unless asked otherwise.
- `skipITs=true` by default; the `native` Maven profile flips it to false so `verify` runs `*IT` tests against the built native image.