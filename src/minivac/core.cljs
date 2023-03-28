(ns minivac.core
  (:require [common.atom :refer [subscriptions atom-state] :as atom]
            ; [reagent.dom :as r-dom]
            ; [re-frame.core :as re]
            [promesa.core :as p]))
            ; ["openai" :refer [Configuration OpenAIApi]]))

(def config
  (clj->js {:gpt-token {:title "GPT Token"
                        :description "A token from GPT - find it at https://platform.openai.com/account/api-keys"
                        :type "string"}}))

  ; ; (open-linter)
  ; (.add @subscriptions
  ;       (.. js/atom -workspace (observeActiveTextEditor #(text-editor-observer %))))
  ; (.add @subscriptions
  ;       (.. js/atom -commands (add "atom-workspace"
  ;                               "star-linter:show-interface"
  ;                               #(open-linter)))))

(defn deactivate []
  (.dispose ^js @subscriptions))

(defn- suggest-code! []
  (if-let [text (not-empty (.. js/atom -workspace getActiveTextEditor getSelectedText))]
    (p/let [^js editor (.. js/atom -workspace getActiveTextEditor)
            selection (.getSelectedBufferRange editor)
            token (.. js/atom -config (get "minivac.gpt-token"))
            ^js f (js/fetch  "https://api.openai.com/v1/completions"
                             (clj->js
                              {:method "POST"
                               :headers {"Authorization" (str "Bearer " token)
                                         "Content-Type" "application/json"}
                               :body (js/JSON.stringify
                                      #js {:model "text-davinci-003"
                                           :prompt text
                                           :max_tokens 100
                                           :temperature 0.1})}))
            ^js json (.json f)
            [choice] (.-choices json)
            suggestion (.-text choice)]
      (.setTextInBufferRange editor selection suggestion))
    (atom/error! "Select a text before prompting")))

(defn- make-edit! [^js editor selection instruction text]
  (p/let [token (.. js/atom -config (get "minivac.gpt-token"))
          ^js f (js/fetch  "https://api.openai.com/v1/edits"
                           (clj->js
                            {:method "POST"
                             :headers {"Authorization" (str "Bearer " token)
                                       "Content-Type" "application/json"}
                             :body (js/JSON.stringify
                                    #js {:model "text-davinci-edit-001"
                                         :input text
                                         :instruction instruction
                                         :temperature 0.1})}))
          ^js json (.json f)
          _ (def json json)
          [choice] (.-choices json)
          suggestion (.-text choice)]
    (.setTextInBufferRange editor selection suggestion)))

(defn- edit-code! []
  (if-let [text (not-empty (.. js/atom -workspace getActiveTextEditor getSelectedText))]
    (p/let [^js editor (.. js/atom -workspace getActiveTextEditor)
            selection (.getSelectedBufferRange editor)
            instruction (atom/prompt! "How do you want your text to be changed?")]
      (when instruction (make-edit! editor selection instruction text)))
    (atom/error! "Select a text to edit before prompting")))

(defn ^:dev/after-load activate [state]
  (reset! atom-state state)
  (.add @subscriptions
        (.. js/atom -commands (add "atom-text-editor"
                                "minivac:suggest-code-for-implementation"
                                #(suggest-code!))))
  (.add @subscriptions
        (.. js/atom -commands (add "atom-text-editor"
                                "minivac:change-selected-code"
                                #(edit-code!)))))
