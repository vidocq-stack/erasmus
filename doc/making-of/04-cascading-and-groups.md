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

## What `@Valid` actually does

Every milestone before this one only ever looked at the properties declared directly on
whatever bean you handed to `validate(...)`. Take a `Person` with an `Address` field:

```java
public class Address {
    @NotBlank
    private String city;
}

public class Person {
    private Address address;   // no @Valid yet
}
```

`Address` has its own real constraint (`city` must not be blank), but
`validator.validate(person)` has no reason to know that `Address` even exists as a
constrained type — it just reads `Person`'s own properties, and `address` isn't one of
them, it's just a plain field holding some object. So this happily reports **zero
violations** even if `address.city` is blank. Proven directly, from
`CascadingAndGroupsTest`:

```java
@Test
void withoutValidAnnotation_nestedBeanIsNotChecked() {
    assertTrue(validator.validate(new PersonWithPlainAddress(new Address(""))).isEmpty());
}
```

`@Valid` is the annotation that changes this. Put it on the `address` field, and
`validate(person)` no longer treats `Address` as an opaque value — it descends into it and
validates *its* constraints too, as part of the same call:

```java
public class Person {
    @Valid
    private Address address;
}
```

```java
@Test
void cascading_descendsIntoValidAnnotatedProperty_withDottedPath() {
    Set<ConstraintViolation<PersonWithCascadedAddress>> violations =
            validator.validate(new PersonWithCascadedAddress(new Address("")));

    assertEquals(1, violations.size());
    assertEquals("address.city", violations.iterator().next().getPropertyPath().toString());
}
```

Same broken `Address`, same call shape (`validator.validate(person)`) — the only difference
is the single annotation on the field. Now it comes back with one violation, and its
`getPropertyPath()` reads `"address.city"`, not just `"city"` or `"address"` — enough
information on its own to know both *which* nested object failed and *which* property of it,
without the caller having to separately dig into `getRootBean()`/`getLeafBean()` to figure
that out. That's the entire point of cascading: without it, a bean graph is only ever
validated one flat layer at a time; with it, `validate()` on the root is enough to catch
problems anywhere in the graph it's willing to cascade into.

## How it's built: one property, a multi-segment path

Two things had to change to make the example above work. First, `PathImpl` only knew how to
*be* a single segment before this milestone — there was never a nested property to point
into, so `"address.city"` as a path literally couldn't be constructed. The fix is an
`append`, called once per level of descent:

```java
PathImpl append(String propertyName) {
    List<Path.Node> extended = new ArrayList<>(nodes);
    extended.add(new NodeImpl(propertyName));
    return new PathImpl(List.copyOf(extended));
}
```

Second, `ConstraintMetadataBuilder` needed to know a property carries `@Valid` even when it
has *no constraints of its own* — a plain `@Valid private Address address;`, exactly like
the example above, has zero `ConstraintDescriptor`s on the `address` field itself (the
`@NotBlank` is on `Address.city`, a different class entirely). Small but easy to get wrong:
the builder used to skip any field with no constraint annotations at all, so "cascaded but
otherwise unconstrained" had to become its own reason to keep a property, not a side effect
of already keeping it for its constraints.

## Cycle detection: identity, not `equals`, scoped per group sheet

A circular graph (two beans holding `@Valid` references to each other, or a bean referencing
itself) has to terminate, and revisiting the same node under an unrelated later group can't
be mistaken for a cycle. Both needs are met by a fresh
`Collections.newSetFromMap(new IdentityHashMap<>())` per group sheet (more on sheets below):
identity, so two unrelated-but-`equals`-equal beans aren't confused for the same node; fresh
per sheet, so cycle detection never leaks across independent group evaluations.

The test that actually proves it terminates instead of stack-overflowing, from
`CascadingAndGroupsTest`:

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

