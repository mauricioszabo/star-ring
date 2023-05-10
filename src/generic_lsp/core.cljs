(ns generic-lsp.core
  (:require [generic-lsp.commands :as cmds]
            [common.atom :refer [subscriptions open-paths atom-state] :as atom]
            [promesa.core :as p]
            ["atom" :refer [CompositeDisposable]]))

(def conjset (fnil conj #{}))

(defn- remove-editor [^js text-editor]
  (when-let [path (.getPath text-editor)]
    (swap! open-paths update path disj text-editor)
    (when (zero? (count (get @open-paths path)))
      (cmds/close-document! text-editor))))

(defn- add-editor [^js text-editor]
  (when-let [path (.getPath text-editor)]
    (swap! open-paths update path conjset text-editor)
    (when (= 1 (count (get @open-paths path)))
      (cmds/open-document! text-editor))))

(defn- text-editor-observer [^js text-editor]
  (add-editor text-editor)

  (.add @subscriptions
        (. text-editor onDidDestroy #(remove-editor text-editor)))
  (.add @subscriptions
        (. text-editor onDidChange #(cmds/sync-document! text-editor (.-changes ^js %))))
  (.add @subscriptions
        (. text-editor onDidSave #(cmds/save-document! text-editor))))

(defn- renamed-file [^js change]
  (let [new-path (.-path change)
        old-path (.-oldPath change)
        editor-d (-> @open-paths (get new-path) first delay)]
    (swap! open-paths #(-> %
                           (assoc new-path (get % old-path))
                           (dissoc old-path)))
    (when @editor-d
      (cmds/rename! @editor-d old-path))))

(defn- make-changes [^js changes]
  (doseq [change changes
          :when (-> change .-action (= "renamed"))]
    (renamed-file change)))

(defn- open-config! []
  (let [editor (.. js/atom -workspace getActiveTextEditor)
        grammar (some-> editor .getGrammar .-name)]
    (when grammar
      (p/let [page (.. js/atom -workspace (open "atom://config/packages/generic-lsp"))
              view (.. js/atom -views (getView page))
              headers (.querySelectorAll view "h3")
              found (->> headers (filter #(-> % .-innerText (= grammar))) first)]
        (when found
          (.scrollIntoView found)
          (.click found))))))

(defn activate [state]
  (reset! atom-state state)
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:start-LSP-server"
                                #(cmds/start! @open-paths))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:stop-LSP-server"
                                #(cmds/stop-lsp-server!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-declaration"
                                #(cmds/go-to-declaration!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-definition"
                                #(cmds/go-to-definition!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-type-definition"
                                #(cmds/go-to-type-definition!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-implementation"
                                #(cmds/go-to-implementation!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:format-document"
                                #(cmds/format-doc!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:open-config-for-current-language"
                                #(open-config!))))

  (.add @subscriptions
        (.. js/atom -project (onDidChangeFiles make-changes)))
  (.add @subscriptions
        (.. js/atom -workspace (observeTextEditors #(text-editor-observer %)))))

(defn deactivate [_]
  (.dispose ^js @subscriptions))

(defn- ^:dev/before-load reset-subs []
  (deactivate @atom-state)
  (doseq [[_ editor] @open-paths]
    (some-> editor first cmds/close-document!))
  (reset! open-paths {}))

(defn- ^:dev/after-load re-activate []
  (reset! subscriptions (CompositeDisposable.))
  (activate @atom-state)
  (atom/info! "Reloaded Generic LSP package"))
