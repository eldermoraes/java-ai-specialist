# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode with live reload
./mvnw quarkus:dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Package
./mvnw package

# Run packaged app
java -jar target/quarkus-app/quarkus-run.jar

# Native build
./mvnw package -Dnative
```

Dev UI is available at `http://localhost:8080/q/dev/` and Swagger UI at `http://localhost:8080/q/swagger-ui/`.

## Architecture

This is a **Quarkus 3.34 + Quarkus LangChain4j** application demonstrating LLM sampling parameter differences. It requires a running **Ollama** instance with the `gpt-oss:120b-cloud` model.

### Core Pattern: Dual AI Service Profiles

Two named Ollama client profiles are configured in `application.properties`, each with distinct sampling parameters:

- **`criativo`** — high creativity: `temperature=0.9`, `top-k=50`, `top-p=0.95`
- **`deterministico`** — deterministic output: `temperature=0.0`, `top-k=1`, `top-p=0.10`

AI services are defined as interfaces annotated with `@RegisterAiService`. The `modelName` attribute binds a service to a specific profile:
- `CriativoService` uses `@RegisterAiService(modelName = "criativo")`
- `DeterministicoService` uses `@RegisterAiService` (defaults to the `deterministico` named config)

`CriatividadeResource` (`/criatividade`) injects both services, calls them with the same prompt, and returns a side-by-side comparison showing how sampling settings affect output for the same input.

### Dependency: Ollama

The app connects to a local Ollama instance. The model used is `gpt-oss:120b-cloud` — ensure Ollama is running and this model is available before starting the app.
