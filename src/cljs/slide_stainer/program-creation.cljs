(ns slide-stainer.program-creation
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def sample-program
  {:name "H&E with Harris' Hematoxylin"
   :jar-contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure-steps
   [{:substance "Hematoxylin" :time-in-seconds (* 25 60) :jar-number 1}
    {:substance "Tap water" :time-in-seconds 150 :jar-number 2}]})

(defn jar-contents [prog]
  [:h3 "Jar Contents"]
  [:table [:tr [:th "Jar #"] [:th "Substance"]]
   (map-indexed (fn [idx substance] [:tr [:td (inc idx)] [:td substance]]) (:jar-contents prog))])

(defn procedure-steps [prog]
  [:h3 "Procedure Steps"]
  [:table [:tr [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
   (map-indexed (fn [idx step]
                  [:tr
                   [:td (inc idx)]
                   [:td (:substance step)]
                   [:td (:time step)]
                   [:td (:jar-number step)]]) (:procedure-steps prog))])

(defn program-creation
  ([] (program-creation sample-program))
  ([prog]
   [:div
    [:h2 (:name prog)]
    [jar-contents prog]
    [procedure-steps prog]]))
