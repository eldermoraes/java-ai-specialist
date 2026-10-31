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
  without it, the user waits for the whole response (an output guardrail waits for the whole
  response by default).
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

## Resilience and risk

- **Provider**: the company that serves the model through its API. It goes down, it slows down,
  and it refuses requests over its **rate limit** (the requests per minute it accepts).
- **Resilience**: staying useful when a dependency fails.
- **Synchronous call**: the caller waits for the response to go on. A synchronous call to the
  provider puts the provider inside the quantum of the caller for the duration of the call: its
  availability and its latency become yours. Availabilities in series multiply (99.9% and 99.5%
  give about 99.4%).
- **Asynchronous call**: the caller goes on without waiting for the response. A queue holds the
  request until someone processes it, and separates the two quanta.
- **Fallacies of distributed computing**: eight false assumptions about the network, each with its
  reading in an agent. The network is reliable (a model call can be lost on the way); latency is
  zero (a model response takes seconds); bandwidth is infinite (the whole context travels in every
  call); the network is secure (the prompt leaves the company in every call); the topology does not
  change (the provider changes versions and retires models); there is one administrator (another
  company runs the provider); transport cost is zero (every token that travels is billed); the
  network is homogeneous (each provider has its own API and its own errors).
- **Timeout**: the most a call may wait. Condition: every call over the network; the timeout always
  applies. Calls in sequence add up their timeouts, and a reasoning model needs a longer one.
- **Retry**: repeating a call that failed. Condition: a transient failure (a lost connection, a
  timeout, a rate limit, a server error) on a call with no effect, or on an idempotent one. An
  invalid request fails the same way the second time. With **backoff** (a wait that grows with
  each attempt) and jitter (a random variation, so the clients do not come back together). Every
  retry is a paid call and counts in the worst case of the per-task ceiling; one more attempt
  doubles the worst wait.
  - A **guardrail retry** repeats the call after a response came and was rejected, with or
    without the reason for the rejection in the conversation; a
    **resilience retry** repeats a call that failed and brought no response. Nested, they
    multiply: a guardrail with two more attempts inside a resilience retry with one more attempt
    makes up to six calls for one response.
- **Fallback**: an alternative response when the call fails for good. Condition: an alternative
  response the user accepts (another model or another provider, which changes the quality of the
  answer; a stored response to the same request; a partial response that says what is missing). A
  clear error, without internal detail, is the minimum fallback.
- **Circuit breaker** (of a dependency, distinct from the loop circuit breaker): stops calling a
  dependency that keeps failing. After several failures in a row it opens; while open, it fails at
  once without waiting for the timeout; after a while, it tries one call. Condition: many calls to
  the same dependency in a short time. With low volume, timeout and retry are enough, and it is
  `not now`.
- **Emitter**: a part that publishes events to whoever subscribes (publish/subscribe). The user
  gets the progress without waiting for the end. Asynchronous to the user, synchronous to the
  provider: the model call still waits for the response, the provider stays in the quantum, and the
  work lives in the memory of the process (a restart loses it).
- **Queue with persisted work**: the request writes the job and answers at once; a consumer reads
  the queue and calls the provider; the result arrives later, by query or notice. One more part to
  run and monitor. Condition: the user does not need the answer at once, the work cannot be lost in
  a crash, or the volume goes past the rate limit of the provider. With none of the three, the
  queue is `not now`.
- **State**: what the agent keeps between one call and the next (the memory of the conversation,
  the current step of the task). **State scoped by user or tenant** (a tenant is a customer with
  its own isolated data): the failure of one does not corrupt the state of another, and the retry
  resumes the state of the right user. Condition: state kept between requests.
- **Compensation**: an action that undoes the effect of a step. **Saga**: a sequence of steps, each
  with its compensation; when a step fails, the compensations of the steps done run. Condition:
  effectful actions in more than one system; without effectful actions, the saga is `not now`.
- **Effectful retry**: a retry that repeats an effectful action. Repeating a read is safe;
  repeating a write can duplicate the effect, and the failure can come after the effect (the
  message left and the response was lost). Repeating a model call repeats the tools it already ran.
- **Idempotent**: repeating it has the same effect as doing it once. An idempotent action accepts a
  retry; an action that is not idempotent gets no automatic retry.
- **Architecture risk**: what can fail and how much it hurts.
- **Risk matrix**: impact first, then likelihood, 1 to 3 each, multiplied; the possible scores are
  1, 2, 3, 4, 6 and 9. Levels: 1–2 low, 3–4 medium, 6–9 high. A scale to recommend with
  consistency:
  - Impact 1: one part degrades and the user is still served. Impact 2: one request fails or loses
    part of its result. Impact 3: the system stops doing its job, loses data, or causes an effect
    outside.
  - Likelihood 1: unlikely, with protection or redundancy seen in the code. Likelihood 2: possible,
    an external dependency without protection seen. Likelihood 3: expected, it has happened or
    happens in normal use.
