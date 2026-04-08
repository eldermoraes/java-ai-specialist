# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```shell
# Run in dev mode (with live reload)
./mvnw quarkus:dev

# Build
./mvnw package

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MyTestClass

# Build native executable
./mvnw package -Dnative

# Build native in container (no GraalVM required)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Dev UI is available at http://localhost:8080/q/dev/ when running in dev mode.
Swagger UI (OpenAPI) is available at http://localhost:8080/q/swagger-ui/.

## Architecture

This is a Quarkus 3.x application (Java 25) that performs **sentiment analysis** using a local LLM via Ollama and LangChain4j.

**Request flow:**
1. `SentimentoResource` — REST endpoint `GET /sentimento?message=...` receives user text
2. `SentimentoService` — `@RegisterAiService` interface wired to the LLM; the `@SystemMessage` sets the AI role, `@UserMessage` formats the prompt, and the return type (`Sentimento` enum) causes LangChain4j to parse the model's output directly into the enum value
3. `Sentimento` — enum with values `POSITIVO`, `NEGATIVO`, `NEUTRO`, `DESCONHECIDO`

**Key config** (`application.properties`):
- Model: `gpt-oss:120b-cloud` served by Ollama
- Temperature: `0.7`
- Timeout: `120s`

Ollama must be running locally before starting the app. The model ID can be changed in `application.properties` to any model available in your Ollama instance.

## Dependencies

- `quarkus-langchain4j-ollama` — connects to local Ollama instance for LLM inference
- `quarkus-rest` + `quarkus-rest-jsonb` — Jakarta REST with JSON-B serialization
- `quarkus-smallrye-openapi` — Swagger UI / OpenAPI docs
