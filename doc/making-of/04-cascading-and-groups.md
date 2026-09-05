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
not the spec's — which is exactly why the quote sits here, so the reader can see the whole
requirement and not just the half we shipped.

Before any of this existed, the test written for exactly that behavior was red:

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

Second, `ConstraintMetadataBuilder` needed to know a property carries `@Valid` even when it
has *no constraints of its own* — look for `isCascaded` in the builder, and for the new
`cascaded` component on `PropertyMetadata` it feeds — a plain `@Valid private Address address;` has zero
`ConstraintDescriptor`s on the `address` field itself (the `@NotBlank` lives on
`Address.city`, a different class entirely). Small but easy to get wrong: the builder used
to skip any field with no constraint annotations at all, so "cascaded but otherwise
unconstrained" had to become its own reason to keep a property. With that in place, open
`ErasmusValidator.java` at `validateGraph`: the recursive walk descends into any cascaded
property whose value is non-null, extending the path one segment at a time — and note that
`evaluateConstraint` now takes a `leafBean` distinct from `rootBean`, because a violation on
`person.address.city` has to report the `Address` as its leaf, not the `Person`.

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
The test for it, written before either mechanism existed:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.circularGraph_terminatesAndValidatesEachNodeOnce:118 expected: <2> but was: <1>
```

Only the root's own violation came back — not a hang, since cascading itself didn't exist
yet to even attempt the recursion, but proof the two-node case wasn't handled.

**What was built.** A fresh `Collections.newSetFromMap(new IdentityHashMap<>())`, checked at
the top of every recursive call. In this commit it is one set per `validate()` call; the
`@GroupSequence` commit later narrows that to one per group *sheet* (explained there):

```java
private <T> void validateGraph(..., Set<Object> visited, ...) {
    if (!visited.add(currentBean)) {
        return;
    }
    // ... look at currentBean's own properties, recurse into cascaded ones
}
```

Identity (`IdentityHashMap`), not `equals()` — two unrelated beans that happen to be
`equals()`-equal must never be confused for the same graph node. Fresh per group sheet, not
per top-level `validate()` call, so revisiting the same bean under a later, independent
group is never mistaken for a cycle.

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
test's premise the mistake.

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
group's. Both were red before groups existed at all:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.defaultGroup_onlyEvaluatesDefaultGroupConstraints:156 expected: <1> but was: <2>
[ERROR]   CascadingAndGroupsTest.explicitGroup_onlyEvaluatesThatGroupsConstraints:164 expected: <1> but was: <2>
```

Two violations instead of one, both times — because the engine evaluated every constraint
unconditionally, `groups` argument or not.

**What was built.** `ConstraintDescriptorImpl.getGroups()` was hardcoded to
`Set.of(Default.class)` since M1 — nobody had populated a constraint's `groups()` attribute
yet to notice it was being ignored. The attributes map already reads every annotation member
generically (that's how `payload()` got wired through back in M2), so the fix was symmetric
with that:

```java
public Set<Class<?>> getGroups() {
    Class<?>[] groups = (Class<?>[]) attributes.get("groups");
    return groups == null || groups.length == 0 ? Set.of(Default.class) : Set.of(groups);
}
```

The graph walk then skips any constraint whose declared groups don't intersect the requested
ones, via a new `GroupsSupport.intersects`, checked once per constraint before it ever runs.
(Expansion of the requested groups — inheritance — is the next commit.)

**Proof.**

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#defaultGroup_onlyEvaluatesDefaultGroupConstraints+explicitGroup_onlyEvaluatesThatGroupsConstraints
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.160 s
[INFO] BUILD SUCCESS
```

### Group inheritance: a case with no honest red state to show

*Commit `feat(m3): group inheritance`. File to open: [`GroupsSupport.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/GroupsSupport.java), `expand` and `collect`.*

[Jakarta Bean Validation 3.1, §5.4.1 *Group inheritance*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-groupsequence-groupinheritance) defines it in one sentence:

> For a given interface Z, constraints marked as belonging to the group Z (i.e. where the
> annotation element `groups` contains the interface Z) or any of the super interfaces of Z
> (inherited groups) are considered part of the group Z.

Which is a recursive walk over `Class.getInterfaces()` in the new `GroupsSupport`, and
nothing more:

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

Worth being honest about this one rather than forcing it into the same shape as the rest:
its test — `interface ExtendedGroup extends BaseGroup {}`, a constraint declared under
`BaseGroup`, requested with `ExtendedGroup.class` — actually **passed even before any group
support existed**, purely by coincidence. With no filtering at all, every constraint ran
unconditionally regardless of which group was requested, and this fixture only has one
constraint in play — so "run everything" and "run the right thing" produce the same answer
when there's nothing else competing for attention. A passing test isn't always proof; it
took the `Default`-vs-explicit tests above (which *do* have two competing constraints) to
actually expose that groups weren't implemented yet. Real proof, now that inheritance is
actually implemented on purpose rather than accidentally correct:

```java
@Test
void groupInheritance_extendedGroupPullsInBaseGroupConstraints() {
    Set<ConstraintViolation<Item>> violations = validator.validate(new Item(null), ExtendedGroup.class);

    assertEquals(1, violations.size());
    assertEquals("sku", violations.iterator().next().getPropertyPath().toString());
}
```

