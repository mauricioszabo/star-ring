(ns minivac.core
  (:require [common.atom :refer [subscriptions atom-state] :as atom]
            [promesa.core :as p]))

(def config
  (clj->js {:gpt-token {:title "GPT Token"
                        :description "A token from GPT - find it at https://platform.openai.com/account/api-keys"
                        :type "string"}}))

(defn deactivate []
  (.dispose ^js @subscriptions))

(defn- make-request-and-replace-editor! [url body]
  (p/let [^js editor (.. js/atom -workspace getActiveTextEditor)
          selection (.getSelectedBufferRange editor)
          token (.. js/atom -config (get "minivac.gpt-token"))
          ^js f (js/fetch  (str "https://api.openai.com/" url)
                           (clj->js
                            {:method "POST"
                             :headers {"Authorization" (str "Bearer " token)
                                       "Content-Type" "application/json"}
                             :body (-> body clj->js js/JSON.stringify)}))
          ^js json (.json f)
          [choice] (.-choices json)
          suggestion (.-text choice)]
    (.setTextInBufferRange editor selection suggestion)))

(defn- suggest-code! []
  (if-let [text (not-empty (.. js/atom -workspace getActiveTextEditor getSelectedText))]
    (make-request-and-replace-editor! "v1/completions"
                                      {:model "text-davinci-003"
                                       :prompt text
                                       :max_tokens 2048
                                       :temperature 0.1})
    (atom/error! "Select a text before prompting")))

(defn- edit-code! []
  (if-let [text (not-empty (.. js/atom -workspace getActiveTextEditor getSelectedText))]
    (p/let [^js editor (.. js/atom -workspace getActiveTextEditor)
            selection (.getSelectedBufferRange editor)
            instruction (atom/prompt! "How do you want your text to be changed?")]
      (when instruction
        (make-request-and-replace-editor! "v1/edits"
                                          {:model "code-davinci-edit-001"
                                           :input text
                                           :instruction instruction
                                           :temperature 0.1})))
        ; (make-edit! editor selection instruction text)))
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
