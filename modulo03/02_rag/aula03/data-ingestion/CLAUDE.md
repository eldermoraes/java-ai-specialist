# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

This module (`modulo03/02_rag/aula03/data-ingestion`) is lesson 3 of the RAG unit in a Java AI Specialist course. It is a Quarkus application that demonstrates a minimal RAG (retrieval-augmented generation) pipeline: ingest a PDF into a vector store at startup, then answer questions against it via an Ollama-backed chat model.

Requires **Java 25** (`maven.compiler.release=25` in `pom.xml`).

## Commands

```shell
./mvnw quarkus:dev                      # live-coding dev mode (serves on :8080, Dev UI at /q/dev/)
./mvnw package                          # produces target/quarkus-app/quarkus-run.jar
./mvnw test                             # unit tests (surefire)
./mvnw verify                           # integration tests (failsafe); skipped unless -DskipITs=false
./mvnw test -Dtest=ClassName#method     # run a single test
./mvnw package -Dnative                 # native executable (GraalVM required)
./mvnw package -Dnative -Dquarkus.native.container-build=true   # native build in container
```

Query the chatbot once running:
```shell
curl "http://localhost:8080/data?message=..."
```

## Architecture

Four classes, all in package `com.eldermoraes`:

- **`RagConfig`** produces a CDI singleton `EmbeddingStore<TextSegment>` as an `InMemoryEmbeddingStore`. The store is **ephemeral** — it lives only for the JVM's lifetime and is rebuilt on every restart. Swapping to a persistent store (pgvector, Redis, Chroma) only requires changing this producer.
- **`IngestionService`** (`@ApplicationScoped`, `@Startup`) loads `src/main/resources/doc/ai_report_2025-NANDA.pdf` via `ApachePdfBoxDocumentParser`, tags it with `department` and `date` metadata, splits recursively (500 chars / 50 overlap), embeds with the injected `EmbeddingModel`, and stores into the injected `EmbeddingStore`. Ingestion runs once at application boot.
- **`ChatbotService`** is a LangChain4j `@RegisterAiService` interface. Its `@SystemMessage` constrains answers to retrieved context only (responds in Portuguese: *"Não possuo esta informação nos meus arquivos"* when context is missing). Quarkus-LangChain4j wires the RAG retrieval automatically because an `EmbeddingStore` + `EmbeddingModel` bean pair is present.
- **`DataResource`** exposes `GET /data?message=...` and delegates to `ChatbotService.chat()`.

### Models

Configured in `src/main/resources/application.properties`:
- Chat: `gpt-oss:120b-cloud` (Ollama cloud model)
- Embeddings: `nomic-embed-text-v2-moe`

Both go through the `quarkus-langchain4j-ollama` extension — assume a reachable Ollama endpoint (the default is `http://localhost:11434`; override with `quarkus.langchain4j.ollama.base-url`).

### Adding documents to ingest

The current `IngestionService.onStart()` hardcodes a single PDF path and department tag. To ingest more documents, either call `process(path, department)` multiple times from `onStart()` or iterate over `src/main/resources/doc/`.