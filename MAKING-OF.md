# Building Erasmus: a Jakarta Bean Validation implementation, one prompt at a time

*A first-person account of how this project came together — the tooling, the false starts,
and the implementation work — kept here for new contributors, or anyone curious how it came
together. Written in a blog-ish style rather than as formal docs; may still turn into an
actual blog post at some point. Split into one file per milestone (or milestone group) so
each post stays a readable size — new posts get added as new milestones land.*

## Posts

1. [Kicking off Erasmus: from an empty reactor to a working validator](doc/making-of/01-kicking-off-erasmus.md) —
   the tooling detour, GPG/CLA/governance, cloning the workspace, the first two prompts, and
   milestones M0 (scaffolding) + M1 (the first 6 built-in constraints, and three real gotchas).
2. [M2: the full built-in constraint set, message interpolation, and custom constraints](doc/making-of/02-full-constraint-set-and-interpolation.md) —
   the remaining 15 built-in constraints, locale-aware message bundles, a homegrown EL-subset
   evaluator, and custom constraint authoring (composed constraints, `@ReportAsSingleViolation`).
