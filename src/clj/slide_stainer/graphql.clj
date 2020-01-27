(ns slide-stainer.graphql
  (:require [slide-stainer.defs :refer :all]))

(defn resolve-state [context args value]
  {:contents (str @state-atom)})

;; (defn resolve-pin-by-id [context args value]
;;   (println "resolve-pin-by-id" args value)
;;   (when (not (:device @state-atom)) (init-pins))
;;   (let [id (normalize-pin-tag (:id args))
;;         pin-info (id (:setup-index @state-atom))
;;         board_value (gpio/get-line (:buffer @state-atom) id)]
;;     {:id (str id)
;;      :board_value board_value
;;      :logical_value (if (:inverted? pin-info) (not board_value) board_value)
;;      :pin_number (:pin_number pin-info)
;;      }))
