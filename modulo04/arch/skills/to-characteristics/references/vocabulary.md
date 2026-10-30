# Architecture vocabulary

Shared reference for every skill in the `arch` plugin. Use these terms, with these meanings, in
every question you ask and every file you write. In the user's language, use the established
translation of a term, or keep the term as it is when there is none (see `language.md`).

Source of these definitions: *Fundamentals of Software Architecture* (Mark Richards and Neal Ford,
O'Reilly, 2020). Use the source to reason. Do not mention the source book in generated files: state
the concepts directly.

## Architecture

- **Software architecture**: the decisions that are hard to change later. Architecture is strategy
  and decision; code implements it.
- **The four dimensions**: every architecture is described along four dimensions.
  - **Structure**: the architecture style of the system (pipeline, mediator, microkernel, ...).
  - **Architecture characteristics**: what the system must guarantee beyond its domain behavior
    (cost per request, latency, availability, auditability, ...).
  - **Architecture decisions**: rules the team follows (what is allowed and what is not).
  - **Design principles**: guidelines the team considers (preferred ways, not hard rules).
- **First Law of Software Architecture**: everything in software architecture is a trade-off. When
  no trade-off is visible, it has not been found yet.
- **Second Law of Software Architecture**: why is more important than how. The how can be read in
  the code; the why is lost unless it is recorded.

## Architecture characteristics

- A concern is an **architecture characteristic** only when it meets all three criteria:
  1. it specifies a non-domain design consideration;
  2. it influences some structural aspect of the design;
  3. it is critical or important to the success of the system.
- Choose the **smallest number** of characteristics: each one costs complexity in the design, and
  an architecture that tries to meet all of them meets none.
- **Target**: the measurable value the characteristic must reach (for example, p95 latency under
  2 s, cost per request under a set amount).
- **Latency in an agent** is measured by **TTFT** (time to first token of the response) and
  **TPOT** (time per output token after the first). With streaming, the user feels the TTFT;
  without it, the user waits for the whole response (an output guardrail can only judge the whole
  response).
- **Measures**: **Operational** (observed at runtime: latency, availability, cost),
  **Structural** (observed in the code: dependencies, layering, where an SDK is used), and
  **Process** (observed in how the team works: test coverage, deploy frequency).
- **Fitness function**: any mechanism that objectively checks that a characteristic or a decision
  still holds (a test, a metric with an alert, an architecture rule in the build). A log line is
  not one by itself: it counts only when a test or an alert reads it and fails when the Target is
  missed.
- **Architecture quantum**: an independently deployable part of the system with its own
  architecture characteristics. More than one deployable is a sign of more than one quantum.
- **Not architecture characteristics**: the **autonomy of the agent** (what it may do on its own)
  is a design principle; the **quality of the response** is checked by a guardrail on every
  request and stays out of the dossier; a **domain requirement** lives in the specification of the
  system.

## Architecture decisions

- A decision is **architecturally significant** when it affects the structure, the architecture
  characteristics, the dependencies, the interfaces, or the construction techniques of the system.
  Only significant decisions get an ADR.
- **ADR (Architecture Decision Record)**: one file per significant decision, with seven sections.
  - **Title**: number and short description (`ADR 0001: Agent, workflow or multi-agent`).
  - **Status**: `proposed`, `accepted`, or `superseded by ADR-NNNN`.
  - **Context**: the forces at play and the options considered.
  - **Decision**: the decision, in affirmative voice, with its justification (the why).
  - **Consequences**: what gets better, what gets worse, and the trade-offs accepted.
  - **Compliance**: how the decision is checked (a fitness function, or a `gap` task when none
    exists yet).
  - **Notes**: author, date, who approved, and links.
- An accepted ADR is never edited to change the decision and never deleted: a new ADR supersedes it.

## Agentic systems

- **Agent**: a system in which the control flow emerges from the decisions of the model.
- **Workflow**: a system in which the path is defined in code ahead of time; the model fills steps
  of a known path.
- **Multi-agent**: several agents, each with its own context, coordinated by code or by another
  agent. Model calls multiply (a common order of magnitude is about 15 times the calls of a single
  chat interaction).
- **Harness**: everything around the model (prompt, tools, memory and context, limits, guardrails,
  observability). Agent = model + harness. The model comes ready-made; the team designs the
  harness, and each piece of the harness is an architecture decision.
- **Non-negotiables of an agent in production**: hard limits (steps, time, tokens), a log of the
  decision path, and a kill switch.
- **Effectful action**: an action whose effect leaves the system (opens a ticket, sends a message,
  writes to another system, moves money). Effectful actions are where an agent can cause damage.
- **Trust boundary**: the perimeter inside which the company may process a given piece of data. It
  is the first design constraint, decided before the choice of model and provider. Opening
  questions:
  - Which data does the system touch?
  - May that data leave the company (to a model provider, to a third-party tool)?
  - Which actions have an effect outside the system?

## Agentic patterns read as architecture styles

| Agentic pattern | Architecture style | Caveat |
|---|---|---|
| Sequential workflow | Pipeline | |
| Conditional, loop or supervisor | Mediator | |
| Tools / MCP | Microkernel | The remote plug-in (an MCP server) brings network and failure inside the system; the model, not the core, chooses which tool to call |

## Tracker labels

- `decision`: an open decision that still needs an ADR.
- `high-risk`: a risk scored 6 or 9 without mitigation.
- `gap`: something the architecture requires that is not in place yet (for example, a missing
  fitness function).
