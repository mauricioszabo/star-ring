(ns star-linter.core
  (:require [common.atom :refer [subscriptions atom-state]]
            [reagent.dom :as r-dom]
            [re-frame.core :as re]))

(defn- text-editor-observer [^js text-editor]
  (when text-editor
    (re/dispatch [:star-linter/change-editor (.getPath text-editor)])))

(defn ^:dev/after-load activate [state]
  (reset! atom-state state)
  (.add @subscriptions
        (.. js/atom -workspace (observeActiveTextEditor #(text-editor-observer %)))))

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
        curr-path @(re/subscribe [:star-linter/current-path])]
    [:ul.list-tree.has-collapsable-children {:style {:height "20em" :overflow "auto"}}
     [:li.list-nested-item
      [:h2 "Star Linter"]
      (for [[file keys] messages
            :when (seq keys)
            :let [curr-file? (= file curr-path)]]
        [:ul.list-tree
         [:li.list-nested-item {:class (if curr-file? [] [:collapsed])}
          [:div.list-item
           [:a {:href "#" :on-click (fn [evt]
                                      (.preventDefault evt)
                                      (.. js/atom
                                          -workspace
                                          (open file
                                                #js {:pending true
                                                     :searchAllPanes true
                                                     :location "center"})))}
            [:span.badge.badge-small.icon-info " " (count keys) " "] " " file]]
          (when curr-file?
            (->> keys
                 (sort-by (juxt :severity :range))
                 (map messages-ui)
                 (into [:ul.list-tree {:style {:margin-left "2em"}}])))]])]]))

(defn- render [added removed _messages]
  (doseq [msg added] (re/dispatch [:star-linter/add-message msg]))
  (doseq [msg removed] (re/dispatch [:star-linter/remove-message msg])))

(defn- dispose [])

(defn- create-pane! []
  (let [div (js/document.createElement "div")]
    (.. js/atom -workspace (addBottomPanel #js {:item div}))))

(defonce pane (create-pane!))

(defn- messages [db]
  (:star-linter/messages db {}))

(defn- add-message [db [_ ^js message]]
  (let [location (.. message -location -file)
        range (.. message -location -position)
        start (.-start range)
        end (.-end range)]
    (assoc-in db [:star-linter/messages location (.-key message)]
               {:file location
                :linter (.-linterName message)
                :range [[(.-row start) (.-column start)]
                        [(.-row end) (.-column end)]]
                :severity (.-severity message)
                :excerpt (.-excerpt message)
                :description (.-description message)})))

(defn- remove-message [db [_ ^js message]]
  (let [location (.. message -location -file)]
    (update-in db [:star-linter/messages location] dissoc (.-key message))))

(defn- change-editor [db [_ path]]
  (assoc db :star-linter/current-path path))

(defn- ^:dev/after-load register-events! []
  (re/reg-sub :star-linter/messages messages)
  (re/reg-sub :star-linter/current-path #(:star-linter/current-path %))
  (re/reg-event-db :star-linter/change-editor change-editor)
  (re/reg-event-db :star-linter/add-message add-message)
  (re/reg-event-db :star-linter/remove-message remove-message)
  (r-dom/render [main-window] (.-item pane)))

(defn ui []
  (register-events!)
  #js {:name "Star Linter"
       :didBeginLinting (fn [linter path] (begin-linting linter path))
       :didFinishLinting (fn [linter path] (finish-linting linter path))
       :render (fn [^js input] (render (.-added input)
                                       (.-removed input)
                                       (.-messages input)))
       :dispose #(dispose)})
