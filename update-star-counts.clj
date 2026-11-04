#!/usr/bin/env bb
(ns update-star-counts
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def github-api "https://api.github.com/repos")
(def github-pattern #"https://github\.com/([a-zA-Z0-9_-]+)/([a-zA-Z0-9._-]+)")
(def star-pattern #"(?:`⭐\s*\d+`|<code>⭐\s*\d+</code>)")
(def clean-regex #"[>\"\)\]\s].*$")
(def default-headers
  {"Accept" "application/vnd.github.v3+json"
   "User-Agent" "update-star-counts-script"})

(defn extract-github-repos [content]
  (->> (re-seq github-pattern content)
       (map (fn [[_ owner repo]]
              [(str "https://github.com/" owner "/" repo) owner (str/replace repo clean-regex "")]))
       distinct))

(defn make-headers []
  (if-let [token (System/getenv "GITHUB_TOKEN")]
    (merge default-headers {"Authorization" (str "Bearer " token)})
    default-headers))

(defn print-error [owner repo msg]
  (binding [*out* *err*]
    (println (format "  ❌ %s/%s: %s" owner repo msg))))

(defn fetch-star-count [owner repo]
  (let [url (str github-api "/" owner "/" repo)]
    (try
      (let [resp (http/get url {:headers (make-headers)})]
        (if (= 200 (:status resp))
          (:stargazers_count (json/parse-string (:body resp) true))
          (do
            (print-error owner repo (str "HTTP " (:status resp)))
            nil)))
      (catch Exception e
        (print-error owner repo (.getMessage e))
        nil))))

(defn format-star [stars html?]
  (if html?
    (str "<code>⭐ " stars "</code>")
    (str "`⭐ " stars "`")))

(defn update-star-in-line [line owner repo star-data]
  (let [key (str owner "/" repo)
        stars (get star-data key)]
    (cond
      (and stars (re-find star-pattern line))
      (str/replace-first line star-pattern
                        (format-star stars (str/includes? line "<code>")))

      stars
      (let [html-end (str/index-of line "</li>")
            pos (or html-end (count line))]
        (str (subs line 0 pos) " " (format-star stars (some? html-end)) (subs line pos)))

      :else line)))

(defn update-star-counts [content star-data]
  (->> (str/split-lines content)
       (map #(reduce (fn [line [_ owner repo]]
                       (update-star-in-line line owner repo star-data))
                     %
                     (reverse (re-seq github-pattern %))))
       (str/join "\n")))

(defn print-help []
  (println "Usage: bb update-star-counts.clj [README_PATH]")
  (println "Update GitHub star counts in README.md")
  (println ""
           "Environment: GITHUB_TOKEN for higher rate limits"))

(defn fetch-star-with-progress [owner repo i total]
  (let [key (str owner "/" repo)]
    (print (format "   [%d/%d] %s..." i total key))
    (flush)
    (if-let [stars (fetch-star-count owner repo)]
      (do
        (println (format "⭐ %d" stars))
        [key stars])
      (do
        (println "skipped")
        nil))))

(defn fetch-all-stars [repos]
  (->> repos
       (keep-indexed (fn [i [_ owner repo]]
                       (fetch-star-with-progress owner repo (inc i) (count repos))))
       (into {})))

(defn -main [& args]
  (when (some #{"--help" "-h"} args)
    (print-help)
    (System/exit 0))

  (let [readme-path (or (first args) "README.md")]

    (when-not (.exists (java.io.File. readme-path))
      (binding [*out* *err*]
        (println (format "Error: README not found at %s" readme-path))
        (println "Use --help for usage information"))
      (System/exit 1))

    (println (format "📖 Reading %s..." readme-path))

    (let [content (slurp readme-path)
          repos (extract-github-repos content)]
      (println (format "🔍 Found %d repos" (count repos)))
      (println "⭐ Fetching star counts...")

      (let [star-data (fetch-all-stars repos)
            updated (update-star-counts content star-data)]
        (spit readme-path updated)

        (println (format "\n✏️  Updated %d stars" (count star-data)))
        (println "📊 Summary:")
        (doseq [[key stars] (sort-by first star-data)]
          (println (format "   %s: ⭐ %d" key stars)))

        (when (< (count star-data) (count repos))
          (println (format "\n⚠️  Skipped %d repos" (- (count repos) (count star-data))))
          (when-not (System/getenv "GITHUB_TOKEN")
            (println "💡 Set GITHUB_TOKEN for higher rate limits")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))