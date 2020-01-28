(ns slide-stainer.program-creation
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [cljs.test :refer-macros [deftest is testing run-tests]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def sample-program
  {:name "H&E with Harris' Hematoxylin"
   :jar-contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure-steps
   [{:substance "Hematoxylin" :time-in-seconds (* 25 60) :jar-number 1}
    {:substance "Tap water" :time-in-seconds 150 :jar-number 2}]})

(def sample-program-atom (reagent/atom sample-program))

(defn jar-contents [prog-atm]
  [:h3 "Jar Contents"]
  [:table [:tr [:th "Jar #"] [:th "Substance"]]
   (map-indexed (fn [idx substance] [:tr [:td (inc idx)] [:td substance]]) (:jar-contents @prog-atm))])

(defn substance-selector [option-list substance-cursor]
  "Ex: (substance-selector [\"Hematoxylin\" \"Tap water\" \"Eosin\"] \"Eosin\")"
  [:select {:name "substance" :value (:substance @substance-cursor)
            :on-change (fn [e] (do
                                 (println (-> e .-target .-value))
                                 (swap! substance-cursor assoc :substance (-> e .-target .-value))))}
   (map (fn [option] [:option {:value option} option]) option-list)])

(defn procedure-steps [prog-atm]
  (let [steps-cursor (reagent/cursor prog-atm [:procedure-steps])]
    (fn []
      [:h3 "Procedure Steps"]
      [:table [:tr [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
       (map-indexed (fn [idx step]
                      [:tr
                       [:td (inc idx)]
                       [:td (substance-selector (:jar-contents @prog-atm) (reagent/cursor steps-cursor [idx]))]
;                       [:td (substance-selector (:jar-contents @prog-atm) (:substance step))]
                       ;; [:td [:select {:name "substance" :value (:substance step)}
                       ;;       [:option {:value "Hematoxylin"} "Hematoxylin"]
                       ;;       [:option {:value "Tap water"} "Tap water"]]]
                       [:td (:time step)]
                       [:td (:jar-number step)]]) @steps-cursor)])))

(defn program-creation
  ([] (program-creation sample-program-atom))
  ([prog-atm]
   [:div
    [:h2 (:name @prog-atm)]
    [jar-contents prog-atm]
    [procedure-steps prog-atm]]))
