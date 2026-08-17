# AGENTS.md — writing a making-of post

Conventions for `doc/making-of/`, the per-milestone blog-style build log referenced from the
root [`MAKING-OF.md`](../../MAKING-OF.md). Read this before adding a new post or editing an
existing one.

## One post per milestone (or milestone group)

One file per milestone, or per small group of milestones that naturally belong together (M0
scaffolding folded into the same post as M1, for instance). Filename: `NN-slug.md`, numbered
in reading order, kebab-case slug describing the post's actual content — not just `m3.md`.
Add the new post to the numbered list in the root `MAKING-OF.md` when it's added here.

## Opening

Every post starts with:

1. An H1 that names what the post covers (not just "M2" — "M2: the full built-in constraint
   set, message interpolation, and custom constraints").
2. An italic line linking back to the series index (`../../MAKING-OF.md`) and to the
   previous post, one sentence on what that previous post left off with. Both links are
   relative — a post never assumes it's read from the repo root.
3. A short recap paragraph (no heading) of where the previous post left off: 3-5 sentences,
   concrete numbers (test count, what's implemented, what's explicitly still missing) rather
   than vibes. Long enough to make the post standalone-readable, short enough that it isn't
   itself a full section — this is a reminder, not a rewrite of the previous post.
4. A transition sentence into the milestone this post covers ("Then it was time to start
   M2."), followed by the **ROADMAP quote** (see below).

## The ROADMAP quote

Right after the transition into the milestone, quote `ROADMAP.md`'s own **Scope spec** and
**Deliverable** lines for that milestone, verbatim, as a blockquote:

```markdown
> **Scope spec:** remaining built-in constraints, message interpolation with EL-subset
> support.
>
> **Deliverable:** all built-in constraints implemented and unit-tested for the narrowed
> target-type set above, locale-aware message interpolation (default + French bundles,
> EL-subset `${...}` expressions), and custom constraint authoring (composed constraints,
> `@ReportAsSingleViolation`, multi-target-type `validatedBy()`).
```

Why bother, rather than just describing the milestone in your own words: it keeps the blog
honest against the actual source of truth. `ROADMAP.md` is the authoritative, living
per-task status (checkboxes, ✅/🚧/❌); this quote is illustrative narration, not a
duplicate ledger. Two rules that follow from that:

- **Quote it at the state it's in when the post is finished, not when the milestone
  started.** If a milestone lands in several passes (M2 did — built-in constraints first,
  then locale/EL/custom-constraint-authoring in a second pass), quote the *final*,
  fully-done wording once the whole milestone is closed, not the "partial" wording from the
  first pass. Don't leave a stale "(partial)" quote sitting in a post that then goes on to
  describe the completed work — readers reading top to bottom would trip over the
  contradiction immediately.
- **Never invent a Scope spec/Deliverable that doesn't exist in `ROADMAP.md`.** If the
  milestone's `ROADMAP.md` entry needs updating to reflect what actually shipped, update
  `ROADMAP.md` first, then quote the updated text — the post and the roadmap must never
  diverge.

## Proof, not just prose

For each real design decision or mechanism described in the post, pick **one or two** actual
examples and back them with a **real, currently-passing test** — not an invented snippet.
Concretely, for each example:

- Quote the real code from the actual source file (constraint/validator/annotation and the
  test method), copy-pasted, not paraphrased or simplified into pseudo-code.
- Show the actual command used to run just that test (`cd erasmus-core && ../mvnw -ntp test
  -Dtest=SomeTest`) and the **real** Surefire output from actually running it — never a
  fabricated "Tests run: N" line. Run the command again if the post is being edited later
  and the numbers might have drifted.

This is the difference between "here's what the code looks like" and "here's proof it
actually works," and it's the reason this series exists instead of just pointing people at
`ROADMAP.md`.

## Closing

End the post with its own **`## Where it stands now`** and **`## What's next`**, reflecting
the state genuinely true *at the time this post was written* — a snapshot, not a living
summary. When a later post covers the next milestone, do **not** go back and update an
earlier post's "where it stands"/"what's next" to reflect what happened after — those
sections are frozen history on purpose; only the newest post's closing sections describe the
current state of the project. If a later post already exists, end with a short `---` +
"Next up: [linked post]" line instead of (or in addition to) "What's next"; if this is the
newest post, "What's next" is the real forward-looking one.

Do **not** add a "Why I'm writing this down" or similar meta-justification section — that
belongs once, in the series intro in the root `MAKING-OF.md`, not repeated per post.

## What NOT to do

- Don't fabricate test output, ever — if you haven't actually run the command, don't paste a
  result.
- Don't let a post's ROADMAP quote go stale once the milestone it describes is fully done.
- Don't repeat a full milestone recap as its own top-level section with a heading — a short
  paragraph under the intro is enough (see "Opening" above).
- Don't add per-post meta-commentary about why the series exists — said once, in the index.
