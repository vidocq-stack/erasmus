# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Erasmus of Rotterdam produced the *Novum Instrumentum* (1516) by cross-checking the
> Latin Vulgate against the original Greek manuscripts, correcting centuries of
> accumulated transcription errors through careful, methodical comparison against a
> source of truth. The Erasmus project implements **Jakarta Bean Validation 3.1**: it
> does exactly that to Java objects — cross-checks their state against a declared set
> of constraints and reports every discrepancy, precisely and without ambiguity.

## Prerequisites

- **Java 25** + **Maven 3.9.16** (`.sdkmanrc` provided — use `sdk env`)
- `jakarta.validation:jakarta.validation-api:3.1.1` is already modular upstream
  (module `jakarta.validation`) — no Vidocq fork needed (unlike Knock's MicroProfile
  Health API).
- The official Jakarta Bean Validation 3.1 TCK's distribution shape (Maven Central
  coordinates vs. build-from-source, Arquillian-or-not) is **not yet confirmed** — this
  is the first task of ROADMAP M8, not an installation step you can follow today.

## Essential Commands

```bash
# SDK environment
sdk env

# Build reactor (without TCK)
./mvnw -ntp install -DskipTests

# Unit tests
./mvnw test

# TCK profile activates (currently an empty placeholder module — see ROADMAP M8)
./mvnw -Ptck -pl erasmus-tck install -DskipTests
```

> `erasmus-tck` is **in-reactor behind the `tck` Maven profile** from day one (no historical
> out-of-reactor phase needed — Erasmus starts life after the workspace's Maven 3.9.16 /
> Model 4.0.0 migration that made the old ShrinkWrap constraint moot). A plain
> `./mvnw install` neither builds nor downloads anything TCK-related.

## Architecture

Erasmus is a **Jakarta Bean Validation 3.1** implementation with **zero third-party
dependency** (no Hibernate Validator, no Apache BVal, no third-party EL engine), virtual
threads where applicable, strict Java Modules, and static constraint-dispatch codegen via
APT as the long-term alternative to runtime reflection.

```
erasmus-api              ← Aggregator: re-exposes jakarta.validation-api (already modular, no fork)
erasmus-core              ← Engine: bootstrap, built-in constraints, message interpolation,
                            reflective constraint metadata builder — the only module with
                            real logic today (ROADMAP M1)
erasmus-codegen-apt       ← Placeholder — static per-bean constraint-dispatch codegen (M5)
erasmus-codegen-maven-plugin ← Placeholder — classpath scan for non-annotatable classes (M5)
erasmus-cdi-vauban        ← Placeholder — Validator/ValidatorFactory as CDI beans,
                            @ValidateOnExecution interceptor (M7)
erasmus-jaxrs             ← Placeholder — ConstraintViolationException → HTTP 400 via Cassini (M9)
erasmus-bench             ← Placeholder — JMH vs Hibernate Validator (no benchmarks written yet)
erasmus-examples          ← Placeholder — usage examples
erasmus-tck               ← Placeholder — official TCK runner, in-reactor behind `tck` (M8)
```

**Current validation flow (M1, `erasmus-core` only):** `Validator.validate(bean)` →
`ConstraintMetadataBuilder.build(bean.getClass())` (cached per class on the owning
`ValidatorFactory`) → for each constrained property, read its value via `PropertyAccessor`
(getter preferred, private field as fallback) → `ConstraintValidatorResolver` picks the
right `ConstraintValidator` for the value's (or, if `null`, the declared) type →
`ErasmusConstraintValidatorContext` collects the resulting violation(s) →
`ErasmusMessageInterpolator` resolves `{bundle.key}` templates and `{attribute}` placeholders
→ `ConstraintViolationImpl`. No cascading (`@Valid`), no groups, no container-element
constraints, no executable validation yet — see ROADMAP M3–M5 for each.

## Architecture Constraints Not to Violate

1. **`erasmus-core` depends on nothing beyond `jakarta.validation-api`** — no CDI, no
   Jakarta REST, no third-party EL engine. Same "fundamental separation" pattern as
   Cervantes' `cervantes-core` / Knock's `knock-core`.
