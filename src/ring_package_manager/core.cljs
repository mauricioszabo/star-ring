(ns ring-package-manager.core
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

(defn- install-deps! [package-path]
  (set! (.. js/process -env -npm_config_target) (.. js/process -versions -electron))
  (set! (.. js/process -env -npm_config_disturl) "https://electronjs.org/headers")
  (set! (.. js/process -env -npm_config_runtime) "electron")
  (set! (.. js/process -env -npm_config_build_from_source) "true")
  (p/let [log (partial prn :LOG)
          ^js arb (new Arborist #js {:path package-path
                                     :installStrategy "hoisted"
                                     :force true
                                     :lockfileVersion 2
                                     :cache "/home/mauricio/.npm/_cacache"})]
    (js/process.on "log" log)
    ; (println "Starting to install deps")
    (.buildIdealTree arb #js {:saveType "prod"})
    ; (println "Dependencies resolved, downloading...")
    (.reify arb (partial prn :reify))
    (println "Done")
    (js/process.off "log" log)))

; (.. arb -actualTree)
; (println (.toString (.. arb -addTracker)))

(defn- rebuild! [package-dir]
  (p/let [node-modules (path/join package-dir "node_modules")]
    (.catch (.. fs -promises (rmdir node-modules #js {:recursive true}))
            #(println "node_modules not removed - probably doesn't exist"))
    (install-deps! package-dir)))

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
(install-package! "https://github.com/nteract/hydrogen.git"
                  "v2.16.5")

#_(p/catch (rebuild! "/tmp/hydrogen.git")
           (partial prn :ERROR))

#_(p/catch (rebuild! "/home/mauricio/.pulsar/packages/x-terminal-reloaded")
           (partial prn :ERROR))

; (. js/process on)
