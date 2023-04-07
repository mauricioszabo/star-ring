(ns star-linter.core
  (:require [common.atom :refer [subscriptions atom-state]]
            [reagent.dom :as r-dom]
            [re-frame.core :as re]
            ["react" :as react]
            [promesa.core :as p]))

(defonce ^:private ui-pair
  (do
    (deftype ^js UiClass []
      Object
      (getTitle [_] "Star Linter")
      (destroy [this]
        (-> (filter #(.. ^js % getItems (includes this))
                    (.. js/atom -workspace getPanes))
            first
            (some-> (.removeItem this)))))
    [UiClass  (UiClass.)]))
(def ^:private Ui (first ui-pair))
(def ^:private ui (second ui-pair))

(defn open-linter []
  (.. js/atom
      -workspace
      (open "pulsar://star-linter" #js {:location "bottom"
                                        :searchAllPanes true
                                        :activatePane true
                                        :activateItem true})))

(defn- text-editor-observer [^js text-editor]
  (when text-editor
    (re/dispatch [:star-linter/change-editor (.getPath text-editor)])))

(defn ^:dev/after-load activate [state]
  (reset! atom-state state)
  ; (open-linter)
  (.add @subscriptions
        (.. js/atom -workspace (observeActiveTextEditor #(text-editor-observer %))))
  (.add @subscriptions
        (.. js/atom -commands (add "atom-workspace"
                                "star-linter:show-interface"
                                #(open-linter)))))

(defn deactivate []
  (.dispose ^js @subscriptions))

(defn- begin-linting [_linter _path]
  (prn :BEGIN))

(defn- finish-linting [_linter _path]
  (prn :END))

(defn- messages-ui [[_ lint-msg]]
  (let [class (case (:severity lint-msg)
                "warning" [:icon-alert]
                "error" [:icon-bug]
                [:icon-stop])
        [[row col]] (:range lint-msg)]
     [:li.list-tree
      [:a.list-item {:href "#" :on-click (fn [evt]
                                           (.preventDefault evt)
                                           (.. js/atom
                                               -workspace
                                               (open (:file lint-msg)
                                                     #js {:pending true
                                                          :searchAllPanes true
                                                          :location "center"
                                                          :initialLine row
                                                          :initialColumn col})))}
       [:span.icon {:class class} " "]
       [:span " " (:description lint-msg) " (" row ", " col ")"]]]))

(defn main-window []
  (let [messages @(re/subscribe [:star-linter/messages])
        curr-path @(re/subscribe [:star-linter/current-path])
        changed-editor? @(re/subscribe [:star-linter/changed-path?])
        ref (react/useRef nil)]
    (when changed-editor?
      (p/do!
       (p/delay 50)
       (when-let [elem (.-current ref)]
         (.scrollIntoView elem))))

    [:div {:class "native-key-bindings star-linter ui"
           :style {:height "100%" :overflow "auto"}}
     [:h3 "Star Linter"]
     [:ul.list-tree.has-collapsable-children
      [:li.list-nested-item
       (for [[file keys] messages
             :when (seq keys)
             :let [curr-file? (= file curr-path)]]
         [:ul.list-tree
          [:li.list-nested-item (if curr-file?
                                  {:ref ref}
                                  {:class :collapsed})
           [:div.list-item
            [:a {:href "#" :on-click (fn [evt]
                                       (.preventDefault evt)
                                       (.. js/atom
                                           -workspace
                                           (open file #js {:pending true
                                                           :searchAllPanes true
                                                           :location "center"})))}
             [:span.badge.badge-small.icon-info " " (count keys) " "] " " file]]
           (when curr-file?
             (->> keys
                  (sort-by (juxt :severity :range))
                  (map messages-ui)
                  (into [:ul.list-tree {:style {:margin-left "2em"}}])))]])]]]))

(defonce div (js/document.createElement "div"))
(defn register-ui! [^js subs]
  (r-dom/render [:f> main-window] div)
  (.add subs
        (.. js/atom -workspace
            (addOpener (fn [uri] (when (= uri "pulsar://star-linter") ui)))))
  (.add subs (.. js/atom -views (addViewProvider Ui (constantly div)))))

(defn- render [added removed _messages]
  (doseq [msg added] (re/dispatch [:star-linter/add-message msg]))
  (doseq [msg removed] (re/dispatch [:star-linter/remove-message msg])))

(defn- dispose [])

; (defn- create-pane! []
;   (let [div (js/document.createElement "div")]
;     (.. div -classList (add "star-linter" "ui"))
;     (.. js/atom -workspace (addBottomPanel #js {:item div}))))
;
; (defonce pane (create-pane!))
(defn- messages [db]
  (:star-linter/messages db {}))

(defn- add-message [db [_ ^js message]]
  (let [location (.. message -location -file)
        range (.. message -location -position)
        start (.-start range)
        end (.-end range)]
    (-> db
        (assoc-in [:star-linter/messages location (.-key message)]
                  {:file location
                   :linter (.-linterName message)
                   :range [[(.-row start) (.-column start)]
                           [(.-row end) (.-column end)]]
                   :severity (.-severity message)
                   :excerpt (.-excerpt message)
                   :description (.-description message)})
        (assoc :star-linter/changed-path? false))))

(defn- remove-message [db [_ ^js message]]
  (let [location (.. message -location -file)]
    (-> db
        (update-in [:star-linter/messages location] dissoc (.-key message))
        (assoc :star-linter/changed-path? false))))

(defn- change-editor [db [_ path]]
  (assoc db :star-linter/current-path path :star-linter/changed-path? true))

(defn- ^:dev/after-load register-events! []
  (re/reg-sub :star-linter/messages messages)
  (re/reg-sub :star-linter/current-path #(:star-linter/current-path %))
  (re/reg-sub :star-linter/changed-path? #(:star-linter/changed-path? %))
  (re/reg-event-db :star-linter/change-editor change-editor)
  (re/reg-event-db :star-linter/add-message add-message)
  (re/reg-event-db :star-linter/remove-message remove-message))
  ; (r-dom/render [:f> main-window] (.-item pane)))

(defonce registered
  (do
    (register-events!)
    (register-ui! @subscriptions)))

(defn linter-ui []
  #js {:name "Star Linter"
       :didBeginLinting (fn [linter path] (begin-linting linter path))
       :didFinishLinting (fn [linter path] (finish-linting linter path))
       :render (fn [^js input]
                 (render (.-added input)
                         (.-removed input)
                         (.-messages input)))
       :dispose #(dispose)})
