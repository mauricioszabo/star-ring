(ns generic-lsp.atom)

(defn info! [message]
  (.. js/atom -notifications (addInfo message)))

(defn error! [message]
  (.. js/atom -notifications (addError message)))

(defn warn! [message]
  (.. js/atom -notifications (addWarning message)))
