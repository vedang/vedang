(ns update-star-counts-test
  (:require [babashka.http-client :as http]
            [clojure.test :refer [deftest is run-tests testing]]))

(load-file "update-star-counts.clj")

(deftest github-json-counts
  (doseq [stars [37 0]]
    (testing (str "GitHub JSON with " stars " stars")
      (with-redefs [http/get (fn [_ _]
                              {:status 200
                               :body (str "{\"stargazers_count\":" stars "}")})]
        (is (= stars (update-star-counts/fetch-star-count "owner" "repo")))))))

(deftest readme-star-refresh
  (doseq [[before after]
          [["- **[repo](https://github.com/owner/repo)**. `⭐ 12`"
            "- **[repo](https://github.com/owner/repo)**. `⭐ 37`"]
           ["<li><a href=\"https://github.com/owner/repo\">repo</a>. <code>⭐ 12</code></li>"
            "<li><a href=\"https://github.com/owner/repo\">repo</a>. <code>⭐ 37</code></li>"]
           ["- **[repo](https://github.com/owner/repo)**."
            "- **[repo](https://github.com/owner/repo)**. `⭐ 37`"]
           ["<li><a href=\"https://github.com/owner/repo\">repo</a>.</li>"
            "<li><a href=\"https://github.com/owner/repo\">repo</a>. <code>⭐ 37</code></li>"]]]
    (testing before
      (is (= after (update-star-counts/update-star-counts before {"owner/repo" 37})))
      (is (= after (update-star-counts/update-star-counts after {"owner/repo" 37}))))))

(let [{:keys [fail error]} (run-tests 'update-star-counts-test)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
