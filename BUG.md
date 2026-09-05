# BUG.md — Erasmus

Reproducible bugs (internal issue, regression, incorrect behavior not yet fixed).
Entry format: `short id · date · symptom · minimal repro · cause hypothesis · status`.

## E-001 · 2026-09-02 · `@NotBlank` and `@NotEmpty` accept `null`

- **Symptom**: `validator.validate(bean)` reports no violation for a `@NotBlank String` (or `@NotEmpty` CharSequence/Collection/Map/array) whose value is `null`.
- **Minimal repro**: `new NotBlankValidator().isValid(null, null)` returns `true`; same for every `NotEmptyValidatorFor*`. `NotBlankValidatorTest.nullIsTriviallyValid` and `NotEmptyValidatorTest` pin the wrong behavior down.
- **Cause**: the M1 convention "every built-in validator except `@NotNull` treats `null` as trivially valid" (`CLAUDE.md`, rule 3) was applied to all constraints. The spec makes it true for most (Bean Validation 3.1 §8.13 `@Size`: "`null` elements are considered valid") but not for these two — §8.21 `@NotBlank`: "The annotated element must not be `null` and must contain at least one non-whitespace character."; §8.20 `@NotEmpty`: "The annotated element must not be `null` nor empty."
- **Found**: while quoting the spec in `doc/making-of/04-cascading-and-groups.md` (M3). The TCK (M8) would have caught it.
- **Fix**: `NotBlankValidator`, the four `NotEmptyValidatorFor*`, their tests, and `CLAUDE.md` rule 3 (exceptions: `@NotNull`, `@NotEmpty`, `@NotBlank`). Separate change, not part of M3.
- **Status**: open.

## E-002 · 2026-09-02 · Cycle detection is per `validate()` call, spec says per navigation path

- **Symptom**: an instance reachable through two different `@Valid` properties of the same root (e.g. `@Valid Address home` and `@Valid Address work` pointing at the same `Address`) is validated once; its violations are reported under the first path only (`home.city`), never under `work.city`.
- **Minimal repro**: `Person p = new Person(); p.home = p.work = new Address("");` — Erasmus reports 1 violation, the spec's algorithm reports 2 (same constraint, same instance, two paths).
- **Cause**: `ErasmusValidator.validateGraph` keeps one identity-based visited set per group sheet for the whole call. Bean Validation 3.1 §5.7.1: "the Jakarta Validation implementation must ignore the cascading operation if the associated object instance has already been validated in the current navigation path (starting from the root object). [...] A given navigation path cannot contain the same instance multiple times (the complete validated object graph can though)."
- **Fix**: scope the visited set to the current path (push on descent, pop on return) instead of the whole call. Small; deferred so that M3's commits stay readable.
- **Status**: open.
