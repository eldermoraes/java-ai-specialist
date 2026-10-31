---
name: to-traces
description: Decide what every request must leave recorded in its trace, so that a trace answers who asked, what the system did and who approved it, how much it cost, and why each step ran. Read the system and what it already emits, give each required attribute its convention (gen_ai.* or the namespace of the application) and its condition, settle content and sampling in one round, and write docs/architecture/traces.md. Opens a gap task for what the code does not record yet.
disable-model-invocation: true
---

# To traces

Decide what one request of the system must leave recorded, so that when something goes wrong the
trace answers four questions: who asked, what the system did and who approved it, how much it
cost, and why each step ran. You propose: you read, recommend and ask; the user decides, and every
question comes with your recommendation.

This skill decides what the trace carries. It does not score risk and does not decide autonomy:
it reads the risk assessment and the autonomy of the system when they exist, and never redoes
them.

The input is what the user wrote with the command: a path to a project, or nothing. With nothing,
work from the current repository.

Read [references/vocabulary.md](references/vocabulary.md) before you start: use its terms with its
meanings, what a trace must carry and the condition of each part. Do not mention the source book in
generated files.

Write every file, task and question in the user's language, template headings and table headers
included, following [references/language.md](references/language.md). Keep the names of
attributes and spans (`gen_ai.*`, `enduser.id`, the names in the namespace of the application)
and `not now` as they are.

Templates for the files you write:
- [references/traces.md](references/traces.md) for the traces of the system;
- [references/dossier-readme.md](references/dossier-readme.md) for the dossier index, when it does
  not exist yet.

Fixed sentences in this skill (the `Next:` lines, the titles of the `gap` tasks) are written here
in the plugin's language: translate each into the user's language, and keep commands and file
names as they are.

## 1. Read the setup and what already exists

- Read `docs/agents/architecture.md`. It gives the tracker (`github` or `local`), the layout and
  the artifact locations.
- With the per-quantum layout, ask which quantum this run is for, in the first round, with your
  recommendation (the path given, or the deployable the input names), and read and write that
  quantum's folder: `docs/architecture/<quantum>/` for brief, characteristics, risk, autonomy,
  traces, check-report and its `README.md`; ADRs stay in `docs/adr/` and tasks in the tracker.
  Every `docs/architecture/` path below then means that folder, and the index row of the ADRs
  links to `../../adr/`.
- Without `docs/agents/architecture.md`, the setup has not run. Use the defaults:
  traces at `docs/architecture/traces.md`,
  dossier index at `docs/architecture/README.md`, ADRs at `docs/adr/`, tasks as local files in
  `.scratch/architecture/issues/`. Do not ask about the tracker. Tell the user in one line, and
  suggest `/arch:setup-architecture` (Claude Code) or `$arch:setup-architecture` (Codex) for later.
  Then go on.
- Read `docs/architecture/traces.md`, if it exists: the decisions already taken (they count as
  answers already given), and the name of the system in its title.
- Read the autonomy of the system (`docs/architecture/autonomy.md`, or the location the setup
  gives), if it exists: the effectful actions, who approves and the level of each one are evidence
  for the rows of the effectful action and of who approved.
- Read the characteristics (`docs/architecture/characteristics.md`), if they exist: the Targets of
  cost and latency (the trace is the source of their Operational measure) and auditability.
- Read the brief (`docs/architecture/brief.md`), if it exists: its trust boundary says which data
  may leave the company, and it holds for the telemetry backend too.
- Read the risk assessment (`docs/architecture/risk.md`), if it exists: its Effectful Retry lists
  effectful actions already found. Never recalculate its scores.
- Read the ADRs in `docs/adr/`: an `accepted` ADR about instrumentation, content or sampling is
  evidence, cited by its number.
- Read the open `gap` tasks in the tracker, so you do not open them twice.
  - GitHub tracker: `gh issue list --label gap --state open`.
  - Local tracker: the files in `.scratch/architecture/issues/` with a `Labels: gap` line and a
    `Status: open` line.
- If there is a project (the path given, or the current repository), read its README, build files
  (`pom.xml`, `build.gradle`, `package.json`, ...), configuration (`application.properties`,
  `application.yaml`, environment files: exporter, endpoint, sampling, profiles, the logging of
  requests and responses of the model client), tests, and the code where a request starts
  (endpoints, authentication, ids generated per request), where the model is called, the agents
  and their names, the tools, the effectful actions, what chooses each step (a planner, a router, a
  supervisor, rules in the code), and how the task ends (goal, limit of steps, error), plus the
  spans, attributes and log lines that already exist. Treat what they show as answered, and say
  where you saw it.

Show the user, in a few lines, what you read and what you did not find. Done when you know where
each file goes and what is already answered.

## 2. Find what one request does

- Where the request enters, and the id the system generates per request, if any: where it is
  created and how far it reaches.
- The steps of one request: each model call, each agent, each tool, each effectful action. For
  each step, what chooses it: the model; or a rule in the code, from the data or from an output of
  the model.
- How the task ends: the goal reached, a limit of steps, nothing left to do, an error. Say where
  each end shows in the code, and whether the reason is recorded.
- What the system already emits: a telemetry dependency in the build, spans, attributes, and log
  lines with the path of the request. A name the stack writes on its own is evidence.
- The name of the system is the name the build file declares: in Maven, `<name>` when it exists,
  otherwise `<artifactId>`; in Gradle, `rootProject.name` in `settings.gradle`; in other builds,
  the project name field (`name` in `package.json`). The README title is not a source for the
  name. When no build file declares a name, ask the user which name to use in the round that
  confirms the list of attributes (the round of step 4), not in a round of its own, with your
  recommendation. With an existing `traces.md`, keep its title as it is.

