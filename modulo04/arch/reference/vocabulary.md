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
  missed. A limit in the configuration is not one by itself either: it implements the
  characteristic, and it counts only when a test exercises it and fails when the limit stops
  holding.
- **Architecture quantum**: an independently deployable part of the system with its own
  architecture characteristics. Count deployables by build unit: one build file with its own
  packaging per service, one app per folder in a monorepo. Several images of the same build (for
  example `Dockerfile.jvm` and `Dockerfile.native`) are one deployable, not more. More than one
  deployable is a sign of more than one quantum.
- **Not architecture characteristics**: the **autonomy of the agent** (what it may do on its own)
  is a design principle; a **domain requirement** lives in the specification of the system.
- **Out of the scope of the dossier**: the **quality of the response** is measured by evals, not
  by an architecture fitness function.

## Architecture decisions

- A decision is **architecturally significant** when it affects the structure, the architecture
  characteristics, the dependencies, the interfaces, or the construction techniques of the system.
  Only significant decisions get an ADR.
- **ADR (Architecture Decision Record)**: one file per significant decision, with seven sections.
  - **Title**: number and short description (`ADR 0001: Agent, workflow or multi-agent`).
  - **Status**: `proposed`, `accepted`, or `superseded by ADR-NNNN`.
  - **Context**: the forces at play, each part of the decision with the condition it needs (a part
    without its condition is `not now`, with the condition that reopens it), and the options
    considered.
  - **Decision**: the decision, in affirmative voice, with its justification (the why).
  - **Consequences**: what gets better, what gets worse, and the trade-offs accepted.
  - **Compliance**: how the decision is checked (a fitness function, or a `gap` task when none
    exists yet).
  - **Notes**: author, date, who approved, and links.
- An accepted ADR is never edited to change the decision and never deleted: a new ADR supersedes it,
  and in the old ADR only the Status line changes, to `superseded by ADR-NNNN`.

## Agentic systems

- **Agent**: a system in which the control flow emerges from the decisions of the model.
- **Workflow**: a system in which the path is defined in code ahead of time; the model fills steps
  of a known path.
- **Multi-agent**: several agents, each with its own context, coordinated by code or by another
  agent. Model calls multiply (a provider measured its own multi-agent research system spending
  about 15 times the tokens of a single-agent chat; the number varies from system to system).
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

## Cost of an agent

- **Token**: a piece of text the model reads or writes. Providers bill per token, and output
  tokens usually cost several times more than input tokens.
- **Task**: the work that one user request triggers. A task may make several model calls.
- **Multiplier**: one user request becomes several model calls, and each call pays its whole input
  again. Calls multiply with several agents over the same input, with retries of a rejected
  response, with a loop in which the model chooses the next step, and with tool calling (each tool
  result goes back to the model in a new call). Hidden tokens add to the count: a reasoning model
  thinks in tokens before it writes the response, billed as output, and the definition of the
  tools travels in every call.
- **Per-task ceiling**: the most a task may spend, in model calls and in tokens (the output limit
  per call and the sum over the task), counted in the worst case: retries, tool results and
  reasoning tokens included. A ceiling per task stops the spending at once; a monthly bill reports
  it after the money is gone. Cost per request is an architecture characteristic, and the per-task
  ceiling is its Target. A token ceiling that is too short cuts the response in the middle: the
  reasoning can spend it before the text.
  - Condition: any task that calls a model; the ceiling always applies (from the design when there
    is no loop, from a counter in the code when there is one).
- **Loop**: the model chooses the next step and repeats (a supervisor that chooses which agent runs
  next, a loop agent, tool calling until the answer is found). The model decides when to stop, and
  it may never decide to stop; every turn is a paid call. A retry is not a loop: its limit is in
  the code (for example `max-retries`), and it counts only in the worst case of the ceiling.
- **Loop circuit breaker**: code that interrupts the task when the ceiling is reached. It counts
  turns, calls or tokens, lives in the code, outside the model (the prompt asks, the code
  guarantees), and ends the task with a clear error for the user.
  - Condition: the model chooses the next step (a supervisor, a loop agent, tool calling).
  - Without a loop (a workflow, whose path is defined in code ahead of time), the number of calls
    is known from the design, the ceiling comes from the design, and the breaker is `not now`, with
    the first loop as the condition that reopens it.
- **Router by cost**: the part that chooses the model of each call, so that not every call goes to
  the largest model. Static routing gives each role its own model, fixed in the configuration;
  dynamic routing uses a classifier (a small model that puts the input in a category) on every
  request, and that choice costs a call too. A small model fits routine work.
  - Condition: more than one model, with calls of very different difficulty or models of very
    different price. With a single model there is nothing to route: `not now`.
- **Model gateway**: one layer between the application and the models. It centralizes access to
  several models behind one interface, the provider keys out of the application, quota and cost
  per user, a record of every call, and the router when there is one. It is one more networked
  part, with its own latency and its own failures.
  - Condition: more than one model, per-user quota, or keys kept out of the application. With
    none of the three, the decision is `not now`.
- **Not now**: a decision postponed on purpose, recorded with the condition that reopens it. It is
  written in the Decision of an ADR; it is not a status.
- **Prompt cache**: the provider reuses the repeated beginning of the input, and cached input
  costs less.
- **Response cache**: the same request gets the stored response back, with no model call.
- **Cost and latency go together**: more output tokens mean a longer wait (TTFT and TPOT).

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
