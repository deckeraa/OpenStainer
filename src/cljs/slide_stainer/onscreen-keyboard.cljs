(ns slide-stainer.onscreen-keyboard
  (:require [reagent.core :as reagent]))

(defn osk-input [osk-atm args]
  (let [input-atm (reagent/atom "")]
    (fn []
      [:input {:type :text
               :on-focus (fn [e]
                           (swap! osk-atm assoc :input-atm input-atm) ; set the on-screen keyboard to point to this input field's input
                           )}])))

(defn onscreen-keyboard [osk-atm]
  (fn [osk-atm]
    [:button {:on-click (fn [e]
                          (println e @osk-atm)
                          (when @osk-atm (swap! (:input-atm @osk-atm) (fn [v] (str v 1))))
                          )} "1"]))
