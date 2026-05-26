# Aula 04 — Supervisor: Triagem Médica Hospitalar

> **Padrão**: Supervisor (LLM-planner roteia entre especialistas)
> **Case**: Paciente descreve sintomas → supervisor escolhe especialidade → especialista diagnostica → validator revisa urgência
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`deepseek-v4-pro:cloud` para planner + `gpt-oss:20b-cloud` para exemplos)

---

## O que você vai aprender

`@SupervisorAgent` é a annotation declarativa para o pattern Supervisor: um **planner LLM** lê a request e decide qual sub-agent invocar entre os disponíveis, baseado nas `description` de cada `@Agent`.

A aula combina três conceitos do framework:

1. **`@SupervisorAgent`** — LLM-planner que escolhe e invoca specialists
2. **`@SupervisorRequest`** — static method que constrói o prompt do planner
3. **`@SequenceAgent`** — encadeia o supervisor com o validator (que revisa urgência)

```
                @Inject TriageAgent triageAgent;
                            │
                            ▼
                  ┌───────────────────────┐
                  │   @SequenceAgent      │
                  │  Diagnóstico → Valid. │
                  └───────────┬───────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
     ┌───────────────────┐         ┌──────────────────────┐
     │ @SupervisorAgent  │  then ─▶│ ConsultationValidator│
     │ DiagnosticoSuperv.│         │  (eleva urgência se  │
     │ + @SupervisorReq. │         │   há sinal de alarme)│
     └─────────┬─────────┘         └──────────────────────┘
               │
   ┌───────────┼───────────┬───────────┐
   ▼           ▼           ▼           ▼
┌──────┐  ┌──────┐  ┌──────────┐  ┌──────────┐
│Cardio│  │Neuro │  │Ortopedia │  │GiClinica │  ← 4 specialists @Agent
└──────┘  └──────┘  └──────────┘  └──────────┘    com description rica
            (planner LLM escolhe baseado nas descriptions)
```

## Como rodar

```bash
cd modulo03/03_agentes/aula04/triagem-medica
./mvnw quarkus:dev
```
Abra <http://localhost:8080/>.

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── CardioAgent.java          — @Agent(description="Cardiologista…")
│   ├── NeuroAgent.java           — @Agent(description="Neurologista…")
│   ├── OrtopediaAgent.java       — @Agent(description="Ortopedista…")
│   ├── GiClinicaAgent.java       — @Agent(description="Clínico/gastro…")
│   ├── ConsultationValidator.java — @Agent: revisa diagnóstico do specialist
│   └── ExampleGenerator.java     — AI Service auxiliar (gera casos clínicos)
├── workflow/
│   ├── DiagnosticoSupervisor.java — @SupervisorAgent + @SupervisorRequest
│   ├── TriageAgent.java           — @SequenceAgent(Diagnóstico, Validator) + @Output
│   └── MedicalSupervisor.java     — wrapper Multi<TriageStep>
├── rest/
│   └── ExampleResource.java       — endpoint /api/example/sintomas
├── dto/                           — Specialty, SpecialistOpinion, SupervisorReview, TriageReport
└── TriageWebsocket.java           — endpoint /ws/triagem
```

### Pontos-chave

#### 1. `@Agent.description` é CRÍTICA — o planner LLM lê para escolher

Cada specialist tem `description` específica que o LLM usa para decidir:

```java
@RegisterAiService
public interface CardioAgent {
    @SystemMessage("...JSON com specialty: CARDIO + hipótese + condutas + urgência...")
    @UserMessage("Sintomas do paciente: {sintomas}")
    @Agent(name = "cardio",
            description = "Cardiologista — avalia dor torácica, palpitações, irradiação, sudorese, sinais de IAM, arritmias, hipertensão crítica",
            outputKey = "opiniao")
    SpecialistOpinion avaliar(@V("sintomas") String sintomas);
}
```

Cada specialist instrui o LLM a colocar `"specialty": "CARDIO"` no JSON — isso é lido depois pelo `@Output assemble`.

#### 2. `@SupervisorAgent` + `@SupervisorRequest` no `DiagnosticoSupervisor`

```java
public interface DiagnosticoSupervisor {
    @SupervisorAgent(
        outputKey = "supervisorOutput",
        subAgents = { CardioAgent.class, NeuroAgent.class, OrtopediaAgent.class, GiClinicaAgent.class },
        responseStrategy = SupervisorResponseStrategy.LAST,
        maxAgentsInvocations = 2)
    String diagnosticar(@V("sintomas") String sintomas);

