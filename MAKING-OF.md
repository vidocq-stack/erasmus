# Building Erasmus: a Jakarta Bean Validation implementation, one prompt at a time

*A first-person account of how this project got bootstrapped — the tooling, the false
starts, and the early implementation work — kept here for new contributors, or anyone
curious how it came together. Written in a blog-ish style rather than as formal docs;
may still turn into an actual blog post at some point.*

## The context

The Vidocq project is a from-scratch reimplementation of the Jakarta EE and MicroProfile
specifications — the specs that power most "enterprise Java" frameworks. Instead of using
Hibernate, RESTEasy, Weld, etc., each spec gets its own clean-room implementation:

- `chappe` — the HTTP server
- `vauban` — the dependency injection container (CDI)
- `champollion` — JSON parsing/binding (JSON-P/JSON-B)
- `cassini` — REST endpoints (JAX-RS)
- ...and about a dozen more, one per spec.

The rules are strict and the same everywhere: **zero third-party dependencies** (only the
official spec API is allowed, never an existing implementation), **strict Java Modules**
(every module has a real `module-info.java`, nothing leaks onto the classpath), and
**TDD** — you write the failing test before the code that makes it pass.

Worth stating plainly, since it's not the kind of thing you'd guess from the code alone:
**Vidocq is an AI-generated project.** Every sub-project, this one included, gets built
through the same loop — prompting an LLM (Claude Code, in my case) and reviewing what comes
back — rather than hand-written line by line. The idea for the project in the first place
came from what Jakarta EE and MicroProfile actually hand you for each spec: not just a
document describing intended behavior, but an official **TCK** alongside it — a
conformance test suite published by the same standards body (more on what that actually
means below). That pairing is exactly what implementing something with an AI needs: the
spec to build against, and the TCK to check the result against, independently of my own
read of the code or the AI's own say-so. Multiply that by a dozen-plus specs, each
individually scoped, and the whole ecosystem reads like a long, structured checklist —
build against the spec, validate against the TCK, spec by spec.

My setup: Windows, WSL2 with Ubuntu, and [Claude Code](https://claude.com/product/claude-code)
through its VS Code extension. Before I got anywhere near writing Java, there were a few
detours worth recording — mostly because they're the kind of thing nobody mentions until
you hit them yourself.

## A quick primer: what's a TCK, anyway?

I'd used Jakarta EE / Spring-ish annotations for years without ever needing to know this, so
in case you're in the same boat: a **TCK (Technology Compatibility Kit)** is the official
test suite that ships alongside a Jakarta EE or MicroProfile spec — published by the same
standards body that publishes the spec document itself, not by any one implementation.

The distinction that matters: when you write `@NotNull` on a field and it works with
Hibernate Validator, that only proves *Hibernate Validator's interpretation* of the spec
behaves the way you expected. The spec document is prose — English (well, legalese-flavored
English) describing intended behavior — and prose has edge cases nobody notices until two
independent teams implement it slightly differently. Does an empty `Optional` cascade or
not? What happens when a `@Size` constraint gets a negative `min`? What's the exact JSON
shape of a validation error? A TCK is thousands of concrete, executable test cases that pin
down those edge cases precisely, so that "implements Jakarta Bean Validation 3.1" means the
same thing regardless of who wrote the implementation.

Concretely, a TCK is usually two things bundled together:

- A **signature test** — checks that your public API's exact shape (classes, methods,
  method signatures) matches the spec byte-for-byte. Adding one convenience overload the
  spec doesn't declare is enough to fail it.
- The **functional test suite** — the actual behavioral cases, often run through a real
  container (Arquillian is the common harness across this ecosystem) deploying test
  artifacts and asserting on the results.

Why it matters enough to get its own milestone here: this is the difference between "I
wrote something that looks like it validates beans" and "this is a conformant Jakarta Bean
Validation 3.1 implementation, provably, against the same yardstick every other
implementation is measured with." Passing 100% of it isn't a nice-to-have checkbox at the
end — it's the actual definition of "done" for a spec implementation, which is why the
roadmap treats it as a signal to watch from the very first milestone rather than a chore
saved for last (more on that below).

## First, a detour: breaking my own Claude Code setup

