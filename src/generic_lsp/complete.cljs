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

(defn- get-prefix [^js editor possible-prefix-regex]
  (let [^js cursor (-> editor .getCursors first)
        word-range (new Range
                     (.getBeginningOfCurrentWordBufferPosition cursor #js {:wordRegex @possible-prefix-regex})
                     (.getBufferPosition cursor))]
    (.getTextInRange editor word-range)))

(def ^:private key-fn (juxt :text :type))

(defn- normalize-result [^js editor cache possible-prefix-regex result]
  (let [to-insert (:insertText result (:label result))
        snippet? (-> result :insertTextFormat (= 2))
        common {:displayText (:label result)
                :type (some-> result :kind dec types)
                :description (:detail result)
                :text to-insert}
        ;; FIXME - ranges is not really working for snippets...
        common (if-let [edit nil #_(:textEdit result)]
                 (assoc common
                        :text (:newText edit)
                        :ranges [(new Range
                                   #js {:row (-> edit :range :start :line)
                                        :column (-> edit :range :start :character)}
                                   #js {:row (-> edit :range :end :line)
                                        :column (-> edit :range :end :character)})])
                 (assoc common :prefix (get-prefix editor possible-prefix-regex)))
        suggestion (cond-> common snippet? (assoc :snippet to-insert))]
    (prn :S suggestion)
    (swap! cache assoc (key-fn suggestion) result)
    suggestion))

(defn- re-escape [string]
  (str/replace string #"[\|\\\{\}\(\)\[\]\^\$\+\*\?\.\-\/]" "\\$&"))

(defn- get-possible-prefix-re [items]
  (let [all-items (->> items
                       (map :label)
                       (str/join ""))
        non-word-chars (distinct (re-seq #"[^\w]" all-items))]
    (re-pattern (str "[\\w" (re-escape (str/join "" non-word-chars)) "]+"))))

(defn- suggestions [^js data cache]
  (reset! cache {})
  (p/let [^js editor (.-editor data)
          {:keys [result]} (cmds/autocomplete editor)
          items (if-let [items (:items result)]
                  items
                  result)
          possible-prefix-regex (delay (get-possible-prefix-re items))
          comparator (fn [a b] (compare (:displayText a) (:displayText b)))
          autocomplete-items (into (sorted-set-by comparator)
                                   (map #(normalize-result editor
                                                           cache
                                                           possible-prefix-regex
                                                           %))
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

(defn- detailed-suggestion [^js data cache]
  #_
  (p/let [selected (js->clj data :keywordize-keys true)
          key (key-fn selected)
          original-message (get @cache key)
          editor (.. js/atom -workspace getActiveTextEditor)
          result (cmds/autocomplete-resolve editor original-message)]
    ; (prn :cache original-message)
    (prn :RES result)
    ; (prn :C (keys @cache) key)))
    (prn :detailed (.-editor data) data)))

(defn provider
  "Provider for autocomplete"
  []
  (let [cache (atom {})]
    #js {:selector ".source"
         :disableForSelector ".source .comment"
         :inclusionPriority 10
         :excludeLowerPriority false
         :suggestionPriority 20
         :filterSuggestions false

         :getSuggestions (fn [data]
                           (suggestions data cache))

         :getSuggestionDetailsOnSelect #(detailed-suggestion % cache)}))
         ; :onDidInsertSuggestion #(prn :ALMOST-THERE)
         ; :dispose #(prn :I-AM-DONE)}))
