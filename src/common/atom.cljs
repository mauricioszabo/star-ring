(ns common.atom
  (:require ["atom" :refer [CompositeDisposable]]
            ["url" :as url]))

(defonce atom-state (atom nil))
(defonce subscriptions (atom (CompositeDisposable.)))

(defn info! [message]
  (.. js/atom -notifications (addInfo message))
  nil)

(defn error!
  ([message] (error! message nil))
  ([message detail]
   (.. js/atom -notifications (addError message #js {:detail detail}))
   nil))

(defn warn! [message]
  (.. js/atom -notifications (addWarning message))
  nil)

(defn open-editor [result]
  (let [result (if (vector? result) (first result) result)
        {:keys [uri range]} result
        start (:start range)
        line (:line start)
        column (:character start)
        file-name (url/fileURLToPath uri)
        position (clj->js (cond-> {:initialLine line :searchAllPanes true}
                                  column (assoc :initialColumn column)))]
    (.. js/atom -workspace (open file-name position))))

(defonce open-paths (atom {}))
