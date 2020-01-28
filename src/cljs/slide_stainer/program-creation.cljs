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

(defn rename-substance [prog-atm jar-number new-substance]
  "Don't forget that jar-number is 1-indexed."
  (swap! prog-atm (fn [prog]
                    (as-> prog $
                      (assoc-in $ [:jar-contents (dec jar-number)] new-substance)
                      (assoc-in $ [:procedure-steps] (mapv (fn [step]
                                                             (if (= jar-number (:jar-number step))
                                                               (assoc step :substance new-substance)
                                                               step))
                                                           (:procedure-steps $)))
                      ))))

(defn jar-contents [prog-atm]
  [:h3 "Jar Contents"]
  [:table [:tbody [:tr [:th "Jar #"] [:th "Substance"]]
           (map-indexed (fn [idx substance]
                          ^{:key idx}
                          [:tr
                           [:td (inc idx)]
                           [:td [:input {:type "text" :value substance
                                         :on-change (fn [e] (rename-substance prog-atm (inc idx) (-> e .-target .-value)))}]]])
                        (:jar-contents @prog-atm))]])

(defn substance-selector [option-list step-cursor]
  "Ex: (substance-selector [\"Hematoxylin\" \"Tap water\" \"Eosin\"] \"Eosin\")"
  (fn [option-list step-cursor]
;    (println "Re-running substance-selector for" @step-cursor option-list)
    [:select {:name "substance" :value (:substance @step-cursor)
              :on-change (fn [e]
                           (let [new-substance (-> e .-target .-value)]
                             (swap! step-cursor
                                    (fn [substance]
                                      (as-> substance $
                                        (assoc $ :substance new-substance)
                                        (assoc $ :jar-number (inc (.indexOf option-list new-substance))))))))}
     (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option]) option-list)]))

(defn jar-selector [option-list step-cursor]
  (fn [option-list step-cursor]
    (let [options (as-> option-list $
                    (map-indexed (fn [idx itm] [(inc idx) itm]) $)
                    (filter #(= (:substance @step-cursor) (second %)) $)
                    (mapv first $))]
      (if (> (count options) 1)
        [:select {:name "jar-number" :value (:jar-number @step-cursor)
                  :on-change (fn [e] (swap! step-cursor #(assoc % :jar-number (-> e .-target .-value))))}
         (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option]) options)]
        [:div (:jar-number @step-cursor)]))))

(defn procedure-steps [prog-atm]
  (fn []
    (let [steps-cursor (reagent/cursor prog-atm [:procedure-steps])
          substance-options (:jar-contents @prog-atm)]
;      (println "Re-running procedure-steps: " substance-options)
      [:h3 "Procedure Steps"]
      [:table
       [:tbody [:tr [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
        (map-indexed (fn [idx step]
                       (let [step-cursor (reagent/cursor steps-cursor [idx])]
                         ^{:key idx}
                         [:tr
                          [:td (inc idx)]
                          [:td [substance-selector substance-options step-cursor]]
                          [:td (:time step)]
                          [:td [jar-selector substance-options step-cursor]]])) @steps-cursor)]])))

(defn program-creation
  ([] (program-creation sample-program-atom))
  ([prog-atm]
   [:div
    [:h2 (:name @prog-atm)]
    [jar-contents prog-atm]
    [procedure-steps prog-atm]
    [:div (str @prog-atm)]]))
