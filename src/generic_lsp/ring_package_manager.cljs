(ns generic-lsp.ring-package-manager
  (:require [common.atom :as atom]
            [promesa.core :as p]
            ["os" :as os]
            ["path" :as path]
            ["@npmcli/arborist" :as Arborist]
            ["isomorphic-git" :as git]
            ["isomorphic-git/http/node" :as git-http]
            ["fs" :as fs]))

(defonce subscriptions (atom []))

(defn ^:dev/after-load activate [state]
  (reset! atom/atom-state state))

(defn ^:dev/after-load deactivate []
  (doseq [^js s @subscriptions]
    (.dispose s))
  (reset! subscriptions []))

(def provider #js {})

#_
(let [package-name "termination"]
  (let [directory (->> js/atom
                       .-packages
                       .getAvailablePackagePaths
                       (filter #(-> % path/basename (= package-name)))
                       first)]
    (if directory
      (do-rebuild! directory)
      (atom/warn! (str "Could not find package " package-name)))))

#_
(defn- do-rebuild! [package-dir]
  (def package-dir package-dir)
  (-> (rebuild #js {:buildPath (path/join package-dir "node_modules" "node-pty-prebuilt-multiarch")
                    ; :force true
                    :electronVersion (.. js/process -versions -electron)})
      (p/then #(prn :WOW "IT WORKED!"))
      (p/catch #(do
                  (prn :WELL "we expected this")
                  (def error %)))))

(defn- rebuild! [package-name])

; (.. js/process -versions -electron)
; (prn :RE rebuild)

#_
(let [package-name "termination"]
  (let [directory (->> js/atom
                       .-packages
                       .getAvailablePackagePaths
                       (filter #(-> % path/basename (= package-name)))
                       first)]
    (if directory
      (p/catch (install-deps! directory)
               #(do
                  (def e %)
                  (atom/error! (str "Could not install dependencies for " package-name)
                               (pr-str %))))
      (atom/warn! (str "Could not find package " package-name)))))


(defn- install-deps! [package-path]
  ; (let [npm_config_target (.. js/process -env -npm_config_target)
  ;       npm_config_disturl (.. js/process -env -npm_config_disturl)
  ;       npm_config_runtime (.. js/process -env -npm_config_runtime)
  ;       npm_config_build_from_source (.. js/process -env -npm_config_build_from_source)]
  (set! (.. js/process -env -npm_config_target) (.. js/process -versions -electron))
  (set! (.. js/process -env -npm_config_disturl) "https://electronjs.org/headers")
  (set! (.. js/process -env -npm_config_runtime) "electron")
  (set! (.. js/process -env -npm_config_build_from_source) "true")

  (p/let [^js arb (new Arborist #js {:path package-path})]
    (println "Starting to install deps")
    (.buildIdealTree arb)
    (println "Dependencies resolved, downloading...")
    (.reify arb)
    (println "Done")))
 ;  process.env.npm_config_target='12.2.3'
 ; process.env.npm_config_disturl='https://electronjs.org/headers'
 ; process.env.npm_config_runtime=''
 ; process.env.npm_config_build_from_source='true')

; (p/catch (.. fs -promises (mkdtemp "asd")) prn)
; (.. js/process -versions)
; (. fs te)
(defn- move-to-pulsar-dir! [dir-to-clone]
  (p/let [package-json (path/join dir-to-clone "package.json")
          package-json-contents (.. fs -promises (readFile package-json "UTF-8"))
          package-name (-> package-json-contents js/JSON.parse .-name)
          dir-to-install (-> (.. js/atom -packages getPackageDirPaths)
                             first
                             (path/join package-name))]
    (.. fs -promises (cp dir-to-clone dir-to-install #js {:recursive true}))
    (.. fs -promises (rmdir dir-to-clone #js {:recursive true}))))

(defn install-package! [git-url tag]
  (p/let [dir-to-clone (path/join (.tmpdir os)
                                  (path/basename git-url))]
    (. git clone #js {:fs fs
                      :http git-http,
                      :dir dir-to-clone,
                      :url git-url})
    (. git checkout #js {:fs fs
                         :dir dir-to-clone,
                         :ref tag})
    (install-deps! dir-to-clone)
    (move-to-pulsar-dir! dir-to-clone)))

#_
(install-package! "https://github.com/b3by/atom-clock.git"
                  "v0.1.18")
