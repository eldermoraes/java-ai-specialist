# Architecture Characteristics: {{system name}}

<!-- Write this file in the user's language, headings and table headers included (language.md).
     Keep `gap` and the paths as they are; translate "none" and "(estimate)" like the rest of the
     text. The system name is the name the build file declares (Maven `<name>`, otherwise
     `<artifactId>`; Gradle `rootProject.name`); without one, the name the user gave in the round
     that confirmed the list. Never the title of the README. Repeat the quantum section
     once per architecture quantum; with a single quantum, keep one section and name the quantum
     after the system. Leave this comment out of the file. -->

What this system must guarantee beyond its domain behavior. Each characteristic meets the three
defining criteria: it is a non-domain consideration, it influences the structure, and it is
critical to the success of the system. Each one has a measurable Target and the fitness function
that checks it, or a `gap` task when none exists yet.

## Quantum: {{quantum name}}

{{One line: what this quantum is and how it is deployed (for example, "one Quarkus service, one
`pom.xml`; the Dockerfiles are image variants of the same build").}}

| Characteristic | Why (meets the 3 defining criteria) | Target | Measure | Fitness Function |
|---|---|---|---|---|
| {{characteristic}} | {{non-domain: ...; structure: what it shapes; critical: why the system fails without it}} | {{number, unit, percentile or window; "(estimate)" when the project shows no evidence, or only a few samples or a single run}} | {{Operational, Structural or Process}} | {{path of the test, metric with alert, architecture rule or CI rule; a log line counts only through the test or alert that reads it, and a configuration limit only through the test that exercises it; or "none: `gap` task <link or .scratch file>"}} |

## Out of Scope

Concerns considered and left out of this dossier, with the reason.

| Concern | Why it is not an architecture characteristic here |
|---|---|
| {{concern}} | {{the criterion it fails, or where it belongs instead}} |

## Notes

- Date: {{YYYY-MM-DD}}
- Sources read: {{brief, ADRs, README, build files, configuration, tests}}
- Related: {{brief, ADRs, `gap` tasks}}