2. **Built-in constraints are NOT resolved via `@Constraint.validatedBy()`** — the spec's
   own annotations declare it empty. They are resolved through `BuiltinConstraints`
   (`io.vidocq.erasmus.core.internal.constraints`), consulted by `ConstraintDescriptorImpl`
   only when `validatedBy()` comes back empty. Custom, user-defined constraints still go
   through `validatedBy()` directly — do not special-case those through the registry.
3. **Null-safety convention**: every built-in `ConstraintValidator` except `NotNullValidator`
   must return `true` for a `null` value. Never "fix" a validator by adding a null check
   that rejects — that breaks composability with `@NotNull` on the same property.
4. **No resource file under a package path already read as another module's export.**
   `erasmus-core` requires (transitively) `jakarta.validation`, which exports package
   `jakarta.validation` — shipping so much as a `.properties` file under
   `src/main/resources/jakarta/validation/...` in `erasmus-core` is a split package and
   fails module resolution at JVM boot, not at compile time. Erasmus's default message
   bundle lives under its own package
   (`io/vidocq/erasmus/core/internal/ValidationMessages.properties`) precisely because of
   this. If you add another resource bundle or SPI file, sanity-check its package against
   every module this one `requires` before assuming it's safe.
5. **`FieldAccessor`/`MethodAccessor` calling `trySetAccessible()` is a deliberate, narrow
   exception to "no reflection tricks in production code"** — it is the only way to read
   arbitrary (possibly private) bean state from a generic validator, and it is exactly what
   `erasmus-codegen-apt` (M5) is designed to make unnecessary for compile-time-processed
   beans. Do not add more reflection than these two narrowly-scoped accessors need.
6. **No `synchronized`, no `ThreadLocal`** — the `BeanMetadata` cache is a
   `ConcurrentHashMap` on the `ValidatorFactory`; validation state is passed explicitly
   through call parameters.
7. **`Validator.getConstraintsForClass(...)` and `forExecutables()` deliberately throw
   `UnsupportedOperationException`** with a ROADMAP pointer (M6/M5) rather than a fake or
   partial implementation. Do not paper over these with a stub that silently returns wrong
   data — replace the exception only when actually implementing the milestone.
8. **JUnit 6** (`org.junit:junit-bom`, managed by `vidocq-parent`) for all tests — no Mockito;
   hand-written fixture beans and doubles only.
9. **Language** — commit messages, Javadoc, and all `.md` file content must be written in
   **English**.

## Conventions

- **Packages**: `io.vidocq.erasmus.core.internal.*` = implementation, not exported from
  `module-info.java` (discovery happens through `provides jakarta.validation.spi.ValidationProvider`,
  which the module system allows without an export). `io.vidocq.erasmus.core.internal.metadata`
  = reflective metadata model (`BeanMetadata`, `PropertyMetadata`, `ConstraintDescriptorImpl`,
  accessors). `io.vidocq.erasmus.core.internal.constraints` = built-in `ConstraintValidator`
  implementations + the `BuiltinConstraints` registry.
- **Maven groupId**: `io.vidocq.erasmus`. Version: `0.3.0-SNAPSHOT` (the workspace's shared
  dev version, parent `io.vidocq:vidocq-parent:0.3.0-SNAPSHOT`).
- **Records** for immutable data (`BeanMetadata`, `PropertyMetadata`,
  `ErasmusConfigurationState`, `MessageInterpolatorContextImpl`) where the interface being
  implemented doesn't dictate `getXxx()`-style accessor names; otherwise plain `final` classes
  with explicit overrides (records can't rename their generated accessors to match a
  pre-existing interface method name).
- **One `ConstraintValidator` class per (constraint, target type) pair** — e.g. four separate
  classes for `@Size` (`CharSequence`/`Collection`/`Map`/`Object[]`), matching the spec's own
  constraint-validator-resolution model. Small, duplicated `initialize()` logic across sibling
  validators (see `SizeValidatorSupport`) is preferred over a premature shared abstraction.

## TDD — Test-Driven Development (mandatory)

Erasmus is developed with **strict TDD**, one Bean Validation 3.1 chapter at a time:

1. **Red** — write the test describing the expected behavior first.
2. **Green** — write the minimum code to make it pass.
3. **Refactor** — clean up while keeping tests green.

Concrete rules:

