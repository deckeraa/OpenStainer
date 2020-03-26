(ns slide-stainer.procedure-run
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest defcard-rg]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.svg :as svg]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk]
            [slide-stainer.procedure-edit]
            [cljs-time.core :as time]
            [cljs-time.format :as format])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; (def status-query "{state{procedure_run_status{current_procedure_id,current_procedure_name,current_procedure_step_number,current_procedure_step_start_time}}}")
(def status-query "{runStatus{currentProcedureStepNumber,currentCycleNumber}}")

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

(defn refresh-query-fn []
  status-query
;  (str "{state{procedure_run_status{" graphql/procedure-run-status-keys "}}}")
  )

(defn refresh-handler-fn [procedure-cursor procedure-run-status-cursor resp]
  (reset! procedure-run-status-cursor (get-in resp [:runStatus])))

(defn rest-fn []
  (http/get "http://localhost:8000/seconds_remaining")
  )

(defn rest-handler-fn [procedure-cursor procedure-run-status-cursor resp raw-resp]
  (swap! procedure-run-status-cursor assoc :seconds-remaining (js/parseInt resp)))

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
  ([] (procedure-run-status (reagent/atom sample-procedure) (reagent/atom {:currentProcedureStepNumber 2}) nil))
  ([procedure-cursor procedure-run-status-cursor back-fn]
   (fn []
     [:div
      [:div {:class "nav-header"}
       (when back-fn [svg/chevron-left {:class "chevron-left" :on-click back-fn} "blue" 36])
       [:h1 (:name @procedure-cursor)]]
      [:table
       [:tbody [:tr [:th ""] [:th "Step #"] [:th "Substance"] [:th "Total Time"] [:th "Jar #"]]
        (doall (map-indexed (fn [idx step]
                              (let [current-step? (= (inc idx) (:currentProcedureStepNumber @procedure-run-status-cursor))
                                    current-start-time (format/parse (:current_procedure_step_start_time @procedure-run-status-cursor))
                                    seconds-remaining (:seconds-remaining @procedure-run-status-cursor)]
                                ^{:key idx}
                                [:tr
                                 [:td (if current-step? "->" "")]
                                 [:td (inc idx)]
                                 [:td (:substance step)]
                                 [:td
                                  (if (and current-step? (> seconds-remaining 0))
                                    (format-time-in-seconds seconds-remaining)
                                    (format-time-in-seconds (:timeInSeconds step)))
                                  ]
                                 [:td (:jarNumber step)]]))
                            (:procedureSteps @procedure-cursor)))]]
      [:p {} (str "Cycle " (:currentCycleNumber @procedure-run-status-cursor) " of " (or (:repeat @procedure-cursor) 1))]
      ;; [:button {:on-click (refresh-fn procedure-cursor procedure-run-status-cursor)} "Refresh"]
      [:button {:on-click (fn [e] (go (let [resp (<! (http/post "http://localhost:8000/pause_procedure"))]
                                        (println "pause_procedure " resp))))} "Pause"]
      [:button {:on-click (fn [e] (go (let [resp (<! (http/post "http://localhost:8000/resume_procedure"))]
                                        (println "resume_procedure " resp))))} "Resume"]
      [slide-stainer.procedure-edit/run-button procedure-run-status-cursor procedure-cursor nil]
      ])))

(defcard-rg procedure-run-status-card
  [procedure-run-status])
