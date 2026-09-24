# M3: cascaded validation, groups, and `@GroupSequence`

*Part 4 of the [Erasmus making-of series](../../MAKING-OF.md). Continues from
[part 3](03-migrating-to-codefloe.md), an infrastructure detour — for the actual milestone
thread, this picks up where [part 2](02-full-constraint-set-and-interpolation.md) left off:
M2 finished, all 21 built-in constraints implemented, locale-aware message interpolation
with a homegrown EL-subset evaluator, and custom constraint authoring (composed constraints,
`@ReportAsSingleViolation`) — 76 tests, all green. Every `validate(bean)` call up to this
point checked one flat bean: no descent into nested beans, no `groups` filtering.*

Then it was time to start M3. From `ROADMAP.md`:

> **Scope spec:** cascaded validation, groups, group sequences.
>
> **Deliverable:** cascaded validation across arbitrary (including circular) object graphs
> for single bean references, correct group-sequence short-circuiting for the
> single-sequence-group case. 87 tests total (up from 76), all green.

One more thing before the sections, about how to read them: **each section below is one
commit**, titled the same way, so `git log --oneline` on this milestone's branch reads like
this post's table of contents and the diff of a commit can sit next to its section. Two
exceptions, both deliberate and both explained where they happen — cascading and cycle
detection share one commit, and the last section has no commit of its own. Every section
opens with the files worth having open alongside it — and quotes the sentence of the spec it
implements, [Jakarta Bean Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1), with its section number. Where the spec has
nothing to say about a point, the section says that too.

## What "cascaded validation" even means

The ROADMAP quote above uses the term without defining it, so worth pausing on before
anything else: **cascaded validation** is the spec's name for validation *cascading* —
flowing down — from the root bean a caller hands to `validate(...)`, through the graph of
objects it references, instead of stopping at that root bean's own properties. A `Person`
holding an `Address`, which might itself hold a `Country`: cascaded validation is what lets
one `validate(person)` call reach all the way down that chain and catch a problem anywhere
in it, rather than only ever seeing `Person`'s own directly-declared constraints. `@Valid` is
the annotation that turns cascading on for a given property — see below for exactly what
that looks like, in the spec's own words ([Jakarta Bean Validation 3.1, §5.1.3 *Graph validation*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-requirements-graphvalidation)).

## Cascading via `@Valid`

