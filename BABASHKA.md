# Babashka Project

Update GitHub star counts in README files.

## Tasks

```bash
bb nrepl              # Start nREPL on port 1337
bb update-stars       # Update star counts
bb update-stars --help
bb update-stars path/to/README.md
bb test               # Run the offline tests
bb refresh            # Refresh README, commit changes, and push main
```

## Weekly profile refresh

`bb refresh` reads the live Pi configuration at `~/.pi/agent/settings.json`.
It uses read-only GitHub queries to find public original Pi packages that Vedang owns.
It also checks already-featured organization packages for Vedang contributions.
A package must declare extensions, skills, prompts, or themes in its `package.json` Pi manifest.
A manifest is a file that declares package resources.

Installed packages belong in the agent tooling section.
Other eligible packages belong in the expandable Unmaintained section.
Here, Unmaintained means absent from the installed package list, not abandoned everywhere.
GitHub redirects resolve Git package names, including the old `vedang/pi-adr` name.
Npm packages match the name in the package manifest.
The refresh accepts string package sources and objects with a `source` field.
If the job cannot identify a source, it stops. This includes local package paths.

Existing descriptions and unrelated entries stay intact, apart from refreshed star badges.
New entries use the GitHub description, with a generic package icon.
Existing entries keep their order. New or moved entries follow them in repository-name order.
Incomplete GitHub discovery or star counts stop the run before any file changes.
The refresh reuses `update-star-counts.clj` for all star badges.

The job requires `bb`, `jj`, and an authenticated `gh` command in the fish login shell.
It requires a fresh empty `jj` change directly on `main`.
This direct-parent rule prevents the job from publishing intervening work.
The job checks this rule before the network queries and again before writing.
A lock under `.jj` prevents two refresh jobs from running together.

If README changes, the job creates one `docs: weekly profile refresh` commit.
It moves `main` to that commit and runs `jj git push -b main` once.
It leaves a fresh empty change after the commit.
If README does not change, the job makes no commit and does not push.
No model calls occur.

If a push fails, the job exits with an error and retains the local commit on `main`.
The error appears in `~/Library/Logs/profile-readme.error.log`.
A later run with no README changes does not retry that push.
Inspect the error and the local history before a manual `jj git push -b main`.

### Install the weekly LaunchAgent

The tracked LaunchAgent runs each Monday at 09:00 local time.
A LaunchAgent is a job that macOS runs for the logged-in user.
The job uses `/opt/homebrew/bin/fish -l -c 'bb refresh'` without a separate `PATH` value.
It writes output under `~/Library/Logs/`.
Loading does not start a refresh. The job has no automatic retry loop.

Installation and loading are manual. Run these commands in fish on Vedang's Mac:

```fish
mkdir -p ~/Library/LaunchAgents ~/Library/Logs
ln -s /Users/vedang/src/vedang/vedang/vedang.root/launchagents/me.vedang.profile-readme.plist ~/Library/LaunchAgents/me.vedang.profile-readme.plist
launchctl enable gui/(id -u)/me.vedang.profile-readme
launchctl bootstrap gui/(id -u) ~/Library/LaunchAgents/me.vedang.profile-readme.plist
```

To unload the job, run this fish command:

```fish
launchctl bootout gui/(id -u) ~/Library/LaunchAgents/me.vedang.profile-readme.plist
```

Do not run `bb refresh` as a preview. If README changes, the command commits and pushes.
Use `bb test` to run tests without changing this repository or contacting GitHub.

## Emacs

Connect via `M-x cider-connect` (host: localhost, port: 1337).

Auto-connect on open:

```elisp
(use-package projectile)

(defun my/babashka-nrepl-hook ()
  (when (and (projectile-project-p)
             (file-exists-p (expand-file-name "bb.edn" (projectile-project-root))))
    (let ((default-directory (projectile-project-root)))
      (start-process "bb-nrepl" "*bb-nrepl*" "bb" "nrepl")
      (run-at-time 1 nil #'cider-connect-clj '(:host "localhost" :port 1337)))))

(add-hook 'clojure-mode-hook #'my/babashka-nrepl-hook)
```