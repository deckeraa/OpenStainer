(ns slide-stainer.procedure-run
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest defcard-rg]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def status-query "{state{procedure_run_status{current_procedure_id,current_procedure_name,current_procedure_step_number,current_procedure_step_start_time}}}")

(def sample-procedure
  {:name "H&E with Harris' Hematoxylin"
   :type :procedure
   :jar_contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure_steps
   [{:substance "Hematoxylin" :time_in_seconds (* 25 60) :jar_number 1}
    {:substance "Tap water" :time_in_seconds 150 :jar_number 2}]})

(defn procedure-run-status
  ([] (procedure-run-status (reagent/atom sample-procedure) (reagent/atom {:current_procedure_step_number 2})))
  ([procedure-cursor procedure-run-status-cursor]
   (fn []
     [:div
      [:h1 (:name @procedure-cursor)]
      [:table
       [:tbody [:tr [:th ""] [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
        (doall (map-indexed (fn [idx step]
                              ^{:key idx}
                              [:tr
                               [:td (if (= (inc idx) (:current_procedure_step_number @procedure-run-status-cursor)) "->" "")]
                               [:td (inc idx)]
                               [:td (:substance step)]
                               [:td ""]
                               [:td (:jar_number step)]])
                            (:procedure_steps @procedure-cursor)))]]
      [:p {} (str "Cycle " (:current_cycle_number @procedure-run-status-cursor) " of " (:repeat @procedure-cursor))]
      [:button {:on-click (graphql/graphql-fn
                           {:query (str "{state{procedure_run_status{" graphql/procedure-run-status-keys "}}}")
                            :handler-fn (fn [resp] (reset! procedure-run-status-cursor (get-in resp [:state :procedure_run_status])))})} "Refresh"]])))

(defcard-rg procedure-run-status-card
  [procedure-run-status])
