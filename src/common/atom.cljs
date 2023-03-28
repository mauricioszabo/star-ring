(ns common.atom
  (:require ["atom" :refer [CompositeDisposable]]
            ["url" :as url]
            [promesa.core :as p]))

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

(defonce focus (atom nil))
(defn save-focus! [elem]
  (def elem elem)
  (when-not @focus
    (reset! focus (some-> js/atom .-workspace .getActiveTextEditor .-element)))
  (p/do!
   (p/delay 100)
   (.focus elem)))

(defn refocus! []
  (when-let [elem @focus]
    (.focus elem)
    (reset! focus nil)))

(defn prompt! [text]
  (let [div (js/document.createElement "div")
        result (p/deferred)
        h2 (js/document.createElement "h2")
        panel (delay (.. js/atom -workspace (addModalPanel #js {:item div})))
        input (js/document.createElement "input")]
    (.. div -classList (add "native-key-bindings"))
    (set! (. input -tabIndex) 1)
    (.. input -classList (add "input-text"))
    (set! (.-innerText h2) text)
    (.appendChild div h2)
    (.appendChild div input)
    (set! (.-onkeydown input)
      #(case (.-key ^js %)
         "Escape" (do
                    (.destroy ^js @panel)
                    (refocus!)
                    (p/resolve! result nil))
         "Enter" (do
                   (.destroy ^js @panel)
                   (p/resolve! result (.-value input)))
         :no-op))
    (save-focus! input)
    @panel
    result))