- **One test class per constraint** (covering every target-type validator it declares), plus
  `ErasmusValidatorTest` for the end-to-end `validate`/`validateProperty`/`validateValue` path
  and `ErasmusBootstrapTest` for `ServiceLoader` discovery.
- **No Mockito** — a `ConstraintValidatorContext` argument of `null` is usually enough since
  none of the built-in validators through M1 touch it; end-to-end tests use real hand-written
  fixture beans.
- Two real bugs were only caught because the tests were written against real reflection
  behavior instead of assumptions — see ROADMAP M1's "Two real bugs caught by writing the
  tests first" note before trusting reflection-adjacent code without a test.

## TCK — Technology Compatibility Kit

Jakarta Bean Validation 3.1 TCK — not yet integrated. `erasmus-tck` is an in-reactor
placeholder module, gated behind the `tck` Maven profile (harmonised with every other
Vidocq sub-project). Before writing real content into it, ROADMAP M8 must first spike:

- the TCK's actual Maven Central coordinates (unconfirmed — Jakarta TCKs are not uniformly
  published there);
- whether the suite needs an Arquillian container at all, or is largely runnable in plain
  Java SE (Bean Validation, unlike Servlet/REST/CDI, does not itself require a container);
- the signature test (`.sig` file) that `erasmus-api` must satisfy with zero extra public
  members.

**Release discipline (once M8 lands):** no structural merge on `erasmus-core` /
`erasmus-codegen-apt` / `erasmus-cdi-vauban` without TCK PASS at 100%. Any challenge
(disabled test, documented spec-interpretation divergence) goes in a `TCK.md` with spec
citation, test hash, and reactivation plan — created only if one is actually filed.

## AI Principles — Collaboration on This Repository

- **Plan mode by default** on any structural change (new module, new SPI, modification of
  the validation engine's public shape).
- **Cite the spec, not just the test**: when implementing a chapter of Bean Validation 3.1,
  reference the concept by name in commit messages / PR descriptions (groups, cascading,
  container-element constraints, executable validation, ...) — the ROADMAP milestone
  boundaries exist so this stays traceable.
- **Zero third-party libraries**: `jakarta.validation-api` is the only compile-scope
  dependency allowed in `erasmus-core`. If an implementation library starts to seem
  necessary (a real EL engine, a reflection-utility library), the decomposition is wrong —
  revisit the design instead.
- Use agents **`java-modules-guardian`**, **`virtual-threads-reviewer`**,
  **`dependency-gatekeeper`** proactively on any `module-info.java` modification,
  concurrent code, or `pom.xml` change.
- If the rules in this file need updating, keep `AGENTS.md` aligned so Copilot/other tools
  can reference it easily.

## Documentation (Antora) conventions

The project documentation lives in `docs/en` as an Antora component and is
aggregated by the **vidocq-docs** site, which provides a **shared UI bundle** (banner,
logo, fonts, colours, footer). **Never customise the documentation UI per project** —
all visual harmonisation is centralised in `vidocq-docs/ui-bundle`. (Not yet created for
Erasmus — see `AGENTS.md` for the layout to follow once it is.)

### Gold reference
**Vauban** is the reference implementation for documentation structure. Mirror its
`docs/en` layout when creating docs for this project.

### TCK / Performance rule
Erasmus is a spec implementation (not Chappe/Vidocq's special case), so its docs will need
a **`tck`** section once M8 lands, and a **`performance`** section once `erasmus-bench` has
real numbers (recorded in `BENCH.md`, not created yet).

> When you change these documentation rules, keep `AGENTS.md` and `CLAUDE.md` in sync.

## Making-of blog series

`MAKING-OF.md` (root) is the index of a per-milestone, blog-style build log under
`doc/making-of/` — kept for new contributors and anyone curious how the project came
together, separate from the Antora docs above. Before adding or editing a post, read
`doc/making-of/AGENTS.md`: one post per milestone (or milestone group), the exact opening
shape (recap paragraph, then the milestone's `ROADMAP.md` Scope spec/Deliverable quoted
verbatim), the "pick 1-2 real examples backed by an actually-run test" rule, and why each
post's closing "Where it stands now"/"What's next" is a frozen snapshot rather than a living
summary.

## Terminology

Use **Java Modules** (or **Java module** for a single module) when referring to
the Java Platform Module System. Do **not** use the abbreviation **JPMS** — in
prose, identifiers, or documentation.
