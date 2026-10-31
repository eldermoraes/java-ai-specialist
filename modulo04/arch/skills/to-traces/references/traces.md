# Traces: {{system name: the name the build file declares; without one, the name the user gave}}

<!-- Write this file in the user's language, headings and table headers included (language.md).
     Keep the attribute names (gen_ai.*, enduser.id and the names in the namespace of the
     application), the span names and `not now` as they are. Redo the whole file in every run.
     Leave this comment out of the file. -->

What one request leaves recorded, so that a trace answers who asked, what the system did and who
approved it, how much it cost, and why each step ran.

## Trace of a Request

{{What starts a trace (the request, and where it enters), the root span, one child span per step
(model call, agent, tool, effectful action), and the id that ties them: the trace id, and the id
the system already generates per request, if any (path).}}

## Required Attributes

| Question | Attribute | Convention | Span | Condition | In the Code |
|---|---|---|---|---|---|
| Who asked | `enduser.id` | standard | root | the system identifies its user | {{path; or a `gap` task; or `not now`, with the condition that reopens it}} |
| Tenant | `{{namespace}}.tenant.id` | own | root | the system has tenants | {{...}} |
| Which agent | `gen_ai.agent.name` | standard | `invoke_agent` | more than one agent or model role | {{...}} |
| Model call | `gen_ai.operation.name`, `gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.response.model` | standard | `chat` | every model call; always | {{...}} |
| Tokens | `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens` | standard | `chat` | every model call; always | {{...}} |
| Effectful action | `{{namespace}}.action.name`, `{{namespace}}.action.target`, `{{namespace}}.action.result` | own | one span per action | an effectful action | {{...}} |
| Who approved | `{{namespace}}.approval.approver`, `{{namespace}}.approval.decision` | own | the span of the action | an approval | {{...}} |
| Reason of each step | `{{namespace}}.step.reason` | own | each step | the model or the data choose the next step | {{...}} |
| End of the task | `{{namespace}}.task.end_reason` | own | root | every task; always | {{...}} |

Cost: {{the tokens of each call, summed per task, per user and per tenant (when those rows hold);
the cost is computed from the tokens and the price, never recorded as the attribute of cost. The
per-task ceiling, if known, and where it comes from.}}

## Content

{{Prompt and response content (gen_ai.system_instructions, gen_ai.input.messages,
gen_ai.output.messages, tool arguments and results): carried or not, in the trace and in the log,
where (for example, development only), and who decided. What the system does today, with the path.}}

## Sampling

{{What is always kept (traces with an error, with an effectful action, over the per-task ceiling)
and the fraction of the rest. The durable record of each effectful action, tied by the trace id; or
`not now`, with the condition that reopens it.}}

## Stack Reference

{{The stack the build file shows, what it already emits on its own (with the names it writes, even
when older than the current conventions), and what the code must add. Without a known way to emit
traces in this stack, say so.}}

## Notes

- Date: {{YYYY-MM-DD}}
- Version of the conventions followed: {{version}}
- Sources read: {{brief, characteristics, autonomy, risk, ADRs, README, build files, configuration, code}}
- Related: {{`gap` tasks, ADRs}}
