# M2: the full built-in constraint set, message interpolation, and custom constraints

*Part 2 of the [Erasmus making-of series](../../MAKING-OF.md). Continues from
[part 1](01-kicking-off-erasmus.md), where M1 shipped a working `validator.validate(bean)`
for the first 6 built-in constraints.*

By the end of part 1: a real, working `Validator.validate(bean)` for the first 6 built-in
constraints (`@NotNull`, `@NotEmpty`, `@NotBlank`, `@Size`, `@Min`, `@Max`), 12 validator
classes, 35 tests, all green. The bootstrap machinery (`ServiceLoader` discovery,
`ConstraintValidatorResolver`, message interpolation with plain `{attribute}` substitution,
reflective constraint metadata) was in place — just narrow in scope: 15 more built-in
constraints to go, no locale variants, no EL expressions in messages, and no custom
constraint authoring path actually exercised end-to-end yet.

Then it was time to start M2. From `ROADMAP.md`:

> **Scope spec:** remaining built-in constraints, message interpolation with EL-subset
> support.
>
> **Deliverable:** all built-in constraints implemented and unit-tested for the narrowed
> target-type set above, locale-aware message interpolation (default + French bundles,
> EL-subset `${...}` expressions), and custom constraint authoring (composed constraints,
> `@ReportAsSingleViolation`, multi-target-type `validatedBy()`).

M2 landed in two passes: the built-in constraints first, then a second pass closing out
locale variants, the EL-subset grammar, and custom constraint authoring.

## Pass 1: the remaining 15 built-in constraints

M1 shipped 6 of Bean Validation 3.1's 21 built-in constraints. This pass closes that gap:
`@AssertTrue`/`@AssertFalse`, the whole
`@Positive`/`@PositiveOrZero`/`@Negative`/`@NegativeOrZero` family, `@DecimalMin`/`@DecimalMax`,
`@Digits`, `@Pattern`, `@Email`, and `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent`.

Where M1 was mostly about standing up the bootstrap machinery (the interesting part), this
pass is largely the same pattern from M1 — one `ConstraintValidator` per (constraint, target
type), registered in `BuiltinConstraints`, tested directly — repeated across a lot more
annotations. Genuinely mechanical in most places, but a few of the constraints forced real
decisions.

### `@Positive`/`@Negative`: the constraint where floating point is actually fine

M1's `@Min`/`@Max` explicitly reject `Float`/`Double` — a threshold comparison against a
float is lossy, so the spec excludes them. A *sign* check has no such problem, so
`@Positive`/`@Negative` support floating point just fine. The subtlety: comparing via
`longValue()` (like `@Min`/`@Max` do) silently breaks here, because `0.5`'s `longValue()`
truncates to `0` — which would wrongly read as "not positive." The fix is comparing the
primitive `double` directly:

```java
static boolean isPositive(Number value) {
    if (value instanceof BigDecimal bd) return bd.signum() > 0;
    if (value instanceof BigInteger bi) return bi.signum() > 0;
    if (value instanceof Float || value instanceof Double) return value.doubleValue() > 0;
    return value.longValue() > 0;
}
```

A nice side effect of using the plain `>`/`>=`/`<`/`<=` operators instead of building a
signed `-1`/`0`/`1` result: `NaN > 0`, `NaN >= 0`, `NaN < 0`, and `NaN <= 0` are all `false`
in Java, which happens to already match what the spec wants — `NaN` is neither positive nor
negative. No special-casing needed, just not routing through a comparison abstraction that
would have needed one.

### `@Past`/`@Future`: borrowing "now" from `ClockProvider`

This is where `ClockProvider` — one of the bootstrap components from M1 that had no real
job yet — finally earns its keep. Each validator asks the `ConstraintValidatorContext` for
a `Clock` and derives "now" from it, rather than calling `Instant.now()`/`LocalDate.now()`
directly:

```java
public final class PastValidatorForInstant implements ConstraintValidator<Past, Instant> {
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        return value == null
                || TemporalComparisons.isPast(value, Instant.now(context.getClockProvider().getClock()));
    }
}
```

