# Babashka Project

Update GitHub star counts in README files.

## Tasks

```bash
bb nrepl              # Start nREPL on port 1337
bb update-stars       # Update star counts
bb update-stars --help
bb update-stars path/to/README.md
```

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