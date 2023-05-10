(ns common.atom
  (:require ["atom" :refer [CompositeDisposable]]
            ["atom-select-list" :as SelectListView]
            ["url" :as url]
            ["fuzzaldrin" :refer [match]]
            [reagent.dom.server :as r-dom]
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

(defn- make-match-elems [matches item]
  (let [to-apply (->> matches
                      (partition 2 1)
                      (reduce (fn [acc [first second]]
                                (let [[fst] (peek acc)]
                                  (if (-> first inc (= second))
                                    (-> acc pop (conj [fst second]))
                                    (-> acc (conj [second])))))
                              [[(first matches)]])
                      (partition-all 2 1)
                      (reduce (fn [elems-to-add [[f s] second]]
                                (let [s (if s (inc s) (inc f))
                                      span (doto (js/document.createElement "span")
                                                 (.. -classList (add "character-match"))
                                                 (aset "innerText" (subs item f s)))]
                                  (cond-> (conj elems-to-add span)
                                    second (conj (subs item s (first second))))))
                              [(subs item 0 (first matches))]))]
    (cond-> to-apply
      (-> item count (not= (last matches)))
      (conj (subs item (-> matches last inc))))))

(defn- item-for-list [panel-a {:keys [text description value]}]
  (let [^js panel @panel-a
        elem (js/document.createElement "li")
        first-line (js/document.createElement "div")
        matches (some->> panel .getFilterQuery not-empty (match text))]
    (.. elem -classList (add "two-lines"))
    (.. first-line -classList (add "primary-line"))
    (.appendChild elem first-line)
    (doseq [match (if matches
                    (make-match-elems matches text)
                    [text])]
      (.append first-line match))
    (when description
      (let [second-line (js/document.createElement "div")]
        (.. second-line -classList (add "secondary-line"))
        (set! (.-innerText second-line) description)
        (.appendChild elem second-line)))
    elem))

#_
(select-view! [{:text "Hell0" :description "Hello, my baby, hello, my honey" :value 0}
               {:text "Is it me" :value 1}
               {:text "Youre looking for" :value 2}])

(defn select-view! [items]
  (let [result (p/deferred)
        select-a (atom nil)
        panel-a (atom nil)
        params #js {:items (into-array items)
                    :filterKeyForItem #(:text %)
                    :didConfirmSelection (fn [{:keys [value]}]
                                           (.destroy ^js @panel-a)
                                           (.destroy ^js @select-a)
                                           (refocus!)
                                           (p/resolve! result value))
                    :elementForItem #(item-for-list select-a %)}
        select (new SelectListView params)
        element (.-element select)
        panel (delay (.. js/atom -workspace (addModalPanel #js {:item element})))
        input (.querySelector element "input")]
    (.. element -classList (add "fuzzy-finder"))
    ;; Stupid OO-based approach...
    (reset! select-a select)
    (reset! panel-a @panel)
    (save-focus! input)
    (set! (.-onkeydown input)
      #(case (.-key ^js %)
         "Escape" (do
                    (.destroy ^js @panel)
                    (.destroy select)
                    (refocus!)
                    (p/resolve! result nil))
         :no-op))
    result))