That's what makes these testable without sleeping or mocking `System.currentTimeMillis()`:
the test hands the validator a `ConstraintValidatorContext` backed by a `Clock.fixed(...)`,
from the actual test file,
`erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/constraints/PastFutureValidatorTest.java`:

```java
private static final Instant NOW_INSTANT = Instant.parse("2026-06-15T12:00:00Z");
private static final ConstraintValidatorContext FIXED_NOW = fixedClockContext(NOW_INSTANT);

private static ConstraintValidatorContext fixedClockContext(Instant instant) {
    Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
    ClockProvider clockProvider = () -> clock;
    return new ConstraintValidatorContext() {
        @Override
        public ClockProvider getClockProvider() {
            return clockProvider;
        }
        // every other method throws UnsupportedOperationException — unused by these validators
    };
}

@Test
void instant() {
    Instant past = NOW_INSTANT.minusSeconds(60);
    Instant future = NOW_INSTANT.plusSeconds(60);

    assertTrue(new PastValidatorForInstant().isValid(past, FIXED_NOW));
    assertFalse(new PastValidatorForInstant().isValid(future, FIXED_NOW));
    assertFalse(new PastValidatorForInstant().isValid(NOW_INSTANT, FIXED_NOW), "exactly now is not strictly past");
    // ... PastOrPresent / Future / FutureOrPresent against the same fixed clock, plus null-is-valid
}
```

Proof it actually runs, not just compiles:

```bash
cd erasmus-core
../mvnw -ntp test -Dtest=PastFutureValidatorTest
```

```
[INFO] --- surefire:3.5.5:test (default-test) @ erasmus-core ---
[INFO] Running io.vidocq.erasmus.core.internal.constraints.PastFutureValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.081 s -- in io.vidocq.erasmus.core.internal.constraints.PastFutureValidatorTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

Deliberately scoped down, though: this covers `Instant`, `LocalDate`, and `java.util.Date`
only. The spec's full temporal type list — `LocalDateTime`, `LocalTime`, `ZonedDateTime`,
`OffsetDateTime`, `OffsetTime`, `Year`, `YearMonth`, `MonthDay`, `Calendar`, plus the
non-ISO chronology dates (`HijrahDate`, `JapaneseDate`, `MinguoDate`, `ThaiBuddhistDate`) —
is a documented gap, same "narrow it, write down what's missing" move as M1's
`Object[]`-only array constraints. Each of those types needs its own validator class (no
single generic "get me now" factory exists across all of them), so it's a lot of near-identical
boilerplate for comparatively little marginal value right now.

### Another naming-collision gotcha, this time in the tests

Writing the `@AssertTrue`/`@AssertFalse` tests, the obvious method name was `assertTrue()`.
That broke everything in the file — every call to the statically-imported
`Assertions.assertTrue(...)` inside that test class started failing to compile, with the
compiler insisting a zero-argument method wasn't "applicable" for a call passing two
arguments. Turns out: the moment a class declares its *own* method with a given simple name,
Java hides *every* statically-imported overload of that name for the rest of that class —
regardless of how different the argument lists are. Renamed the test methods to
`assertTrueConstraint()`/`assertFalseConstraint()` and the problem disappeared. Small, but
the kind of thing that's genuinely confusing the first time the compiler error doesn't
mention the real cause at all.

### The rest of the manifest

- `NumberSignSupport` — the shared sign-comparison helper above, used by all four
  `@Positive`/`@Negative`-family validators.
- `DecimalBoundSupport` + `DecimalMinValidatorFor{Number,CharSequence}` /
  `DecimalMaxValidatorFor{Number,CharSequence}` — `@DecimalMin`/`@DecimalMax` target
  `CharSequence` too (parsing the string as a `BigDecimal`), which plain `@Min`/`@Max`
  don't — a real difference between the two, not an oversight.
- `DigitsSupport` + `DigitsValidatorFor{Number,CharSequence}` — checks integer/fraction
  digit counts via `BigDecimal.stripTrailingZeros()`.
- `PatternValidator` — whole-string match (`Matcher.matches()`, not `.find()`), with
  `Pattern.Flag[]` OR'd into the compiled `java.util.regex.Pattern`'s flags.
- `EmailValidator` — a pragmatic, deliberately-not-RFC-5322 shape check. Full RFC 5322
  (quoted local parts, comments, the works) is famously impractical to validate with a
  regex and rarely what anyone actually wants enforced; documented as a known limitation
  rather than pretended away.
- `TemporalComparisons` + 12 `{Past,PastOrPresent,Future,FutureOrPresent}ValidatorFor{Instant,LocalDate,Date}`
  classes.
- A real bug fix: `ConstraintDescriptorImpl.getPayload()` was hardcoded to return an empty
  set from M1 onward — nobody had populated `@interface`s with a non-default `payload()`
  yet to notice. Now reads the annotation's actual `payload()` attribute.

26 new validator classes, 58 tests total (up from 35), all green. Still open at that point:
locale variants of the message bundle, the homegrown EL-subset grammar for message
interpolation (not yet needed — every constraint added so far is covered by the plain
`{attribute}` substitution from M1), and custom constraint authoring end-to-end.

## Pass 2: locale bundles, a homegrown EL subset, and custom constraint authoring

Three items sat unfinished after pass 1 merged — the kind of thing that's easy to leave
"for later" until "later" quietly becomes "never." Closed all three here, on a fresh branch
off `main`, before building anything further on top.

### Locale variants

The least interesting of the three, in the best possible way: the default-bundle mechanism
from M1 was already standard `ResourceBundle` lookup, so a `ValidationMessages_fr.properties`
sitting next to the existing `ValidationMessages.properties` is all it takes — the JDK's own
locale-fallback rules do the rest. No new lookup code, just a translated properties file.

The test that actually proves the fallback chain behaves correctly — including for a locale
with no bundle at all, where the JDK must fall back to the default rather than error out —
from `LocaleMessageInterpolatorTest`:

```java
@Test
void frenchLocale_resolvesTranslatedMessage() throws NoSuchFieldException {
    ErasmusMessageInterpolator interpolator = new ErasmusMessageInterpolator();
    var descriptor = new ConstraintDescriptorImpl<>(annotationOf("field", NotNull.class));

    String message = interpolator.interpolate(
            descriptor.getMessageTemplate(),
            new MessageInterpolatorContextImpl(descriptor, null),
            Locale.FRENCH);

    assertEquals("ne doit pas être nul", message);
}