*Commit `feat(m3): cascading via @Valid, with cycle detection`. Files to open: [`PathImpl.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/PathImpl.java),
[`PropertyMetadata.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/metadata/PropertyMetadata.java), [`ConstraintMetadataBuilder.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/metadata/ConstraintMetadataBuilder.java), [`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java), and the test, [`CascadingAndGroupsTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/CascadingAndGroupsTest.java).*

**Goal.** Every milestone before this one only ever looked at the properties declared
directly on whatever bean you handed to `validate(...)`. Take a `Person` with an `Address`
field:

```java
public class Address {
    @NotBlank
    private String city;
}

public class Person {
    @Valid
    private Address address;
}
```

The behavior wanted: `validator.validate(person)` should reach into `address` and check
`Address`'s own constraints too, reporting *where* in the graph it failed —
`"address.city"`, not just `"city"` or `"address"` — so the caller never has to separately
dig into `getRootBean()`/`getLeafBean()` to figure out which nested object was the problem.

None of that is our invention — it is the spec's definition of the feature, [Jakarta Bean Validation 3.1, §5.1.3 *Graph validation*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-requirements-graphvalidation):

> Consider the situation where bean X contains a field of type Y. By annotating field Y with
> the `@Valid` annotation, the Validator will validate Y (and its properties) when X is
> validated.

Note what else the same section asks for. `@Valid` on "Collection-valued, array-valued and
generally `Iterable` fields and properties" must validate every element ("This causes the
contents of the iterator to be validated"). For the spec, a single reference and a collection
are one and the same requirement, stated in one breath. This milestone implements only the
single reference; validating the elements of a collection, array or `Map` waits for M4, where
the `ValueExtractor` SPI handles every container type at once. That split is our decision,
not the spec's — which is exactly why the quote sits here: read the whole requirement, not
just the half we shipped.

Here is the test, written first. It wraps a blank `Address` in a `Person` and asks two things
of `validate(person)`: exactly one violation, and a path that says *where* — `address.city`,
the nested property, not just `city`:

```java
@Test
void cascading_descendsIntoValidAnnotatedProperty_withDottedPath() {
    Set<ConstraintViolation<PersonWithCascadedAddress>> violations =
            validator.validate(new PersonWithCascadedAddress(new Address("")));

    assertEquals(1, violations.size());
    assertEquals("address.city", violations.iterator().next().getPropertyPath().toString());
}
```

Before any of this existed, it was red on the very first assertion:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.cascading_descendsIntoValidAnnotatedProperty_withDottedPath:81 expected: <1> but was: <0>
```

Zero violations instead of one — the engine had no concept of `@Valid` at all yet, so
`address` was just an opaque field to it, and `Address`'s own `@NotBlank` was invisible.

**What was built.** Two things had to change. First, `PathImpl` only knew how to *be* a
single segment before this milestone — `"address.city"` as a path literally couldn't be
constructed. The fix is an `append`, called once per level of descent:

```java
PathImpl append(String propertyName) {
    List<Path.Node> extended = new ArrayList<>(nodes);
    extended.add(new NodeImpl(propertyName));
    return new PathImpl(List.copyOf(extended));
}
```

On `Person`, that is two calls, one per level the walk goes down:

```
PathImpl.ofProperty("address")  ->  "address"
        .append("city")         ->  "address.city"
```

The second string is exactly what the test asserts on.

Second, the metadata had to remember that a property carries `@Valid`. `PropertyMetadata` is a
record; it gains one more component:

```java
public record PropertyMetadata(
        String name, PropertyAccessor accessor, List<ConstraintDescriptorImpl<?>> constraints, boolean cascaded) {
}
```

Hard to picture in the abstract, so here are the two instances the running example produces —
one per property, and they are mirror images of each other:

```java
// Person.address — carries @Valid and nothing else
new PropertyMetadata("address", <FieldAccessor for Person#address>,
        List.of(),                              // no constraint of its own
        true);                                  // cascaded

// Address.city — carries @NotBlank and no @Valid
new PropertyMetadata("city", <FieldAccessor for Address#city>,
        List.of(<descriptor for @NotBlank>),    // one constraint
        false);                                 // not cascaded
```

`address` is the case that did not exist before M3: an empty `constraints` list, and the
whole reason to keep the property is that last boolean.

and `ConstraintMetadataBuilder` fills it by looking for the annotation on the field (same
again for getters):

```java
private static boolean isCascaded(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
        if (annotation.annotationType() == Valid.class) {
            return true;
        }
    }
    return false;
}
```

Hand it `Person#address`'s annotations — `[@Valid]` — and it returns `true`; hand it
`Address#city`'s — `[@NotBlank]` — and it returns `false`. That single boolean is the only
difference between the two records above.

Small but easy to get wrong, and the reason this is more than one added flag: a plain
`@Valid private Address address;` has *zero* `ConstraintDescriptor`s on the `address` field
itself — the `@NotBlank` lives on `Address.city`, a different class entirely — and the builder
used to skip any field with no constraint annotations at all. "Cascaded but otherwise
unconstrained" had to become its own reason to keep a property. The whole fix is one `&&`:

```java
List<ConstraintDescriptorImpl<?>> descriptors = constraintDescriptorsOf(field.getAnnotations());
boolean cascaded = isCascaded(field.getAnnotations());
if (descriptors.isEmpty() && !cascaded) {
    continue;
}
```

Run it over `Person`: `descriptors` comes back empty (the field carries no constraint) and
`cascaded` is `true`, so the property survives. With the old condition — just
`descriptors.isEmpty()` — `address` was dropped from the metadata entirely, so nothing
downstream ever had a property to descend into. That is the zero-violation red run above, and
this `&&` is its whole fix. `Address.city` goes through the other way round: one descriptor,
not cascaded, kept for its constraint.

With that in place, the walk itself, `validateGraph` in `ErasmusValidator`: for every property
of the current bean, build its path (a fresh one at the root, `pathPrefix.append(...)` below
it), run its constraints, then descend if it is cascaded and non-null:

```java
for (PropertyMetadata property : metadata.properties()) {
    Object value = property.accessor().get(currentBean);
    PathImpl propertyPath = pathPrefix == null ? PathImpl.ofProperty(property.name()) : pathPrefix.append(property.name());

    for (ConstraintDescriptorImpl<?> descriptor : property.constraints()) {
        // ... group check, then:
        violations.addAll(evaluateConstraint(rootBean, rootBeanClass, currentBean, propertyPath,
                property.accessor().getType(), value, descriptor));
    }

    if (property.cascaded() && value != null) {
        validateGraph(rootBean, rootBeanClass, value, effectiveGroups, propertyPath, visited, violations);
    }
}
```

Follow `validator.validate(person)` through it, with the blank city from the test:

```
currentBean = person, pathPrefix = null
  property "address" -> value = the Address, path = "address"
      constraints: none           -> nothing to evaluate here
      cascaded, value != null     -> recurse
    currentBean = the Address, pathPrefix = "address"
      property "city" -> value = "", path = "address.city"
          constraint @NotBlank    -> violation, reported on "address.city"
          not cascaded            -> stop
```

Look at the third argument to `evaluateConstraint`: `currentBean`, not `rootBean`. That is the
new `leafBean` parameter — a violation on `person.address.city` has to report the `Address` as
`getLeafBean()`, not the `Person`, and before cascading the two had always been the same
object.

**Proof.** The same test, now green:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#cascading_descendsIntoValidAnnotatedProperty_withDottedPath
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.177 s
[INFO] BUILD SUCCESS
```

And the control case — the exact same `Address`, minus the `@Valid` annotation — stays at
zero violations both before and after this milestone, which is exactly the point: cascading
is opt-in, per property.

```java
@Test
void withoutValidAnnotation_nestedBeanIsNotChecked() {
    assertTrue(validator.validate(new PersonWithPlainAddress(new Address(""))).isEmpty());
}
```

## Cycle detection: making sure descent actually stops

*Same commit as the previous section, on purpose — the goal below says why. File to open:
[`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java), the first three lines of `validateGraph`.*

**Goal.** A bean graph can point back to itself — two `Node`s holding `@Valid` references to
each other, or a `Node` referencing itself directly. Plain recursive descent through `@Valid`
links has no reason to stop on its own once it hits a cycle; cycle detection has to land in
the *same* change as cascading, not as an afterthought, or the very first circular fixture
anyone writes recurses forever instead of returning. The spec makes the guard mandatory and
says what "already visited" means, [Jakarta Bean Validation 3.1, §5.7.1 *Object graph validation*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-validationroutine-graphvalidation):

> To prevent infinite loops, the Jakarta Validation implementation must ignore the cascading
> operation if the associated object instance has already been validated in the current
> navigation path (starting from the root object).

Two words in there matter below: *instance* (identity, not `equals()`) and *navigation path*.

The fixture is the smallest graph that can loop — a `Node` with a constrained `name` and a
`@Valid next`:

```java
private static final class Node {
    @NotNull
    private String name;

    @Valid
    private Node next;
}
```

The test wires two of them into a ring and asks for exactly two violations: `a`'s own `name`,
and `b`'s reached as `next.name`. Two — not one, which would mean no descent happened, and
not a `StackOverflowError`, which would mean descent never stopped:

