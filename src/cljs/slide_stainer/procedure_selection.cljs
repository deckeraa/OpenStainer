(ns slide-stainer.procedure-selection
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn procedure-selection [procedure-list-cursor selection-cursor selected-success-fn]
  (let [list-query-sent-atom (atom false)]
    (fn []
      (when (and true (not @list-query-sent-atom))
        (do
          (reset! list-query-sent-atom true)
          ((graphql/graphql-fn {:query "{procedures{_id,name,runs}}"
                                :handler-fn (fn [resp]
                                              (reset! procedure-list-cursor (:procedures resp)))}))))
      [:div {:class "procedure_selection"}
       [:h1 {:class "nav-header"} "Select a staining procedure:"]
       [:ul
        (map (fn [procedure]
               ^{:key (:_id procedure)}
               [:li {:on-click
                     (graphql/graphql-fn {:query (str "{procedureById(id:\"" (:_id procedure) "\"){" graphql/procedure-keys "}}")
                                          :handler-fn (fn [resp]
                                                        (reset! selection-cursor (:procedureById resp))
                                                        (when selected-success-fn (selected-success-fn))
                                                        (println "procedureById resp: " resp))})}
                [:h3 (:name procedure)]
                [:p (str "Runs: " (or (:runs procedure) 0))]
                ])
             (sort (fn [el1 el2]
                     ;; sort first by number of runs, then alphabetically
                     (cond (> (:runs el1) (:runs el2)) true
                           (< (:runs el1) (:runs el2)) false
                           (< (clojure.string/lower-case (:name el1))
                              (clojure.string/lower-case (:name el2))) true
                           :default false))
                   @procedure-list-cursor))
        [:li {:class "new-procedure-button"
              :on-click (fn [e]
                             (reset! selection-cursor graphql/empty-procedure)
                          (when selected-success-fn (selected-success-fn)))}
         "Create new procedure"]]])))
