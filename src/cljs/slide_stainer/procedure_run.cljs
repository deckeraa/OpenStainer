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

(def sample-procedure
  {:name "H&E with Harris' Hematoxylin"
   :type :procedure
   :jarContents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedureSteps
   [{:substance "Hematoxylin" :timeInSeconds (* 25 60) :jarNumber 1}
    {:substance "Tap water" :timeInSeconds 150 :jarNumber 2}]})

;; screen refresh functions
(defn refresh-query-fn []
  "{runStatus{currentProcedureStepNumber,currentCycleNumber}}")

(defn refresh-handler-fn [procedure-cursor procedure-run-status-cursor resp]
  (swap! procedure-run-status-cursor (fn [atm]
                                       (as-> atm $
                                         (assoc $ :currentProcedureStepNumber (get-in resp [:runStatus :currentProcedureStepNumber]))
                                         (assoc $ :currentCycleNumber (get-in resp [:runStatus :currentCycleNumber]))))))

;; functions to update the seconds remaining
(defn run-query-fn [procedure-run-status-cursor]
  (not (nil? (:currentCycleNumber @procedure-run-status-cursor))))

(defn rest-fn []
  (http/get "http://localhost:8000/seconds_remaining"))

(defn rest-handler-fn [procedure-cursor procedure-run-status-cursor resp raw-resp]
  (let [seconds-remaining (js/parseInt resp)]
    (when (not (nil? seconds-remaining))
      (swap! procedure-run-status-cursor assoc :seconds-remaining seconds-remaining))))

;; Reagent controls and drawing code

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
     [:div {:style {:width "100%"}}
      [:div {:class "nav-header"}
       (when back-fn
       [:button {:class "round-button" :on-click back-fn}
        [svg/chevron-left {:class "chevron-left" } "white" 36]])
       [:h1 (:name @procedure-cursor)]]
      [:div {:style {:display :flex :align-items :center :width "100%" :flex-direction :column}}
       [:table {:class "procedure-run-status"}
        [:tbody [:tr [:th ""] [:th "Step #"] [:th "Substance"] [:th "Total Time"] [:th "Jar #"]]
         (doall (map-indexed (fn [idx step]
                               (let [current-step? (= (inc idx) (:currentProcedureStepNumber @procedure-run-status-cursor))
                                     current-start-time (format/parse (:current_procedure_step_start_time @procedure-run-status-cursor))
                                     seconds-remaining (:seconds-remaining @procedure-run-status-cursor)]
                                 ^{:key idx}
                                 [:tr
                                  [:td {:style {:min-width "48px"}} (if current-step? [svg/right-arrow {} "black" "48px"] "")]
                                  [:td (inc idx)]
                                  [:td (:substance step)]
                                  [:td
                                   (if (and current-step? (> seconds-remaining 0))
                                     (format-time-in-seconds seconds-remaining)
                                     (format-time-in-seconds (:timeInSeconds step)))
                                   ]
                                  [:td (:jarNumber step)]]))
                             (:procedureSteps @procedure-cursor)))]]
       [:p {} (str "Cycle "
                   (or (:currentCycleNumber @procedure-run-status-cursor) 1)
                   " of "
                   (or (:repeat @procedure-cursor) 1))]]
      [:div {:style {:display :flex :justify-content :space-between :width "100%"} }
       [:button {:on-click (fn [e] (go (let [resp (<! (http/post "http://localhost:8000/stop_procedure"))]
                                         (println "stop_procedure " resp))))}
        "Stop procedure"]
       [:div 
        [:button {:on-click (fn [e] (go (let [resp (<! (http/post "http://localhost:8000/pause_procedure"))]
                                          (println "pause_procedure " resp))))} "Pause"]
        [:button {:on-click (fn [e] (go (let [resp (<! (http/post "http://localhost:8000/resume_procedure"))]
                                          (println "resume_procedure " resp))))} "Resume"]]]
      ])))

(defcard-rg procedure-run-status-card
  [procedure-run-status])