- **Unproven technology**: a technology unknown to the team. It always scores 9, without the
  matrix, because nobody can score what they do not know. Checked in every run, whatever the
  dimension, and scored in the area that depends on the technology. It is mitigated with a proof of
  concept or spike (a small test of the technology before depending on it), which is not an
  architecture decision.
- **Risk storming**: an exercise in which a group finds the risks of an architecture, one dimension
  per run, one block per dimension in `risk.md`. A **dimension** is the kind of risk assessed; the
  candidate dimensions are performance, scalability, availability (with its transitive
  dependencies), data loss, single point of failure and security. An **area** is a part of the
  architecture where the risk can show. Effectful retry and unproven technology are checked in
  every run, not picked as a dimension.
  - **Identification**: each person scores alone, without seeing the other scores, so nobody pulls
    the attention of the group.
  - **Consensus**: the group discusses the scores that diverge, and also a score only one person
    gave (that person may have seen what nobody saw), and ends with one score per risk.
  - **Mitigation**: a change in the architecture that lowers the risk. Every mitigation costs, and
    whoever pays decides whether the risk is worth the cost.
- **Single-assessor**: a risk only one person scored. Without consensus, the score is marked. The
  agent prepares and recommends; people score.
- **Direction**: the score against the previous assessment of the same dimension: `improving`
  (lower), `stable` (the same) or `worsening` (higher); `new` on the first assessment, with no
  previous score to compare. A fitness function shows the trend.

## Autonomy

- **Autonomy**: what the agent may do without a person, decided per action, not for the agent as a
  whole. It is a design principle, not an architecture characteristic (see Architecture
  characteristics). The question holds for an agent and for a workflow.
- **Agent identity**: who the system recognizes as the author of an action. An agent has its own
  identity, separate from the user's, with only the permissions of its actions; it acts on behalf
  of the user and never does more than the user may; the record keeps both. Who approved is an
  identity too: an approval that does not record who approved proves nothing.
- **Blast radius**: the most damage if the action goes wrong: what it reaches, what the error
  costs, whether it can be undone. The larger the blast radius, the lower the autonomy.
- **The three excesses**: excessive functionality (the agent reaches functions the use case does
  not need), excessive permission (the identity can do more than the action needs) and excessive
  autonomy (a high-impact action without a person checking). An error of the model becomes damage
  through them, whatever caused the error.
- **Least agency**: the least functionality, permission and autonomy, decided per tool (what it
  does, how often, how far it reaches). It extends **least privilege** (an identity with only the
  access it needs) to what the agent decides. A model without a tool only writes text, and the
  code acts on it.
- **Permission levels**: `forbidden` (the use case does not need the action), `propose` (the agent
  suggests, a person decides), `with-approval` (the agent acts after a person approves),
  `autonomous` (the agent acts alone, within hard limits). The four values, written as they are in
  every language: `forbidden`, `propose`, `with-approval`, `autonomous`.
  - A large blast radius or an effect that cannot be undone asks for `propose` or `with-approval`:
    `propose` when a person or the code acts after the yes, `with-approval` when the agent acts
    after the yes.
  - Recommend `autonomous` only when the blast radius is small and the effect can be undone.
  - The one exception: when the action only delivers to the person who made the request what a
    person already approved in the same request, and a hard limit in the code checks that the
    content matches the approval, recommend `autonomous` within that hard limit and say why: a
    second approval of the same decision becomes a rubber stamp. Its Approval names the approval
    it delivers.
- **Human-in-the-loop**: a person approves before the effect. The approval pauses the work; every
  pause has a deadline, and a deadline that passes ends the request. Too many approvals become a
  rubber stamp: whoever approves everything stops reading.
  - Condition of the approval: a large blast radius or an effect that cannot be undone, unless the
    action only delivers what a person already approved in the same request.
- **One ADR per effectful action**: the design principle guides; the ADR decides the rule of each
  action.
- **Idempotency key**: a unique identifier of the request, checked before the effect; a repeated
  key returns the result of the first time. Checking the state before the effect (only a pending
  request is approved) also makes an action idempotent.
  - Condition: every effectful action that can run twice (a resent request, a retry, two
    approvals).
- **Hard limit**: a rule in the code the agent cannot cross, outside the model; the prompt asks,
  the code guarantees. A limit that only the prompt states is not a hard limit. Execution limits
  (steps, time, tokens) and limits of the action (value, frequency, reach).
  - Condition: every effectful action; hard limits always apply.
  - **Prompt injection** (an instruction in the context that changes the behavior of the
    application) can ask for what the rule forbids; the hard limit holds whatever the prompt.
- **Kill switch**: turns off what the model drives, at once, without a deploy; one per system. It
  says who may trigger it, what happens to the work in progress (pending actions stop before the
  effect; effects already done stay recorded), and the path without the agent.
  - Condition: the model chooses the next step (with the hard limits, a non-negotiable of an agent
    in production), or a model output leads to an effectful action, even through an approval.
  - With neither, `not now`, with the condition that reopens it.

