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

## The spec quote

`ROADMAP.md` is our reformulation; the spec is the source. So each section's **Goal** also
quotes the sentence of the originating spec it implements — for Erasmus, Jakarta Bean
Validation **3.1** (say the version: section numbers move between versions) — as a blockquote
with section number, title and a link to the anchor in the spec HTML
(`https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1#<anchor>`).
One or two normative sentences per point, verbatim, fetched from the actual text — never
quoted from memory: a misremembered section number or paraphrase presented as a quote is
worse than no quote.

That closes the chain spec → ROADMAP → commit → test, and it earns its keep in three places:

- **Narrowed scope**: when the spec asks for more than the milestone did (cascading into
  collections, §5.1.3), quote the wider text so the reader sees the gap against the source,
  not just against our own notes.
- **Wrong conventions**: quoting is how `E-001` was found — the spec said `@NotBlank` must
  reject `null`, our convention said otherwise. When the text contradicts the code, the
  section says so plainly and points at the `BUG.md` entry; it does not soften the finding.
- **"Not in the spec"**: don't quote everywhere. When a point is a design decision the spec
  leaves open (where the graph walk lives, how the leaf bean is threaded), say explicitly
  that the spec constrains the result and not the shape — that sentence is as useful as a
  quote, and pretending a citation exists is not an option.

## Proof, not just prose

For each real design decision or mechanism described in the post, pick **one or two** actual
examples and back them with a **real, currently-passing test** — not an invented snippet.
Concretely, for each example:

- Quote the real code from the actual source file (constraint/validator/annotation and the
  test method), copy-pasted, not paraphrased or simplified into pseudo-code.
- Show the code before the command, every time — the fixture (the class and annotations the
  point is about) and the test method itself, trimmed, with a sentence on what the test asks
  for. A bare `expected: <1> but was: <0>` means nothing to someone who has not seen the
  assertion that produced it. This applies to the red run in the goal as much as to the
  green run in the proof.
- Show the actual command used to run just that test (`cd erasmus-core && ../mvnw -ntp test
  -Dtest=SomeTest`) and the **real** Surefire output from actually running it — never a
  fabricated "Tests run: N" line. Run the command again if the post is being edited later
  and the numbers might have drifted.

This is the difference between "here's what the code looks like" and "here's proof it
actually works," and it's the reason this series exists instead of just pointing people at
`ROADMAP.md`.

## One commit per section, and the files to open

Shape the branch so that it reads like the post: **one commit per section, titled the same
way**, so `git log --oneline` is the table of contents and a section can be read next to
`git show` of its commit. Build the commits incrementally and keep every intermediate state
green for the tests it contains — the point is that each diff is small enough to read in
one sitting, alongside the section that explains it. Where two sections genuinely have to
land together (cascading and its cycle detection, say), or a section has no commit of its
own (a design discussion, a test-only check), say so in the section's opening line rather
than forcing a split or a fake commit.

Every section then **opens with the files worth having open** — an italic line naming the
commit and linking the two to five files a reader should look at, with relative links from
`doc/making-of/` (`../../erasmus-core/src/main/java/...`). In the prose, point at the
specific method or lines ("open `ErasmusValidator.java` at `validateGraph`, the first three
lines") rather than only naming the file: the reader has the code on the other half of the
screen, so tell them where to look.

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
- Don't write about "the reader" — write *to* them. The register is a colleague sitting next
  to you looking at the same screen: "look at that last line", "read the whole requirement",
  never "so the reader can see". Second person, or an imperative; the third person turns a
  conversation into a report.
- Don't slip into the passive for your own decisions ("then came the request to…", "it was
  decided…") — first person, always: "then I asked Claude to…". The journal has an author.