## `@GroupSequence`: stopping at the first failing step

*Commit `feat(m3): @GroupSequence -- stop at the first failing step`. Files to open: [`GroupsSupport.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/GroupsSupport.java)
(`resolve` becomes `resolveSheets`) and [`ErasmusValidator.java`](../../erasmus-core/src/main/java/io/vidocq/erasmus/core/internal/ErasmusValidator.java) (the per-sheet loop at the top of
`validate`, `validateProperty`, `validateValue`).*

**Goal.** `validate(bean, OrderedSequence.class)`, where `OrderedSequence` is
`@GroupSequence({StepOne.class, StepTwo.class})`, should evaluate `StepOne`'s constraints
first and, if any fail, never even look at `StepTwo`'s — [Jakarta Bean Validation 3.1, §5.4.2 *Group sequence*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-groupsequence-groupsequence):

> Each group in a group sequence must be processed sequentially in the order defined by
> `@GroupSequence.value` when the group defined as a sequence is requested. [...] if one of
> the groups processed in the sequence generates one or more constraint violations, the groups
> following in the sequence must not be processed.

Before sequences existed, the test for the "stops at the first failure" direction was red:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.groupSequence_stopsAtFirstFailingGroup:222 expected: <1> but was: <2>
```

Both steps' violations came back, because — same root cause as plain groups above —
nothing was filtering by group yet, sequence or not.

**What was built.** The requested groups for a `validate(...)` call resolve to an ordered
list of "sheets" — each one a flat set of groups to check as a unit, evaluated in order,
stopping at the first sheet that produces any violation at all:

```java
static List<List<Class<?>>> resolveSheets(Class<?>[] requestedGroups) {
    Class<?>[] groups = requestedGroups.length == 0 ? new Class<?>[] {Default.class} : requestedGroups;
    if (groups.length == 1 && groups[0].isAnnotationPresent(GroupSequence.class)) {
        List<List<Class<?>>> sheets = new ArrayList<>();
        for (Class<?> step : groups[0].getAnnotation(GroupSequence.class).value()) {
            sheets.add(List.copyOf(expand(step)));
        }
        return List.copyOf(sheets);
    }
    // ... single collapsed sheet otherwise
}
```

`validate()` loops over these sheets and returns as soon as one produces a non-empty result —
with a fresh visited set per sheet, which is the narrowing the cycle-detection section
promised: revisiting a bean under a later, independent sheet is never mistaken for a cycle.

**Proof.** Both directions — stopping at the first failing step, and passing through when
the first step is clean:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#groupSequence_stopsAtFirstFailingGroup+groupSequence_proceedsToSecondGroupWhenFirstPasses
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s
[INFO] BUILD SUCCESS
```

Small aside, same flavor as group inheritance above: the "proceeds to the second group"
direction of this pair also happened to already read correctly before sequences existed —
with `StepOne` passing and only `StepTwo` failing, "run everything unconditionally" and
"stop at the first failure, then check the next" land on the same single violation. Only the
"stops at the first *failing* step" direction (both steps would fail if evaluated) could
actually tell the two implementations apart, which is why that's the one quoted as red above.

**Deliberate scope gap, documented rather than silently wrong** — and quoting the spec
actually shrinks it. Several *plain* groups in one call collapsing into one unordered sheet is
not a gap at all; it is what [Jakarta Bean Validation 3.1, §6.1.3 *groups*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#validationapi-validatorapi-groups) prescribes: "When more than one group is evaluated
and passed to the various validate methods, order is not constrained. It is equivalent to the
validation of a group G inheriting all groups". The gap is narrower: when one of several
requested groups is *itself* a sequence, §5.4.2 says "each composed group must respect the
sequence order as well", and we flatten it instead. Rare in practice (most real calls pass
either `Default` or one custom sequence), but a real divergence, not an oversight.

## Putting cascading and groups together

*Commit `test(m3): cascading and groups together`. One file: [`CascadingAndGroupsTest.java`](../../erasmus-core/src/test/java/io/vidocq/erasmus/core/internal/CascadingAndGroupsTest.java) — nothing in
`erasmus-core/src/main` changes.*

**Goal.** The two mechanisms above were built and tested mostly independently — the real
question is whether they compose: does a cascaded property's own constraint still respect
the group that was requested at the *root* `validate()` call? The spec answers in one line,
[Jakarta Bean Validation 3.1, §5.7.1 *Object graph validation*](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#constraintdeclarationvalidationprocess-validationroutine-graphvalidation): "`@Valid` is an orthogonal concept to the notion of group. If two groups are in
sequence, the first group must pass for all associated objects before the second group is
evaluated." Orthogonal, so the requested groups travel down the graph unchanged. Before groups
existed, this was red too:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.cascadedProperty_respectsRequestedGroupsDuringTraversal:261 expected: <1> but was: <0>
```

**What was built.** Nothing new, mechanically — the graph walk in `validateGraph` already
threads `effectiveGroups` down through every recursive call, unchanged as it descends. This
test exists specifically to confirm that threading actually happens rather than resetting to
`Default` (or nothing) at each level.

**Proof.**

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