    @SupervisorRequest
    static String request(@V("sintomas") String sintomas) {
        return "Avalie os sintomas a seguir e roteie para o especialista médico mais apropriado…" + sintomas;
    }
}
```

**`@SupervisorRequest`** é fundamental — sem ele o planner recebe prompt vazio e responde `"done — no request was provided"`. Ele constrói o prompt em texto livre que o planner LLM lê.

**Retorno é `String`** porque o supervisor retorna o output do specialist como string (forma genérica). O `SpecialistOpinion` tipado fica no `AgenticScope` via `outputKey = "opiniao"` do specialist e é lido depois pelo `@Output assemble`.

#### 3. `TriageAgent` — `@SequenceAgent` + `@Output assemble`

```java
public interface TriageAgent {
    @SequenceAgent(outputKey = "triageReport",
                   subAgents = { DiagnosticoSupervisor.class, ConsultationValidator.class })
    TriageReport triar(@V("sintomas") String sintomas);

    @Output
    static TriageReport assemble(AgenticScope scope) {
        SpecialistOpinion opinion = (SpecialistOpinion) scope.readState("opiniao");
        SupervisorReview review = (SupervisorReview) scope.readState("review");
        return new TriageReport(
            opinion == null ? null : opinion.specialty(),
            opinion, review, DISCLAIMER);
    }
}
```

O `@Output` static lê: `opiniao` (escrito pelo specialist ativo), `review` (do validator), e `specialty` (do próprio `SpecialistOpinion`).

#### 4. Configuração crítica em `application.properties`

```properties
quarkus.langchain4j.ollama.chat-model.model-id=deepseek-v4-pro:cloud
quarkus.langchain4j.ollama.chat-model.temperature=0
quarkus.langchain4j.ollama.chat-model.format=json
quarkus.langchain4j.timeout=240s
```

Três decisões críticas:

- **`deepseek-v4-pro:cloud`** em vez de `gpt-oss:120b-cloud` — o planner LLM precisa de capacidade superior de structured output. `gpt-oss:120b` retorna texto livre mesmo solicitado JSON e o framework não consegue parsear o `AgentInvocation`
- **`format=json`** — força o Ollama a retornar JSON estritamente
- **`temperature=0`** — planner LLM precisa ser determinístico para escolher specialists

Sem essas 3 configurações, o supervisor oscila com `"Failed to parse \"done\\nresponse: No request was provided\""` ou `"Unrecognized token 'The'"`.

## O que observar

| Observação | Explica… |
|---|---|
| Sequência WS: `STARTED → DONE` apenas (sem ROUTED/SPECIALIST_DONE/REVIEWING) | Declarativa não emite eventos intermediários — framework executa internamente |
| Tempo total ~30-60s | Planner roda ~1-2 ciclos + specialist + validator |
| `TriageReport.specialty` ≠ null | Specialist incluiu `specialty` no JSON output, e `@Output assemble` propagou |
| Sintomas de AVC suspeitos elevados para VERMELHO pelo validator | `ConsultationValidator` revisa urgência |

## Trade-offs e riscos

- **LLM-planner é fragile com modelos pequenos** — `gpt-oss:120b-cloud` (open-source) não foi suficiente; `deepseek-v4-pro` funcionou. Em produção, considere modelos comerciais (GPT-4, Claude) ou tune o prompt do `@SupervisorRequest` cuidadosamente
- **Tempo de resposta** — supervisor adiciona 1 ciclo de planner LLM antes de invocar specialist, então é ~10s mais lento que `@ConditionalAgent` (aula05)
- **Vantagem pedagógica** — esta aula demonstra LLM **tomando decisões dinâmicas** baseadas em `description` natural language, em contraste com a aula05 onde a decisão é feita por código com `@ActivationCondition` booleana

## Para experimentar

- Edite a `description` do `CardioAgent` removendo "IAM" — veja se o supervisor ainda escolhe corretamente
- Adicione `@AgentListenerSupplier` para logar cada decisão do planner LLM (úteis para debugging)
- Troque `responseStrategy=LAST` para `SUMMARY` — o planner LLM combina todos os outputs em um resumo

## Disclaimer

Material didático. Triagem inicial automatizada NÃO substitui avaliação médica presencial. Em caso de urgência, procure atendimento imediato.

## Próxima aula

Aula 06: **HITL** — Aprovação de desconto B2B com `@LoopAgent` + `@HumanInTheLoop` binário.