```java
@Test
void circularGraph_terminatesAndValidatesEachNodeOnce() {
    Node a = new Node(null);
    Node b = new Node(null);
    a.next = b;
    b.next = a;

    Set<ConstraintViolation<Node>> violations = validator.validate(a);

    assertEquals(2, violations.size());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("next.name")));
}
```

Before either mechanism existed:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.circularGraph_terminatesAndValidatesEachNodeOnce:118 expected: <2> but was: <1>
```

Only the root's own violation came back — not a hang, since cascading itself didn't exist
yet to even attempt the recursion, but proof the two-node case wasn't handled.

**What was built.** A `visited` set — the bean instances the walk has already entered, added
on the way in. Before looking at a bean, `validateGraph` tries to add it to that set; if it
was already there, this path has looped back on itself, and the walk stops right there
instead of descending again. In this commit, there is one such set per `validate()` call; the
`@GroupSequence` commit later narrows that to one per *step* (explained there):

```java
private <T> void validateGraph(..., Set<Object> visited, ...) {
    if (!visited.add(currentBean)) {
        return;
    }
    // ... look at currentBean's own properties, recurse into cascaded ones
}
```

On the ring the test builds — `a.next = b`, `b.next = a`, both names `null`:

```
validate(a)
  visited = {}          add a  -> new, walk it: @NotNull on name fails      -> "name"
    a.next = b, cascaded and non-null -> descend
  visited = {a}         add b  -> new, walk it: @NotNull on name fails      -> "next.name"
    b.next = a, cascaded and non-null -> descend
  visited = {a, b}      add a  -> already in the set, return immediately
```

Two violations, and that third descent is where the walk would otherwise have looped forever.

What kind of set matters: it has to compare by *identity* (`==`), not by `equals()` — two
unrelated beans that happen to be `equals()`-equal must never be confused for the same graph
node. Java has no `IdentityHashSet`, so the idiom is a `Set` view over an `IdentityHashMap`.
It is created right before the walk starts, in `validate()`, and handed down as a parameter —
never a field, never static, never a `ThreadLocal`:

```java
Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
validateGraph(object, beanClass, object, step, null, visited, stepViolations);
```

Why identity rather than `equals()`, on that same ring: `a` and `b` are two distinct `Node`s
that both hold `name = null`. Give `Node` the `equals` you would naturally write — by `name` —
and an `equals()`-based set would consider `b` already visited the moment `a` went in, skip
it, and report one violation instead of two. Identity keeps them apart because they *are*
apart.

**Proof.**

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#circularGraph_terminatesAndValidatesEachNodeOnce
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.158 s
[INFO] BUILD SUCCESS
```

`a -> b -> a` — the walk validates `a`, descends into `b`, tries to descend back into `a`,
finds it already visited, and stops. Two violations (`a`'s own and `b`'s, reached as
`next.name`), not an infinite one. The self-referencing case (`self.next = self`) is the
same mechanism with the cycle one hop shorter.

One honest gap against the spec text, though. §5.7.1 scopes the rule to the *current
navigation path* and adds that "the complete validated object graph can" contain the same
instance more than once — meaning an `Address` shared by `@Valid home` and `@Valid work`
should be reported twice, once per path. Our visited set lives for the whole call, so it is
reported once, under the first path. Terminates the same, differs on that edge; logged as
`E-002` in [`BUG.md`](../../BUG.md), a small fix kept out of this branch so the commits stay
readable.

## A gotcha, found by quoting the spec: `@NotBlank` and `null` — the test was right

*No commit of its own — the fix sits inside the first commit's [`CascadingAndGroupsTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/CascadingAndGroupsTest.java), the two-line comment
above `new Address("")`. What it exposed is `E-001` in [`BUG.md`](../../BUG.md), fixed separately.*

First draft of the cascading test used `new Address(null)`, expecting the nested `@NotBlank
city` to fire. It didn't — zero violations, indistinguishable from the real red state above.
A throwaway test dumping `ConstraintMetadataBuilder`'s output confirmed the metadata was
right, so the walk wasn't the problem: `NotBlankValidator` was returning `true` for `null`, by
design, following the convention this project has carried since M1 — "every built-in
validator except `@NotNull` treats `null` as trivially valid", rule 3 of `CLAUDE.md`. I changed
the test to `new Address("")` and moved on, and the first version of this section called the
test's premise the mistake. Here is the line, in `NotBlankValidator.java` — the
`value == null ||` is that convention made code:

```java
public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
    return value == null || !value.toString().strip().isEmpty();
}
```

On the two cities the test tried: `new Address(null)` short-circuits on `value == null` and
returns `true` — valid, no violation, the zero I was staring at. `new Address("")` reaches
`strip().isEmpty()`, returns `false`, and the violation on `address.city` appears.

