(ns slide-stainer.procedure-run
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest defcard-rg]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk]
            [slide-stainer.program-creation]
            [cljs-time.core :as time]
            [cljs-time.format :as format])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def status-query "{state{procedure_run_status{current_procedure_id,current_procedure_name,current_procedure_step_number,current_procedure_step_start_time}}}")

(def sample-procedure
  {:name "H&E with Harris' Hematoxylin"
   :type :procedure
   :jar_contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure_steps
   [{:substance "Hematoxylin" :time_in_seconds (* 25 60) :jar_number 1}
    {:substance "Tap water" :time_in_seconds 150 :jar_number 2}]})

(defn refresh-fn [procedure-cursor procedure-run-status-cursor] 
  (graphql/graphql-fn
   {:query (str "{state{procedure_run_status{" graphql/procedure-run-status-keys "}}}")
    :handler-fn (fn [resp] (reset! procedure-run-status-cursor (get-in resp [:state :procedure_run_status])))}))

(defn format-time-in-seconds [seconds]
  (let [min (Math/floor (/ seconds 60))
        sec (as-> (rem seconds 60) $
              (str $)
              (if (= 1 (count $)) (str "0" $) $))]
    (str min ":" sec)))

(deftest format-time-in-seconds-test
  (is (= "2:00" (format-time-in-seconds 120)))
  (is (= "0:12" (format-time-in-seconds  12)))
  (is (= "1:01" (format-time-in-seconds  61))))

(defn procedure-run-status
  ([] (procedure-run-status (reagent/atom sample-procedure) (reagent/atom {:current_procedure_step_number 2})))
  ([procedure-cursor procedure-run-status-cursor]
   (fn []
     [:div
      [:h1 (:name @procedure-cursor)]
      [:table
       [:tbody [:tr [:th ""] [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
        (doall (map-indexed (fn [idx step]
                              (let [current-step? (= (inc idx) (:current_procedure_step_number @procedure-run-status-cursor))
                                    current-start-time (format/parse (:current_procedure_step_start_time @procedure-run-status-cursor))]
                                ^{:key idx}
                                [:tr
                                 [:td (if current-step? "->" "")]
                                 [:td (inc idx)]
                                 [:td (:substance step)]
                                 [:td (format-time-in-seconds (:time_in_seconds step))
;;                                   (if (and current-step?)
;;       ;                                  (:current_procedure_step_start_time @procedure-run-status-cursor)
;;                                    ;     (str current-start-time)
;; ;                                        (str (time/in-seconds (time/interval current-start-time (time/now))))
                                  ;;                                         (format-time-in-seconds (:time_in_seconds step)))
                                  ]
                                 [:td (:jar_number step)]]))
                            (:procedure_steps @procedure-cursor)))]]
      [:p {} (str "Cycle " (:current_cycle_number @procedure-run-status-cursor) " of " (:repeat @procedure-cursor))]
      [:button {:on-click (refresh-fn procedure-cursor procedure-run-status-cursor)} "Refresh"]
      [slide-stainer.program-creation/run-button procedure-run-status-cursor procedure-cursor nil]
      ])))

(defcard-rg procedure-run-status-card
  [procedure-run-status])
