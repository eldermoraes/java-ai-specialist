# Risk: {{system name: the name the build file declares; without one, the name the user gave}}

<!-- Write this file in the user's language, headings and table headers included (language.md).
     Keep the scores, risk storming, effectful retry, unproven technology, single-assessor and the
     Direction values (new, improving, stable, worsening) as they are. One block per dimension: a
     run replaces only the block of that dimension, and a new dimension adds a block after the
     last one. Effectful Retry and Other Dimensions Seen cover the whole file and are redone in
     every run. Leave this comment out of the file. -->

## Dimension: {{one dimension}}

Assessed on: {{YYYY-MM-DD}}

### Risk Storming

#### Identification

| Area | Where it shows | Impact (1–3) | Likelihood (1–3) | Why |
|---|---|---|---|---|
| {{area}} | {{path, class or configuration key}} | {{1, 2 or 3}} | {{1, 2 or 3; "unproven" for a technology unknown to the team}} | {{the evidence, against the scale of the vocabulary}} |

#### Consensus

| Area | Score | Level | Assessors | Divergence and how it was settled |
|---|---|---|---|---|
| {{area}} | {{score from the script}} | {{level from the script}} | {{names; `single-assessor` when only one person scored}} | {{the scores that diverged and why the group settled on this one; "none" with a single assessor}} |

#### Mitigation

| Area | Score | Mitigation | In place (evidence, ADR) | Task |
|---|---|---|---|---|
| {{area scored 6 or 9}} | {{score}} | {{the change in the architecture that lowers the risk; a proof of concept or spike for an unproven technology}} | {{path, or ADR number, or "no"}} | {{link or .scratch file of the `high-risk` task}} |

### Risk Matrix

Risk = impact × likelihood (impact first). 1–2 low, 3–4 medium, 6–9 high. A technology unknown
to the team is unproven: 9, without the matrix, checked in every run whatever the dimension, and
scored as an area of this block.

| Area | Impact | Likelihood | Score | Level | Direction |
|---|---|---|---|---|---|
| {{area}} | {{1–3}} | {{1–3, or "unproven"}} | {{score}} | {{level}} | {{`new`, `improving`, `stable` or `worsening`, against the previous block of this dimension}} |

### Assessors

{{Names and date; `single-assessor` on every risk only one person scored. The agent recommends and
is not an assessor. On a rerun, the date of the previous block too.}}

### Direction

{{A summary, not a second copy of the values: `new` on the first block of this dimension;
otherwise how many areas are `improving`, `stable` or `worsening` against the previous block of
this dimension, citing the fitness function that measures each when there is one. The value per
area is in the Risk Matrix table.}}

{{next dimension block, same shape}}

## Effectful Retry

{{Whole system, redone in every run.}}

| Action | Effect | Retried by (resilience retry, guardrail retry, retry of a call that ran tools) | Idempotent | Score |
|---|---|---|---|---|
| {{action}} | {{what leaves the system}} | {{each retry that can repeat it}} | {{yes or no, with the evidence}} | {{score of a duplicated effect}} |

{{Or, with no effectful action: no effectful action; every retry is safe; the analysis reopens with
the first effectful action.}}

## Other Dimensions Seen

{{Whole file, redone in every run: what the reading found in dimensions with no block yet,
unscored, each with the suggestion of another run.}}
