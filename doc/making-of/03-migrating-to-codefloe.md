# Migrating the whole workspace from Codeberg to Codefloe

*Part 3 of the [Erasmus making-of series](../../MAKING-OF.md). Continues from
[part 2](02-full-constraint-set-and-interpolation.md), where M2 finished. This one isn't a
milestone post — it's an infrastructure story, the same kind as part 1's tooling detour and
GPG/governance setup, except this time it happened mid-project instead of before the first
line of code.*

Erasmus, and the rest of the Vidocq workspace with it, moved its git hosting off Codeberg
and onto a new instance, Codefloe. Not something I decided — Yann, the co-maintainer, pinged
me on Slack (translated from French, like every quote in this section):

> **Yann:** Hey, remember to create your account on codefloe.com and let me know once it's
> done — no account, no PR. Also, keep the same username, or tell me so I can update the CLA
> accordingly.
>
> **sunix:** ok, kept the same name, sunix, done!
>
> **Yann:** Thanks, you're added to the dev team now.

Account created, same handle, added to the team. Then the actual migration steps, also from
Yann:

> **Yann:** Also, remember to add your SSH key on Codefloe:
>
> ```bash
> # 1. Check the current state (origin should point to codeberg.org)
> git remote -v
>
> # 2. Repoint origin at Codefloe (SSH)
> git remote set-url origin git@codefloe.com:Vidocq/erasmus.git
> #    — or over HTTPS if you don't have an SSH key:
> #    git remote set-url origin https://codefloe.com/Vidocq/erasmus.git
>
> # 3. Register the host key, then verify SSH access
> ssh-keyscan codefloe.com >> ~/.ssh/known_hosts
> ssh -T git@codefloe.com        # expected: "Hi there, sunix! You've successfully authenticated..."
>
> # 4. Resync
> git fetch --prune origin
> git status -sb
> ```

## Two keys needed registering on the new account, not one

A fresh Codefloe account doesn't inherit anything from the Codeberg one, even though both
are "me." Two separate credentials needed re-registering before I could do anything useful:
the SSH key (to push/pull at all) and the GPG key (for commits to show as signed and
verified — one of governance's three non-negotiable pillars from part 1, alongside the CLA
and DCO). Easy to do the SSH half and consider yourself done, since that's the one that
blocks you immediately; the GPG half doesn't block a `git push`, so it's the one that's easy
to quietly forget.

### GPG first

Same key already attached to my Codeberg account — RSA 2048, generated back in 2014, the one
from part 1's GPG setup story. Verification status isn't a property of the *key*, though,
it's a property of the *account* it's attached to on a given instance, so the exact same key
needed adding to Codefloe too:

```bash
gpg --export --armor sunix@sunix.org
```

Pasted the resulting armored block into Codefloe's Settings → SSH / GPG keys → "Manage GPG
keys" → Add key. Rather than just trust the web UI's confirmation banner, checked it against
the actual API:

```
$ tea api --login codefloe user/gpg_keys
[{"key_id":"4F43AFF6BF10DAEC", ...,
  "emails":[{"email":"sunix@sunix.org","verified":true}], ...}]
```

`key_id` matches, `email` matches, `verified` is `true` — the same key `gpg
--list-secret-keys` shows locally, now actually attached to the Codefloe account and ready to
back a "Verified" badge on the next signed commit there.

### Then SSH: permission denied

Same story, different key. A new account on a different Forgejo instance doesn't inherit
your SSH key either, and the first attempt went the way these things always go: try it
anyway, watch it fail, then fix it properly.

```
$ ssh -T git@codefloe.com
git@codefloe.com: Permission denied (publickey,gssapi-keyex,gssapi-with-mic).
```

Added the same public key to the new Codefloe account (Settings → SSH / GPG keys → Add key),
named it `mylaptop`:

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINjfoVOcpyEQmAf8HbcJ3DjsmF1ITFehFCDNZG57Ixhu sunix@sunix.org
```

then the exact same command succeeded:

```
$ ssh -T git@codefloe.com
Hi there, sunix! You've successfully authenticated with the key named mylaptop, but Forgejo
does not provide shell access.
```

Worth a note for whoever reads this later wondering if publishing a screenshot of that "Add
key" screen leaks anything: no, for either key. Both an SSH *public* key and a GPG *public*
key are public by definition — that's the entire point of asymmetric key pairs. Only the
private halves, which never left the machine and never appear in a screenshot of the web UI,
would matter.

## Then: repointing `origin` for `erasmus`

```bash
git remote set-url origin git@codefloe.com:Vidocq/erasmus.git
git fetch --prune origin
git pull --ff-only
```

The interesting part wasn't the mechanics — it was what `fetch` actually returned. I'd
expected an empty mirror waiting for a first push; instead:

```
From codefloe.com:Vidocq/erasmus
 - [deleted]         (none)     -> origin/docs/making-of-milestone-provenance
   f0e5ee2..8d7f0c8  main       -> origin/main
