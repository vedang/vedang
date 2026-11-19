(ns refresh-profile-test
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

(load-file "refresh-profile.clj")

(def root (str (fs/absolutize ".")))
(def before (slurp "test/fixtures/profile-before.md"))
(def expected (slurp "test/fixtures/profile-after.md"))
(def settings (json/parse-string (slurp "test/fixtures/profile-settings.json") true))
(def repositories (json/parse-string (slurp "test/fixtures/profile-repositories.json") true))

;; Model GitHub's JSON boundary, not section selection or the generated README.
(def api-fixture
  (merge {"users/vedang/repos?per_page=100&page=1"
            (vec (filter #(= "vedang" (get-in % [:owner :login])) repositories)),
          "repos/vedang/old-org-package" {:full_name "team/org-package"},
          "repos/other/steady" {:full_name "other/steady", :owner {:login "other"}}}
         (into {} (map (juxt #(str "repos/" (:full_name %)) identity) repositories))
         (into {}
               (for [repo repositories
                     :when (:manifest repo)]
                 [(str "repos/" (:full_name repo) "/contents/package.json")
                  {:encoding "base64",
                   :content (.encodeToString (java.util.Base64/getEncoder)
                                             (.getBytes (json/generate-string (:manifest repo))
                                                        "UTF-8"))}]))
         {"repos/team/org-package/contributors?per_page=100&page=1" [{:login "vedang"}]}))

(defn fixture-github
  [endpoint & [optional?]]
  (if (contains? api-fixture endpoint)
    (get api-fixture endpoint)
    (when-not optional? (throw (ex-info "Unexpected fixture query" {:endpoint endpoint})))))

(deftest package-placement-and-preservation
  (with-redefs [refresh-profile/github fixture-github]
    (let [canonical (refresh-profile/canonical-settings settings)
          after (refresh-profile/refresh-sections before canonical repositories)]
      (is
        (= expected after)
        "Moves both ways, adds packages, excludes non-authored/fork/private/non-Pi data, preserves all other bytes")
      (is (= after (refresh-profile/refresh-sections after canonical (reverse repositories)))
          "Stable across repeated runs and repository listing order")
      (let [inactive (refresh-profile/refresh-sections after {:packages []} repositories)
            active-again (refresh-profile/refresh-sections inactive canonical repositories)]
        (is (str/includes? inactive
                           "  <li>📚 <strong><a href=\"https://github.com/vedang/active\""))
        (is (str/includes? active-again
                           "- 📚 <strong><a href=\"https://github.com/vedang/active\""))
        (is (str/includes? active-again "Preserve this curated description. <code>⭐ 3</code>"))))))

(deftest github-json-pagination
  (let [page1 (mapv (fn [n] {:full_name (str "vedang/repo-" n)}) (range 100))
        page2 [{:full_name "vedang/last-repo"}]
        queries (atom [])]
    (with-redefs [process/process (fn [args _]
                                    (swap! queries conj (last args))
                                    (delay {:exit 0,
                                            :out (json/generate-string
                                                   (case (last args)
                                                     "users/vedang/repos?per_page=100&page=1" page1
                                                     "users/vedang/repos?per_page=100&page=2"
                                                       page2))}))]
      (is (= (conj page1 (first page2)) (refresh-profile/github-pages "users/vedang/repos")))
      (is (= ["users/vedang/repos?per_page=100&page=1" "users/vedang/repos?per_page=100&page=2"]
             @queries)))))

(deftest refuse-ambiguous-input
  (doseq [[content installed]
            [[(str/replace before "### Agent tooling & practical automation" "### Renamed")
              settings] [before {:packages ["./unidentified-local-package"]}]
             [(str/replace
                before
                "</ul>"
                (str
                  "  <li><a href=\"https://github.com/vedang/active\">duplicate</a></li>\n</ul>"))
              settings]]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (refresh-profile/refresh-sections content installed repositories)))))

(defn run-in
  [directory & args]
  @(process/process args {:dir (str directory), :out :string, :err :string}))

(defn checked-in
  [directory & args]
  (let [{:keys [exit out err]} (apply run-in directory args)]
    (when-not (zero? exit) (throw (ex-info err {:args args, :exit exit})))
    (str/trim out)))

(deftest weekly-job-local-history-and-failure-safety
  (let
    [temp (fs/create-temp-dir {:prefix "profile-refresh-test-"})
     repo (fs/path temp "repo")
     home (fs/path temp "home")
     script (str root "/refresh-profile.clj")
     push-log (str (fs/path temp "pushes"))
     push-fails (str (fs/path temp "push-fails"))
     ;; External services are canned; the filesystem, subprocess entry point,
     ;; jj guard, commit, and fresh-change lifecycle are real.
     program
       (str
         "(load-file "
         (pr-str script)
         ") "
         "(def fixture "
         (pr-str api-fixture)
         ") "
         "(def real-command refresh-profile/command) "
         "(with-redefs [refresh-profile/command "
         "(fn [& args] (if (= args [\"jj\" \"git\" \"push\" \"-b\" \"main\"]) "
         "(do (spit "
         (pr-str (str push-log))
         " \"push\\n\" :append true) "
         "(when (.exists (java.io.File. " (pr-str (str push-fails))
         ")) " "(throw (ex-info \"Push rejected\" {})))) (apply real-command args))) "
         "refresh-profile/github " "(fn [endpoint & [optional?]] "
         "(if (contains? fixture endpoint) (get fixture endpoint) "
           "(when-not optional? (throw (ex-info \"Unexpected query\" {:endpoint endpoint}))))) "
         "babashka.http-client/get (fn [& _] {:status 200 :body \"{\\\"stargazers_count\\\":42}\"})] "
           "(refresh-profile/-main))")
     invoke (fn [code] (run-in repo "bb" (str "-Duser.home=" home) "-e" code))
     readme (str (fs/path repo "README.md"))
     commits (fn []
               (checked-in repo
                           "jj"
                           "log" "--no-graph"
                           "-r" "subject(\"docs: weekly profile refresh\")"
                           "-T" "commit_id ++ '\n'"))]
    (try (fs/create-dirs repo)
         (fs/create-dirs (fs/path home ".pi/agent"))
         (spit (str (fs/path home ".pi/agent/settings.json")) (json/generate-string settings))
         (checked-in repo "jj" "git" "init")
         (spit readme before)
         (checked-in repo "jj" "commit" "-m" "test: initial profile")
         (checked-in repo "jj" "bookmark" "create" "main" "-r" "@-")
         (let [main (checked-in repo "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id")
               first-run (invoke program)]
           (is (= 0 (:exit first-run)) (:err first-run))
           (let [after (slurp readme)
                 commit (commits)]
             (is (str/includes? after "<code>⭐ 42</code>"))
             (is (not (str/includes? after "⭐ 8")) "Other sections also receive new star counts")
             (is (= "true" (checked-in repo "jj" "log" "--no-graph" "-r" "@" "-T" "empty")))
             (is (= 1 (count (str/split-lines commit))) "Exactly one refresh commit")
             (is (= "push\n" (slurp push-log)) "One push after committing")
             (is (= 0 (:exit (invoke program))))
             (is (= "push\n" (slurp push-log)) "No-op run does not push")
             (is (= commit (commits)) "No-op run makes no commit")
             (is (= after (slurp readme)))
             (is (not= main (checked-in repo "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id")))
             (is (= (str/trim commit)
                    (checked-in repo "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id"))
                 "main moves to exactly the new refresh commit")
             (testing "incomplete API data never writes or commits"
               (let [failure (invoke (str "(load-file " (pr-str script)
                                          ") " "(with-redefs [refresh-profile/github "
                                          "(fn [& _] (throw (ex-info \"API unavailable\" {})))] "
                                            "(refresh-profile/-main))"))]
                 (is (not (zero? (:exit failure))))
                 (is (str/includes? (:err failure) "API unavailable"))
                 (is (= after (slurp readme)))
                 (is (= commit (commits)))))
             (testing "missing stars also prevent a partial refresh"
               (let [failure (invoke (str/replace program "{\\\"stargazers_count\\\":42}" "{}"))]
                 (is (not (zero? (:exit failure))))
                 (is (str/includes? (:err failure) "Incomplete star counts"))
                 (is (= after (slurp readme)))
                 (is (= commit (commits)))))
             (testing "unrelated edits are refused without absorbing them into a commit"
               (spit (str (fs/path repo "unrelated.txt")) "owner's unfinished work\n")
               (let [failure (invoke program)]
                 (is (not (zero? (:exit failure))))
                 (is (str/includes? (:err failure) "unrelated changes"))
                 (is (= after (slurp readme)))
                 (is (= commit (commits)))))
             (fs/delete (fs/path repo "unrelated.txt"))
             (testing
               "a failed push is loud, preserves the local commit, and is not retried on a no-op"
               (spit push-fails "fail")
               (let [program43
                       (str/replace program "stargazers_count\\\":42" "stargazers_count\\\":43")
                     failure (invoke program43)]
                 (is (not (zero? (:exit failure))))
                 (is (str/includes? (:err failure) "Push rejected"))
                 (is (= "push\npush\n" (slurp push-log)))
                 (is (str/includes? (slurp readme) "⭐ 43"))
                 (is (= 2 (count (str/split-lines (commits)))))
                 (is (= "true" (checked-in repo "jj" "log" "--no-graph" "-r" "@" "-T" "empty")))
                 (is (= (checked-in repo "jj" "log" "--no-graph" "-r" "@-" "-T" "commit_id")
                        (checked-in repo "jj" "log" "--no-graph" "-r" "main" "-T" "commit_id")))
                 (is (= 0 (:exit (invoke program43))))
                 (is (= "push\npush\n" (slurp push-log)))))
             (testing "even clean intervening history cannot be pushed"
               (checked-in repo "jj" "describe" "-m" "test: intervening work")
               (checked-in repo "jj" "new")
               (let [failure (invoke program)]
                 (is (not (zero? (:exit failure))))
                 (is (str/includes? (:err failure) "directly on main"))
                 (is (= "push\npush\n" (slurp push-log)))))))
         (finally (fs/delete-tree temp)))))

(let [{:keys [fail error]} (run-tests 'refresh-profile-test)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
