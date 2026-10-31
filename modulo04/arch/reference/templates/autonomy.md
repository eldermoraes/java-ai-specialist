# Autonomy: {{system name: the name the build file declares; without one, the name the user gave}}

<!-- Write this file in the user's language, headings and table headers included (language.md).
     Keep blast radius, kill switch, least agency, not now and the Permission values (`forbidden`, `propose`, `with-approval`, `autonomous`) as they are. One block per effectful action: a run
     with one action replaces only the block of that action. Design Principle, Kill Switch, Excess
     Check and Actions Without Effect cover the whole file. Leave this comment out of the file. -->

## Design Principle

{{One sentence the team follows for the autonomy of this system, for example "the agent proposes,
a person decides". A guideline, not a rule: the rule of each action is its ADR.}}

## Actions

### Action: {{name of the action, in the words of the domain}}

| Field | Value |
|---|---|
| Effect | {{what leaves the system, and where it shows (path, class)}} |
| Blast Radius | {{what it reaches, what the error costs, whether it can be undone}} |
| Agent Identity | {{the identity it runs with (its own, the user's, the whole application's), on behalf of whom, and whether the record keeps both}} |
| Permission | {{one of `forbidden`, `propose`, `with-approval`, `autonomous`}} |
| Approval | {{who approves, how they are identified and recorded, and the deadline; or `not now`, with the condition that reopens it}} |
| Idempotency | {{idempotent or not, with the evidence; the idempotency key or the state checked before the effect}} |
| Hard Limits | {{each limit, in the code (path) or decided and not in the code yet; a limit only the prompt states is not one}} |
| Kill Switch | {{whether the kill switch of the system stops this action}} |
| ADR | {{the number of the accepted ADR, or the link or file of the `decision` task}} |

{{next action block, same shape}}

## Kill Switch

{{One per system: how it is triggered without a deploy, who may trigger it, what happens to the
work in progress, and the path without the agent. Or `not now`, with the condition that reopens
it.}}

## Excess Check

| Excess | Found | Evidence |
|---|---|---|
| Functionality | {{yes or no}} | {{functions the agent reaches that the use case does not need}} |
| Permission | {{yes or no}} | {{what the identity can do beyond what the action needs}} |
| Autonomy | {{yes or no}} | {{high-impact actions without a person checking}} |

## Actions Without Effect

{{One line: the model calls and reads with no effect outside the system.}}