```

`main` was already 9 commits ahead of what I had locally. Yann had already migrated the
repository's own content — `pom.xml` and CI workflow URLs repointed at codefloe.com, a new
merge-bot workflow, a whole Antora documentation component — before I'd even finished
registering my key. A straightforward fast-forward pull picked all of it up cleanly.

## Extending it to the rest of the workspace

Erasmus is one of twenty repositories `mani.yaml` tracks in this workspace. Yann's
instructions were erasmus-specific, but the migration obviously wasn't — so the same
treatment needed to apply everywhere `mani.yaml` points at `codeberg.org`:

```bash
sed -i 's#git@codeberg\.org#git@codefloe.com#g' mani.yaml
```

Twenty `url:` lines updated in one pass — that file is the canonical source for where a
fresh `mani sync` would clone from, so it needed fixing regardless of what any individual
checkout's `origin` currently pointed at. Then, for each of the other eighteen already-cloned
repositories (`erasmus` and `mani.yaml` were already done):

```bash
git remote set-url origin "git@codefloe.com:Vidocq/$repo.git"
git fetch --prune origin
git pull --ff-only
```

Same surprise as `erasmus`, repeated eighteen times over: every single repository had real,
independent work already sitting on Codefloe — `v0.3.0` tags, `release/0.3.0` and
`docs/0.3.0` branches, stale finished-review branches (`pr/ybl/...`) pruned away. This wasn't
a bare mirror waiting to be filled; Codefloe had already become the actual working host by
the time I got to it, ahead of my own local checkouts. Every fast-forward pull succeeded
without a single conflict — clean history all the way through, which is the whole point of
"stop pushing to the old host the moment the new one is live."

`governance` was the one outlier: its `origin` had been HTTPS
(`https://codeberg.org/Vidocq/governance.git`) even though `mani.yaml` had always declared it
as SSH — a pre-existing drift, not something the migration caused. Folded it into the same
SSH-on-Codefloe treatment as everything else while I was there.

## The blind spot: `vidocq-workspace` itself

Twenty repositories migrated, `mani.yaml` edited — and I still managed to miss one thing:
`mani.yaml` doesn't live in a vacuum, it lives in the `vidocq-workspace` repository, which
has its own `origin`. That one was still pointing at `codeberg.org` the entire time, and it
never occurred to me to check, because I was thinking of it as "the folder my `sed` ran in,"
not as "a twenty-first repository that also needs migrating."

Found out the hard way once I actually tried to open a pull request for the `mani.yaml`
change: pushing required `origin` to point somewhere real, so I went to repoint it the same
way as everything else — and `git fetch` came back with a real surprise:

```
## main...origin/main [ahead 1, behind 8]
```

*Ahead* one commit — a local, never-pushed commit that had added `erasmus` (and
`governance`) to `mani.yaml`. *Behind* eight — because Yann had already migrated this repo's
`mani.yaml` too, on Codefloe, along with several things I didn't have locally at all (a new
`pages` project, flat-worktree handling for `vidocq-docs`). My earlier `sed` across the
twenty URLs, done in blissful ignorance of any of this, turned out to be entirely moot — a
fast-forward pull replaced it outright with the real, already-fixed, more complete version.

The one thing that *wasn't* moot: `erasmus` genuinely was missing from Codefloe's
`mani.yaml`, in a way the URL fix hadn't touched. That became its own small, honest PR
(add the entry, nothing else) rather than trying to force my stale local edit through.

Lesson worth keeping: fixing every leaf repo in a workspace doesn't mean the workspace
repo itself is exempt from the exact same check. It's easy to treat "the folder mani.yaml
happens to sit in" as scaffolding rather than as a repository with its own remote — right
up until you need to push to it.

## Where it stands now

- All 20 repositories in the workspace, plus `mani.yaml` itself, point at
  `git@codefloe.com:Vidocq/<repo>.git` over SSH.
- Every repository fast-forwarded cleanly onto what was already on Codefloe — no
  conflicts, no divergent history to reconcile.
- Both keys are registered on the new Codefloe account — SSH (verified via `ssh -T`) and
  GPG (verified via the API, not just the UI's say-so) — so pushes work and signed commits
  show as verified.
- `feat/m3-cascading-and-groups` (the M3 branch and its draft PR) survived the migration
  intact — Codefloe has it too, not just `main`.
- Two follow-up PRs came out of double-checking the migration rather than assuming it was
  complete: `mani.yaml` was missing an `erasmus` entry entirely (never registered as a mani
  project, even after the URL fixes), and `governance`'s `CONTRIBUTING.md`/`README.md`/
  `CLA.md` still referenced Codeberg everywhere — clone URLs, the `tea login` instructions,
  "Codeberg username" wording — none of it touched by the earlier CI/`pom.xml` migration
  commits.

## What's next

Back to the actual roadmap: M3, cascading and groups, now against the new host.
