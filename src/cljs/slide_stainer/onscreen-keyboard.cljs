(ns slide-stainer.onscreen-keyboard
  (:require [reagent.core :as reagent]
            [devcards.core])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))

(defn osk-input [osk-atm args]
  (let [input-atm (reagent/atom "foo")
        ref-atm   (clojure.core/atom nil)]
    (fn []
      [:input {:type :text
               :on-focus (fn [e]
                           (swap! osk-atm assoc :input-atm input-atm) ; set the on-screen keyboard to point to this input field's input
                           (swap! osk-atm assoc :el-atm ref-atm)
                           (println "on-focus" @osk-atm)
                           )
               :on-blur (fn [e] (println "BBBBBBBBBBBBBBBBBBBLLLLLLLLLLLLLLLLLLLLLLLUUUUUUUUUUUUUUUUUUUURRRRRRRRRRRRR"))
               :on-change (fn [e] (reset! input-atm (-> e .-target .-value)))
               :ref (fn [el] (reset! ref-atm el))
               :value @input-atm}])))

(def osk-atm (reagent/atom {}))

(defcard-rg osk-input-card
  [osk-input osk-atm {}])

(defn onscreen-keyboard [osk-atm]
  (let [button-fn (fn [val] 
                    (let [input-atm (:input-atm @osk-atm)
                          el-atm (:el-atm @osk-atm)]
                      ^{:key val}
                      [:button {:on-click (fn [e]
                                            (when el-atm (println @el-atm (.-selectionStart @el-atm)))
                                            (println "on-click" e @osk-atm)
                                            (when (and input-atm el-atm)
                                              (let [cursor-pos (.-selectionStart @el-atm)]
                                                (swap! input-atm (fn [v]
                                                                   (str (subs v 0 cursor-pos)
                                                                        val
                                                                        (subs v cursor-pos (count v))))))))
                                :on-mouse-down (fn [e]
                                                 (println "on-mousedown")
                                                 (.preventDefault e))} val])) ]
    (fn [osk-atm]
      [:div
       [:div
        (doall (map button-fn (range 10)))
        [:button {:on-click (fn [e]
                              (println "backspace" @osk-atm)
                              (when (:input-atm @osk-atm)
                                (swap! (:input-atm @osk-atm) (fn [v] (subs v 0 (dec (count v))))))
                              )} "Backspace"]]
       [:div
        (doall (map button-fn "qwertyuiop"))]
       [:div
        (doall (map button-fn "asdfghjkl"))]
       [:div
        (doall (map button-fn "zxcvbnm"))]
       [:div
        (button-fn "Space")]
       ])))

(defcard-rg onscreen-keyboard-card
  [onscreen-keyboard osk-atm])

(defcard osk-atm-card
  (str @osk-atm))

(defcard-rg comp
  [:p (reagent/current-component)])