@Test
void unsupportedLocale_fallsBackToDefaultBundle() throws NoSuchFieldException {
    // ... same setup, Locale.JAPANESE instead
    assertEquals("must not be null", message, "no _ja bundle exists, so English is the correct fallback");
}
```

### The EL subset

The one Bean Validation actually requires: message templates like
`"must be between {min} and {max}"` only need `{attribute}` substitution, but the spec also
allows `${expression}` — real Unified EL, including things like
`"{max} character${max > 1 ? 's' : ''}"` for pluralization. Pulling in a real EL engine was
never on the table (zero third-party dependencies is non-negotiable here), so
`MinimalElExpression` is a small hand-written recursive-descent parser covering exactly the
subset that shows up in constraint messages in practice — ternary, `||`/`&&`, comparisons,
basic arithmetic, literals, and variable lookup — and explicitly nothing else: no method
calls, no `.`/`[]` navigation, no user-defined functions.

`ErasmusMessageInterpolator.interpolate` now runs three steps in sequence: resolve the bundle
key, substitute `{attribute}` placeholders, then evaluate any remaining `${...}` expressions
against the constraint's attributes plus `validatedValue`. From `MinimalElExpressionTest`,
the exact real-world case this exists for:

```java
@Test
void ternary_pluralizationStyleExpression() {
    assertEquals("s", MinimalElExpression.evaluate("max > 1 ? 's' : ''", Map.of("max", 5L)));
    assertEquals("", MinimalElExpression.evaluate("max > 1 ? 's' : ''", Map.of("max", 1L)));
}
```

### Custom constraint authoring

The trickiest of the three, because it's not a new validator — it's a change to how
constraints get *resolved and combined*. Two mechanisms needed support: composed constraints
(an `@interface` meta-annotated with other constraint annotations, so one custom annotation
triggers several checks) and `@ReportAsSingleViolation` (collapse all of those into one
violation instead of one per failing part). A composed constraint with no validator of its
own — pure delegation — used to hit the "no `ConstraintValidator` registered" exception; now
it's recognized and resolved to its composing constraints instead. From
`CustomConstraintAuthoringTest`:

```java
@NotBlank
@Size(min = 8, max = 100)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
public @interface StrongPassword {
    String message() default "password does not meet strength requirements";
}
```

`ErasmusValidator.evaluateConstraint` walks this recursively — a composing constraint that is
itself composed just works, no special-casing needed — collecting every failing part, then
collapsing to a single violation carrying the composed constraint's own message when
`@ReportAsSingleViolation` is present:

```java
@Test
void reportAsSingleViolation_collapsesEveryFailingPartIntoOne() {
    // blank AND too short: both composing constraints fail, but only ONE violation should surface
    Set<ConstraintViolation<Credentials>> violations = validator.validate(new Credentials(""));

    assertEquals(1, violations.size());
    assertEquals("password does not meet strength requirements", violations.iterator().next().getMessage());
}
```

Proof it actually runs:

```bash
cd erasmus-core
../mvnw -ntp test -Dtest=CustomConstraintAuthoringTest
```

```
[INFO] --- surefire:3.5.5:test (default-test) @ erasmus-core ---
[INFO] Running io.vidocq.erasmus.core.internal.CustomConstraintAuthoringTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.154 s -- in io.vidocq.erasmus.core.internal.CustomConstraintAuthoringTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

