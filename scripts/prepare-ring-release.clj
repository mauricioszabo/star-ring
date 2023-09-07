(require '[babashka.process :refer [shell]])

(defn- last-commit-changed-ring? [ring]
  (->> (shell/sh "git" "diff" "HEAD^")
      :out
      (re-find (re-pattern (str "src/" (str/replace ring #"-" "_"))))))

(defn- fix-shadow-cljs! [ring]
  (let [shadow-config (->> "shadow-cljs.edn"
                           slurp
                           edn/read-string)
        target-path [:builds :package :exports]
        only (->> target-path
                  (get-in shadow-config)
                  (filter (fn [[k]]
                            (str/starts-with? (name k) ring)))
                  (into {}))
        new (assoc-in shadow-config target-path only)]
    (->> new
         pr-str
         (spit "shadow-cljs.edn"))))

(def version (-> (shell/sh "date" "-u" "+%Y.%m.%d-%H")
                 :out
                 str/trim
                 delay))

(defn- fix-packages-files! [ring]
  (let [index (-> (str ring "/index.js")
                  slurp
                  (str/replace-first #"\." ""))
        package (-> (str ring "/package.json")
                    slurp
                    (str/replace-first #"version\":\s*?\"(.*?)\""
                                       (str "version\": \"" @version "\"")))]
    (spit (str ring "/index.js") index)
    (spit (str ring "/package.json") package)))

(defn- delete-unrelated-dirs [dirs]
  (doseq [dir dirs
          :when (and (not= dir ".git")
                     (not= dir "star-ring"))]
    (fs/delete-tree dir)))

(defn- find-all-dirs []
  (->> (fs/list-dir ".")
       (filter fs/directory?)
       (map #(-> % fs/normalize str))))

(defn- copy-files! [ring]
  (fs/copy (str ring "/index.js") "index.js" {:replace-existing true})
  (fs/copy (str ring "/README.md") "README.md" {:replace-existing true})
  (fs/copy (str ring "/CHANGELOG.md") "CHANGELOG.md" {:replace-existing true})
  (fs/copy (str ring "/package.json") "package.json" {:replace-existing true}))

(defn main! [ring]
  (when (last-commit-changed-ring? ring)
    (shell "git" "checkout" "-b" "RELEASE-DELETE-THIS")
    (shell "npm" "install")
    (shell "git" "tag" (str ring "@" @version "-source"))
    (fix-shadow-cljs! ring)
    (fix-packages-files! ring)
    (shell "npx" "shadow-cljs" "release" "package")
    (copy-files! ring)
    (delete-unrelated-dirs (find-all-dirs))
    (shell "git" "add" "-u" ".")
    (shell "git" "add" ".")
    (shell "git" "add" "star-ring/common.js" "-f")
    (shell "git" "commit" "-m" (str "Compiled release for " ring "@" @version))
    (shell "git" "tag" (str ring "@" @version))
    (shell "git" "checkout" "-")
    (shell "git" "branch" "-D" "RELEASE-DELETE-THIS")
    (shell "git" "push" "--tags")
    (shell "git" "remote" "add" "github" "git@github.com:mauricioszabo/star-ring.git")
    (shell "git" "push" "github" (str ring "@" @version))
    (println "Successfully tagged" (str ring "@" @version))))

(when (= *file* (System/getProperty "babashka.file"))
  (if-let [ring (first *command-line-args*)]
    (main! ring)
    (do
      (println "Need the ring to generate")
      (System/exit 1))))