Done when every step of a request has the place where it shows and what chooses it.

## 3. Give each required attribute its convention and its condition

Go through the rows of the template: who asked, tenant, which agent, model call, tokens, effectful
action, who approved, reason of each step, end of the task. For each row:

- **Convention**: the standard name when there is one (`gen_ai.*` for the model, the agent and the
  tool; `enduser.id` for who asked); otherwise a name in the namespace of the application. The
  `gen_ai.*` conventions do not name the user, the tenant, the approver or the reason of a step:
  these go in the namespace of the application. Use the current names of the conventions
  (`gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`); a name the stack still writes in an
  older form is evidence, recorded in the Stack Reference.
- **Condition**, from the vocabulary: who asked (the system identifies its user); tenant (the
  system has tenants); which agent (more than one agent or model role); model call and tokens
  (every model call; always); effectful action (an effectful action); who approved (an approval);
  reason of each step (the model or the data choose the next step); end of the task (every task;
  always). Say whether the system meets each condition, and where you saw it. A row whose
  condition the system does not meet is `not now`, recorded with the condition that reopens it.
- **In the Code**: the path where the code records it, or what is missing.
- The trace carries the tokens and the model of each call; the cost is computed from them and the
  price, never recorded as the attribute of cost. A cost the stack records on its own is evidence,
  not the attribute of cost.
- What the code shows (the authentication, the actions, the ids, the names) is evidence: say it,
  do not ask it.

Done when every row has its convention, its condition met or not, and its place in the code or
what is missing.

## 4. Ask in one round

- At most three questions, numbered, each with your recommendation and its reason in one line.
  The user may answer "ok" to accept one recommendation, or "ok to all" to accept the whole round.
- **Content**: prompt and response content in the trace and in the log. Prompt and response
  content stays out of the telemetry unless the user decides otherwise, with the place where it
  holds (for example, development only). Recommend it out, except in development, and say what
  the system does today, with the path.
- **Namespace of the application**: recommend one from the build file (the group it declares, for
  example the Maven `groupId`, with the name of the system).
- **Sampling**: what is always kept (every trace with an error, with an effectful action, or over
  the per-task ceiling) and the fraction of the rest. Sampling may drop a trace: an effectful
  action also goes to a durable record, tied to the trace by its trace id. Without an effectful
  action, the durable record is `not now`, with the condition that reopens it.
- When no build file declares the name of the system (step 2), the same round asks for it.
- After the answers, say in one line what they settled.

Done when content, namespace and sampling are settled, or the user left a part open.

## 5. Show the draft, then write

Show the user `traces.md` as a draft, with the tasks you will open. Write only after the user
confirms or corrects it.

1. **traces.md**: fill the template, in the user's language. Redo the whole file in every run;
   the decisions of an earlier run, read in step 1, come in as answers already given.
2. **Tasks**: open one `gap` task per required row (its condition met) that the code does not
   record. When the system emits no trace at all, open one gap task for the instrumentation, with
   every required attribute in its body, not one task per attribute. Before opening a gap task,
   look for an open one with the gap label and the same subject, whichever skill opened it, and
   reuse it. Match by the label and the subject, not by the exact title: an earlier run may have
   translated the title differently. The title is "Trace attributes for <system>" for the
   instrumentation, or "Trace attribute <attribute>" for one row (translated); the body has each
   attribute, its condition, what the code must record, a link to `traces.md` and, when recording
   it needs an architecture decision (a dependency, an exporter, the content, the sampling), the
   line `Next: /arch:to-adr "<the decision, with its parts>"`, with the decision already written.
   - GitHub tracker: find with `gh issue list --label gap --state open --search "<subject>"`;
     create with `gh issue create --title "<title>" --label gap --body "<attributes, conditions, what the code must record, Next: line>"`.
   - Local tracker: find a file in `.scratch/architecture/issues/` with the `Labels: gap` line, a
     `Status: open` line and the subject in its `#` title. Create a file
     `.scratch/architecture/issues/NN-slug.md` (next free `NN`, from `01`; `slug` is the title
     already translated, in kebab-case) with a `# <title>` line, a `Labels: gap` line, a
     `Status: open` line, and the same body as on GitHub.
   - This skill never closes a task.
   Put the link or file name of each task in the In the Code column of its rows.
3. **Dossier index**: in `docs/architecture/README.md` (the `README.md` of the quantum
   folder, with the per-quantum layout), set the row that links to `traces.md` to
   `generated` with today's date. Find the row by its link, never by its label, which is
   translated into the user's language. If the row is missing, add it. If the file does not exist,
   create it from [the dossier template](references/dossier-readme.md), translated, with that row
   as `generated` and the other rows as `pending`.

Done when `traces.md` has every row with its convention, condition and place in the code, every
task is opened as above, and the dossier index shows Traces as `generated`.

## 6. Report and next step

List the files written and the tasks opened. State each row that is `not now` with the condition
that reopens it. Then end with one of these lines, translated into the user's language, with
commands and file names as they are:

- If the plugin you are running from has the `check-architecture` skill, end with
  `Next: /arch:check-architecture (Claude Code) · $arch:check-architecture (Codex)`.
- Otherwise end with: `Next: review docs/architecture/traces.md, and mark it reviewed in docs/architecture/README.md.`

The `Next: /arch:to-adr` line stays in the `gap` task; `check-architecture` brings it back as the
next command.
