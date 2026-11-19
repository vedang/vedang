#!/usr/bin/env bb
(ns refresh-profile
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]))

(load-file (str (.getParent (.getCanonicalFile (java.io.File. *file*))) "/update-star-counts.clj"))

(defn command
  [& args]
  (let [{:keys [exit out err]} @(process/process args {:out :string, :err :string})]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " args) "\n" err) {:exit exit})))
    out))

(defn github
  [endpoint & [optional?]]
  (let [{:keys [exit out err]} @(process/process ["gh" "api" "--method" "GET" endpoint]
                                                 {:out :string, :err :string})
        data (when-not (str/blank? out) (json/parse-string out true))]
    (cond (zero? exit) data
          (and optional? (= "404" (:status data))) nil
          :else (throw (ex-info (str "GitHub query failed: " endpoint "\n" err) {:exit exit})))))

(defn github-pages
  [endpoint]
  (loop [page 1
         result []]
    (let [items (github (str endpoint "?per_page=100&page=" page))]
      (when-not (sequential? items) (throw (ex-info "Expected a GitHub list" {:endpoint endpoint})))
      (let [result (into result items)]
        (if (= 100 (count items)) (recur (inc page) result) result)))))

(defn manifest
  [full-name]
  (when-let [file (github (str "repos/" full-name "/contents/package.json") true)]
    (when-not (= "base64" (:encoding file))
      (throw (ex-info "Expected a base64 package.json" {:repo full-name})))
    (json/parse-string (String. (.decode (java.util.Base64/getMimeDecoder) ^String (:content file))
                                "UTF-8")
                       true)))

