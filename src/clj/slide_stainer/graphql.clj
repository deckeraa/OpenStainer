(ns slide-stainer.graphql
  (:require [slide-stainer.defs :refer :all]))

(defn resolve-state [context args value]
  {:contents (str @state-atom)})
