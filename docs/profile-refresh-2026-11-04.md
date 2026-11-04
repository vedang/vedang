# Profile refresh: November 4, 2026

This review records the choices behind the README refresh for `jeevesroot-tsin.1`.
Jeeves approved the project selection and the small star-updater repair.
No repositories were pinned, pushed, or published.

## Selection evidence

The agent section now lists projects that Vedang created and uses in his current setup.
The current installation takes precedence over older catalog entries and compatibility guides.

Local evidence inspected:

- `~/.pi/agent/settings.json`, linked to `~/src/vedang/agents/agents.root/pi-settings.json`.
- `~/.pi/agent/extensions`, linked through `~/.config/agents/pi-extensions`.
- `~/.claude/settings.json`, including its enabled plugins and hook paths.
- `~/src/vedang/dotfiles/dotfiles.root`, for configuration context.
- `~/.local/bin`, for installed tools. Installation alone does not establish project authorship.

The public [dotagents catalog](https://vedang.me/dotagents/) confirms the active extensions and attributes them to Vedang.
The Pi package list includes `pi-exa`, `pi-load-skill`, `pi-opentelemetry`, and `pi-adr`.
GitHub marks these repositories as original projects, not forks.
GitHub also attributes 65 contributions to Vedang in `unravel-team/pi-adr`.

| README change | Reason |
| --- | --- |
| Rename `agents` to `dotagents` | GitHub redirects the old URL to `vedang/dotagents`. The local installation still uses the `agents` directory name. |
| Remove `pi-learn-stuff` | Absent from the current Pi packages and extension directory. No active Claude integration was found. |
| Remove `pi-prompt-history` | Absent from the current packages and extensions. An older catalog entry and Claude compatibility guide do not establish current use. |
| Remove `pi-antigravity-image-gen` | Absent from the current packages and extensions. No active Claude integration was found. |
| Remove `pi-quizme` | Absent from the current packages and extensions. Its older catalog evidence and compatibility guide describe capabilities, not current use. |
| Remove `pi-simplify-code` | Absent from the current packages and extensions. A ported Claude hook exists in dotagents, but the current Claude hooks do not enable it. |
| Remove `pi-custom-provider-zai` | Absent from the current packages. The separate current model configuration does not prove use of this extension. |
| Add `pi-exa` | Installed Pi package, also listed in the public catalog. |
| Add `pi-load-skill` | Installed Pi package, also listed in the public catalog. |
| Add `pi-opentelemetry` | Installed Pi package, also listed in the public catalog. This is opt-in telemetry, not a claim that every session exports it. |
| Add `unravel-team/pi-adr` | Installed through the old `vedang/pi-adr` locator. Use the canonical repository URL after the GitHub redirect. |

Removal means that current use was not established. It does not mean that the project is abandoned or unused elsewhere.
Forks such as `chrome-cdp-skill`, `modularity`, `shaping-skills`, and `SimpleEnglish` remain outside this section despite installation.
This refresh does not claim those upstream projects as Vedang's creations.

## Other open-source work

Discovery used read-only, paginated GitHub API requests for `users/vedang/repos` and public repositories in `unravel-team` and `EffiScale-Elara`.
The `EffiScale-Elara` public listing returned no repositories.
No private project was featured.

The Clojure section adds two original public repositories with confirmed Vedang contributions:

- [litellm-clj](https://github.com/unravel-team/litellm-clj): 19 contributions in the contributors API, with a shared API for language model providers.
- [DSCloj](https://github.com/unravel-team/DSCloj): 12 contributions in the contributors API, with declarative language model pipelines inspired by DSPy.

The descriptions follow the public project READMEs.
Other candidates, including `instructor-clj`, `effective-agents-clj`, `ek`, and `tablo`, did not list Vedang in their contributor responses.
They are not added on the strength of organization membership alone.
The existing Emacs, Clojure, and unmaintained entries retain their descriptions and order.

## Star counts

All 23 featured repositories now have a star count, including zero-star projects.
Counts are a snapshot from November 4, 2026, not a ranking or a promise of future totals.

Existing badges changed for `pdf-tools` (790 to 851), `alabaster-themes` (18 to 34), `mcp-clojure-sdk` (61 to 65), `clj_fdb` (27 to 28), and `cljc-fsrs` (13 to 17).
The other eight existing badges retain their counts.
Badges were added for ten entries that lacked them or are newly featured.

| Project | Stars |
| --- | ---: |
| vedang/dotagents | 13 |
| vedang/pi-exa | 0 |
| vedang/pi-load-skill | 0 |
| vedang/pi-opentelemetry | 0 |
| unravel-team/pi-adr | 0 |
| vedang/pdf-tools | 851 |
| vedang/alabaster-themes | 34 |
| unravel-team/emacs | 16 |
| vedang/denote-publish | 1 |
| unravel-team/mcp-clojure-sdk | 65 |
| vedang/clj_fdb | 28 |
| open-spaced-repetition/cljc-fsrs | 17 |
| vedang/clj-logging | 7 |
| unravel-team/metaprogramming | 2 |
| unravel-team/litellm-clj | 52 |
| unravel-team/DSCloj | 42 |
| vedang/bloomclj | 20 |
| vedang/nginx-nonewlines | 18 |
| vedang/python-emacs | 11 |
| vedang/emacs-up | 33 |
| vedang/org-mode-crate | 23 |
| vedang/bb-scripts | 12 |
| vedang/csaoid | 21 |

## Recommended pins

Use these six pins to show both established work and the current agent focus:

| Repository | Reason |
| --- | --- |
| `vedang/pdf-tools` | Keep. It is the most widely starred featured project and represents the Emacs work. |
| `unravel-team/mcp-clojure-sdk` | Keep. It connects Clojure with agent integrations and has 65 stars. |
| `unravel-team/litellm-clj` | Add. It shows provider integration work, with confirmed authorship and 52 stars. |
| `unravel-team/DSCloj` | Add. It shows language model pipeline work beyond protocol integration and has 42 stars. |
| `vedang/dotagents` | Add. It provides one entry point to the current setup, original extensions, and published guides. |
| `vedang/alabaster-themes` | Add. It gives the profile a second Emacs project with a visual result and 34 stars. |

This set replaces the current pins for `cljc-fsrs`, `unravel-team/emacs`, `clj-logging`, and `clj_fdb`.
Those projects remain featured in the README.
The recommendation favors current work and breadth, not removal of the older work.
No pin changes were made.

## Validation and updater repair

The original `bb update-star-counts.clj` was first run against a disposable README copy.
It reported zero stars for every repository because it did not decode the JSON response body.
It also inserted duplicate HTML badges instead of replacing them.

The separate repair decodes JSON, replaces complete Markdown or HTML badges, and inserts missing badges in the matching format.
`bb test` runs the regression suite in `update-star-counts-test.clj`.
Before the repair, the suite reported six failures across ten assertions.
After the repair, both tests pass all ten assertions.
The cases cover nonzero and zero JSON counts, Markdown and HTML badges, missing badges, and repeat updates without duplicates.

Validation results:

- `bb update-stars`: 23 repositories updated, none skipped.
- Independent `gh api repos/<owner>/<repo>` reads: all 23 counts match, and all repositories are public.
- `pandoc --from=gfm --to=html5 README.md`: successful render.
- All 39 unique targets extracted from the rendered HTML: 38 HTTPS targets returned HTTP 200, and the local banner exists.
- The seven HTML list items and the expandable section retain their closing tags.
- `git diff --check`: passes. Jujutsu has no `diff --check` option, so this command uses Git.
- `make format`, `make check`, and `make test`: unavailable because this repository has no Makefile. These are not code failures.

This repository has no `AGENTS.md` or local Beads ledger.
The coordinator owns issue completion and ledger synchronization.