## Traceability

- **Telemetry**: data the system emits about its own work. A **log** is a line of text with an
  event; a **metric** is a number aggregated over time; a **trace** is the path of one request
  through the system. A **span** is one step of the path, with a start and an end; spans nest to
  show who called whom; an **attribute** is a name and a value recorded on a span; the **trace id**
  ties the spans of one request.
- **OpenTelemetry**: an open standard to emit and export telemetry, the same in every stack. The
  **exporter** sends the telemetry to a backend that stores and shows the traces; changing the
  backend does not change the code.
- **Semantic conventions**: standard names of spans and attributes, so every backend reads the
  same name. The `gen_ai.*` conventions name model calls (span `chat`), agents (span
  `invoke_agent`) and tools (span `execute_tool`), with `gen_ai.operation.name`,
  `gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.response.model`,
  `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens` (reasoning tokens included in the
  output) and `gen_ai.agent.name`. They are still in development and their names may change, so
  the dossier records the version followed.
- The `gen_ai.*` conventions do not name the user, the tenant, the approver or the reason of a
  step: these go in the namespace of the application (a **namespace** is the prefix that groups
  attributes, like `gen_ai`). Who asked is `enduser.id`, from the standard conventions outside
  `gen_ai`: an id, never a name or an e-mail.
- **What a trace must carry**, each with its condition (without it, `not now`, with the condition
  that reopens it):
  - who asked: the system identifies its user;
  - the tenant: the system has tenants;
  - which agent: more than one agent or model role;
  - each model call and its tokens: every model call; always;
  - each effectful action, with what it did, on which record and with what result: an effectful
    action;
  - who approved, as an identity: an approval;
  - the reason of each step: the model or the data choose the next step; with a path fixed in the
    code, the spans are enough;
  - the end of the task, and why it ended (goal reached, limit of steps, nothing left to do, error):
    every task; always.
- **Cost in the trace**: the trace carries the tokens and the model of each call; the cost is
  computed from them and the price, never recorded as the attribute of cost, because the price
  changes and the tokens stay. Cost per task, per user or per tenant is the sum of the tokens by
  the task, or by who asked.
- **Decision path**: the steps that ran, in order, why each ran, and why the task ended. A
  non-negotiable of an agent in production (see Agentic systems). Without the reason of the end, a
  stop looks like a success. A log line per step loses the nesting; the trace keeps the order, the
  duration and the parent of each step.
- **Content**: prompt and response content stays out of the telemetry unless the user decides
  otherwise, with the place where it holds (for example, development only). The conventions treat
  the content (`gen_ai.system_instructions`, `gen_ai.input.messages`, `gen_ai.output.messages`,
  tool arguments and results) as opt-in: it may carry personal data or a secret to the backend,
  and the trust boundary holds for the telemetry backend too. A log is telemetry: the same rule
  holds for the log.
- **Sampling**: keeping only part of the traces, so the telemetry costs less. Sampling may drop a
  trace: an effectful action also goes to a durable record, tied to the trace by its trace id. A
  trace is not an audit record.

## Architecture check

- **Dossier**: the folder with the architecture documents of the system; each document states
  something about the system that can be checked.
- **What is checked**: every Target of a characteristic, every Compliance of an accepted ADR,
  every required attribute of the traces, and every part a document says is in the code (approval,
  idempotency, hard limits, kill switch, mitigation, the id a request carries, where the content
  goes). Without a dossier, the non-negotiables of an agent in production.
- A required attribute is checked twice: in the code, whether the code records it (Structural);
  and, when the code records it, at runtime, whether it shows in the traces (Operational, Not
  Verified (needs telemetry) without telemetry).
- Each item has a type, the family of what it observes (see Measures): Operational, Structural or
  Process.
- **Results**: **Verified** (the evidence shows the item holds), **Violated** (the evidence shows
  it does not hold) and **Not Verified (needs telemetry)** (only the running system can tell).
  Never mark a runtime item Verified by reading the code: without telemetry there is no data, and
  a result without data is a guess. Not Verified is not Violated.
- A Verified item without a fitness function gets a `gap` task: checked today, it can break in the
  next commit.
- **What to do first**: Violated before Not Verified. Each gap becomes a `gap` task, an open one is
  reused, and the check states the next command without running it.

## Tracker labels

- `decision`: an open decision that still needs an ADR; for `to-autonomy`, an effectful action
  whose autonomy is not settled in the code or in an accepted ADR.
- `high-risk`: a risk scored 6 or 9 without mitigation in place.
- `gap`: something the architecture requires that is not in place yet (for example, a missing
  fitness function); for `to-traces`, a required attribute the code does not record; for
  `check-architecture`, an item that is not Verified or has no fitness function.
