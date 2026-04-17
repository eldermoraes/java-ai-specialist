# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run in dev mode (live reload at http://localhost:8080)
./mvnw quarkus:dev

# Build
./mvnw package

# Run tests
./mvnw test

# Run a single test
./mvnw test -Dtest=MyTestClassName

# Build über-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Native build (requires GraalVM)
./mvnw package -Dnative
```

The app requires a running **PostgreSQL** database and a running **Ollama** instance with the `gpt-oss:120b-cloud` model available.

## Architecture

This is a **Quarkus 3.x** application (Java 25) that implements a Star Wars chatbot backed by LangChain4j + Ollama, with persistent chat memory stored in PostgreSQL.

### Request Flow

When a WebSocket client connects to `/ws/c3po`:

1. `C3POWebsocket.onOpen()` fetches Darth Vader's data from the external SWAPI (`https://swapi.build/api`)
2. `StarWarsPersonService` (AI service) parses the raw JSON into a `Person` record and assigns a `Force` enum value (`DARK_SIDE`, `LIGHT_SIDE`, or `NEUTRAL`)
3. `C3POService` (AI service with `c3po` named model) responds in character as C-3PO, commenting on the fetched character
4. Subsequent messages go directly to `C3POService.chat()`

### AI Services

- **`C3POService`** — `@RegisterAiService(modelName = "c3po")`, `@SessionScoped`. Uses the `c3po` named Ollama model config (low temperature/top-p for deterministic in-character responses). System prompt defines C-3PO persona in Portuguese.
- **`StarWarsPersonService`** — `@RegisterAiService` (default model). Structured extraction: converts SWAPI JSON → `Person` record with AI-inferred `Force` alignment.

### Chat Memory

`MemoryConfig` produces a `ChatMemoryProvider` using `MessageWindowChatMemory` (max 1000 messages). `DatabaseChatStore` implements `ChatMemoryStore` to persist/retrieve message history in PostgreSQL via the `ChatSessionEntity` Panache entity (stores serialized JSON in a TEXT column, keyed by session ID).

### External REST Client

`StarWarsAPIService` is a MicroProfile REST Client (`@RegisterRestClient`) targeting `https://swapi.build/api`. It is `@SessionScoped`.

### Configuration

Key `application.properties` settings:
- `quarkus.langchain4j.ollama.c3po.*` — named model config for the C-3PO service (low temperature for consistent persona)
- `quarkus.datasource.db-kind=postgresql` — datasource must be configured with connection URL/credentials (not committed)
- `quarkus.hibernate-orm.log.sql=true` — SQL logging enabled