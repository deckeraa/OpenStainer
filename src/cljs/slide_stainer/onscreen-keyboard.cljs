(ns slide-stainer.onscreen-keyboard
  (:require [reagent.core :as reagent]
            [devcards.core])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))

(defn osk-input [osk-atm args]
  (let [input-atm (reagent/atom (or (:value args) ""))
        ref-atm   (clojure.core/atom nil)]
    (fn []
      [:input {:type :text
               :class "onscreen-keyboard-input"
               :on-focus (fn [e]
                           (swap! osk-atm (fn [osk-map]
                                           (-> osk-map
                                               (assoc :input-atm input-atm) ; set the on-screen keyboard to point to this input field's input
                                               (assoc :el-atm ref-atm)
                                               (assoc :args args)
                                               (assoc :open? true)))))
               :on-blur (fn [e]
                          (println "BBBBBBBBBBBBBBBBBBBLLLLLLLLLLLLLLLLLLLLLLLUUUUUUUUUUUUUUUUUUUURRRRRRRRRRRRR")
                          (swap! osk-atm (fn [osk-map]
                                           (-> osk-map
                                               (assoc :input-atm nil)
                                               (assoc :el-atm nil)
                                               (assoc :args nil)
                                               (assoc :open? false)))))
               :on-change (fn [e]
                            (reset! input-atm (-> e .-target .-value))
                            (println "on-change" args)
                            (when (:on-change args) ((:on-change args) @input-atm)))
               :ref (fn [el] (reset! ref-atm el))
               :value @input-atm}])))

(def osk-atm (reagent/atom {}))

(defcard-rg osk-input-card
  [osk-input osk-atm {}])

(defcard-rg osk-input-card-with-change-handler
  (let [counter-atm (reagent/atom 0)]
    (fn []
      [:div
       [:p (str "Counter: " @counter-atm)]
       [osk-input osk-atm {:on-change (fn [e] (println "Calling inc") (swap! counter-atm inc))}]])))

(defn osk-button
  ([osk-atm val]
   (osk-button osk-atm val nil nil))
  ([osk-atm val display-name]
   [osk-button osk-atm val display-name nil])
  ([osk-atm val display-name special-click-handler]
   (let [input-atm (:input-atm @osk-atm)
         el-atm (:el-atm @osk-atm)]
     ^{:key val}
     [:button {:on-click (fn [e]
                           (when (and input-atm el-atm)
                             (let [cursor-pos (.-selectionStart @el-atm)]
                               (if special-click-handler ; if we have a special handler (for example, Backspace)...
                                 (special-click-handler osk-atm e input-atm el-atm cursor-pos) ; ... then run that ...
                                 (swap! input-atm (fn [v] ; ... otherwise, insert val at the cursor position
                                                    (str (subs v 0 cursor-pos)
                                                         val
                                                         (subs v cursor-pos (count v))))))
                               (when (:args @osk-atm)
                                 (when-let [change-fn (get-in @osk-atm [:args :on-change])] 
                                   (change-fn @input-atm))))))
               :on-mouse-down (fn [e] ; try to avoid the event from bubbling so far as to take focus away from the input field
                                (.preventDefault e))}
      (if display-name display-name val)])))

(defn onscreen-keyboard [osk-atm]
  (let [button-fn (partial osk-button osk-atm)]
    (fn [osk-atm]
      [:div {:class (str "onscreen-keyboard" " " (if (:open? @osk-atm) "onscreen-keyboard-open" "onscreen-keyboard-closed"))}
       [:div
        (doall (map button-fn (range 10)))
        (button-fn nil "Backspace" (fn [osk-atm e input-atm el-atm cursor-pos]
                                     (swap! input-atm (fn [v]
                                                        (str (subs v 0 (dec cursor-pos))
                                                             (subs v cursor-pos (count v)))))))]
       [:div
        (doall (map button-fn "qwertyuiop"))]
       [:div
        (doall (map button-fn "asdfghjkl"))]
       [:div
        (doall (map button-fn "zxcvbnm"))]
       [:div
        (button-fn " " "Space")
        (button-fn ".")
        (button-fn nil "Done" (fn [osk-atm e input-atm el-atm cursor-pos]
                                (.blur @el-atm)
                                (swap! osk-atm (fn [osk-map]
                                                 (-> osk-map
                                                     (assoc :input-atm nil)
                                                     (assoc :el-atm nil)
                                                     (assoc :open? false))))))]])))

(defcard-rg onscreen-keyboard-card
  [onscreen-keyboard osk-atm])

(defcard-rg osk-atm-card
  (fn []
    [:div 
     (str @osk-atm)]))


