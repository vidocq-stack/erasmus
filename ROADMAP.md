# Erasmus — Implementation plan

> Jakarta Bean Validation 3.1 implementation in the Vidocq style: zero third-party
> dependency, JDK 25, virtual threads where applicable, strict Java Modules, static
> generation of constraint-dispatch code via APT where possible (reflective introspection
> as the fallback path).
>
> Spec reference: [Jakarta Bean Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1).
> API package: `jakarta.validation`. Aligned with Jakarta EE 11 / MicroProfile-adjacent bricks
> of this workspace (Vauban for CDI, Cassini for Jakarta REST, Champollion for any JSON
> rendering of violation reports).

## Design principles

| Principle | Concrete application |
|---|---|
| Zero third-party dependency | No Hibernate Validator, no Apache BVal in `erasmus-core`. Only `jakarta.validation-api` is compiled against. No third-party EL engine either — message interpolation ships a homegrown minimal EL subset (see M2). |
| Jakarta / MicroProfile specs allowed at the edges | `erasmus-cdi-vauban` depends on `jakarta.enterprise.cdi-api` + `jakarta.interceptor-api`. `erasmus-jaxrs` depends on `jakarta.ws.rs`. `erasmus-core` itself depends on nothing beyond `jakarta.validation-api`. |
| Virtual threads | No `synchronized`, no `ThreadLocal`. Constraint metadata caches (`ConcurrentHashMap`/`ClassValue`), per-validation-call graph traversal state passed explicitly (a `ValidationContext` object), never thread-local. |
| Maximum static code generation | `erasmus-codegen-apt` generates, at compile time, a per-bean constraint-dispatch class (property + cascading + group filtering already resolved) so that a hot validation path does zero annotation reflection at runtime. Reflective introspection (`erasmus-core` metadata builder) remains the fallback for classes that were not processed by the APT (third-party classes, dynamically loaded classes). |
| Java Modules strict | `module-info.java` everywhere, `internal.*` not exported, SPI (`ConstraintValidator`, `ValueExtractor`, codegen-generated dispatchers) via `provides/uses`. |
| Strict TDD | Red → Green → Refactor. Tests written before production code, each citing the relevant Bean Validation 3.1 chapter. |
| TCK PASS 100 % | Hard contract on the official Jakarta Bean Validation 3.1 TCK before any structural merge. |
| AOT-friendly | No dynamic proxies for `ConstraintValidator` instantiation, no `setAccessible(true)` in production code. Compatible with GraalVM `native-image` and CDS. |

## Methodology: TDD + TCK + codegen/reflection differential testing

Erasmus is developed with **strict TDD** (Red → Green → Refactor). Beyond the internal
TDD cycle, three additional safety layers run in parallel from the milestone that first
makes them meaningful:

- **Layer 1 — TDD unit tests**: drive the design of each class, one Bean Validation 3.1
  chapter at a time.
- **Layer 2 — `erasmus-core` integration tests**: full bean graphs, group sequences,
  container element constraints, executable validation — independent of the TCK and
  reproducible without any external harness.
- **Layer 3 — official TCK** (`jakarta.validation:validation-tck-tests:3.1.1`): runs on
  **every commit**, as a ratchet — see "The TCK drives every milestone" below. In-reactor
  behind the `tck` Maven profile, per the ecosystem's current convention (a plain
  `mvn install` neither builds nor downloads the TCK harness).
- **Layer 4 — codegen/reflection differential testing** (from M5 onward, once
  `erasmus-codegen-apt` exists): the same fixture beans are validated twice — once through
  the reflective metadata builder, once through APT-generated dispatch classes — and the
  resulting `Set<ConstraintViolation<T>>` must be equal (same paths, same messages, same
  root beans) on every commit touching `erasmus-core` or `erasmus-codegen-apt`.

## The TCK drives every milestone

*Decided 2026-10-09.* The official TCK is not a milestone at the end of the list any more:
it is the acceptance test of every milestone, and it runs on every commit.

- `erasmus-tck/tck-known-failures.txt` lists the TCK tests Erasmus does not pass yet.
  `KnownFailuresRatchet` fails the run when a listed test passes (remove its line in that
  commit) or when an unlisted test fails (a regression). `.forgejo/workflows/tck.yml` runs it
  on every commit of a pull request.
- Each milestone names its **TCK slice**: the TCK packages it owns. Work on a milestone
  starts by running its slice and watching it fail; a commit that turns TCK tests green
  deletes their lines, so `git log -p erasmus-tck/tck-known-failures.txt` is the
  milestone's progress, test by test.
- A milestone is done when none of its slice is left in the list, except tests that fail
  only because a *later* milestone is missing (the `UnsupportedOperationException`s of the
  metadata API and of executable validation), and tests filed in `TCK.md`.

Where each milestone's slice stood on the first run (TCK 3.1.1, 981 tests run, the
`@IntegrationTest` ones excluded). "Other failures" fail for a reason of their own — not
because M5 or M6 is missing:

| Milestone | TCK packages (under `org.hibernate.beanvalidation.tck.tests`) | Tests | Passing | Other failures | Blocked by M5 | Blocked by M6 |
|---|---|---:|---:|---:|---:|---:|
| M1–M2 | `bootstrap*`, `validatorfactory`, `validation.validatorcontext`, `constraints.*` (except the groups, container-element, cross-parameter and method ones), `time`, `messageinterpolation` | 192 | 68 | 86 | 18 | 20 |
| M3 | `constraints.groups*`, `validation`, `validation.graphnavigation`, `validation.groupconversion`, `traversableresolver` | 153 | 46 | 55 | 46 | 6 |
| M4 | every `*.containerelement` package outside `xmlconfiguration`, `valueextraction.*` | 136 | 5 | 96 | 34 | 1 |
| M5 | `methodvalidation*`, `constraints.crossparameter`, `constraints.application.method`, `constraints.inheritance.method.*` | 176 | 2 | 11 | 163 | 0 |
| M6 | `metadata` | 164 | 0 | 2 | 0 | 162 |
| M10 | `xmlconfiguration*` | 146 | 2 | 74 | 15 | 55 |
| — | `util` (the TCK's own helpers) | 14 | 12 | 0 | 2 | 0 |
| **Total** | | **981** | **135** | **324** | **278** | **244** |

`./run-official-tck-bean-validation-3.1.sh` prints the same view, per package, from
`erasmus-tck/target/tck-summary.txt`.

## Jakarta Bean Validation 3.1 — spec recap (key points to implement)

| Chapter (approximate) | Concept | Notes |
|---|---|---|
| Bootstrapping | `Validation.buildDefaultValidatorFactory()`, `Configuration<?>`, `ValidatorFactory`, `Validator` | Default provider resolution via `ServiceLoader` (`jakarta.validation.spi.ValidationProvider`). Erasmus is *the* provider — no provider-selection logic needed beyond exposing itself. |
| Constraint declaration & validation | `@Constraint`, `ConstraintValidator<A, T>`, `ConstraintValidatorContext`, built-in constraints | `@NotNull`, `@NotEmpty`, `@NotBlank`, `@Min`/`@Max`, `@DecimalMin`/`@DecimalMax`, `@Digits`, `@Size`, `@Pattern`, `@Email`, `@Past`/`@PastOrPresent`, `@Future`/`@FutureOrPresent`, `@Positive`/`@PositiveOrZero`, `@Negative`/`@NegativeOrZero`, `@AssertTrue`/`@AssertFalse`. |
| Graph validation | `@Valid` cascading, cycle detection | Traversal must detect already-visited (bean, group) pairs to avoid infinite loops on circular object graphs. |
| Groups & group sequences | `Default` group, `@GroupSequence`, group inheritance | Sequence short-circuits on first group with a violation. |
| Container element constraints | `List<@NotBlank String>`, `Optional<@Positive Integer>`, `Map<@NotNull K, V>`, arrays | Requires the `ValueExtractor` SPI (built-in extractors for `Collection`, `Map`, `Optional`, arrays; pluggable for custom containers). |
| Method & constructor validation | `ExecutableValidator`, parameter / return-value / cross-parameter constraints | Needs parameter names — either `-parameters` at compile time or a custom `ParameterNameProvider`. Usable standalone, without CDI. |
| Constraint metadata API | `BeanDescriptor`, `PropertyDescriptor`, `ConstraintDescriptor<?>`, `MethodDescriptor`, `ConstructorDescriptor` | Read-only introspection of the constraint model — needed for tooling and for some TCK tests. |
| Message interpolation | `MessageInterpolator`, default resource bundles, EL expression subset in message templates | Own minimal EL-subset interpolator (see M2) — no third-party EL engine. |
| XML mapping | `META-INF/validation.xml`, `constraints.xml` mapping files | Lower priority — see M9, deferred until after the TCK's core suites are green. |
| Integration | CDI (`erasmus-cdi-vauban`), Jakarta REST (`erasmus-jaxrs`) | Not part of the core spec module itself — see M6/M7. |

---

## Phases

### M0 — Bootstrap ✅

- [x] `.sdkmanrc` (`java=25-tem`, `maven=3.9.16`)
- [x] `.gitignore`, `.mvn/maven.config`
- [x] parent `pom.xml` (Model 4.0.0, multi-module, Jakarta dependency management:
      `jakarta.validation:jakarta.validation-api:3.1.1` — already modular upstream, no fork needed)
- [x] `CLAUDE.md` (pending — see follow-up), `AGENTS.md` ✅ (this file's sibling), `ROADMAP.md` ✅ (this file)
- [x] `LICENSE` (disjunctive EPL-2.0/EUPL-1.2/GPL-2.0-or-later, matching the rest of the workspace), `NOTICE`, `CONTRIBUTING.md`
- [x] Creation of the module skeletons (`pom.xml` + skeletal `module-info.java`):
      `erasmus-api`, `erasmus-core`, `erasmus-codegen-apt`, `erasmus-codegen-maven-plugin`,
      `erasmus-cdi-vauban`, `erasmus-jaxrs`, `erasmus-bench`, `erasmus-examples`
      + `erasmus-tck` (out of the default build, gated behind the `tck` profile from the start)
- [x] Validate that `./mvnw -ntp install -DskipTests` succeeds on the reactor
- [x] Smoke test: `Validation.buildDefaultValidatorFactory()` resolves the Erasmus provider
      and returns a non-null `Validator`

**Deliverable:** empty-but-buildable reactor, provider resolvable via `ServiceLoader`. ✅ Confirmed:
`./mvnw -ntp install` and `./mvnw -ntp -Ptck install` both succeed on a clean reactor.

**Java Modules gotcha found here, worth keeping in mind for every future resource bundle:**
a resource file (not just a `.class`) placed under a package path that a module's own
`requires` graph already reads as an *export of another module* is a split package, and
`ServiceLoader`/module resolution fails at JVM boot with `ResolutionException`, not at
compile time. Concretely: `erasmus-core` requires (transitively) the `jakarta.validation`
module, which exports package `jakarta.validation` — so `erasmus-core` cannot also ship a
resource under `src/main/resources/jakarta/validation/...`, even a `.properties` file with
no `.class` in it. The default message bundle therefore lives under Erasmus's own package
(`io/vidocq/erasmus/core/internal/ValidationMessages.properties`), consulted only after the
spec's own user-override bundle name (`jakarta.validation.ValidationMessages`) comes up empty
— see `ErasmusMessageInterpolator`.

---

### M1 — Core engine: bootstrap + first built-in constraints ✅

**Scope spec:** bootstrapping, constraint declaration, a first slice of built-in constraints.

| Task | Notes | Status |
|---|---|---|
| `ErasmusValidatorFactory` / `ErasmusValidator` | Implements `ValidatorFactory` / `Validator`. | ✅ |
| `ErasmusConfiguration` | Implements `Configuration<ErasmusConfiguration>`; resolves every component to its default before building the `ConfigurationState` snapshot handed to the provider. | ✅ |
| `ConstraintMetadataBuilder` (reflective) | Walks a class's field hierarchy + public getters, collects any annotation meta-annotated `@Constraint` generically (works for any future custom constraint with zero change here) into a `BeanMetadata`/`PropertyMetadata` model, cached per `Class` in a `ConcurrentHashMap` on the `ValidatorFactory`. | ✅ |
| First built-in constraints | `@NotNull`, `@NotEmpty` (CharSequence/Collection/Map/Array), `@NotBlank`, `@Size` (CharSequence/Collection/Map/Array), `@Min`/`@Max` (integral Number + BigInteger/BigDecimal, floating-point rejected outright) — 12 `ConstraintValidator` classes total. | ✅ |
| `BuiltinConstraints` registry | **Discovered mid-implementation, not originally planned**: the spec's own built-in annotations declare `@Constraint(validatedBy = {})` — an *empty* array (verified against the actual class files) — so `@Constraint.validatedBy()` alone can never resolve a built-in constraint's validators. Every compliant implementation must ship its own default-validator registry for the built-ins; `BuiltinConstraints` is that registry, consulted by `ConstraintDescriptorImpl` only when `validatedBy()` comes back empty (custom user constraints still go through `validatedBy()` directly). | ✅ |
| `ConstraintViolationImpl` + `PathImpl`/`NodeImpl` | Property-level only for now — a single `PropertyNode` per path, no bean/container-element/index nodes until cascading (M3). | ✅ |
| `ErasmusConstraintValidatorContext` + `ConstraintViolationBuilderImpl` | Default violation + N custom violations via `buildConstraintViolationWithTemplate(...).addConstraintViolation()`; custom node navigation (`addPropertyNode`, ...) explicitly deferred to M3 (throws, documented). | ✅ |
| Unit tests | One test class per constraint (6 files covering all 12 validator classes) + `ErasmusBootstrapTest` (service discovery, `unwrap`, `usingContext`) + `ErasmusValidatorTest` (end-to-end `validate`/`validateProperty`/`validateValue`, field- and getter-based properties, custom message interpolation) — 35 tests, all green. | ✅ |

**Deliverable:** `validator.validate(bean)` returns correct `ConstraintViolation`s for a flat
bean (no cascading, no groups yet) using the first 6 built-in constraints. ✅ Confirmed by
`ErasmusValidatorTest` (11 tests) plus 24 direct validator unit tests.

**Two real bugs caught by writing the tests first (kept here as a reminder of why TDD earns
its keep on a reflection-heavy engine like this one):**
- **Null-safety convention**: every built-in validator except `@NotNull` must treat `null` as
  trivially valid (the spec delegates "must not be null" entirely to `@NotNull` so it composes
  cleanly with `@Size`, `@Min`, etc. on the same property). Validator resolution for a `null`
  value must therefore use the property's *declared* type, not `value.getClass()` (which doesn't
  exist for `null`) — see `ConstraintValidatorResolver`/`ErasmusValidator`.
- **Getter accessibility**: a `public` getter declared on a non-public class (package-private bean,
  private nested test fixture) is still not reflectively callable without `trySetAccessible()` —
  Java checks the *declaring class's* accessibility, not just the member's modifiers. Caught by
  `ErasmusValidatorTest`'s getter-based `email` property against a `private static final` test
  fixture; fixed in `MethodAccessor`.

---

### M2 — Full built-in constraint set + message interpolation ✅

**Scope spec:** remaining built-in constraints, message interpolation with EL-subset support.

| Task | Notes | Status |
|---|---|---|
| Remaining built-in constraints | `@AssertTrue`/`@AssertFalse`; `@Positive`/`@PositiveOrZero`/`@Negative`/`@NegativeOrZero` (Number, floating-point supported — unlike Min/Max, a sign check has no precision problem); `@DecimalMin`/`@DecimalMax` (Number + CharSequence, floating-point excluded, `inclusive` attribute); `@Digits` (Number + CharSequence, floating-point excluded); `@Pattern` (CharSequence, whole-string match, `Flag[]`); `@Email` (CharSequence, pragmatic non-RFC-5322 shape check + optional additional `regexp`); `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent` for `Instant`/`LocalDate`/`java.util.Date` only — **`LocalDateTime`, `LocalTime`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Year`, `YearMonth`, `MonthDay`, `Calendar`, and the non-ISO chronology date types (`HijrahDate`, `JapaneseDate`, `MinguoDate`, `ThaiBuddhistDate`) are a deliberate, documented gap** — same "narrow the target-type set, document the rest" pattern as M1's `Object[]`-only array constraints. 26 new validator classes, 58 tests total (up from 35). | ✅ (narrowed scope) |
| `Payload` support | `ConstraintDescriptorImpl.getPayload()` now reads the annotation's `payload()` attribute instead of returning an empty set unconditionally. | ✅ |
| Default resource bundles + locale variants | `ValidationMessages_fr.properties` added alongside the default bundle — French translations for all 21 built-in constraint default messages. Locale resolution rides on the JDK's standard `ResourceBundle` fallback (no bespoke lookup logic): an unsupported locale (tested with Japanese) falls back to the default bundle. | ✅ |
| Homegrown minimal EL-subset interpolator | `MinimalElExpression` — a small recursive-descent parser/evaluator for the `${...}` grammar actually used by real-world constraint messages: ternary, `\|\|`/`&&`, equality/relational, `+ - * /` `%`, unary `!`/`-`, string/number/boolean/null literals, and variable lookup against the constraint's attributes plus `validatedValue`. Deliberately **not** a general-purpose EL engine — no method calls, no bracket/dot navigation, no user-defined functions. Wired into `ErasmusMessageInterpolator` as a third pipeline step after bundle-key resolution and `{attribute}` substitution. | ✅ |
| Custom constraint authoring | `ConstraintDescriptorImpl` now resolves a "pure composition" constraint (empty `validatedBy()`, no `BuiltinConstraints` match) by delegating entirely to its composing constraints instead of throwing; `getComposingConstraints()`/`isReportAsSingleViolation()` are read through instead of hardcoded. `ErasmusValidator.evaluateConstraint()` recursively evaluates a descriptor's own validator (if any) plus every composing constraint, and collapses the result to one violation carrying the composed constraint's own message when `@ReportAsSingleViolation` is present. Covered end-to-end by `CustomConstraintAuthoringTest`: a simple custom constraint with its own `validatedBy()` validator (including EL in its message template), a composed constraint reporting one violation per failing part, the same composed constraint with `@ReportAsSingleViolation` collapsing to one, and a custom constraint declaring multiple `ConstraintValidator`s for different target types resolved through the same `ConstraintValidatorResolver` used for built-ins. | ✅ |

**Deliverable:** all built-in constraints implemented and unit-tested for the narrowed
target-type set above, locale-aware message interpolation (default + French bundles, EL-subset
`${...}` expressions), and custom constraint authoring (composed constraints,
`@ReportAsSingleViolation`, multi-target-type `validatedBy()`).

**A naming-collision gotcha worth remembering:** a JUnit test method named `assertTrue()` in
the same class as a `static import Assertions.assertTrue` silently breaks — Java hides
*every* statically-imported overload of a name the instant the class declares its own method
of that name, regardless of differing arity. `AssertBooleanValidatorTest` renamed its methods
to `assertTrueConstraint()`/`assertFalseConstraint()` to sidestep it.

---

### M3 — Graph validation (`@Valid`) + groups & group sequences ✅ (narrowed scope)

**Scope spec:** cascaded validation, groups, group sequences.

| Task | Notes | Status |
|---|---|---|
| `@Valid` cascading | Recursive descent into a single nested bean reference (`@Valid private Address address;`), `PathImpl` extended to multi-segment (`address.city`) via a new `.append(name)`. **Cascading into collection/array/map elements is explicitly deferred to M4** — that's exactly the container-traversal problem the `ValueExtractor` SPI milestone exists to solve uniformly, so doing it twice (once ad hoc here, once properly in M4) would be wasted work. | ✅ (single-bean only) |
| Cycle detection | A fresh `Set<Object>` (`IdentityHashMap`-backed, bean *identity* not `equals()`) per processed group (see below) — naturally satisfies "(bean identity, group)" scoping without needing a composite key, since each processed group already gets its own fresh visited-set in the per-group loop. **Known divergence (`BUG.md` E-002)**: the spec (§5.7.1) scopes the rule to the *current navigation path*, so an instance shared by two `@Valid` properties should be reported once per path; the whole-call set reports it once. Terminates identically; small fix, deferred. | ✅ (E-002 open) |
| `Default` group + explicit groups | `validate(bean, Group1.class, Group2.class)` — a `ConstraintDescriptorImpl` now actually reads the constraint annotation's `groups()` attribute (was hardcoded to `Set.of(Default.class)` since M1) instead of ignoring it. | ✅ |
| Group inheritance | `GroupsSupport.expand(group)` walks `Class.getInterfaces()` recursively — a group interface extending others pulls in the supers automatically. | ✅ |
| `@GroupSequence` | Short-circuits correctly **for the common case**: a single requested group that is itself `@GroupSequence`-annotated expands into its groups, processed one at a time in the declared order, stopping at the first with any violation. **Deliberate scope gap**: mixing a sequence group with other, unrelated groups in the same `validate(...)` call collapses everything into one unordered entry instead of correctly interleaving the sequence's short-circuit with the other groups — rare in practice (most real calls pass either `Default` or a single custom sequence), documented rather than silently wrong. | ✅ (narrowed scope) |
| Integration tests | `CascadingAndGroupsTest`: nested cascading with dotted paths, null/absent-`@Valid` non-cascading, a circular two-node graph, a self-referencing node, `Default` vs. explicit group, group inheritance, both directions of `@GroupSequence` short-circuiting (fails at first group / passes through to the second), a mixed cascading+groups case (a cascaded property whose own constraint is group-gated), and an all-three case (an M2 composed constraint, declared in a group, on a property of a cascaded bean — asserting the dotted path, the leaf bean, and that the composing constraints inherit the main annotation's groups per §3.3). 12 tests. | ✅ |

**Deliverable:** cascaded validation across arbitrary (including circular) object graphs for
single bean references, correct group-sequence short-circuiting for the single-sequence-group
case. 88 tests total (up from 76), all green.

---

### M3.5 — TCK catch-up on what M1–M3 already claim (next)

**Scope:** the TCK tests of M1–M3's slices that fail for a reason of their own — 141 on the
first run (86 in M1–M2's packages, 55 in M3's) — as opposed to failing on the metadata API
(M6) or executable validation (M5), which do not exist yet.

**Why before M4:** M1–M3 were closed against our own tests only. The TCK passes 114 of
their 345 tests. M4 builds on all three; fixing the gap now is cheaper than carrying it.

| Task | Notes |
|---|---|
| M1–M2 slice | Seen on the first run: no validator for `@Null`; `@Min` rejecting `Double`; no validator for `LocalTime`, `ZonedDateTime`, `GregorianCalendar` (M2's narrowed type set); constraint-definition checks (`constraints.invalidconstraintdefinitions`, 0/17); validator resolution (5/26); message interpolation (9/29). |
| M3 slice | Groups and group sequences (`constraints.groups.groupsequenceisolation`, 0/5); group conversion (`@ConvertGroup`, never in M3's narrowed scope); graph navigation; `TraversableResolver`. |
| `E-001`, `E-002` | Fix them through the TCK tests that cover them, where there are any. |
| Blocked tests | The M1–M3 tests that fail only on M5's or M6's `UnsupportedOperationException` stay listed; they move with those milestones. |

**Deliverable:** no test of the M1–M3 slices left in `tck-known-failures.txt`, except the
ones blocked by M5/M6 and any filed in `TCK.md`.

---

### M4 — Container element constraints (`ValueExtractor` SPI)

**Scope spec:** validation of type-argument-annotated container elements.

| Task | Notes |
|---|---|
| `ValueExtractor<T>` SPI | `provides ... with` wiring for built-in extractors. |
| Built-in extractors | `Collection<@X E>`, `Map<@X K, @Y V>` (keys and values), `Optional<@X T>`, arrays (including primitive arrays). |
| Nested generics | `List<Optional<@NotNull String>>`-style nesting resolved via extractor composition. |
| Custom `ValueExtractor` registration | Via `Configuration.addValueExtractor(...)` and `ServiceLoader`. |
| Unit + integration tests | One suite per built-in extractor, plus a nested-generics suite. |

**Deliverable:** `List<@NotBlank String>`, `Optional<@Positive Integer>`,
`Map<@NotNull String, @Valid Address>` all validate correctly, including nested cases.

**TCK slice:** every `*.containerelement` package outside `xmlconfiguration`, and
`valueextraction.*` — 136 tests, 5 passing on the first run.

---

### M5 — Method & constructor validation + `erasmus-codegen-apt`

**Scope spec:** executable validation; static code generation (ecosystem philosophy).

| Task | Notes |
|---|---|
| `ExecutableValidator` | `validateParameters`, `validateReturnValue`, `validateConstructorParameters`, `validateConstructorReturnValue`. |
| Parameter name resolution | Default: `-parameters` compiler flag (reflection on `Parameter.getName()`); pluggable `ParameterNameProvider` for classes compiled without it. |
| Cross-parameter constraints | `@SupportedValidationTarget(PARAMETERS)` custom constraints spanning multiple parameters. |
| `erasmus-codegen-apt` (new module) | Pure JDK Annotation Processor: for each `@Constraint`-annotated class it can see at compile time, generates a static dispatch class (`<Bean>$ErasmusValidator`) that validates properties/cascading/groups without any annotation reflection at runtime. Usable standalone (no Maven plugin required). |
| `erasmus-codegen-maven-plugin` (new module) | Mojo that scans the classpath for classes the APT could not see directly (third-party jars) and generates dispatch classes for them too — mirrors the Champollion codegen split. |
| Reflective fallback wiring | If no generated dispatcher is found for a type (`ServiceLoader`-registered `BindingFactoryProvider`-equivalent), `erasmus-core` falls back to the reflective metadata builder from M1–M4. |
| Layer 4 differential tests | Same fixtures validated through both paths; violation sets must match exactly. |

**Deliverable:** `Validator.forExecutables()` fully functional; a first working
`erasmus-codegen-apt` generating dispatch classes for at least the M1–M4 constraint set,
with differential tests green against the reflective path.

**TCK slice:** `methodvalidation*`, `constraints.crossparameter`,
`constraints.application.method`, `constraints.inheritance.method.*` — 176 tests, 2 passing
on the first run — plus, in every other slice, the tests that failed only on executable
validation's `UnsupportedOperationException` (115 on the first run).

---

### M6 — Constraint metadata API

**Scope spec:** read-only introspection.

| Task | Notes |
|---|---|
| `BeanDescriptor`, `PropertyDescriptor` | Exposes constrained properties, cascaded status, groups. |
| `MethodDescriptor`, `ConstructorDescriptor`, `ParameterDescriptor`, `ReturnValueDescriptor` | Executable metadata mirroring M5. |
| `ConstraintDescriptor<?>` | Composing constraints, attributes, payload, groups — built once per class, cached. |
| `Validator.getConstraintsForClass(...)` | Entry point wiring into the cached metadata model (shared with the reflective builder and, where possible, with codegen-generated metadata). |

**Deliverable:** full metadata introspection API, tested against every constraint family
introduced in M1–M5.

**TCK slice:** `metadata` — 164 tests, none passing on the first run — plus, in every other
slice, the tests that failed only on the metadata API's `UnsupportedOperationException`
(82 on the first run).

---

### M7 — `erasmus-cdi-vauban`: CDI integration

**Scope spec:** integration with a CDI container (not itself part of the core spec chapters,
but required by the Jakarta EE platform and by a subset of TCK challenges).

| Task | Notes |
|---|---|
| `Validator` / `ValidatorFactory` as CDI beans | `@Default @ApplicationScoped` producers, discoverable via `@Inject`. |
| `@ValidateOnExecution` interceptor | Interceptor binding wrapping `ExecutableValidator`, wired through Vauban's BCE — no dynamic proxy generation; prefer Vauban's static interceptor stack. |
| `ConstraintValidator` as CDI managed beans | When a `ConstraintValidator` is itself a CDI bean (has injection points), instantiate it through the container instead of `Class.newInstance()`. |
| Integration tests | Embedded Vauban container, injected `Validator`, method-validated managed bean throwing `ConstraintViolationException` on invalid invocation. |

**Deliverable:** a Vauban-managed bean with `@ValidateOnExecution`-annotated methods
rejects invalid invocations with a correctly populated `ConstraintViolationException`.

**TCK slice:** the TCK's `@IntegrationTest` tests, which need a CDI/EJB container and are
excluded today (`-DexcludeIntegrationTests=true`). Issue #16 also asks for this module to be
container-neutral (a standard Build Compatible Extension and interceptor) rather than
Vauban-specific, with a Weld integration test.

---

### M8 — TCK: the signature test, the container subset, the last gaps

**Scope:** what no other milestone owns. The TCK itself runs on every commit from the
ratchet onward (see "The TCK drives every milestone"); this milestone no longer *starts*
TCK work, it closes it.

| Task | Notes | Status |
|---|---|---|
| Spike: TCK distribution & shape | Resolved: `jakarta.validation:validation-tck-tests:3.1.1` and `validation-standalone-container-adapter:3.1.1` on Maven Central. TestNG + Arquillian in plain Java SE, no application server; the provider is selected with `-Dvalidation.provider`. It runs on the class path, which is why `erasmus-core` ships `META-INF/services`. | ✅ |
| `erasmus-tck` + `run-official-tck-bean-validation-3.1.sh` | In-reactor behind `-Ptck`; the TCK's own `tck-tests.xml`, unpacked from the jar; whole suite (about ten seconds) or `-Dtest=SomeTckTest`. | ✅ |
| Ratchet | `tck-known-failures.txt` + `KnownFailuresRatchet`; `.forgejo/workflows/tck.yml` runs it on every commit of a pull request. | ✅ |
| Signature test | The TCK ships a `.sig` file asserting the exact public shape of `jakarta.validation.*`. `erasmus-api` must re-export the spec API with **zero** extra public members. | |
| Container subset | The `@IntegrationTest` tests, after M7. | |
| `TCK.md` | Only created if a challenge is filed (disabled test, documented spec-interpretation divergence) — same discipline as Knock/Champollion. | |

**Deliverable:** `tck-known-failures.txt` empty, or holding only entries filed in `TCK.md`;
signature test PASS; the container subset run.

---

### M9 — `erasmus-jaxrs`: Jakarta REST integration (optional, post-TCK-green)

**Scope:** wiring Erasmus into Cassini-based deployments.

| Task | Notes |
|---|---|
| `ConstraintViolationException` → HTTP 400 | `ExceptionMapper<ConstraintViolationException>` producing a structured JSON body (via Champollion) listing property paths and messages. |
| Resource-method parameter validation | Reuse `ExecutableValidator` (M5) against JAX-RS resource methods — no bespoke validation logic in `erasmus-jaxrs` itself. |
| Integration tests | Embedded Cassini resource with `@NotNull`/`@Valid`-annotated parameters, asserting 400 responses with correct violation payloads. |

**Deliverable:** a Cassini JAX-RS resource with constrained parameters rejects invalid
requests with HTTP 400 and a structured violation report.

---

### M10 — XML mapping support (`validation.xml`, `constraints.xml`) — lower priority

**Scope spec:** deployment-descriptor-based constraint declaration, deferred until the
annotation-driven path (M1–M9) is TCK-green, since the bulk of the TCK and of real-world
usage is annotation-driven.

| Task | Notes |
|---|---|
| `META-INF/validation.xml` parsing | Default provider/message-interpolator/traversable-resolver overrides. |
| `constraints.xml` mapping | XML-declared constraints merged with annotation-declared ones per the spec's merging rules. |
| TCK coverage for XML mapping | Re-run the relevant TCK subset once implemented. |

**Deliverable:** XML-declared constraints validate identically to their annotation-declared
equivalents; merging rules respected.

**TCK slice:** `xmlconfiguration*` — 146 tests, 2 passing on the first run.

---

### M11 — Vidocq ecosystem integration

**Scope:** deploy Erasmus into `vidocq`; make it the default Bean Validation provider for
every Vidocq deployment (mirrors Knock's M5 / Cervantes' final integration milestone).

| Task | Notes |
|---|---|
| `docs/integration-cassini.md`, `docs/integration-vidocq-runtime.md` | Dependencies, Java Modules, a constrained-resource example. |
| ADR: integration strategy | Rationale, deployment order, risks — same pattern as Knock's ADR-002. |
| Wrapper module in `vidocq-runtime-integration-tests` or `vidocq-runtime-core-extensions` | Prefer a Maven/Java-Modules-only wrapper (no new `VidocqExtension` Java code) if Erasmus can self-deploy through existing SPIs, following the precedent set by Knock's ADR-002. |

**Deliverable:** Bean Validation available by default in every Vidocq deployment through a
single dependency, documented and TCK-backed.

---

## Priority order — why this one?

1. **M1 (core bootstrap + first constraints)** first: nothing is testable, let alone
   TCK-able, without a working `Validator.validate(bean)` on flat beans.
2. **M2 (full constraint set + interpolation)** before graph/groups: keeps each milestone
   focused on one axis of complexity at a time; the homegrown EL subset is isolated and
   independently testable before it has to interact with cascading.
3. **M3 (cascading + groups)** before **M4 (container elements)**: container element
   constraints are themselves cascade-aware (`List<@Valid Address>`), so the graph-walking
   machinery must exist first.
   **M3.5 (TCK catch-up)** in between: M4 builds on M1–M3, and the TCK showed those three
   were closed with 231 of their 345 TCK tests failing.
4. **M4 (container elements)** before **M5 (executable validation)**: method/constructor
   validation can itself take container-typed parameters; build the extractor SPI first.
5. **M5 (executable validation + codegen)** is deliberately paired: introducing
   `erasmus-codegen-apt` once the constraint model (M1–M4) is stable avoids generating code
   against a moving target, and the differential-testing safety net (Layer 4) only makes
   sense once there is a non-trivial reflective baseline to compare against.
6. **M6 (metadata API)** is comparatively low-risk introspection over an already-built
   model — placed after the model stabilizes, before CDI integration needs it.
7. **M7 (CDI)** before **M9 (Jakarta REST)**: `@ValidateOnExecution` and CDI-managed
   `ConstraintValidator`s are a platform integration point that Cassini's resource-method
   validation will end up depending on conceptually (even if not directly on the module).
8. **The TCK is not a milestone at the end any more**: it is the acceptance test of every
   milestone, run on every commit as a ratchet. M8 only keeps what no milestone owns — the
   signature test, the container subset, the last gaps — and comes after M7 because the
   container subset needs it.
9. **M9 (Jakarta REST)** and **M10 (XML mapping)** are explicitly lower priority: neither
   blocks the TCK's annotation-driven core, and both are additive once the engine is solid.
10. **M11 (ecosystem integration)** last, same rationale as every other sub-project: don't
    pollute `vidocq` before Erasmus is TCK-green.

## Known risks

| Risk | Mitigation |
|---|---|
| A listed TCK test that changes its reason for failing goes unnoticed (the ratchet tracks pass/fail, not why) | When a milestone closes, review what is still listed in its slice: every remaining entry must fail only on a later milestone's `UnsupportedOperationException`, or be in `TCK.md`. |
| Homegrown EL-subset interpolator diverging from the spec's required grammar | Build a dedicated grammar conformance test suite in M2, expand it as TCK message-interpolation tests surface gaps. |
| Parameter name resolution without `-parameters` on third-party-compiled classes | `ParameterNameProvider` SPI as designed by the spec — document the default behavior clearly; do not silently guess names. |
| Circular object graphs causing infinite cascading recursion | Visited-(bean identity, group) tracking per top-level `validate()` call, covered by dedicated cycle tests from M3. |
| `erasmus-codegen-apt` / reflective-path divergence | Layer 4 differential testing from M5 onward, on every commit touching either path. |
| `ResourceBundle` message-bundle loading across Java Module boundaries | Document any unavoidable `opens` explicitly; prefer bundles co-located in `erasmus-core`'s own module first. |
| Constraint validator instantiation strategy differs standalone vs. CDI-managed | Design the dual instantiation path (public no-arg constructor vs. CDI-managed) explicitly in M1, revisit in M7 — do not bolt CDI awareness onto `erasmus-core`. |

## Decided decisions

- ✅ **Zero third-party dependency**: no Hibernate Validator, no Apache BVal, no third-party
  EL engine. Only `jakarta.validation-api` at compile scope in `erasmus-core`.
- ✅ **`erasmus-core` depends on neither CDI nor Jakarta REST** — same "fundamental
  separation" pattern as Cervantes' `cervantes-core` / Knock's `knock-core`.
- ✅ **Static code generation via APT** (`erasmus-codegen-apt`) is a first-class design
  goal, not an afterthought — aligned with the workspace-wide "maximum static code
  generation" philosophy, with the reflective builder kept as the fallback path.
- ✅ **Strict TDD** on all production modules, one Bean Validation 3.1 chapter at a time.
- ✅ **The TCK is a ratchet on every commit** (2026-10-09), not a milestone at the end:
  `tck-known-failures.txt` only shrinks, each milestone names its TCK slice, and a
  milestone is done when its slice is out of the list. Target: the list empty (or holding
  only `TCK.md` challenges) by M8.
- ✅ **TCK in-reactor behind the `tck` Maven profile** from the start — no historical
  out-of-reactor phase needed, since Erasmus starts life after the workspace's Maven
  3.9.16 / Model 4.0.0 migration that made the old ShrinkWrap constraint moot.
- ✅ **groupId `io.vidocq.erasmus`**; JUnit 6 for all tests; no Mockito — hand-written
  test doubles and fixture beans.
- ✅ **No `synchronized`, no `ThreadLocal`** — validation context passed explicitly through
  call parameters, metadata caches on `ConcurrentHashMap`/`ClassValue`.

## Open decisions

- [ ] XML mapping (M10) — confirm real-world demand before investing; candidate to demote
      to "documented as unsupported" if no consumer in the ecosystem needs it.
- [ ] Should `erasmus-jaxrs` register its `ExceptionMapper` globally by default, or
      require an explicit opt-in dependency/activation? → Decide during M9 in light of how
      Knock's Cassini wiring handled default activation.
- [ ] Locale/i18n strategy for message resolution: integrate with Ravel (MicroProfile
      Config) for a default locale override, or keep it purely `Locale.getDefault()` +
      explicit API parameter? → Revisit in M2.
- [ ] Bean Validation 3.2 / next major (whenever released) — keep the milestone boundaries
      (constraints / graph / groups / containers / executable / metadata) stable enough
      that a version bump does not force a deep re-architecture.