Going to the spec to quote it for this post is what turned that around. The convention is
real for most constraints — [§8.13 `@Size`](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#builtinconstraints-size): "`null` elements are considered valid" — but not for
these two:

> [§8.21 `@NotBlank`](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#builtinconstraints-notblank): The annotated element must not be `null` and must contain at least one
> non-whitespace character.
>
> [§8.20 `@NotEmpty`](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#builtinconstraints-notempty): The annotated element must not be `null` nor empty.

So the original test was right and the validator is wrong: a `null` city under `@NotBlank`
*is* a violation, and so is a `null` list under `@NotEmpty`. Erasmus's `NotBlankValidator` and
all four `NotEmptyValidatorFor*` return `true` for `null`, with unit tests pinning that down
since M1 — a conformance bug the TCK would have caught at M8, caught earlier only because a
making-of section insisted on quoting its source. Logged as `E-001`; fixing it (the
validators, their tests, and `CLAUDE.md` rule 3) is its own change, not smuggled into M3. The
cascading test keeps `new Address("")`, a violation under both readings.

## Groups: only run the constraints that were actually asked for

*Commit `feat(m3): groups -- only run the constraints that were actually asked for`. Files to
open: [`ConstraintDescriptorImpl.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/metadata/ConstraintDescriptorImpl.java) (`getGroups`), [`GroupsSupport.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/GroupsSupport.java) (new), [`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java) (the `intersects` check in
`validateGraph` and `validatePropertyConstraints`), and the test.*

**Goal.** `validate(bean, SomeGroup.class)` should only evaluate constraints declared under
`SomeGroup` (or under it via inheritance) — not every constraint on the bean regardless of
what was requested. Both halves are spelled out — [Jakarta Bean Validation 3.1, §5.4 *Group and group sequence*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-groupsequence):

> Each constraint declaration defines the list of groups it belongs to. If no group is
> explicitly declared, a constraint belongs to the `Default` group.

and, for the call site, [Jakarta Bean Validation 3.1, §6.1.3 *groups*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#validationapi-validatorapi-groups): "If no group is passed, the `Default` group is assumed."
Two tests pin this down: one confirming the *default* call still only
sees `Default`-group constraints, one confirming an *explicit* group call only sees that
group's. One fixture serves both, and the whole point is that it carries **exactly two
constraints, in two different groups, and violates both at once**:

```java
private static final class Account {
    @NotBlank                                   // no groups() declared -> Default group
    private String username;

    @Size(min = 8, groups = Strict.class)       // Strict group only
    private String password;
}
```

Built as `new Account("", "short")`, both are broken: the username is blank, the password is
five characters. So "evaluate everything" would report *two* violations, and a correct
group filter reports exactly *one* — a different one per call. That is what makes this pair
able to tell the two behaviours apart, where a single-constraint fixture could not.

The two tests differ by a single argument to `validate`, and each asserts on the *set of
violated paths* rather than on a count, so a failure says which constraints fired instead of
just how many:

```java
@Test
void defaultGroup_onlyEvaluatesDefaultGroupConstraints() {
    Set<ConstraintViolation<Account>> violations = validator.validate(new Account("", "short"));

    assertEquals(Set.of("username"), violatedPaths(violations),
            "Default requested: @NotBlank on username must fire, @Size(groups = Strict) on password must not");
}

@Test
void explicitGroup_onlyEvaluatesThatGroupsConstraints() {
    Set<ConstraintViolation<Account>> violations = validator.validate(new Account("", "short"), Strict.class);

    assertEquals(Set.of("password"), violatedPaths(violations),
            "Strict requested: @Size(groups = Strict) on password must fire, @NotBlank on username must not");
}
```

Before groups existed at all, both were red — and the failure lines say exactly what went
wrong, without having to open the fixture:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR] CascadingAndGroupsTest.defaultGroup_onlyEvaluatesDefaultGroupConstraints:174
        Default requested: @NotBlank on username must fire, @Size(groups = Strict) on password must not
        ==> expected: <[username]> but was: <[username, password]>
[ERROR] CascadingAndGroupsTest.explicitGroup_onlyEvaluatesThatGroupsConstraints:182
        Strict requested: @Size(groups = Strict) on password must fire, @NotBlank on username must not
        ==> expected: <[password]> but was: <[username, password]>
```

`[username, password]` both times: the engine evaluated every constraint it could find and
ignored the `groups` argument entirely — which is also why the two expectations differ
(`[username]` under `Default`, `[password]` under `Strict`) while the actual result was
identical.

**What was built.** `ConstraintDescriptorImpl.getGroups()` was hardcoded to
`Set.of(Default.class)` since M1 — nobody had populated a constraint's `groups()` attribute
yet to notice it was being ignored. The attributes map already reads every annotation member
generically (that's how `payload()` got wired through back in M2), so the fix was symmetric
with that:

```java
// before — written in M1 and never revisited: whatever the annotation declared, it was Default
public Set<Class<?>> getGroups() {
    return Set.of(Default.class);
}

// after
public Set<Class<?>> getGroups() {
    Class<?>[] groups = (Class<?>[]) attributes.get("groups");
    return groups == null || groups.length == 0 ? Set.of(Default.class) : Set.of(groups);
}
```

On the `Account` above, the two constraints now answer differently, where the old body
answered `{Default}` for both:

```
@NotBlank                       on username   groups() = {}              -> {Default}
@Size(min = 8,                  on password   groups() = {Strict.class}  -> {Strict}
      groups = Strict.class)
```

That second line is the one the old body got wrong, and the whole reason `password` was being
validated on a `Default` call.

The requested groups become a list (no groups at all means `Default`), and a constraint is
kept when any of its declared groups is in that list — `GroupsSupport.intersects`, which is as
plain as it sounds:

```java
static boolean intersects(Set<Class<?>> constraintGroups, List<Class<?>> effectiveGroups) {
    for (Class<?> group : constraintGroups) {
        if (effectiveGroups.contains(group)) {
            return true;
        }
    }
    return false;
}
```

Put the two calls the tests make next to the two constraints `Account` declares, and every
line of the red output above is accounted for:

```
validate(account)                 effective groups [Default]
  username's {Default}            intersects -> evaluated -> violation "username"
  password's {Strict}             no          -> skipped

validate(account, Strict.class)   effective groups [Strict]
  username's {Default}            no          -> skipped
  password's {Strict}             intersects -> evaluated -> violation "password"
```

Before this method existed, that right-hand column read "evaluated" four times out of four —
`[username, password]` on both calls.

That check is the "group check, then:" elided from the walk two sections up — one `continue`
per constraint, before its validator is ever instantiated:

```java
for (ConstraintDescriptorImpl<?> descriptor : property.constraints()) {
    if (!GroupsSupport.intersects(descriptor.getGroups(), effectiveGroups)) {
        continue;
    }
    violations.addAll(evaluateConstraint(rootBean, rootBeanClass, currentBean, propertyPath,
            property.accessor().getType(), value, descriptor));
}
```

Note where the `continue` does *not* sit: it guards the constraint loop, not the cascading
branch a few lines below it. `Account` is flat, so nothing here shows that — it is the
cascaded fixture of [Putting cascading and groups together](#putting-cascading-and-groups-together),
further down, that does: under `Default`, the walk still descends into a `@Valid` property
whose only constraint is `Strict`-only, reaches it, and drops it on this `continue`. Zero
violations, but the descent happened. Group filtering picks constraints; it never prunes the
graph.

(Expansion of the requested groups — inheritance — is the next commit.)

**Proof.**

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#defaultGroup_onlyEvaluatesDefaultGroupConstraints+explicitGroup_onlyEvaluatesThatGroupsConstraints
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.114 s
[INFO] BUILD SUCCESS
```

### Group inheritance: the test that could not fail, and its replacement

*Commit `feat(m3): group inheritance`. File to open: [`GroupsSupport.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/GroupsSupport.java), `expand` and `collect`.*

**Goal.** A group can be a superset of another, by plain interface inheritance, and asking
for the subgroup has to bring the supergroup's constraints along —
[Jakarta Bean Validation 3.1, §5.4.1 *Group inheritance*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-groupsequence-groupinheritance):

> For a given interface Z, constraints marked as belonging to the group Z (i.e. where the
> annotation element `groups` contains the interface Z) or any of the super interfaces of Z
> (inherited groups) are considered part of the group Z.

Concretely: `ExtendedGroup extends BaseGroup`, a constraint declared under `BaseGroup`, and a
call that names only `ExtendedGroup` — the constraint must run, without `BaseGroup` ever
appearing at the call site.

**First attempt at a test, and why it was worthless.** The original fixture had exactly one
constraint:

```java
private static final class Item {
    @NotNull(groups = BaseGroup.class)
    private String sku;
}
```

and the test asserted `1` violation on `sku` for `validate(item, ExtendedGroup.class)`. It
passed. It also passed against the engine *before any group support existed at all*, because
back then every constraint ran regardless of the groups argument — with a single constraint in
the fixture, "run everything" and "run exactly the right thing" give the same answer. A test
that cannot distinguish the two implementations proves neither.

The fix is the same one the `Account` pair uses: give the fixture something that must *not*
fire, in a group that is genuinely unrelated to the one requested.

```java
private interface BaseGroup {
}

private interface ExtendedGroup extends BaseGroup {
}

private interface UnrelatedGroup {
}

private static final class Item {
    @NotNull(groups = BaseGroup.class)          // ExtendedGroup extends BaseGroup -> must fire
    private String sku;

    @NotNull(groups = UnrelatedGroup.class)     // no relation to ExtendedGroup -> must not fire
    private String ean;
}
```

```java
@Test
void groupInheritance_extendedGroupPullsInBaseGroupConstraints() {
    Set<ConstraintViolation<Item>> violations = validator.validate(new Item(null, null), ExtendedGroup.class);

    assertEquals(Set.of("sku"), violatedPaths(violations),
            "ExtendedGroup extends BaseGroup, so sku must fire; UnrelatedGroup is unrelated, so ean must not");
}
```

Both fields are `null`, so both constraints would fire if the group filter let them. Now the
test has an opinion, and it is red against both of the engines that came before it — for
opposite reasons, which is what makes it a real test. Against the previous commit, where
groups filter but do not expand, `ExtendedGroup` matches nothing at all:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#groupInheritance_extendedGroupPullsInBaseGroupConstraints
[ERROR] CascadingAndGroupsTest.groupInheritance_extendedGroupPullsInBaseGroupConstraints:221
        ExtendedGroup extends BaseGroup, so sku must fire; UnrelatedGroup is unrelated, so ean must not
        ==> expected: <[sku]> but was: <[]>
```

And against the engine as it stood before this milestone, where nothing filtered, *both*
constraints fire:

```
        ==> expected: <[sku]> but was: <[sku, ean]>
```

`[]` on one side, `[sku, ean]` on the other, `[sku]` wanted: only an implementation that walks
up the interface hierarchy — and stops there — lands in between.

**What was built.** `expand` collects a group and everything it extends, recursively, and
`resolve` runs every requested group through it before `intersects` ever sees the list:

```java
private static void collect(Class<?> group, Set<Class<?>> into) {
    if (!into.add(group)) {
        return;
    }
    for (Class<?> superGroup : group.getInterfaces()) {
        collect(superGroup, into);
    }
}
```

On this fixture:

```
expand(ExtendedGroup)     ->  {ExtendedGroup, BaseGroup}
  sku's {BaseGroup}       ->  in that set      -> evaluated -> violation "sku"
  ean's {UnrelatedGroup}  ->  not in that set  -> skipped
```

The `into.add` guard doubles as the cycle protection: interface hierarchies are acyclic in
Java, but a group can be reached twice through a diamond, and adding it twice would just be
wasted work.

**Proof.**

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#groupInheritance_extendedGroupPullsInBaseGroupConstraints
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.119 s
[INFO] BUILD SUCCESS
```

Worth keeping the first version of this section in mind, though, because the lesson outlived
it: a green test is not evidence until you know it can go red. This one only earned its place
once the fixture gained a second constraint that had to stay silent.

## `@GroupSequence`: stopping at the first failing step

*Commit `feat(m3): @GroupSequence -- stop at the first failing step`. Files to open: [`GroupsSupport.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/GroupsSupport.java)
(`resolve` becomes `resolveSteps`) and [`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java) (the per-step loop at the top of
`validate`, `validateProperty`, `validateValue`).*

**Goal.** `validate(bean, OrderedSequence.class)`, where `OrderedSequence` is
`@GroupSequence({StepOne.class, StepTwo.class})`, should evaluate `StepOne`'s constraints
first and, if any fail, never even look at `StepTwo`'s — [Jakarta Bean Validation 3.1, §5.4.2 *Group sequence*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-groupsequence-groupsequence):

> Each group in a group sequence must be processed sequentially in the order defined by
> `@GroupSequence.value` when the group defined as a sequence is requested. [...] if one of
> the groups processed in the sequence generates one or more constraint violations, the groups
> following in the sequence must not be processed.

The fixture: two step groups, a sequence over them, and a `Form` with one `@NotBlank` per
step:

```java
private interface StepOne {
}

private interface StepTwo {
}

@GroupSequence({StepOne.class, StepTwo.class})
private interface OrderedSequence {
}

private static final class Form {
    @NotBlank(groups = StepOne.class)
    private String field1;

    @NotBlank(groups = StepTwo.class)
    private String field2;
}
```

Both fields blank, so both steps *would* fail if evaluated. The test asks for exactly one
violation, on `field1` — `StepTwo` must never have run:

```java
@Test
void groupSequence_stopsAtFirstFailingGroup() {
    Set<ConstraintViolation<Form>> violations = validator.validate(new Form("", ""), OrderedSequence.class);

    assertEquals(1, violations.size());
    assertEquals("field1", violations.iterator().next().getPropertyPath().toString());
}
```

Before sequences existed, it was red:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.groupSequence_stopsAtFirstFailingGroup:222 expected: <1> but was: <2>
```

Both steps' violations came back, because — same root cause as plain groups above —
nothing was filtering by group yet, sequence or not.

**What was built.** One idea, and everything else follows from it: a `validate(...)` call
does not evaluate *one* set of groups, it evaluates an ordered *list* of sets, one after
another, and stops as soon as one of them produces a violation. Call each element of that
list a **step** — the spec's own word for what a sequence is made of ("each group in a group
sequence must be processed sequentially").

Almost every call has exactly one step, and then the list adds nothing: `validate(account)`
is the single step `[Default]`, `validate(account, Strict.class)` the single step `[Strict]`.
Nothing to stop at, because there is nothing after it. A sequence is the case where the list
is longer than one: `@GroupSequence({StepOne.class, StepTwo.class})` becomes the two steps
`[StepOne]` then `[StepTwo]`, and *that* is where stopping early means something.

So `resolveSteps` turns whatever was asked for into that list:

```java
static List<List<Class<?>>> resolveSteps(Class<?>[] requestedGroups) {
    Class<?>[] groups = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;

    // a sequence: one step per group it lists, in the declared order
    if (groups.length == 1 && groups[0].isAnnotationPresent(GroupSequence.class)) {
        List<List<Class<?>>> steps = new ArrayList<>();
        for (Class<?> sequencedGroup : groups[0].getAnnotation(GroupSequence.class).value()) {
            steps.add(List.copyOf(expand(sequencedGroup)));
        }
        return List.copyOf(steps);
    }

    // anything else: everything asked for, expanded, as one single step
    Set<Class<?>> merged = new LinkedHashSet<>();
    for (Class<?> group : groups) {
        merged.addAll(expand(group));
    }
    return List.of(List.copyOf(merged));
}
```

What it returns for the calls this post has already made, the last one using the fixture
below:

```
validate(account)                       ->  [[Default]]              nothing asked, Default assumed
validate(account, Strict.class)         ->  [[Strict]]               plain group, single step
validate(item, ExtendedGroup.class)     ->  [[ExtendedGroup,         plain group, single step,
                                              BaseGroup]]            inheritance expanded into it
validate(form, OrderedSequence.class)   ->  [[StepOne], [StepTwo]]   a sequence: one step per group
```

Only the last line takes the first branch of the method; the three above it fall through to
the merge at the bottom and come back as a one-element list. Note what the outer list means
in each case: for the first three it is "one step, nothing to short-circuit"; for the last it
is "`StepOne` first, and `StepTwo` only if `StepOne` was clean".

The second half is what the previous two sections already relied on — every requested group,
expanded with its super-interfaces, merged into one flat step. The first half is new: a
sequence becomes one step per group it lists, in order. `validate()` then walks those steps and returns at the first one that produces anything —
with a fresh visited set per step, which is the narrowing the cycle-detection section
promised: revisiting a bean under a later, independent step is never mistaken for a cycle.

```java
for (List<Class<?>> step : GroupsSupport.resolveSteps(groups)) {
    Set<ConstraintViolation<T>> stepViolations = new LinkedHashSet<>();
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    validateGraph(object, beanClass, object, step, null, visited, stepViolations);
    if (!stepViolations.isEmpty()) {
        return stepViolations;   // short-circuit: later steps are never evaluated
    }
}
return Set.of();
```

On `new Form("", "")` — both fields blank, so both steps would fail if both were evaluated —
`validate(form, OrderedSequence.class)` runs step `[StepOne]`, gets the `field1` violation,
and returns right there: `field2` is never looked at, and the caller sees one violation, not
two. Fix `field1` only and the first step comes back empty, so the loop moves on to
`[StepTwo]` and reports `field2`. Those are precisely the two tests below.

`validateProperty` and `validateValue` get the same loop around their single-property check.

**Proof.** Both directions — stopping at the first failing step, and passing through when
the first step is clean:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#groupSequence_stopsAtFirstFailingGroup+groupSequence_proceedsToSecondGroupWhenFirstPasses
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s
[INFO] BUILD SUCCESS
```

Small aside, the same trap as the first group-inheritance test: the "proceeds to the second group"
direction of this pair also happened to already read correctly before sequences existed —
with `StepOne` passing and only `StepTwo` failing, "run everything unconditionally" and
"stop at the first failure, then check the next" land on the same single violation. Only the
"stops at the first *failing* step" direction (both steps would fail if evaluated) could
actually tell the two implementations apart, which is why that's the one quoted as red above.

**Deliberate scope gap, documented rather than silently wrong** — and quoting the spec
actually shrinks it. Several *plain* groups in one call collapsing into one unordered step is
not a gap at all; it is what [Jakarta Bean Validation 3.1, §6.1.3 *groups*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#validationapi-validatorapi-groups) prescribes: "When more than one group is evaluated
and passed to the various validate methods, order is not constrained. It is equivalent to the
validation of a group G inheriting all groups". The gap is narrower: when one of several
requested groups is *itself* a sequence, §5.4.2 says "each composed group must respect the
sequence order as well", and we flatten it into one step instead. Rare in practice (most real calls pass
either `Default` or one custom sequence), but a real divergence, not an oversight.

## Putting cascading and groups together

*Commit `test(m3): cascading and groups together`. One file: [`CascadingAndGroupsTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/CascadingAndGroupsTest.java) — nothing in
`erasmus-core/src/main` changes.*

**Goal.** The two mechanisms above were built and tested mostly independently — the real
question is whether they compose: does a cascaded property's own constraint still respect
the group that was requested at the *root* `validate()` call? The spec answers in one line,
[Jakarta Bean Validation 3.1, §5.7.1 *Object graph validation*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-validationroutine-graphvalidation): "`@Valid` is an orthogonal concept to the notion of group. If two groups are in
sequence, the first group must pass for all associated objects before the second group is
evaluated." Orthogonal, so the requested groups travel down the graph unchanged.

`StrictAddress` is `Address` with its one constraint moved into the `Strict` group, behind a
`@Valid`:

```java
private static final class StrictAddress {
    @NotBlank(groups = Strict.class)
    private String city;
}

private static final class PersonWithStrictAddress {
    @Valid
    private StrictAddress address;
}
```

The test validates the same `person` twice: under `Default` it expects nothing — the nested
constraint is not in that group — and under `Strict` it expects `address.city`:

```java
@Test
void cascadedProperty_respectsRequestedGroupsDuringTraversal() {
    PersonWithStrictAddress person = new PersonWithStrictAddress(new StrictAddress(""));

    assertTrue(validator.validate(person).isEmpty(), "Strict-only constraint must not fire under Default");

    Set<ConstraintViolation<PersonWithStrictAddress>> violations = validator.validate(person, Strict.class);
    assertEquals(1, violations.size());
    assertEquals("address.city", violations.iterator().next().getPropertyPath().toString());
}
```

Before groups existed, it was red too:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.cascadedProperty_respectsRequestedGroupsDuringTraversal:261 expected: <1> but was: <0>
```

**What was built.** Nothing new, mechanically — the graph walk already threads
`effectiveGroups` down through every recursive call, unchanged as it descends. It is the
fourth argument here, passed straight through:

```java
if (property.cascaded() && value != null) {
    validateGraph(rootBean, rootBeanClass, value, effectiveGroups, propertyPath, visited, violations);
}
```

This test exists specifically to confirm that this stays true — that nothing resets the
groups to `Default` (or to nothing) one level down.

**Proof.** The same test, green:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#cascadedProperty_respectsRequestedGroupsDuringTraversal
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.314 s
[INFO] BUILD SUCCESS
```

## Making this compose with M2's composed constraints

*No commit of its own — it is a property of where the first two commits put things. Files to
open: [`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java) (`evaluateConstraint`, and who calls it) next to [`CustomConstraintAuthoringTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/CustomConstraintAuthoringTest.java).*

**Goal.** M2's finishing pass had already rewritten the constraint-evaluation path to
recurse into composing constraints and collapse under `@ReportAsSingleViolation`
(`evaluateConstraint` / `evaluateOwnValidator`). The question wasn't whether cascading and
groups worked in isolation — the tests above already showed that — it was whether bolting
them on top would silently break M2's composed-constraint behavior, since both features now
share the exact same evaluation method.

**What was built.** Being precise about *where* each concern lives, rather than tangling
them together:

- **Group membership is decided once, by the caller**, before `evaluateConstraint` ever
  runs — not recursed into for each composing constraint. A composing constraint's own
  `groups()` isn't consulted separately; the top-level descriptor's group membership is what
  gates the whole composition tree.
- **The graph walk owns cascading and cycle detection**; constraint evaluation stays exactly
  as unaware of the object graph as it was in M2 — it just gets called once per
  group-matching descriptor, at whatever path the walk has reached.
- **`leafBean` had to become a real, separate parameter** from `rootBean` through
  `evaluateConstraint`/`evaluateOwnValidator`, instead of the two always being the same
  object like they were pre-cascading — a violation on `person.address.city` has to report
  `getLeafBean()` as the `Address` instance, not the root `Person`.

All three are visible in one place, the head of `evaluateConstraint`. Nothing in it knows
about groups or about the graph; it receives a `leafBean` it did not compute, evaluates the
descriptor's own validator, then recurses into the composing constraints with the *same*
`leafBean` and no group check of its own:

```java
private <T, A extends Annotation> List<ConstraintViolationImpl<T>> evaluateConstraint(
        T rootBean, Class<T> rootBeanClass, Object leafBean, PathImpl propertyPath, Class<?> declaredType,
        Object value, ConstraintDescriptorImpl<A> descriptor) {

    List<ConstraintViolationImpl<T>> collected = new ArrayList<>();
    if (!descriptor.getConstraintValidatorClasses().isEmpty()) {
        collected.addAll(evaluateOwnValidator(rootBean, rootBeanClass, leafBean, propertyPath, declaredType, value, descriptor));
    }
    for (ConstraintDescriptor<?> composing : descriptor.getComposingConstraints()) {
        @SuppressWarnings("unchecked")
        ConstraintDescriptorImpl<Annotation> composingImpl = (ConstraintDescriptorImpl<Annotation>) composing;
        collected.addAll(evaluateConstraint(rootBean, rootBeanClass, leafBean, propertyPath, declaredType, value, composingImpl));
    }
    // ... collapse under @ReportAsSingleViolation, or return collected
}
```

For the violation the running example produces, those parameters carry: `rootBean` = the
`Person`, `leafBean` = the `Address`, `propertyPath` = `address.city`, `value` = `""`. Swap
`@NotBlank` for a composed constraint and the recursive call below passes that same `Address`
and that same path down to each composing constraint — the leaf bean is the nested object, at
every level of the composition.

The group check you saw in the groups section sits in the *caller*, one line above the call
to this method — so a composed constraint is filtered exactly once, by its own `groups()`,
and its composing constraints are never filtered again.

Only the first of those three is the spec's decision — [Jakarta Bean Validation 3.1, §3.3 *Constraint composition*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintsdefinitionimplementation-constraintcomposition):

> Groups from the main constraint annotation are inherited by the composing annotations. Any
> groups definition on a composing annotation is ignored.

The other two are ours. The spec constrains the *result* — what a `ConstraintViolation`
reports as its leaf bean, its path — and says nothing about where an implementation keeps
its graph walk or how it threads the leaf bean through its evaluation code. Worth being
clear about which is which: the first bullet is conformance, the second and third are
design.

**Proof.** Not a single new test — the existing ones, run *together*, sharing the same code
path instead of just independently:

```
$ cd erasmus-core && ../mvnw -ntp test
[INFO] Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`CustomConstraintAuthoringTest`'s composed constraints and `@ReportAsSingleViolation` cases
pass alongside `CascadingAndGroupsTest`'s 11 — the actual proof that the split above was the
right one, not just a plausible-sounding one.

## Where it stands now

- `@Valid` cascading into a single nested bean reference, with correct multi-segment paths
  (`address.city`) and identity-based cycle detection that terminates on circular and
  self-referencing graphs.
- `Default` group and explicit groups actually filter which constraints run; group
  inheritance expands correctly.
- `@GroupSequence` short-circuits for the single-sequence-group case.
- Composed constraints and `@ReportAsSingleViolation` (M2) still work, now combined with
  cascading and groups in the same evaluation path — proven by running both test suites
  together, not just each in isolation.
- 87 tests total, all green. Full reactor build (`./mvnw -ntp clean install`) succeeds.
- Two conformance bugs found by quoting the spec for this post, both logged in `BUG.md` and
  neither fixed here: `E-001`, `@NotBlank`/`@NotEmpty` accept `null` (they must not, §8.20 and
  §8.21 — an M1 convention that was wrong for these two); `E-002`, cycle detection scoped to
  the whole call instead of the current navigation path (§5.7.1).
- Deliberate scope gaps, documented in `ROADMAP.md`: cascading into collection/array/map
  elements is M4's job (the `ValueExtractor` SPI milestone exists precisely to solve
  container traversal uniformly); mixing a `@GroupSequence` with other unrelated groups in
  one call isn't correctly interleaved.

## What's next

M4: container-element constraints via the `ValueExtractor` SPI —
`List<@NotBlank String>`, `Optional<@Positive Integer>`, `Map<@NotNull String, @Valid
Address>` — which is also where the cascading-into-collections gap from this milestone
gets closed, uniformly, instead of as a one-off.

## Postscript: what quoting the spec turned up

This series has a rule against talking about itself, and this section breaks it once, on
purpose, because a rule of the series changed the outcome of the milestone.

The spec quotes above were not in the first version of this post. The seven commits were
done, the post was written, the proofs were green. Then I asked Claude for one more thing:
quote, in every section, the sentence of Bean Validation 3.1 the section implements —
verbatim, with its section number, fetched from the actual text rather than recalled. To do
that, Claude pulled the 3.1 HTML and read the relevant sections side by side with the code. Three things came
out of that reading that nothing before it had caught:

- **`@NotBlank` and `@NotEmpty` were wrong since M1** (`E-001`). §8.21 and §8.20 say "must
  not be `null`"; our convention said every validator but `@NotNull` accepts `null`, the
  validators did exactly that, and unit tests pinned the wrong behavior down for two
  milestones. The gotcha section of this very post had blamed the *test* for expecting a
  violation on `null`. The test was right.
- **Cycle detection diverges from §5.7.1** (`E-002`). The spec scopes "already validated" to
  the current navigation path; ours is scoped to the whole call. Same termination, different
  answer for an instance shared by two `@Valid` properties. Neither `ROADMAP.md` nor the
  first version of this post noticed — both described what I *meant* to build.
- **A documented gap was half imaginary.** Flattening several plain groups into one unordered
  set is what §6.1.3 prescribes, not a shortcut we took. The real gap is narrower than the
  one we had written up.

Why did this catch what TDD and the roadmap didn't? Because the tests encode what I believed
the spec said, and the roadmap is my reformulation of it — both inherit the same misreading,
and both pass each other's checks. Before the TCK lands at M8, the only independent oracle is
the text itself, and "quote it, verbatim" is a cheap way to force an actual read of it at the
sentence level. Paraphrasing from memory would have produced plausible citations and found
nothing; the cost of fetching a page and copying a sentence is minutes, and it paid for
itself twice in one post.

It does not replace the TCK — it only finds what happens to sit next to the sentences you
went looking for. But it is the first time in this series that a rule about *writing* the
journal fed back into the *code*, and I wanted that recorded once.
