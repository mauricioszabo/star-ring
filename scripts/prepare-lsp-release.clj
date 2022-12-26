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

(def version (-> (shell/sh "date" "-u" "+%Y.%m.%d-%H")
                 :out
                 str/trim
                 delay))

(defn- fix-packages-files! []
  (let [index (-> "generic-lsp/index.js"
                  slurp
                  (str/replace-first #"\." ""))
        package (-> "generic-lsp/package.json"
                    slurp
                    (str/replace-first #"version\":\s*?\"(.*?)\""
                                       (str "version\": \"" @version "\"")))]
    (spit "generic-lsp/index.js" index)
    (spit "generic-lsp/package.json" package)))

(defn- delete-unrelated-dirs [dirs]
  (doseq [dir dirs
          :when (and (not= dir ".git")
                     (not= dir "star-ring"))]
    (fs/delete-tree dir)))

(defn- find-all-dirs []
  (->> (fs/list-dir ".")
       (filter fs/directory?)
       (map #(-> % fs/normalize str))))

(defn- copy-files! []
  (fs/copy "generic-lsp/index.js" "index.js" {:replace-existing true})
  (fs/copy "generic-lsp/README.md" "README.md" {:replace-existing true})
  (fs/copy "generic-lsp/CHANGELOG.md" "CHANGELOG.md" {:replace-existing true})
  (fs/copy "generic-lsp/package.json" "package.json" {:replace-existing true}))

(defn main! []
  ; (when (last-commit-changed-lsp?)
    (shell "git" "checkout" "-b" "RELEASE-DELETE-THIS")
    (shell "npm" "install")
    (shell "git" "tag" (str "generic-lsp-v" @version "-source"))
    (fix-shadow-cljs!)
    (fix-packages-files!)
    (shell "npx" "shadow-cljs" "release" "package")
    (copy-files!)
    (delete-unrelated-dirs (find-all-dirs))
    (shell "git" "add" "-u" ".")
    (shell "git" "add" ".")
    (shell "git" "add" "star-ring/common.js" "-f")
    (shell "git" "commit" "-m" (str "Compiled release for generic-lsp@" @version))
    (shell "git" "tag" (str "generic-lsp-v" @version))
    (shell "git" "checkout" "-")
    (shell "git" "branch" "-D" "RELEASE-DELETE-THIS")
    (shell "git" "push" "--tags")
    (println "Successfully tagged" (str "generic-lsp-v" @version)))

(when (= *file* (System/getProperty "babashka.file"))
  (main!))
