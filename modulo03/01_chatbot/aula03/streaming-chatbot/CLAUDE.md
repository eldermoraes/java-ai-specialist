# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

- **Dev mode (live reload):** `./mvnw quarkus:dev`
- **Build:** `./mvnw package`
- **Run tests:** `./mvnw test`
- **Run a single test:** `./mvnw test -Dtest=ClassName#methodName`
- **Native build:** `./mvnw package -Dnative`

## Architecture

This is a Quarkus 3.34 application (Java 25) that demonstrates **streaming chat responses** using LangChain4j with an Ollama-compatible LLM backend.

### Key Pattern: Declarative AI Service with Streaming

- `StreamingService` — a LangChain4j AI service interface (`@RegisterAiService`) that returns `Multi<String>` for token-by-token streaming. System and user prompts are declared via `@SystemMessage` / `@UserMessage` annotations.
- `StreamingResource` — a JAX-RS endpoint (`GET /streaming?message=...`) that produces `SERVER_SENT_EVENTS`, directly returning the reactive `Multi<String>` stream from the AI service.

### LLM Configuration

Configured in `application.properties` to use the Ollama provider (`quarkus-langchain4j-ollama`). Model ID and temperature are set there. No API key is needed for local Ollama; adjust the model-id property for different models.
