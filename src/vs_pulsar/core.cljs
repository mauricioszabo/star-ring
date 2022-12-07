(ns vs-pulsar.core
  (:require [common.atom :refer [subscriptions atom-state]]))

(defn activate [state]
  (reset! atom-state state))

(defn deactivate [])