Running it:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#circularGraph_terminatesAndValidatesEachNodeOnce
[INFO] Running io.vidocq.erasmus.core.internal.CascadingAndGroupsTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.158 s
[INFO] BUILD SUCCESS
```

`a -> b -> a` — the walk validates `a`, descends into `b`, tries to descend back into `a`,
finds it already visited, and stops. Two violations, not an infinite one.

## A gotcha, found by testing: `@NotBlank` and `null` aren't the bug they look like

First run of the cascading test used `new Address(null)` expecting the nested `@NotBlank
city` to fire. It didn't — zero violations instead of one. Spent a few minutes suspecting
the cascading recursion itself: added a throwaway test dumping `ConstraintMetadataBuilder`'s
output directly, confirmed the metadata was exactly right (`address` cascaded with zero own
constraints, `city` non-cascaded with one `@NotBlank` descriptor) — so the walk itself wasn't
the problem.

The actual answer was the null-safety convention this project has followed since M1:
`@NotBlank`, like every built-in validator except `@NotNull`, must treat `null` as trivially
valid — that's what lets `@NotNull @NotBlank` compose cleanly on the same property instead of
both firing on a `null` value. My test was passing `null` and expecting a *blank-string*
failure mode. Fixed the test (`new Address("")` instead of `new Address(null)`), not the
validator — the validator was already correct, the test's premise wasn't.

## Groups: reading what was already sitting in the attributes map

`ConstraintDescriptorImpl.getGroups()` was hardcoded to `Set.of(Default.class)` since M1 —
nobody had populated a constraint's `groups()` attribute yet to notice it was being ignored.
The attributes map already reads every annotation member generically (that's how `payload()`
got wired through back in M2), so the fix was symmetric with that:

```java
public Set<Class<?>> getGroups() {
    Class<?>[] groups = (Class<?>[]) attributes.get("groups");
    return groups == null || groups.length == 0 ? Set.of(Default.class) : Set.of(groups);
}
```

Group *inheritance* — a group interface extending others should pull in the supers — is a
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

Proven directly — `interface ExtendedGroup extends BaseGroup {}`, a constraint declared
`groups = BaseGroup.class`, requested with `ExtendedGroup.class`:

```java
@Test
void groupInheritance_extendedGroupPullsInBaseGroupConstraints() {
    Set<ConstraintViolation<Item>> violations = validator.validate(new Item(null), ExtendedGroup.class);

    assertEquals(1, violations.size());
    assertEquals("sku", violations.iterator().next().getPropertyPath().toString());
}
```

## `@GroupSequence`: sheets, evaluated one at a time

The requested groups for a `validate(...)` call resolve to an ordered list of "sheets" —
each one a flat set of groups to check as a unit, evaluated in order, stopping at the first
sheet that produces any violation at all. The common case (no explicit sequence) collapses to
one sheet; a single requested group that is itself `@GroupSequence`-annotated expands into
one sheet per step:

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

Both directions, proven — stopping at the first failing step, and passing through when the
first step is clean:

```
$ cd erasmus-core && ../mvnw -ntp test -Dtest=CascadingAndGroupsTest#groupSequence_stopsAtFirstFailingGroup+groupSequence_proceedsToSecondGroupWhenFirstPasses
[INFO] Running io.vidocq.erasmus.core.internal.CascadingAndGroupsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s
[INFO] BUILD SUCCESS
```

**Deliberate scope gap, documented rather than silently wrong**: this only handles a single
requested group that happens to be a sequence. Mixing a sequence with other, unrelated
groups in the same call collapses everything into one unordered sheet instead of correctly
interleaving the short-circuit — rare in practice (most real calls pass either `Default` or
one custom sequence), but a real gap, not an oversight nobody noticed.

## The actual design question: does this compose with M2's composed constraints?

The trickiest part of this milestone wasn't cascading or groups individually — both are
fairly mechanical once you've decided the data structures. It was that M2's finishing pass
had already rewritten the constraint-evaluation path to recurse into composing constraints
and collapse under `@ReportAsSingleViolation` (`evaluateConstraint` / `evaluateOwnValidator`).
Bolting cascading and groups on top without breaking that meant being precise about *where*
each concern lives:

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

Getting this split right meant the full suite — `CustomConstraintAuthoringTest`'s composed
constraints and `@ReportAsSingleViolation` cases alongside the new cascading/groups tests —
had to pass *together*, sharing the same code path, not just independently. It does: 87
tests, all green.

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
