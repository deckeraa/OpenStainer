(ns slide-stainer.procedure-selection
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn procedure-selection [selection-cursor selected-success-fn]
  (let [procedure-list-atom (reagent/atom []) ;; [{:_id "8e17e6aa10dee3e07cc42c2107006cfc"
        ;;   :_rev "4-9d6dd9148fc2a3d46894a82d35af2497"
                       ;;   :name "H&E with Harris' Hematoxylin"}
                       ;;  {:_id "123"
                       ;;   :name "Carpet stain"}]
        list-query-sent-atom (atom false)]
    (fn []
      (when (and (empty? @procedure-list-atom) (not @list-query-sent-atom))
        (do
          (reset! list-query-sent-atom true)
          ((graphql/graphql-fn {:query "{procedures{_id,name}}"
                                :handler-fn (fn [resp]
                                              (reset! procedure-list-atom (:procedures resp)))}))))
      [:div {:class "procedure_selection"}
       [:h3 "Procedure Selection"]
       [:ul
        (map (fn [procedure]
               ^{:key (:_id procedure)}
               [:li {:on-click
                     (graphql/graphql-fn {:query (str "{procedure_by_id(_id:\"" (:_id procedure) "\"){" graphql/procedure-keys "}}")
                                          :handler-fn (fn [resp]
                                                        (reset! selection-cursor (:procedure_by_id resp))
                                                        (when selected-success-fn (selected-success-fn))
                                                        (println "procedure_by_id resp: " resp))})}
                (:name procedure)])
             @procedure-list-atom)]
       [:button {:on-click (fn [e]
                             (reset! selection-cursor {:type "procedure"})
                             (when selected-success-fn (selected-success-fn)))} "+"]])))