The same test class also exercises the case that had never actually been proven end-to-end
before: a fully custom `@Constraint(validatedBy = ...)` with its own validator — including one
with *two* validators declared for two different target types (`Collection` and `Map`),
resolved through the exact same `ConstraintValidatorResolver` the built-ins use. That last one
matters: up to this point, every test had gone through `BuiltinConstraints`, so the
`validatedBy()`-direct path was implemented but never actually exercised end-to-end.

7 new tests for custom constraint authoring, 3 for locale, 8 for the EL evaluator — 76 tests
total, all green. M2 is done.

## Where it stands now

- A real, working `Validator.validate(bean)` for **all 21 built-in constraints** (M1's 6
  plus M2's remaining 15), spanning 38 validator classes across their various target types
  — though the `@Past`/`@Future` family is still narrowed to 3 temporal types, see above.
- Locale-aware message interpolation (default + French bundles, standard `ResourceBundle`
  fallback) and a homegrown minimal EL-subset evaluator for `${...}` expressions in message
  templates.
- Custom constraint authoring: composed constraints, `@ReportAsSingleViolation`, and
  multiple `ConstraintValidator`s per custom annotation, resolved the same way as built-ins.
- 76 tests, all green, including every gotcha above locked in as a regression test.
- A clean, reproducible build: `./mvnw install` and `./mvnw -Ptck install` both succeed.
- M2 is fully done now — `ROADMAP.md` no longer marks it partial.
- No cascading (`@Valid`), no groups, no container-element constraints
  (`List<@NotBlank String>`), no method/constructor validation, and — the big one — no TCK
  integration yet. All scoped as later milestones in `ROADMAP.md`.

## What's next

The rest of the roadmap, roughly in order: object-graph cascading (`@Valid`), groups and
group sequences, container-element constraints, method/constructor validation (paired with a
compile-time code generator so the reflection in `erasmus-core` becomes optional for
annotated beans), the constraint-metadata introspection API, CDI integration, and — treated
as its own first-class track rather than an afterthought — getting the official TCK green.

Plus one loose end from the tooling detour at the very start of part 1: actually writing up
the "optional per-contributor tooling" section that whole `context-mode`/`rtk` saga was
arguing for — the GPG/CLA/`tea` side of things is already sorted, but that one's still just a
takeaway in this series, not a real section in `CONTRIBUTING.md` yet.
