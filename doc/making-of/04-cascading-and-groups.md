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

## What "cascaded validation" even means

The ROADMAP quote above uses the term without defining it, so worth pausing on before
anything else: **cascaded validation** is the spec's name for validation *cascading* —
flowing down — from the root bean a caller hands to `validate(...)`, through the graph of
objects it references, instead of stopping at that root bean's own properties. A `Person`
holding an `Address`, which might itself hold a `Country`: cascaded validation is what lets
one `validate(person)` call reach all the way down that chain and catch a problem anywhere
in it, rather than only ever seeing `Person`'s own directly-declared constraints. `@Valid` is
the annotation that turns cascading on for a given property — see below for exactly what
that looks like.

## Cascading via `@Valid`

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
has *no constraints of its own* — a plain `@Valid private Address address;` has zero
`ConstraintDescriptor`s on the `address` field itself (the `@NotBlank` lives on
`Address.city`, a different class entirely). Small but easy to get wrong: the builder used
to skip any field with no constraint annotations at all, so "cascaded but otherwise
unconstrained" had to become its own reason to keep a property. With that in place, the
recursive graph walk (`validateGraph`) descends into any cascaded property whose value is
non-null, extending the path one segment at a time.

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

**Goal.** A bean graph can point back to itself — two `Node`s holding `@Valid` references to
each other, or a `Node` referencing itself directly. Plain recursive descent through `@Valid`
links has no reason to stop on its own once it hits a cycle; cycle detection has to land in
the *same* change as cascading, not as an afterthought, or the very first circular fixture
anyone writes recurses forever instead of returning. The test for it, written before either
mechanism existed:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest
[ERROR]   CascadingAndGroupsTest.circularGraph_terminatesAndValidatesEachNodeOnce:118 expected: <2> but was: <1>
```

Only the root's own violation came back — not a hang, since cascading itself didn't exist
yet to even attempt the recursion, but proof the two-node case wasn't handled.

**What was built.** A fresh `Collections.newSetFromMap(new IdentityHashMap<>())` per group
sheet (sheets are explained below), checked at the top of every recursive call:

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

## A gotcha, found by testing: `@NotBlank` and `null` aren't the bug they look like

Worth a detour, since it's exactly the kind of thing "goal, then proof" can paper over if
the first attempt at the proof is itself wrong. First draft of the cascading test above used
`new Address(null)` expecting the nested `@NotBlank city` to fire. It didn't — zero
violations instead of one, which looked identical to the real red state shown above. Spent a
few minutes suspecting the cascading recursion itself: added a throwaway test dumping
`ConstraintMetadataBuilder`'s output directly, confirmed the metadata was exactly right
(`address` cascaded with zero own constraints, `city` non-cascaded with one `@NotBlank`
descriptor) — so the walk itself wasn't the problem.

The actual answer was the null-safety convention this project has followed since M1:
`@NotBlank`, like every built-in validator except `@NotNull`, must treat `null` as trivially
valid — that's what lets `@NotNull @NotBlank` compose cleanly on the same property instead of
both firing on a `null` value. The test was passing `null` and expecting a *blank-string*
failure mode. Fixed the test (`new Address("")` instead of `new Address(null)`), not the
validator — the validator was already correct, the test's premise wasn't.

## Groups: only run the constraints that were actually asked for

**Goal.** `validate(bean, SomeGroup.class)` should only evaluate constraints declared under
`SomeGroup` (or under it via inheritance) — not every constraint on the bean regardless of
what was requested. Two tests pin this down: one confirming the *default* call still only
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
(expanded) ones, via a new `GroupsSupport.intersects`, checked once per constraint before it
ever runs.

**Proof.**

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#defaultGroup_onlyEvaluatesDefaultGroupConstraints+explicitGroup_onlyEvaluatesThatGroupsConstraints
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.160 s
[INFO] BUILD SUCCESS
```

### Group inheritance: a case with no honest red state to show

Group inheritance — a group interface extending others should pull in the supers — is a
small recursive walk over `Class.getInterfaces()` in the new `GroupsSupport`:

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

**Goal.** `validate(bean, OrderedSequence.class)`, where `OrderedSequence` is
`@GroupSequence({StepOne.class, StepTwo.class})`, should evaluate `StepOne`'s constraints
first and, if any fail, never even look at `StepTwo`'s. Before sequences existed, the test
for the "stops at the first failure" direction was red:

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

`validate()` loops over these sheets and returns as soon as one produces a non-empty result.

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

**Deliberate scope gap, documented rather than silently wrong**: this only handles a single
requested group that happens to be a sequence. Mixing a sequence with other, unrelated
groups in the same call collapses everything into one unordered sheet instead of correctly
interleaving the short-circuit — rare in practice (most real calls pass either `Default` or
one custom sequence), but a real gap, not an oversight nobody noticed.

## Putting cascading and groups together

**Goal.** The two mechanisms above were built and tested mostly independently — the real
question is whether they compose: does a cascaded property's own constraint still respect
the group that was requested at the *root* `validate()` call? Before groups existed, this
was red too:

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
- Deliberate scope gaps, documented in `ROADMAP.md`: cascading into collection/array/map
  elements is M4's job (the `ValueExtractor` SPI milestone exists precisely to solve
  container traversal uniformly); mixing a `@GroupSequence` with other unrelated groups in
  one call isn't correctly interleaved.

## What's next

M4: container-element constraints via the `ValueExtractor` SPI —
`List<@NotBlank String>`, `Optional<@Positive Integer>`, `Map<@NotNull String, @Valid
Address>` — which is also where the cascading-into-collections gap from this milestone
gets closed, uniformly, instead of as a one-off.