(defn pi-package?
  [repo]
  (let [pi (get-in repo [:manifest :pi])]
    (and (not (:private repo))
         (not (:fork repo))
         (or (= "vedang" (get-in repo [:owner :login]))
             (some #(= "vedang" (:login %)) (:contributors repo)))
         (map? pi)
         (some seq (map pi [:extensions :skills :prompts :themes])))))

(defn git-repo
  [source]
  (when-let [[_ owner repo] (re-find #"github\.com[/:]([A-Za-z0-9_-]+)/([A-Za-z0-9._-]+)" source)]
    (str owner "/" (str/replace repo #"\.git$" ""))))

(defn active-packages
  [settings]
  (reduce (fn [result package]
            (let [source (if (string? package) package (:source package))]
              (cond (and (string? source) (str/starts-with? source "npm:"))
                      (if-let [[_ name] (re-find #"^npm:((?:@[^/]+/)?[^@]+)(?:@.+)?$" source)]
                        (update result :npm conj name)
                        (throw (ex-info "Invalid npm package source" {:source source})))
                    (and (string? source) (git-repo source))
                      (update result :git conj (git-repo source))
                    :else (throw (ex-info
                                   "Cannot identify installed package; refusing to infer inactivity"
                                   {:source source})))))
    {:git #{}, :npm #{}}
    (:packages settings)))

(defn escape-html
  [s]
  ;; Repository descriptions are text, not executable HTML or Markdown.
  (str/escape s
              {\& "&amp;",
               \< "&lt;",
               \> "&gt;",
               \" "&quot;",
               \' "&#39;",
               \` "&#96;",
               \[ "&#91;",
               \] "&#93;",
               \* "&#42;",
               \_ "&#95;",
               \\ "&#92;"}))

(defn new-entry
  [repo]
  ;; HTML inline markup works in both Markdown bullets and the HTML list.
  (str "📦 <strong><a href=\"https://github.com/" (:full_name repo)
       "\">" (escape-html (:name repo))
       "</a></strong> — " (escape-html
                            (str/replace (or (:description repo) (:name repo)) #"[\r\n]+" " "))))

(defn entry-repo
  [line]
  (when-let [[_ owner repo] (re-find update-star-counts/github-pattern line)] (str owner "/" repo)))

(defn as-html
  [line]
  (let [body (str/replace line #"^- " "")]
    (str "  <li>"
         (-> body
             (str/replace #"\*\*\[([^\]]+)\]\((https://github\.com/[^)]+)\)\*\*"
                          "<strong><a href=\"$2\">$1</a></strong>")
             (str/replace #"`([^`]+)`" "<code>$1</code>"))
         "</li>")))

(defn as-markdown
  [line]
  (str "- "
       (-> line
           str/trim
           (str/replace #"^<li>|</li>$" ""))))

(defn refresh-sections
  [content settings repositories]
  (let [active (active-packages settings)
        packages (->> repositories
                      (filter pi-package?)
                      (sort-by :full_name))
        names (set (map :full_name packages))
        active? (fn [repo]
                  (or (contains? (:git active) (:full_name repo))
                      (contains? (:npm active) (get-in repo [:manifest :name]))))
        lines (vec (str/split content #"\n" -1))
        start (.indexOf lines "### Agent tooling & practical automation")
        end (first (keep-indexed #(when (and (> %1 start) (str/starts-with? %2 "### ")) %1) lines))
        summary (.indexOf
                  lines
                  "<summary><strong>Unmaintained projects that I'm proud of</strong></summary>")
        ul (first (keep-indexed #(when (and (> %1 summary) (= %2 "<ul>")) %1) lines))
        close (first (keep-indexed #(when (and ul (> %1 ul) (= %2 "</ul>")) %1) lines))]
    (when-not (and (<= 0 start) end (<= 0 summary) ul close (< end ul))
      (throw (ex-info "README section structure changed; refusing to rewrite" {})))
    (let [agent-lines (subvec lines (inc start) end)
          old-lines (subvec lines (inc ul) close)
          all-entries (concat agent-lines old-lines)
          existing (into {} (keep #(when-let [repo (entry-repo %)] [repo %]) all-entries))
          occurrences (frequencies (keep entry-repo all-entries))
          _ (when (some #(> (get occurrences % 0) 1) names)
              (throw (ex-info "Duplicate managed package entries" {})))
          keep-line? (fn [section line]
                       (let [name (entry-repo line)
                             repo (some #(when (= name (:full_name %)) %) packages)]
                         (or (nil? repo) (= section (boolean (active? repo))))))
          additions (fn [to-active?]
                      (for [repo packages
                            :when (= to-active? (boolean (active? repo)))
                            :let [name (:full_name repo)
                                  old (get existing name)]
                            :when (not (some #(= name (entry-repo %))
                                             (if to-active? agent-lines old-lines)))]
                        (if to-active?
                          (if old (as-markdown old) (str "- " (new-entry repo)))
                          (if old (as-html old) (str "  <li>" (new-entry repo) "</li>")))))
          kept-agent (vec (filter #(keep-line? true %) agent-lines))
          ;; Insert before the section's trailing blank lines, preserving those bytes.
          split (inc (or (last (keep-indexed #(when-not (str/blank? %2) %1) kept-agent)) -1))]
      (str/join "\n"
                (concat (subvec lines 0 (inc start))
                        (subvec kept-agent 0 split)
                        (additions true)
                        (subvec kept-agent split)
                        (subvec lines end (inc ul))
                        (filter #(keep-line? false %) old-lines)
                        (additions false)
                        (subvec lines close))))))

(defn discover
  [content]
  (let [owned (->> (github-pages "users/vedang/repos")
                   (remove #(or (:private %) (:fork %))))
        others (->> (update-star-counts/extract-github-repos content)
                    (remove #(= "vedang" (second %)))
                    (map (fn [[_ owner repo]] (github (str "repos/" owner "/" repo)))))]
    (->> (concat owned others)
         (map (fn [repo]
                (let [repo (assoc repo :manifest (manifest (:full_name repo)))]
                  (if (and (get-in repo [:manifest :pi])
                           (not= "vedang" (get-in repo [:owner :login])))
                    (assoc repo
                      :contributors (github-pages (str "repos/" (:full_name repo) "/contributors")))
                    repo))))
         (filter pi-package?)
         vec)))

(defn canonical-settings
  [settings]
  (let [active (active-packages settings)
        aliases (into {}
                      (for [name (sort (:git active))]
                        [name (:full_name (github (str "repos/" name)))]))]
    (update settings
            :packages
            (fn [packages]
              (mapv (fn [package]
                      (let [source (if (string? package) package (:source package))]
                        (if-let [canonical (get aliases (git-repo source))]
                          (str "git:github.com/" canonical)
                          package)))
                packages)))))

(defn assert-empty-change!
  []
  (when-not (= "true" (str/trim (command "jj" "log" "--no-graph" "-r" "@" "-T" "empty")))
    (throw
      (ex-info
        "Refusing refresh: working jj change contains unrelated changes. Start a fresh empty change."
        {}))))

(defn assert-refresh-base!
  []
  (assert-empty-change!)
  (when-not (= (command "jj" "log" "--no-graph" "-r" "@-" "-T" "commit_id")
               (command "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id"))
    (throw
      (ex-info
        "Refusing refresh: the empty working change must be directly on main; intervening work would be pushed."
        {}))))

(defn -main
  [& _]
  (with-open [file (java.io.RandomAccessFile. ".jj/profile-refresh.lock" "rw")
              channel (.getChannel file)]
    ;; Closing the channel releases its lock, including on failure.
    (let [lock (.tryLock channel)]
      (when-not lock (throw (ex-info "Another profile refresh is running" {})))
      (assert-refresh-base!)
      (let [base (command "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id")
            before (slurp "README.md")
            settings (-> (str (System/getProperty "user.home") "/.pi/agent/settings.json")
                         slurp
                         (json/parse-string true)
                         canonical-settings)
            repos (discover before)
            sections (refresh-sections before settings repos)
            links (update-star-counts/extract-github-repos sections)
            stars (update-star-counts/fetch-all-stars links)]
        (when-not (= (count links) (count stars))
          (throw (ex-info "Incomplete star counts; README was not changed" {})))
        (let [after (str (update-star-counts/update-star-counts sections stars)
                         (when (str/ends-with? sections "\n") "\n"))]
          ;; Network queries can be slow. Check again before writing anything.
          (assert-refresh-base!)
          (when-not (= base (command "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id"))
            (throw (ex-info "main changed during refresh" {})))
          (when-not (= before (slurp "README.md"))
            (throw (ex-info "README changed during refresh" {})))
          (if (= before after)
            (println "README is current; no commit.")
            (do (spit "README.md" after)
                (command "jj" "commit" "README.md" "-m" "docs: weekly profile refresh")
                (when-not (= base (command "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id"))
                  (throw (ex-info "main changed before publication; local refresh commit retained"
                                  {})))
                (command "jj" "bookmark" "set" "main" "-r" "@-")
                (command "jj" "git" "push" "-b" "main")
                (println "Committed and pushed weekly profile refresh."))))))))

(when (= *file* (System/getProperty "babashka.file")) (apply -main *command-line-args*))
