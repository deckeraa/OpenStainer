(ns slide-stainer.onscreen-keyboard
  (:require [reagent.core :as reagent]
            [devcards.core])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))

(defn osk-input [osk-atm args]
  (let [input-atm (reagent/atom "foo")]
    (fn []
      [:input {:type :text
               :on-focus (fn [e]
                           (swap! osk-atm assoc :input-atm input-atm) ; set the on-screen keyboard to point to this input field's input
                           (println "on-focus" @osk-atm)
                           )
               :on-blur (fn [e] (println "BBBBBBBBBBBBBBBBBBBLLLLLLLLLLLLLLLLLLLLLLLUUUUUUUUUUUUUUUUUUUURRRRRRRRRRRRR"))
               :on-change (fn [e] (reset! input-atm (-> e .-target .-value)))
               :value @input-atm}])))

(def osk-atm (reagent/atom {}))

(defcard-rg osk-input-card
  [osk-input osk-atm {}])

(defn onscreen-keyboard [osk-atm]
  (let [button-fn (fn [val] [:button {:on-click (fn [e]
                                                  (println "on-click" e @osk-atm)
                                                  (when (:input-atm @osk-atm)
                                                    (swap! (:input-atm @osk-atm) (fn [v] (str v val))))
                                                  (println (reagent/current-component))
                                                  (this-as this
                                                    (println this))
                                                  )
                                      :on-mouse-down (fn [e]
                                                       (println "on-mousedown")
                                                       (.preventDefault e))} val]) ]
    (fn [osk-atm]
      [:div
       [:div
        (map button-fn (range 10))
        [:button {:on-click (fn [e]
                              (println "backspace" @osk-atm)
                              (when (:input-atm @osk-atm)
                                (swap! (:input-atm @osk-atm) (fn [v] (subs v 0 (dec (count v))))))
                              )} "Backspace"]]
       [:div
        (map button-fn "qwertyuiop")]
       [:div
        (map button-fn "asdfghjkl")]
       [:div
        (map button-fn "zxcvbnm")]
       [:div
        (button-fn "Space")]
       ])))

(defcard-rg onscreen-keyboard-card
  [onscreen-keyboard osk-atm])

(defcard osk-atm-card
  (str @osk-atm))

(defcard-rg comp
  [:p (reagent/current-component)])
