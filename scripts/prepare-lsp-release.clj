(require '[babashka.process :refer [shell]])

(defn- last-commit-changed-lsp? []
  (->> (shell/sh "git" "diff" "HEAD^")
      :out
      (re-find #"src/generic_lsp")))

(defn- fix-shadow-cljs! []
  (let [shadow-config (->> "shadow-cljs.edn"
                           slurp
                           edn/read-string)
        target-path [:builds :package :exports]
        only (->> target-path
                  (get-in shadow-config)
                  (filter (fn [[k]]
                            (str/starts-with? (name k) "generic-lsp")))
                  (into {}))
        new (assoc-in shadow-config target-path only)]
    (->> new
         pr-str
         (spit "shadow-cljs.edn"))))


(defn- fix-packages-files! []
  (let [index (-> "generic-lsp/index.js"
                  slurp
                  (str/replace-first #"\." ""))
        version (-> (shell/sh "date" "-u" "+%Y.%m.%d-%H")
                    :out
                    str/trim)
        package (-> "generic-lsp/package.json"
                    slurp
                    (str/replace-first #"version\":\s*?\"(.*?)\""
                                       (str "version\": \"" version "\""))
                    println)]
    (spit "generic-lsp/index.js" index)
    (spit "generic-lsp/package.json" package)))

(defn- delete-unrelated-dirs [dirs]
  (doseq [dir dirs
          :when (and (not= dir ".dir")
                     (not= dir "star-ring")
                     (not= dir "generic-lsp"))]
    (fs/delete-tree dir)))

(defn- find-all-dirs []
  (->> (fs/list-dir ".")
       (filter fs/directory?)
       (map #(-> % fs/normalize str))))

(defn- copy-files! []
  (fs/copy "generic-lsp/index.js" "index.js")
  (fs/copy "generic-lsp/README.md" "README.md")
  (fs/copy "generic-lsp/package.json" "package.json"))

(defn main! []
  (when (last-commit-changed-lsp?)
    (shell "git" "checkout" "-b" "RELEASE-DELETE-THIS")
    (fix-shadow-cljs!)
    (fix-packages-files!)
    (shell "npx" "shadow-cljs" "release" "package"))
  (let []))
