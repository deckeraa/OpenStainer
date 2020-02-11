(ns slide-stainer.procedure-selection
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn procedure-selection [selection-cursor]
  (let [procedure-list [{:_id "8e17e6aa10dee3e07cc42c2107006cfc"
                         :_rev "4-9d6dd9148fc2a3d46894a82d35af2497"
                         :name "H&E with Harris' Hematoxylin"}
                        {:_id "123"
                         :name "Carpet stain"}]]
    (fn []
      [:div
       [:h3 "Procedure Selection"]
       [:ul
        (map (fn [procedure]
               ^{:key (:_id procedure)}
               [:li (:name procedure)])
             procedure-list)]])))
