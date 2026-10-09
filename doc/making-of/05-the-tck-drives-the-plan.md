# The TCK drives the plan: two issues, the official TCK on every commit, and a new M3.5

*Part 5 of the [Erasmus making-of series](../../MAKING-OF.md). Continues from
[part 4](04-cascading-and-groups.md), where M3 finished: `@Valid` cascading with cycle
detection, groups, group inheritance and `@GroupSequence`, composing with M2's composed
constraints — 88 tests, all green. That post's "What's next" said M4. This one explains why
it isn't.*

M3 was merged, and before starting M4 I asked Claude for three other things: the two issues
Yann had opened on Erasmus, and a change of plan for the TCK. In my words:

> next step will not be m4 but 3 things:
> - tackling the 2 issues
> - talking about changing the plan: I would like the tck to be run milestone by milestone,
>   commit by commit … almost in the tdd way. if it is possible, start doing the tck on the
>   existing.

The two issues, [#15](https://codefloe.com/Vidocq/erasmus/issues/15) and
[#16](https://codefloe.com/Vidocq/erasmus/issues/16), looked like housekeeping. They were
not: the second one turned out to be the reason the TCK could not have run at all.

How to read this post: like part 4, **each section below is one commit** of the branch,
titled the same way, so `git log --oneline` reads as this post's table of contents. Every
section opens with the files worth having open, and on the run that failed before the
commit; it closes on the same run, passing.

## Aligning on 0.4.0-SNAPSHOT

*Commit `chore: align on 0.4.0-SNAPSHOT`. Files to open: [`pom.xml`](../../pom.xml) (the
`<parent>` and `<version>` at the top), and any module's `pom.xml`, for instance
[`erasmus-core/pom.xml`](../../erasmus-core/pom.xml).*

**Goal.** Issue #15, from Yann:

> The other Vidocq repositories moved to 0.4.0-SNAPSHOT in September, and 0.4.0 is being
> prepared. erasmus is still entirely on the 0.3 line […]
>
> - `vidocq-parent` → `0.4.0-SNAPSHOT`, and erasmus's own version → `0.4.0-SNAPSHOT` […]
> - Any Vidocq dependency still pinned to `0.3.0-SNAPSHOT` (vauban, cassini, ravel, …) →
>   `0.4.0-SNAPSHOT`.
> - `clean verify` and the Jakarta Validation TCK green.

No failing test to start from here: a version is not something a test can be red about. What
the issue warns about is what might break once the parent moves — vidocq-parent 0.4.0 removed
the `target/javamodules` copy, and the stack now keeps `module-info.java` in `src/main/java`
with tests on the module path.

**What changed.** Every `pom.xml`, the same edit — the parent:

```xml
<parent>
    <groupId>io.vidocq</groupId>
    <artifactId>vidocq-parent</artifactId>
    <version>0.4.0-SNAPSHOT</version>      <!-- was 0.3.0-SNAPSHOT -->
</parent>
```

and each module's own `<version>`; plus `CLAUDE.md`, the Antora descriptor and the
getting-started page, which quote the version. The brick
dependencies (`vauban.version`, `cassini.version`, …) were already on 0.4.0-SNAPSHOT: Yann
had bumped them in September. And nothing in Erasmus read `target/javamodules`: its
`module-info.java` already lived in `src/main/java`, and its tests already ran on the module
path — surefire's stack traces name the module in every frame,
`io.vidocq.erasmus.core@0.3.0-SNAPSHOT/io.vidocq.erasmus.core.internal…`.

**Proof.**

```
$ ./mvnw -ntp clean install
[INFO] Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The issue's last line, "the TCK green", is the subject of the rest of this post. Spoiler:
far from it, on day one.

## Erasmus was invisible on a class path

*Commit `fix: register ErasmusValidationProvider on the class path`. Files to open:
[`ClassPathBootstrapTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/ClassPathBootstrapTest.java),
[`erasmus-core/pom.xml`](../../erasmus-core/pom.xml) (the new `<build>` section),
[`module-info.java`](../../erasmus-core/src/main/java/module-info.java), and the new
one-line file
[`META-INF/services/jakarta.validation.spi.ValidationProvider`](../../erasmus-core/src/main/resources/META-INF/services/jakarta.validation.spi.ValidationProvider).*

**Goal.** Issue #16, also from Yann, part of an audit of every brick
([Vidocq/vidocq-workspace#15](https://codefloe.com/Vidocq/vidocq-workspace/issues/15):
every brick must also work without Vauban, in Weld or OpenLiberty):

> `ErasmusValidationProvider` is registered only through `provides ValidationProvider` in
> `erasmus-core/src/main/java/module-info.java:32`. There is no
> `META-INF/services/jakarta.validation.spi.ValidationProvider` file. On a class path,
> `Validation.buildDefaultValidatorFactory()` does not find Erasmus and fails with
> `NoProviderFoundException`, or picks another provider.

That is more than a portability nicety: the spec requires the file, in so many words —
[Jakarta Bean Validation 3.1, §6.5.4.2 *ValidationProvider*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#validationapi-bootstrapping-validationprovider-provider):

> Every Jakarta Validation provider must provide a `ValidationProvider` implementation
> containing a public no-arg constructor and add the corresponding
> `META-INF/services/jakarta.validation.spi.ValidationProvider` file descriptor in one of its
> jars.

Our 88 tests never saw the problem, and the reason is where they run. Here is the same call,
`Validation.buildDefaultValidatorFactory()`, followed down to the `ServiceLoader` that looks
for providers, in the two places Erasmus can live:

```
on the module path — erasmus-core's own 88 tests
  Validation.buildDefaultValidatorFactory()
    ServiceLoader.load(ValidationProvider.class)
      module io.vidocq.erasmus.core
        provides ValidationProvider with ErasmusValidationProvider   -> found
  -> ErasmusValidatorFactory

on a class path — the TCK, Weld, OpenLiberty
  Validation.buildDefaultValidatorFactory()
    ServiceLoader.load(ValidationProvider.class)
      erasmus-core.jar is a plain jar: module-info.class is ignored
      META-INF/services/jakarta.validation.spi.ValidationProvider     -> absent
  -> NoProviderFoundException
```

So the test has to run on a class path, and has to *know* it does — otherwise it passes
through the `provides` route and proves nothing. That is its first assertion:

```java
@Test
void buildDefaultValidatorFactory_findsErasmusOnTheClassPath() {
    assertFalse(getClass().getModule().isNamed(),
            "this test must run on the class path, or it proves nothing about META-INF/services");

    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
        assertInstanceOf(ErasmusValidatorFactory.class, factory.unwrap(ErasmusValidatorFactory.class),
                "the default provider on the class path must be Erasmus");
    }
}
```

And it runs in a surefire execution of its own, with the module path switched off; the
default execution, on the module path, excludes it:

```xml
<execution>
    <id>class-path-bootstrap</id>
    <goals>
        <goal>test</goal>
    </goals>
    <configuration>
        <useModulePath>false</useModulePath>
        <includes>
            <include>**/ClassPathBootstrapTest.java</include>
        </includes>
    </configuration>
</execution>
```

Before the services file existed, it failed with exactly the issue's symptom:

```
$ cd erasmus-core && ../mvnw -ntp test
[INFO] --- surefire:3.5.5:test (class-path-bootstrap) @ erasmus-core ---
[INFO] Running io.vidocq.erasmus.core.internal.ClassPathBootstrapTest
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.059 s <<< FAILURE! -- in io.vidocq.erasmus.core.internal.ClassPathBootstrapTest
[ERROR] io.vidocq.erasmus.core.internal.ClassPathBootstrapTest.buildDefaultValidatorFactory_findsErasmusOnTheClassPath -- Time elapsed: 0.038 s <<< ERROR!
jakarta.validation.NoProviderFoundException: Unable to create a Configuration, because no Jakarta Validation provider could be found. Add a provider like Hibernate Validator (RI) to your classpath.
```

**What changed.** One file, one line, naming the same class the `provides` clause names:

```
io.vidocq.erasmus.core.internal.ErasmusValidationProvider
```

in `erasmus-core/src/main/resources/META-INF/services/jakarta.validation.spi.ValidationProvider`.
On the class path, the walk above now ends differently:

```
on a class path
  Validation.buildDefaultValidatorFactory()
    ServiceLoader.load(ValidationProvider.class)
      META-INF/services/jakarta.validation.spi.ValidationProvider
        io.vidocq.erasmus.core.internal.ErasmusValidationProvider   -> found
  -> ErasmusValidatorFactory
```

On the module path nothing changes: for a named module, `ServiceLoader` reads `provides` and
ignores `META-INF/services`. The two now have to name the same class, which the comment above
the `provides` clause in `module-info.java` says, and which this test checks every build.

**Proof.** The same test:

```
[INFO] Running io.vidocq.erasmus.core.internal.ClassPathBootstrapTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Proving it inside OpenLiberty, without its Bean Validation

*Commit `test: erasmus-it-openliberty, Erasmus on OpenLiberty without beanValidation`.
Files to open:
[`ErasmusOnOpenLibertyIT.java`](../../erasmus-it-openliberty/src/test/java/io/vidocq/erasmus/it/openliberty/ErasmusOnOpenLibertyIT.java),
[`ValidateResource.java`](../../erasmus-it-openliberty/src/main/java/io/vidocq/erasmus/it/openliberty/ValidateResource.java),
[`server.xml`](../../erasmus-it-openliberty/src/main/liberty/config/server.xml), and
[`erasmus-it-openliberty/pom.xml`](../../erasmus-it-openliberty/pom.xml).*

**Goal.** The second half of #16:

> `erasmus-it-openliberty`: a WAR bundling erasmus on Liberty without `beanValidation`,
> validating a bean through `Validation.buildDefaultValidatorFactory()`.

A unit test on a class path is one thing; an application server, with its own class loaders,
is where the services file actually earns its keep. Workspace#15 fixes the shape: OpenLiberty
26.0.0.10, MicroProfile distribution, a server that enables only the Jakarta features the
brick needs — **never** the feature matching the brick, here `beanValidation`. So Erasmus,
bundled in the WAR's `WEB-INF/lib`, is the only Bean Validation in the server.

The test is two HTTP calls on the running example — a `Person` whose `@Valid Address` has a
`@NotBlank city`:

```java
@Test
void blankCity_isReportedByErasmus_atTheCascadedPath() throws Exception {
    HttpResponse<String> response = get(BASE + "?city=");

    assertEquals(200, response.statusCode(), response.body());
    assertEquals("""
            factory: io.vidocq.erasmus.core.internal.ErasmusValidatorFactory
            violations: 1
            address.city: must not be blank
            """, response.body());
}
```

and the same with `?city=Rotterdam`, expecting `violations: 0`. Behind it, a JAX-RS resource
in the WAR does nothing but the plain bootstrap:

```java
@GET
@Produces(MediaType.TEXT_PLAIN)
public String validate(@QueryParam("city") String city) {
    StringBuilder out = new StringBuilder();
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
        Set<ConstraintViolation<Person>> violations =
                factory.getValidator().validate(new Person(new Address(city)));
        out.append("factory: ").append(factory.getClass().getName()).append('\n');
        out.append("violations: ").append(violations.size()).append('\n');
        // ... one line per violation: path, then message
    }
    return out.toString();
}
```

To see it fail for the right reason, I built `erasmus-core` without the services file of the
previous section, then ran the Liberty test against it:

```
$ ./mvnw -ntp -Pit-containers -pl erasmus-it-openliberty clean verify     # erasmus-core without META-INF/services
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 0.786 s <<< FAILURE! -- in io.vidocq.erasmus.it.openliberty.ErasmusOnOpenLibertyIT
[ERROR]   ErasmusOnOpenLibertyIT.blankCity_isReportedByErasmus_atTheCascadedPath:49 Unable to create a Configuration, because no Jakarta Validation provider could be found. Add a provider like Hibernate Validator (RI) to your classpath. ==> expected: <200> but was: <500>
[ERROR]   ErasmusOnOpenLibertyIT.validCity_hasNoViolation:61 Unable to create a Configuration, because no Jakarta Validation provider could be found. Add a provider like Hibernate Validator (RI) to your classpath. ==> expected: <200> but was: <500>
```

HTTP 500, `NoProviderFoundException`: the issue's symptom again, this time inside the server.

**What changed.** A new module, `erasmus-it-openliberty`, packaged as a WAR, behind a new
`it-containers` profile — like `tck`, a plain build neither downloads nor runs anything.
`liberty-maven-plugin` creates the server from the pinned distribution, deploys the WAR,
starts the server before failsafe and stops it after. The whole server configuration is one
feature:

```xml
<featureManager>
    <feature>restfulWS-3.1</feature>
</featureManager>
```

My first version said `servlet-6.0`, the obvious choice for one HTTP endpoint. Liberty
refused it:

```
E CWWKF0001E: A feature definition could not be found for servlet-6.0.
```

The MicroProfile 7 distribution is at the Jakarta EE 10 level and ships no public servlet
feature; `restfulWS-3.1` brings the web container with it. Hence JAX-RS rather than a
servlet, and a comment in `server.xml` saying why. A `jvm.options` also pins the locale to
English, so the expected `must not be blank` does not depend on the build machine's language.

**Proof.** With the services file back:

```
$ ./mvnw -ntp -Pit-containers -pl erasmus-it-openliberty -am verify
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.918 s -- in io.vidocq.erasmus.it.openliberty.ErasmusOnOpenLibertyIT
[INFO] BUILD SUCCESS
```

And the server's own log confirms what it was running — no Bean Validation feature among
them:

```
CWWKF0012I: The server installed the following features: [cdi-4.0, jndi-1.0, jsonp-2.1, restfulWS-3.1, restfulWSClient-3.1].
```

The third item of #16, a Weld test of Erasmus's CDI integration, waits for that integration
to exist: it is M7, and ROADMAP's M7 now says so.

## Running the official TCK

*Commit `test(tck): run the official Jakarta Validation 3.1 TCK`. Files to open:
[`erasmus-tck/pom.xml`](../../erasmus-tck/pom.xml) and
[`run-official-tck-bean-validation-3.1.sh`](../../run-official-tck-bean-validation-3.1.sh).*

**Goal.** "If it is possible, start doing the TCK on the existing." Until this commit,
`erasmus-tck` was a placeholder: a `pom.xml` with no test in it, and ROADMAP M8 opening on a
spike nobody had done —

> Confirm exact Maven coordinates (candidate: `jakarta.validation:jakarta.validation-tck` —
> **to verify**, TCKs are not uniformly published to Maven Central) or clone-and-build […].
> Confirm whether the suite needs Arquillian at all […].

So the red, here, is that there was nothing to run.

**What changed.** The spike took one search on Maven Central. The TCK is published there,
as `jakarta.validation:validation-tck-tests:3.1.1`, next to a
`validation-standalone-container-adapter`: an Arquillian "container" that is just the
current JVM. No application server, no build from source — TestNG and Arquillian in plain
Java SE.

Reading the TCK's own `TestUtil` told me how it finds the implementation under test: a
system property naming the provider class, `validation.provider`, handed to
`Validation.byProvider(...)`. Followed down:

```
-Dvalidation.provider=io.vidocq.erasmus.core.internal.ErasmusValidationProvider
  TestUtil.getValidatorUnderTest()
    Validation.byProvider(ErasmusValidationProvider.class)
      the default ValidationProviderResolver
        ServiceLoader.load(ValidationProvider.class)    on the CLASS PATH
          META-INF/services/jakarta.validation.spi.ValidationProvider   -> Erasmus
```

That last line is the previous two sections: the TCK runs on a class path, and without #16's
services file it would have found nothing, for every one of its tests. The issue that looked
like housekeeping was the TCK's first prerequisite.

The module itself is a handful of test-scope dependencies — the TCK, its standalone adapter,
TestNG, Arquillian's TestNG integration — and a surefire configuration:

```xml
<configuration>
    <useModulePath>false</useModulePath>
    <suiteXmlFiles>
        <suiteXmlFile>${tck.suite.dir}/tck-tests.xml</suiteXmlFile>
    </suiteXmlFiles>
    <systemPropertyVariables>
        <validation.provider>io.vidocq.erasmus.core.internal.ErasmusValidationProvider</validation.provider>
        <excludeIntegrationTests>true</excludeIntegrationTests>
        <includeJavaFXTests>false</includeJavaFXTests>
    </systemPropertyVariables>
</configuration>
```

Three choices in there are worth a word. The suite file is the TCK's own `tck-tests.xml`,
unpacked from its jar by `maven-dependency-plugin` rather than copied — so we run the
official suite, not our edit of it. The two other properties feed the TCK's own method
selectors: `excludeIntegrationTests` leaves out the tests that need a full Jakarta EE
container (CDI, EJB), `includeJavaFXTests` the JavaFX ones. And the four other Jakarta API
jars the TCK's pom marks `provided` (CDI, EL, EJB, annotations): I tried the run with and
without them, got the same results, and left them out.

The script follows the UX of every other brick's TCK script: the whole suite by default,
`-Dtest=SomeTckTest` for one TCK class (surefire's `dependenciesToScan` lets it find a class
inside the TCK jar), and a report in `erasmus-tck/target/`.

**Proof.** The first run of the official TCK against Erasmus:

```
$ ./run-official-tck-bean-validation-3.1.sh
[ERROR] Tests run: 981, Failures: 846, Errors: 0, Skipped: 0
```

135 passing out of 981, in about ten seconds. I grouped the 846 failures by exception and
first line of message, from TestNG's `testng-results.xml`; they are less alarming than they
look:

```
  233  UnsupportedOperationException: Constraint metadata API lands in ROADMAP M6
  202  UnsupportedOperationException: Executable validation lands in ROADMAP M5
  133  AssertionError: Expecting: ...
   45  TestException: Expected exception of type class jakarta.validation.ConstraintDeclarationException but got java.lang.UnsupportedOperationException ...
  ...
    8  UnexpectedTypeException: No ConstraintValidator found for type java.time.LocalTime ...
    8  UnexpectedTypeException: No ConstraintValidator found for type java.time.ZonedDateTime ...
    6  UnexpectedTypeException: No ConstraintValidator found for type java.util.GregorianCalendar ...
    4  ValidationException: No ConstraintValidator registered for jakarta.validation.constraints.Null
    3  UnexpectedTypeException: @Min does not support floating-point types — got class java.lang.Double
```

The first two lines are the `UnsupportedOperationException`s we put in on purpose, with a
ROADMAP pointer, rather than fake implementations: 435 failures that mean "not done yet",
exactly as designed. The bottom lines are real gaps in what M1 and M2 claim: `@Null` has no
validator at all, and M2's narrowed set of time types shows.

One failure in the middle group I recognised. `NotBlankConstraintTest#testNotBlankConstraint`
fails with `Expecting: <[]> to contain exactly in any order: …`, and the TCK's source (its
`-sources.jar` is on Maven Central too) shows why — the very first assertion:

```java
NotBlankDummyEntity foo = new NotBlankDummyEntity();       // name is null

Set<ConstraintViolation<NotBlankDummyEntity>> constraintViolations = validator.validate( foo );
assertThat( constraintViolations ).containsOnlyViolations(
        violationOf( NotBlank.class ).withProperty( "name" )
);
```

A `null` name must be a `@NotBlank` violation, and Erasmus returns none. That is `E-001`,
which part 4 found by quoting §8.21 — now confirmed by an independent oracle. I added the
test's name to the `BUG.md` entry.

For now the module reports and does not gate (`testFailureIgnore`); deciding what "passing"
means is the next commit.

## A ratchet, run on every commit

*Commit `test(tck): a ratchet, run on every commit`. Files to open:
[`KnownFailuresRatchet.java`](../../erasmus-tck/src/test/java/io/vidocq/erasmus/tck/KnownFailuresRatchet.java),
[`tck-known-failures.txt`](../../erasmus-tck/tck-known-failures.txt), and
[`.forgejo/workflows/tck.yml`](../../.forgejo/workflows/tck.yml).*

**Goal.** "Commit by commit, almost in the TDD way." 846 red tests cannot gate a build — no
commit would ever go through — and a report nobody has to read cannot drive anything either.
What I wanted, and chose among three options Claude laid out, is a ratchet that moves one
way and fails *both* ways:

- a TCK test that passed before and fails now is a regression: the build fails;
- a TCK test that failed before and passes now must be *recorded*: the build fails until it
  is, so the commit that made it pass shows it in its own diff.

The second rule is what makes it TDD-like. Working on a milestone becomes: run its TCK
tests, watch them fail, make them pass, delete their lines.

**What changed.** A list and a listener. The list,
`erasmus-tck/tck-known-failures.txt`, holds the 846 failing tests of the first run, one
`fully.qualified.TestClass#method` per line (each of the 981 TCK methods runs exactly once,
so that name is enough):

```
org.hibernate.beanvalidation.tck.tests.constraints.builtinconstraints.NotBlankConstraintTest#testNotBlankConstraintOnStringBuilder
org.hibernate.beanvalidation.tck.tests.constraints.builtinconstraints.NotEmptyConstraintTest#testNotEmptyConstraint
org.hibernate.beanvalidation.tck.tests.constraints.builtinconstraints.NullNotNullConstraintsTest#testNullConstraint
org.hibernate.beanvalidation.tck.tests.constraints.builtinconstraints.SizeConstraintTest#testSizeConstraint
```

The listener, `KnownFailuresRatchet`, is a TestNG `IInvokedMethodListener`. TestNG calls its
`afterInvocation` after each test method and *before* the result reaches surefire, which is
the one place a result can still be rewritten:

```java
@Override
public void afterInvocation(IInvokedMethod method, ITestResult result) {
    if (!method.isTestMethod()) {
        return;
    }
    String key = result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    boolean known = knownFailures.contains(key);
    switch (result.getStatus()) {
        case ITestResult.SUCCESS -> {
            outcomes.put(key, true);
            if (known) {
                result.setStatus(ITestResult.FAILURE);
                result.setThrowable(new AssertionError(key
                        + " now passes: remove it from tck-known-failures.txt in this commit"));
            }
        }
        case ITestResult.FAILURE -> {
            outcomes.put(key, false);
            if (known) {
                Throwable original = result.getThrowable();
                result.setStatus(ITestResult.SKIP);
                result.setThrowable(new KnownFailure(key, original));
            }
        }
        default -> {
            // skipped by TestNG itself (a failed dependency, a selector): nothing to judge
        }
    }
}
```

Step by step, for the three cases that matter, with real TCK tests:

```
NullNotNullConstraintsTest#testNullConstraint            listed, and Erasmus has no @Null validator
  result.getStatus()   = FAILURE
  known                = true
  -> setStatus(SKIP), the original failure attached     surefire counts it as skipped

EmailConstraintTest#testEmailConstraint                  not listed, Erasmus passes it
  result.getStatus()   = SUCCESS
  known                = false
  -> untouched                                          surefire counts it as passed

the same EmailConstraintTest#testEmailConstraint         if someone listed it by mistake
  result.getStatus()   = SUCCESS
  known                = true
  -> setStatus(FAILURE): "now passes: remove it ..."    the build fails
```

The fourth case — not listed, and failing — needs no code: the listener leaves it alone, and
a failure is a failure. At the end of the suite, the listener also writes
`target/tck-summary.txt`, passing and failing per TCK package, which the script prints; the
next section builds the plan on it.

Last, `.forgejo/workflows/tck.yml` runs the ratchet in CI on **every commit** of a pull
request, oldest first, skipping commits from before the ratchet existed:

```bash
commits=$(git rev-list --reverse "origin/${BASE_REF}..HEAD")
for commit in ${commits}; do
  git checkout -q "${commit}"
  if [[ ! -f erasmus-tck/tck-known-failures.txt ]]; then
    echo "skip $(git log --oneline -1) (before the TCK ratchet)"
    continue
  fi
  ./run-official-tck-bean-validation-3.1.sh
done
```

It is a separate workflow on purpose: `pr.yml` is a shared template, copied verbatim into
every producer repo from `GestionProjet`, and a change there is everybody's change. Vauban
already runs its CDI TCK in CI, after each merge on `main`; Erasmus is the first brick to run
its TCK on every commit of a pull request.

**Proof.** A ratchet that can only go green proves nothing, so I made it fail both ways on
purpose, editing the list by hand and restoring it after each run. First, a regression —
`testNullConstraint` taken off the list while Erasmus still fails it:

```
$ cd erasmus-tck && ../mvnw -ntp -Ptck test       # testNullConstraint removed from the list
[ERROR] Tests run: 981, Failures: 1, Errors: 0, Skipped: 845
[ERROR]   NullNotNullConstraintsTest>Arquillian.run:138->testNullConstraint:52 » Validation No ConstraintValidator registered for jakarta.validation.constraints.Null
```

Then an unrecorded pass — `testEmailConstraint`, which Erasmus passes, added to the list:

```
$ cd erasmus-tck && ../mvnw -ntp -Ptck test       # testEmailConstraint added to the list
[ERROR] Tests run: 981, Failures: 1, Errors: 0, Skipped: 846
[ERROR]   EmailConstraintTest.testEmailConstraint org.hibernate.beanvalidation.tck.tests.constraints.builtinconstraints.EmailConstraintTest#testEmailConstraint now passes: remove it from tck-known-failures.txt in this commit
[INFO] BUILD FAILURE
```

And with the list as committed, the official TCK goes green for the first time — not
because Erasmus passes it, but because every failure is accounted for:

```
$ ./run-official-tck-bean-validation-3.1.sh
[INFO] Tests run: 981, Failures: 0, Errors: 0, Skipped: 846
Maven exit status: 0
...
      135      846  TOTAL (981 run)
```

## The TCK drives every milestone

*Commit `docs(roadmap): the TCK drives every milestone`. One file:
[`ROADMAP.md`](../../ROADMAP.md) — the new section "The TCK drives every milestone", the new
M3.5, and the TCK slice under M4, M5, M6, M7 and M10.*

**Goal.** "Talking about changing the plan." The old plan put the TCK in M8, after CDI
integration, with a sequencing note asking for it to run "even partially, even red — from
M1 onward". Nobody had, and the first run showed what that cost: milestones closed against
our own tests only.

No test can be red about a plan. What this section rests on instead is the summary the
ratchet writes, which can be checked: every number below comes from it.

**What changed.** The 79 TCK packages, mapped onto the milestones that own them — each
milestone's *TCK slice*. For each slice, I split the failures by cause: failing for a reason
of their own, or failing only because M5 (executable validation) or M6 (the metadata API) is
still an `UnsupportedOperationException`:

| Milestone | Tests | Passing | Other failures | Blocked by M5 | Blocked by M6 |
|---|---:|---:|---:|---:|---:|
| M1–M2 | 192 | 68 | 86 | 18 | 20 |
| M3 | 153 | 46 | 55 | 46 | 6 |
| M4 | 136 | 5 | 96 | 34 | 1 |
| M5 | 176 | 2 | 11 | 163 | 0 |
| M6 | 164 | 0 | 2 | 0 | 162 |
| M10 | 146 | 2 | 74 | 15 | 55 |
| `util` (the TCK's helpers) | 14 | 12 | 0 | 2 | 0 |
| **Total** | **981** | **135** | **324** | **278** | **244** |

Read the first two rows. M1, M2 and M3 are marked ✅ in `ROADMAP.md`, and the TCK passes 114
of their 345 tests. Of the 231 failures, 141 are theirs to fix right now — not blocked by
anything later. Building M4 on top would carry them forward, and M4 is the milestone that
builds most directly on M3's graph walk. So the plan gains a milestone before M4:

> ### M3.5 — TCK catch-up on what M1–M3 already claim (next)
>
> **Scope:** the TCK tests of M1–M3's slices that fail for a reason of their own — 141 on the
> first run (86 in M1–M2's packages, 55 in M3's) — as opposed to failing on the metadata API
> (M6) or executable validation (M5), which do not exist yet.

The rule for every milestone from now on: its work starts by running its slice and watching
it fail, and it is done when none of its slice is left in `tck-known-failures.txt` — except
tests blocked by a later milestone, and anything filed in `TCK.md`. M8 keeps only what no
milestone owns: the signature test, the `@IntegrationTest` subset (which needs a container,
so after M7), and the last gaps.

**Proof.** The table adds up — 192 + 153 + 136 + 176 + 164 + 146 + 14 = 981, and
135 + 324 + 278 + 244 = 981 — and its "Passing" column is the summary the ratchet printed
in the previous section, regrouped by milestone:

```
$ ./run-official-tck-bean-validation-3.1.sh
...
       19       14  constraints.builtinconstraints
...
        5        8  constraints.groups
        2        1  constraints.groups.groupsequence
...
        9       20  messageinterpolation
        0      164  metadata
...
       32       55  validation
...
      135      846  TOTAL (981 run)
```

## Where it stands now

- Erasmus is on 0.4.0-SNAPSHOT, like the rest of the stack (#15).
- Erasmus is found on a class path, as §6.5.4.2 requires, and proved in two places: a
  class-path unit test, and an OpenLiberty server with no Bean Validation of its own (#16).
  The Weld test of #16 waits for M7.
- The official Jakarta Validation 3.1 TCK runs in about ten seconds, in plain Java SE:
  `./run-official-tck-bean-validation-3.1.sh`. 135 of 981 pass.
- The TCK confirms `E-001` independently (`NotBlankConstraintTest#testNotBlankConstraint`).
- The other 846 are listed in `erasmus-tck/tck-known-failures.txt`, and the ratchet holds
  them there: a regression fails the build, and so does an unrecorded pass. CI runs it on
  every commit of a pull request.
- 89 unit tests in `erasmus-core` (88 + the class-path bootstrap test), all green, plus the
  two Liberty tests behind `-Pit-containers`.
- `ROADMAP.md` maps every TCK package onto a milestone. M1–M3 are ✅ against our own tests and
  114/345 against the TCK — which is why the next milestone is not M4.

## What's next

M3.5: the 141 TCK tests of M1–M3 that fail for reasons of their own. The first run already
named some — no `@Null` validator, `@Min` on `Double`, the time types M2 left out,
constraint-definition checks, `E-001` already confirmed — and `E-002` may surface among
them too. Each commit will make some of them pass and delete their lines, and
`git log -p erasmus-tck/tck-known-failures.txt` will be the milestone's progress, test by
test.
