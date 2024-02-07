(ns generic-lsp.complete
  (:require [generic-lsp.commands :as cmds]
            [promesa.core :as p]
            [clojure.string :as str]
            ["atom" :refer [Range]]))

;; TODO: Atom/Pulsar does not have icons for all elements that are available on LSP,
;; and there are some that are not used at all, like builtin, import and require.
;; Maybe we need to add icons for these that are not defined. These are keywords
;; below, for us to be able to know they are not present on Atom yet
(def ^:private types
  [""
   "method"
   "function"
   :constructor
   :field
   "variable"
   "class"
   :interface
   :module
   "property"
   :unit
   "value"
   :enum
   "keyword"
   "snippet"
   "tag"
   :file
   :reference
   :folder
   :enummember
   "constant"
   :struct
   :event
   "builtin"
   "type"])

(defn- get-range! [^js editor prefix]
  (let [^js cursor (-> editor .getCursors first)
        first-word (first prefix)
        buffer (.getBuffer editor)
        word-ish-range (new Range
                         (.getBeginningOfCurrentWordBufferPosition cursor #js {:wordRegex #"[^\s]*"})
                         (.getBufferPosition cursor))
        offset (. (.getTextInRange buffer word-ish-range) indexOf first-word)]
    (when (not= -1 offset)
      (set! (.-start word-ish-range) (.. word-ish-range -start (traverse #js {:column offset})))
      word-ish-range)))

(defn- re-escape [str]
  (str/replace str #"[.*+?^${}()|\[\]\\]" "\\$&"))

(defn- normalize-result [editor result]
  (let [to-insert (:insertText result (:label result))
        snippet? (-> result :insertTextFormat (= 2))
        common {:displayText (:label result)
                :type (some-> result :kind dec types)
                :description (:detail result)
                :ranges [(get-range! editor to-insert)]}]
    (if snippet?
      (assoc common :snippet to-insert)
      (assoc common :text to-insert))))

(defn- suggestions [^js data]
  (p/let [^js editor (.-editor data)
          {:keys [result]} (cmds/autocomplete editor)
          items (if-let [items (:items result)]
                  items
                  result)
          comparator (fn [a b] (compare (:displayText a) (:displayText b)))
          autocomplete-items (into (sorted-set-by comparator)
                                   (map #(normalize-result editor %))
                                   items)
          fuzzy-filtered (.. js/atom -ui -fuzzyMatcher
                             (setCandidates (->> autocomplete-items
                                                 (map :displayText)
                                                 into-array))
                             (match (.-prefix data)))
          fuzzy-indexed (into {} (map (fn [v] [(.-id v) v])) fuzzy-filtered)]

    (->> (for [[id value] (zipmap (range) autocomplete-items)
               :let [fuzzy-data (get fuzzy-indexed id)]
               :when fuzzy-data]
           (assoc value :score (- (.-score fuzzy-data))))
         (sort-by :score)
         clj->js)))

(defn- detailed-suggestion [_data])
  ; (prn :detailed data))

(defn provider
  "Provider for autocomplete"
  []
  #js {:selector ".source"
       :disableForSelector ".source .comment"
       :inclusionPriority 10
       :excludeLowerPriority false
       :suggestionPriority 20
       :filterSuggestions false

       :getSuggestions (fn [data]
                         (suggestions data))

       :getSuggestionDetailsOnSelect #(detailed-suggestion %)})
