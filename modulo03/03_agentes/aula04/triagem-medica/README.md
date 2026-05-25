# Aula 04 — Supervisor: Triagem Médica Hospitalar

> **Padrão**: Supervisor
> **Case**: Triagem médica em emergência hospitalar
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud` + `gpt-oss:20b-cloud`)

> ⚠️ **Material didático — não use para decisão clínica real.** Este projeto demonstra um padrão de software, não um produto médico aprovado.

---

## O que você vai aprender

Este projeto demonstra o padrão **Supervisor**: um agente LLM **decide qual sub-agente especializado chamar** e depois **valida/revisa** a resposta antes de devolver ao usuário.

```
                    Sintomas
                        │
                        ▼
              ┌─────────────────────┐
              │  Supervisor (Router)│   ← LLM decide: CARDIO/NEURO/ORTO/GI
              │  modelo "smaller"   │
              └──────────┬──────────┘
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼            ▼
       ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
       │ Cardio │  │ Neuro  │  │ Orto   │  │ GI/    │   ← Especialistas
       │ Agent  │  │ Agent  │  │ Agent  │  │ Clínica│      (modelo 120b)
       └───┬────┘  └───┬────┘  └───┬────┘  └───┬────┘
           └──────┬────┴────────────┴──────────┘
                  ▼
        ┌──────────────────────┐
        │ Supervisor (Validator)│   ← LLM REVISA diagnóstico,
        │      modelo 120b      │      eleva urgência se necessário
        └──────────┬───────────┘
                   ▼
              Parecer final
```

### Por que este padrão importa

- **Decisão LLM em runtime**: o roteamento não é hardcoded — o LLM lê o texto livre dos sintomas e decide qual especialidade chamar
- **Validação cruzada reduz erro grave**: o supervisor pode **elevar** a urgência se notar sinais de alarme que o especialista subestimou
- **Custo otimizado**: o router usa modelo barato (`gpt-oss:20b-cloud`), só os especialistas e o validador usam o modelo robusto
- **Auditoria explícita**: cada etapa (roteamento → diagnóstico → revisão) é visível no frontend e logs

### Casos de uso reais

- **Atendimento N1 multi-especialidade** (suporte técnico que escala para billing/técnico/comercial)
- **Compliance review** (executor produz documento, supervisor valida cláusulas obrigatórias)
- **Aprovação de operações financeiras** (analista decide, supervisor valida limites e compliance)

---

## Como rodar

```bash
cd modulo03/03_agentes/aula04/triagem-medica
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Como usar a UI

1. **Descreva os sintomas** do paciente (texto livre) ou clique em um exemplo (cardio/neuro/orto/gi)
2. Clique em **Triar Paciente**
3. Observe as 3 etapas se preenchendo em sequência:
   - **① Decisão do Supervisor** — qual especialidade foi escolhida (badge colorido por especialidade)
   - **② Avaliação do Especialista** — hipótese diagnóstica, condutas, urgência preliminar
   - **③ Revisão do Supervisor** — urgência **revisada** (pode ter sido elevada), parecer consolidado em linguagem leiga

Teste o exemplo **cardio** — o supervisor de revisão tipicamente confirma VERMELHO (sinal de IAM).

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── SpecialistRouter.java       — supervisor: decide especialidade (modelo smaller)
│   ├── CardioAgent.java            — especialista cardiologia
│   ├── NeuroAgent.java             — especialista neurologia
│   ├── OrtopediaAgent.java         — especialista ortopedia
│   ├── GiClinicaAgent.java         — clínica geral / gastro
│   └── ConsultationValidator.java  — supervisor: revisa o diagnóstico
├── workflow/
│   └── MedicalSupervisor.java      — orquestra os 3 papéis em sequência
├── dto/                             — records + enums Specialty, UrgencyLevel
└── TriageWebsocket.java            — endpoint /ws/triagem
```

### Pontos-chave para os alunos

#### 1. O supervisor é DUAS chamadas LLM, não uma

Distintos de "router LLM" simples, o padrão Supervisor inclui **revisão** da resposta:

- `SpecialistRouter.decidirEspecialidade(sintomas)` → escolhe `Specialty`
- `ConsultationValidator.revisar(specialty, sintomas, opinion)` → revisa e produz parecer final

A revisão pode **elevar a urgência** mesmo se o especialista classificou abaixo. Isso é parte da pedagogia: o supervisor é uma segunda linha de defesa.

#### 2. Router usa modelo barato

Para o router (decisão simples), `@RegisterAiService(modelName = "smaller")` aponta para `gpt-oss:20b-cloud`. Especialistas e validador usam o modelo default (`gpt-oss:120b-cloud`).

#### 3. Coerção automática de enum

O `SpecialistRouter` retorna `Specialty` (enum). LangChain4j faz coerção automática do texto da LLM (`"CARDIO"`) para o enum. Funciona desde que o prompt seja claro sobre os valores válidos.

#### 4. Disclaimer triplo

- No system prompt do validator
- Adicionado pela classe `MedicalSupervisor` ao `TriageReport.disclaimer`
- Banner amarelo permanente no frontend

---

## Alternativa: `@SupervisorAgent` (anotação)

O módulo `langchain4j-agentic` oferece a anotação `@SupervisorAgent` que faz roteamento + validação automaticamente:

```java
@RegisterAiService
@SupervisorAgent(
    subAgents = { CardioAgent.class, NeuroAgent.class, OrtopediaAgent.class, GiClinicaAgent.class },
    responseStrategy = SupervisorResponseStrategy.SUMMARY,
    maxAgentsInvocations = 2
)
public interface MedicalSupervisor {
    @SystemMessage("Você é o supervisor médico…")
    TriageReport triage(@UserMessage String sintomas);
}
```

**Trade-offs**:
- ✅ Menos código
- ❌ A decisão do planner é menos visível (precisa de `AgentInvocationListener` para hook)
- ❌ Com Ollama, o LLM-planner pode oscilar — descrições do `@Agent` precisam ser muito específicas
- ❌ Mais difícil emitir progresso por etapa no WebSocket

A versão explícita deste projeto **mostra cada chamada LLM separadamente** — você consegue ver no frontend o roteamento, o diagnóstico parcial e a validação, com seus tempos individuais. É mais pedagógico.

---

## O que observar no frontend

| Observação | Explica… |
|---|---|
| Especialidade aparece **primeiro** (rápida — modelo smaller) | Router separado do especialista |
| Diagnóstico aparece depois (lento — modelo 120b) | Especialistas usam modelo robusto |
| Urgência do especialista vs. urgência revisada | Supervisor pode **elevar** o nível |
| Badge VERMELHO pulsa | Casos emergenciais ficam visualmente óbvios |
| `diagnosticoFazSentido: false` | Sinal de discordância entre especialista e supervisor |

---

## Para experimentar

- **Adicione um sintoma com sinal de alarme oculto** (ex: "dor de cabeça forte que começou subitamente há 1h, vômitos em jato") — veja se o supervisor eleva para VERMELHO
- **Modifique o `ConsultationValidator`** para ficar mais conservador (sempre elevar 1 nível) e veja o impacto
- **Adicione um 5º especialista** (ex: Pediatra) — atualize router, switch do supervisor, frontend
- **Compare** a saída quando o router usa `modelName = "smaller"` vs. default — qualidade da classificação muda?

---

## Próxima aula

Aula 05: **Dynamic Routing** — classificador escolhe rota e **troca o modelo LLM em runtime** conforme a categoria do ticket.