Before starting on Erasmus, I'd tried installing two community add-ons for Claude Code:
[context-mode](https://github.com/mksglu/context-mode) and
[rtk](https://github.com/rtk-ai/rtk), both promising to cut down token usage.

That's what broke my VS Code extension's *login*. Signing in would just hang, then
eventually fail with:

```
Subprocess initialization did not complete within 60000ms.
```

I ruled out the obvious suspects first — network, shell startup, WSL interop — all fine.
Turned out to be a `SessionStart` hook installed by the `context-mode` plugin, quietly
blocking the extension's subprocess initialization. A known-ish failure mode, apparently:
custom Claude Code hooks/plugins that run at session start can interfere with how the
VS Code extension bootstraps itself, and that seems to bite harder on a less-common setup
like VS Code-on-WSL. Disabling `context-mode` (and an unrelated `rtk` hook) in
`~/.claude/settings.json` fixed the login immediately.

Takeaway worth carrying into the actual project: **third-party Claude Code plugins/hooks
are genuinely useful, but they add real failure surface.** For Vidocq, that argues for
treating this kind of tooling as **optional, per-contributor**, rather than baked into the
shared project setup — with the trade-offs (what it's useful for, what can break, on which
setups) written down for whoever wants to opt in. That's a contributor-guide addition in its
own right, separate from anything Erasmus-specific.

## Before any code: GPG and the governance repo

Every Vidocq contribution needs three things, per
[`Vidocq/governance`](https://codeberg.org/Vidocq/governance)'s `CONTRIBUTING.md`: a signed
CLA, GPG-signed commits, and DCO sign-off. Worth getting all three sorted before touching
any code, so I cloned `governance` before anything else and followed it step by step.

I already had a GPG key lying around from 2014 (RSA 2048, no expiry — old, but still
accepted everywhere; nothing in Vidocq's rules demands a fresh 4096-bit one), with its UID
already matching my git `user.email`, so no new key generation needed. `CONTRIBUTING.md`
itself links out to [Codeberg's own GPG-signing guide](https://docs.codeberg.org/security/gpg-key/)
for the actual key/Git setup, so I followed that for the config steps, plus the few
Vidocq-specific ones the doc calls out on top: signing tags too (`tag.gpgsign`), the
`Signed-off-by` automation (see the shared hooks below), and exporting the key for the CLA
signature.

```bash
git config --global user.signingkey <MY-KEY-ID>
git config --global commit.gpgsign true
git config --global tag.gpgsign true
```

Then the CLA itself, exactly as `CONTRIBUTING.md` describes it: its own dedicated pull
request, separate from any code contribution — a row in `CLA-signatures/individuals.md`,
the exported public key as `.forgejo/keys/<username>.asc`, and a PR description containing
the literal sentence "I have read and agree to the Vidocq Contributor License Agreement
v1.0." Opened that PR first, got it merged, then moved on.

One more piece `CONTRIBUTING.md` calls for: opening pull requests. Codeberg runs on
Forgejo, not GitHub, so the `gh` CLI is useless here — the actual tool is
[`tea`](https://gitea.com/gitea/tea), the official Gitea/Forgejo CLI (worth a note: `apt
install tea` gets you an unrelated GUI text editor of the same name; the real one only
ships as a Go module or a signed release binary). Installed it, generated a Codeberg
access token scoped to `repository` (read/write, for PR creation), `issue` (read/write),
and `user` (read — `tea login add` calls the "current user" endpoint to verify the token,
an easy scope to forget since it has nothing to do with what you're actually trying to
do), logged in, and used it for the rest of the contribution workflow.

None of this is mandatory — everything above works fine from the web UI too. The reason it
was worth the ten minutes: it means Claude (or whatever LLM you're pairing with) can view,
create, and edit pull requests directly from the terminal alongside you, instead of you
having to relay diffs and PR descriptions back and forth by hand through a browser tab it
can't see into.

## Cloning the workspace with mani

With the governance side sorted, I cloned the actual workspace following
[`Vidocq/vidocq-workspace`](https://codeberg.org/Vidocq/vidocq-workspace)'s own
instructions: `mani` clones each sub-project (`vauban`, `chappe`, `champollion`, ...) into
its own directory, each one its own independent git repo. First thing after cloning:

```bash
mani run -a install-hooks
```

This points every repo's `core.hooksPath` at one shared `.githooks/` directory instead of
each repo's own local hooks — two scripts in there that auto-append the DCO
`Signed-off-by` trailer and hard-block any commit that somehow ends up without one anyway
(a bad `--amend`, a cherry-pick, an IDE-driven commit). One command, every repo covered, no
per-repo `git config` to repeat by hand.

## Prompt 1: kicking things off

First actual prompt to Claude Code, inside the freshly cloned workspace:

> i want to start a new jakarta ee implementation from bean validation specs:
> https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1
> it should be named erasmus. create an agent.md file for this project (take inspiration to
> vauban/main/AGENTS.md). it should sit next to the other project of this repo (next to
> champollion, chappe, vauban, etc ...) Also create a roadmap file containing the plan for
> implementing and supporting the bean validation tck.

No code yet — just planning. Claude read `vauban/main/AGENTS.md` as the template, looked at
how sibling projects like `knock` and `cervantes` structure their own `ROADMAP.md`, and
produced two files: `erasmus/main/AGENTS.md` (basically identical to Vauban's — mostly
documentation conventions shared workspace-wide) and a much longer `erasmus/main/ROADMAP.md`:
design principles, a recap of the actual Bean Validation 3.1 spec chapters, milestones `M0`
through `M11`, and a dedicated section on the official TCK, since that was explicitly what
I'd asked for.

What I found genuinely useful, though, was what came *after* the files — Claude flagged a
few things back to me instead of just declaring the plan done:

- **The TCK track isn't just "the last milestone."** `M8` is where TCK conformance becomes
  a hard 100%-PASS gate, but the plan calls for running it — even red, even partially —
  from `M1` onward, as a continuous signal rather than something bolted on at the end.
  Fair distinction, and one I wouldn't have thought to ask for explicitly.
- **Two open unknowns flagged as risk, not silently assumed away**: the exact Maven Central
  coordinates for the official Bean Validation TCK aren't confirmed, and neither is whether
  it needs a full Arquillian container the way the Servlet/REST/CDI TCKs in this ecosystem
  do — Bean Validation is largely usable in plain Java SE, so it might not. Both are marked
  as needing a real spike against the actual TCK distribution before committing to a harness
  design, instead of guessing and baking the guess into the roadmap.
- **A module pair I hadn't asked for**: `erasmus-codegen-apt` / `erasmus-codegen-maven-plugin`,
  mirroring how `champollion` does static code generation. Not something my prompt mentioned —
  added because the workspace's stated philosophy favors compile-time codegen over runtime
  reflection wherever possible, and Claude connected that dot on its own rather than only
  doing the literal minimum I'd typed.

That's the kind of thing I want more of from a planning pass: not just "here are the files,"
but an explicit account of what's genuinely uncertain versus what's a design choice made on
my behalf and why.

No `pom.xml`, no Java yet, on purpose. Then, next prompt:

> ok start the implementation following the roadmap

— which is where the actual engine work below happened.

## M0: the boring-but-necessary part

M0 is just scaffolding: a Maven reactor with one module per concern —

- `erasmus-api` — a thin module that just depends on the official `jakarta.validation-api`
  jar (which, happily, already ships as a proper Java module — no repackaging needed, unlike
  some other specs in this ecosystem where the upstream jar predates Java Modules).
- `erasmus-core` — where the actual engine lives.
- A handful of placeholder modules for things not built yet: CDI integration, REST
  integration, static code generation, benchmarks, examples, and the TCK runner.

Nothing clever here — just making sure `mvn install` produces a green, empty-but-buildable
reactor before writing any real logic. Boring, but skipping it always costs more later.

### Wait, why are half these modules just... empty?

Good question, and one I asked myself looking at the module list — `erasmus-bench`,
`erasmus-examples`, `erasmus-cdi-vauban`, `erasmus-jaxrs`, `erasmus-codegen-apt` all exist
right now as basically a `pom.xml` and, in most cases, a `module-info.java` with nothing in
it besides a `requires`. No behavior, nothing you could call. Here's why they're there
already instead of getting created whenever their milestone actually starts:

- **They mirror a layout every other Vidocq sub-project already uses.** `champollion` (JSON)
  and `cervantes` (JWT) both split "the core engine" from "the CDI adapter," "the REST
  adapter," "the benchmarks," and "the examples" into their own modules from day one, mostly
  so each piece can declare *only* the dependencies it actually needs — `erasmus-core` itself
  will never depend on CDI or Jakarta REST, only whatever eventually sits in
  `erasmus-cdi-vauban` / `erasmus-jaxrs` does. Setting the module boundaries up front, even
  empty, means later milestones slot into an already-decided shape instead of triggering a
  restructuring.
- **`erasmus-tck` being reserved (and gated) from the start matters for how the project
  gets released.** It's wrapped in a Maven `tck` profile, so a plain `./mvnw install` never
  builds or downloads anything TCK-related — you only get it via `./mvnw -Ptck ...`. Given
  the TCK's own distribution shape is still an open question (see the primer above), reserving
  the slot now means that question can get answered later without touching the reactor's
  overall structure.
- **`erasmus-api` is the one placeholder that's a placeholder for a different reason** — not
  "nothing built yet," but "there's genuinely nothing to build." Its only job is to depend on
  the official `jakarta.validation-api` jar so every other module depends on `erasmus-api`
  instead of the spec artifact directly. Since that jar is already a proper Java module, there's
  no repackaging work for Erasmus to do here (unlike, elsewhere in this ecosystem, specs whose
  upstream jar predates Java Modules and needs an Erasmus-side fork just to get a
  `module-info`).
- **One placeholder wasn't actually allowed to stay fully empty**: `erasmus-codegen-maven-plugin`
  is packaged as `maven-plugin`, and Maven's own `maven-plugin-plugin` refuses to build a
  plugin module with zero `@Mojo`-annotated classes — it fails the build with "No mojo
  definitions were found for plugin." So that one module got a tiny no-op `GenerateMojo`
  purely so the reactor builds; its real logic (scanning a host project's classpath and
  generating constraint-dispatch code for classes that can't be annotated directly) is still
  entirely unwritten, scoped for the same milestone as `erasmus-codegen-apt`.

None of these will stay empty — `erasmus-cdi-vauban` gets real CDI wiring, `erasmus-jaxrs`
gets a `ConstraintViolationException` → HTTP 400 mapper, `erasmus-codegen-apt` gets an actual
annotation processor, and so on, each at its own milestone in `ROADMAP.md`. Right now they're
just correctly-shaped boxes waiting for their contents.

## M1: the actual engine — and where it got interesting

M1's goal: make `validator.validate(person)` really work, for a first batch of six built-in
constraints (`@NotNull`, `@NotEmpty`, `@NotBlank`, `@Size`, `@Min`, `@Max`):

```java
public class Person {
    @NotNull
    @Size(min = 2, max = 20)
    private String name;

    @Min(0) @Max(150)
    private int age;
}
```

The bootstrapping side of Bean Validation is a small SPI dance: your implementation
registers itself as a `jakarta.validation.spi.ValidationProvider` via Java's standard
`ServiceLoader` mechanism, and `Validation.buildDefaultValidatorFactory()` finds it
automatically. Once that's wired up, the actual engine is "just" reflection: look at a
bean's annotated fields, find a validator for each constraint, run it, collect violations.

Except it wasn't "just" that. Three things went sideways in genuinely instructive ways.

### Gotcha #1: the annotations don't say who validates them

My first instinct was: "`@Size` is meta-annotated with `@Constraint(validatedBy = SomeClass.class)`,
so just read that annotation off the constraint and instantiate whatever it points to."

Except when actually inspecting the compiled `jakarta.validation-api` jar, `@Size` (and
`@NotNull`, and every other built-in constraint) declares:

```java
@Constraint(validatedBy = {})   // <- empty array!
```

The spec's own annotations don't know about any implementation's validator classes — because
the spec module can't depend on any specific implementation. That mechanism
(`validatedBy = SomeValidatorClass.class`) is only meant for *your own* custom constraints,
where you control both the annotation and the validator.

So every compliant implementation has to ship its own internal lookup table mapping
"built-in constraint → its default validator classes." That became `BuiltinConstraints`, a
small registry that `ConstraintDescriptorImpl` falls back to whenever `validatedBy()` comes
back empty. Not something you'd guess from the Javadoc alone — only from actually
decompiling the jar and going "...huh, that's empty."

### Gotcha #2: a `.properties` file broke the module system at *runtime*, not compile time

Bean Validation lets you override the default error messages via a resource bundle named
`jakarta.validation.ValidationMessages`. Natural first move: ship Erasmus's own default
messages at exactly that path, `src/main/resources/jakarta/validation/ValidationMessages.properties`.

It compiled fine. It built fine. Then the test suite failed with:

```
java.lang.module.ResolutionException: Module io.vidocq.erasmus.core contains package
jakarta.validation, module jakarta.validation exports package jakarta.validation to
io.vidocq.erasmus.core
```

...at JVM *boot*, before a single test even ran. Took a moment to realize what happened: the
module already `requires` the real `jakarta.validation` module, which owns and exports the
Java package `jakarta.validation`. Under the Java Module System, a *resource* file counts
toward "this module contains this package" just as much as a `.class` file does — even a
`.properties` file with zero code in it. Two modules can't both claim the same package when
one reads the other's export of it. That's a "split package," and the module system refuses
to resolve it.

The fix: move Erasmus's own default messages to its *own* namespace
(`io/vidocq/erasmus/core/internal/ValidationMessages.properties`), and have the interpolator
look for the spec's official override bundle name first, falling back to Erasmus's own bundle
only if that one isn't found. Same user-facing behavior, no module conflict.

This is the kind of bug that's invisible in a tutorial project (nobody puts three JSON files
in a toy repo) but shows up immediately the moment you're building something that has to
`require` the exact module whose namespace you're tempted to reuse.

### Gotcha #3 (the "found by testing" kind): getters aren't always callable

Bean Validation supports putting constraints on a getter instead of a field. To test that
path: a private nested test class with a `public` getter annotated `@NotBlank` — and reading
it via reflection blew up with `IllegalAccessException`, even though the getter itself is
`public`.

Turns out: Java's reflection checks accessibility of the *declaring class*, not just the
member. A `public` method on a non-`public` class still isn't callable without
`setAccessible`. Obvious in hindsight, invisible until a test actually exercised a
non-public bean — which is exactly the point of writing the test first instead of assuming
the happy path.

### The actual manifest

Beyond the three war stories above, here's what M1 concretely dropped into `erasmus-core`
(all inside `io.vidocq.erasmus.core.internal` and its two sub-packages) — useful to have
written down once, since "what did the AI actually write" is usually the first question
people ask:

**Bootstrapping** — the SPI plumbing that makes `Validation.buildDefaultValidatorFactory()`
work at all: `ErasmusValidationProvider` (the `ServiceLoader`-discovered entry point),
`ErasmusConfiguration` + `ErasmusConfigurationState` (the builder-style setup API and the
immutable snapshot handed off once you call `buildValidatorFactory()`), `ErasmusValidatorFactory`
+ `ErasmusValidatorContext` (produce `Validator`s, let you override components per-validator),
and five small default-component classes for the pieces nobody overrides in the common case:
`ErasmusTraversableResolver`, `ErasmusClockProvider`, `ErasmusParameterNameProvider`,
`ErasmusConstraintValidatorFactory`, `ErasmusBootstrapConfiguration`.

**The validation engine itself** — `ErasmusValidator` (implements the actual
`validate`/`validateProperty`/`validateValue` methods you call as a user) and
`ConstraintValidatorResolver` (given a constraint's candidate validator classes and a target
type, picks the right one — the piece that had to learn about the null/declared-type
subtlety from gotcha territory above).

**Violations and message building** — `ConstraintViolationImpl`, `PathImpl` + `NodeImpl`
(property paths — just single-segment for now, no cascading yet),
`ErasmusConstraintValidatorContext` + `ConstraintViolationBuilderImpl` (lets a validator
report a custom message instead of the default one), `MessageInterpolatorContextImpl`, and
`ErasmusMessageInterpolator` + its `ValidationMessages.properties` bundle (the piece with the
split-package story above).

**Constraint metadata** (`.internal.metadata` sub-package) — `BeanMetadata` +
`PropertyMetadata` (what's constrained on a class, computed once and cached),
`ConstraintDescriptorImpl` (wraps one constraint annotation instance plus its resolved
validator classes), `ConstraintMetadataBuilder` (the reflective scanner that builds all of
the above from a `Class`), and the two `PropertyAccessor` implementations,
`FieldAccessor`/`MethodAccessor` (read a property via its getter if one exists, otherwise
the field directly).

**The built-in constraints** (`.internal.constraints` sub-package) — `BuiltinConstraints`
(the registry from gotcha #1) plus 12 validator classes: `NotNullValidator`; `NotBlankValidator`;
four `NotEmptyValidatorFor*` (CharSequence/Collection/Map/Array); four `SizeValidatorFor*`
(same four target types, sharing a small `SizeValidatorSupport` helper for the
min/max sanity check); `MinValidatorForNumber` and `MaxValidatorForNumber` (covering
`BigDecimal`/`BigInteger`/integral wrapper types, explicitly rejecting `Float`/`Double`).

**Tests** — 8 classes, 35 tests: `ErasmusBootstrapTest` (service discovery, `unwrap`,
`usingContext`), `ErasmusValidatorTest` (11 end-to-end tests against a hand-written `Person`
fixture — field-based and getter-based properties, `validate`/`validateProperty`/`validateValue`,
custom message interpolation), and one test class per constraint
(`NotNullValidatorTest`, `NotEmptyValidatorTest`, `NotBlankValidatorTest`, `SizeValidatorTest`,
`MinValidatorTest`, `MaxValidatorTest`) exercising every target-type validator directly.

**Docs** — `ROADMAP.md`'s `M0`/`M1` checkboxes filled in with what actually shipped (plus the
split-package gotcha written up right there, not just in this draft), and a new `CLAUDE.md`
for the project — the per-repo contributor guide every Vidocq sub-project carries, covering
the architecture, the constraints-not-to-violate list (including "don't resolve built-ins
through `validatedBy()`" and "don't ship a resource under a package this module already
reads"), and the TDD/TCK conventions.

### Proof: the actual test code

Manifests are one thing, but the part that actually convinced me `validator.validate(person)`
works is the test file itself —
`erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/ErasmusValidatorTest.java`.
It's built around one hand-written fixture class, annotated the same way you'd annotate a
real bean:

```java
private static final class Person {
    @NotNull
    @Size(min = 2, max = 20)
    private String name;

    @Min(0)
    @Max(150)
    private int age;

    @NotEmpty
    private List<String> nicknames;

    private String email;

    Person(String name, int age, List<String> nicknames, String email) {
        this.name = name;
        this.age = age;
        this.nicknames = nicknames;
        this.email = email;
    }

    @NotBlank
    public String getEmail() {
        return email;
    }
}
```

Note `email` is validated through its *getter*, not the field — deliberately, to exercise the
property-access path rather than only the field-access one.

Every test gets a fresh `Validator` from a fresh `ValidatorFactory` (no shared state between
tests):

```java
private Validator validator;

@BeforeEach
void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
}

private static Person validPerson() {
    return new Person("Erasmus", 42, List.of("Desiderius"), "erasmus@rotterdam.example");
}
```

The happy path — nothing broken, no violations:

```java
@Test
void validBean_hasNoViolations() {
    assertTrue(validator.validate(validPerson()).isEmpty());
}
```

And the case that actually exercises the most machinery at once — a `null` name, which must
trigger `@NotNull` but must *not* also trigger the `@Size` on the same field. That's a spec
rule I hadn't expected going in: every built-in validator except `@NotNull` itself must treat
`null` as trivially valid, so the two constraints compose cleanly instead of both firing at
once:

```java
@Test
void nullName_violatesNotNull() {
    Person person = validPerson();
    person.name = null;

    Set<ConstraintViolation<Person>> violations = validator.validate(person);

    assertEquals(1, violations.size());
    ConstraintViolation<Person> violation = violations.iterator().next();
    assertEquals("name", violation.getPropertyPath().toString());
    assertEquals("must not be null", violation.getMessage());
    assertEquals(person, violation.getRootBean());
}
```

That last assertion — checking `getRootBean()` — is a small but real one: it confirms the
violation actually points back at the exact `Person` instance that failed, not just at a
message string.

### Running just this one test

No need to run the whole suite to check this file — Maven's Surefire plugin can filter down
to a single test class with `-Dtest`:

```bash
cd erasmus-core
../mvnw -ntp test -Dtest=ErasmusValidatorTest
```

Output:

```
[INFO] --- surefire:3.5.5:test (default-test) @ erasmus-core ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running io.vidocq.erasmus.core.internal.ErasmusValidatorTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.534 s -- in io.vidocq.erasmus.core.internal.ErasmusValidatorTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

`-Dtest=ErasmusValidatorTest#nullName_violatesNotNull` narrows it down to one single method,
if you only care about that one case. (One gotcha while trying this: running `-Dtest=...`
from the *workspace root* with `-am` tries to run it against every reactor module, including
ones with zero tests, and Surefire fails loudly with "No tests matching pattern ... were
executed" for those. Simplest fix: `cd` into `erasmus-core` first, like above.)

## M2: the rest of the built-in constraints

M1 shipped 6 of Bean Validation 3.1's 21 built-in constraints. M2's goal — still in
progress — is closing that gap: `@AssertTrue`/`@AssertFalse`, the whole
`@Positive`/`@PositiveOrZero`/`@Negative`/`@NegativeOrZero` family, `@DecimalMin`/`@DecimalMax`,
`@Digits`, `@Pattern`, `@Email`, and `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent`.

Where M1 was mostly about standing up the bootstrap machinery (the interesting part),
M2 is largely the same pattern from M1 — one `ConstraintValidator` per (constraint, target
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
the test just hands the validator a `ConstraintValidatorContext` backed by a
`Clock.fixed(...)`.

```java
Clock clock = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
ClockProvider clockProvider = () -> clock;
ConstraintValidatorContext fixedNow = /* a small test double whose getClockProvider()
                                          returns clockProvider, everything else unused */;

assertTrue(new PastValidatorForInstant().isValid(NOW_INSTANT.minusSeconds(60), fixedNow));
assertFalse(new PastValidatorForInstant().isValid(NOW_INSTANT.plusSeconds(60), fixedNow));
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

26 new validator classes, 58 tests total (up from 35), all green. `ROADMAP.md` has the full
per-task status. Still open within M2: locale variants of the message bundle, the homegrown
EL-subset grammar for message interpolation (not yet needed — every constraint added so far
is covered by the plain `{attribute}` substitution from M1), and custom constraint
authoring end-to-end.

## Where it stands now

- A real, working `Validator.validate(bean)` for **all 21 built-in constraints** (M1's 6
  plus M2's remaining 15), spanning 38 validator classes across their various target types
  — though the `@Past`/`@Future` family is still narrowed to 3 temporal types, see below.
- 58 tests, all green, including every gotcha above locked in as a regression test.
- A clean, reproducible build: `./mvnw install` and `./mvnw -Ptck install` both succeed.
- Two PRs merged so far ([#1](https://codeberg.org/Vidocq/erasmus/pulls/1) for M0+M1) and
  open ([#2](https://codeberg.org/Vidocq/erasmus/pulls/2) for M2's built-in constraints).
- No cascading (`@Valid`), no groups, no container-element constraints
  (`List<@NotBlank String>`), no method/constructor validation, and — the big one — no TCK
  integration yet. All scoped as later milestones in `ROADMAP.md`.

## What's next

Finishing M2 first: locale variants for the message bundle, the EL-subset grammar, and a
worked custom-constraint-authoring example. Then the rest of the roadmap, roughly in order:
object-graph cascading, groups, container-element constraints, method/constructor
validation (paired with a compile-time code generator so the reflection in `erasmus-core`
becomes optional for annotated beans), the constraint-metadata introspection API, CDI
integration, and — treated as its own first-class track rather than an afterthought —
getting the official TCK green.

Plus one loose end from the tooling detour at the very start: actually writing up the
"optional per-contributor tooling" section that whole `context-mode`/`rtk` saga was
arguing for — the GPG/CLA/`tea` side of things is already sorted, but that one's still
just a takeaway in this draft, not a real section in `CONTRIBUTING.md` yet.

## Why I'm writing this down

Two of the three engine surprises (the empty `validatedBy()`, the split-package resource
file) aren't the kind of thing you'd find by reading a "getting started with Bean
Validation" guide — they only show up once you're the one *implementing* the spec instead
of consuming it. And the tooling detour at the very start is its own small lesson: half the
friction in a project like this happens before the first line of Java, in the gap between
"clone the repo" and "actually able to commit to it." Worth writing up properly once there's
more of the picture to show.
