# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode (live reload, auto-starts PostgreSQL via Dev Services)
./mvnw quarkus:dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MyTestClass

# Package
./mvnw package

# Native build (requires GraalVM)
./mvnw package -Dnative
```

The app exposes Swagger UI at `http://localhost:8080/q/dev/` in dev mode.

## Required Configuration

Set `OPENAI_API_KEY` as an environment variable before running. The app connects to PostgreSQL — in dev mode, Quarkus Dev Services provisions a container automatically.

## Architecture

This is a Quarkus 3.x application demonstrating **token-window chat memory** using quarkus-langchain4j + OpenAI.

**Request flow:**

```
GET /memory/{sessionId}?message=...
  → WithMemoryResource        (JAX-RS endpoint)
  → WithMemoryService         (AI service interface, @RegisterAiService)
      uses @MemoryId to scope memory per sessionId
  → MemoryConfig              (produces ChatMemoryProvider)
      TokenWindowChatMemory with 1000-token limit
  → DatabaseChatStore         (ChatMemoryStore impl)
      reads/writes serialized JSON messages to PostgreSQL
  → ChatSessionEntity         (Panache entity, PK = sessionId, stores messageJson TEXT)
```

**Key design decisions:**
- Memory is bounded by **token count** (not message count). `TokenCountEstimator` is injected from quarkus-langchain4j and used in `MemoryConfig` to cap history at 1000 tokens.
- Chat history is **persisted to PostgreSQL** (not in-memory), making sessions survive restarts. `DatabaseChatStore` serializes/deserializes messages using LangChain4j's `ChatMessageSerializer`/`ChatMessageDeserializer`.
- The AI service system prompt is in Portuguese — this is intentional for the course module.
