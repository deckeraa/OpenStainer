(ns slide-stainer.onscreen-keyboard
  (:require [reagent.core :as reagent]
            [devcards.core])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))

(defn get-supported-args [args]
  (select-keys args [:size]))

(defn osk-input [osk-atm args]
  (let [input-atm (reagent/atom (or (:value args) ""))
        ref-atm   (clojure.core/atom nil)]
    (fn []
      [:input (merge
               (get-supported-args args)
               {:type :text
                :class "onscreen-keyboard-input"
                :on-focus (fn [e]
                            (swap! osk-atm (fn [osk-map]
                                             (-> osk-map
                                                 (assoc :input-atm input-atm) ; set the on-screen keyboard to point to this input field's input
                                                 (assoc :el-atm ref-atm)
                                                 (assoc :args args)
                                                 (assoc :open? true))))
                            (js/setTimeout (fn [] (.scrollIntoView @ref-atm)) 100)) ; TODO putting this in a timeout is kind of hacky -- we need to call scrollIntoView after the onscreen-keyboard-placeholder becomes visible. Hence the timeout as a poor man's way of making that happen.
                :on-blur (fn [e]
                           (when (:on-blur args) ((:on-blur args) @input-atm))
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
                :value @input-atm})])))

(def osk-atm (reagent/atom {}))

(defcard-rg osk-input-card
  [osk-input osk-atm {}])

(defcard-rg osk-input-card-with-change-handler
  (let [counter-atm (reagent/atom 0)]
    (fn []
      [:div
       [:p (str "Counter: " @counter-atm)]
       [osk-input osk-atm {:on-change (fn [e] (println "Calling inc") (swap! counter-atm inc))}]])))

(defn- shift [val shift?]
  "Take either a string or a vector of strings (the first element being the 'lowercase' version
and the second element being the 'uppercase version) and a boolean indicating whether shifting should occur,
and returns the properly shifted string."
  ; if we passed in something like ["`" "~"]
  (cond (vector? val)
        (if shift? (second val) (first val))
        (string? val)
        (if shift? (clojure.string/upper-case val) (clojure.string/lower-case val))
        :default val))

(defn osk-button
  ([osk-atm val]
   (osk-button osk-atm val nil nil))
  ([osk-atm val display-name]
   [osk-button osk-atm val display-name nil])
  ([osk-atm val display-name special-click-handler]
   (let [input-atm (:input-atm @osk-atm)
         el-atm (:el-atm @osk-atm)
         shift? (:shift? @osk-atm)]
     ^{:key val}
     [:button {:on-click (fn [e]
                           (when (and input-atm el-atm)
                             (let [cursor-pos (.-selectionStart @el-atm)]
                               (if special-click-handler ; if we have a special handler (for example, Backspace)...
                                 (special-click-handler osk-atm e input-atm el-atm cursor-pos) ; ... then run that ...
                                 (swap! input-atm (fn [v] ; ... otherwise, insert val at the cursor position
                                                    (str (subs v 0 cursor-pos)
                                                         (shift val shift?)
                                                         (subs v cursor-pos (count v))))))
                               (when (:args @osk-atm)
                                 (when-let [change-fn (get-in @osk-atm [:args :on-change])] 
                                   (change-fn @input-atm))))))
               :on-mouse-down (fn [e] ; try to avoid the event from bubbling so far as to take focus away from the input field
                                (.preventDefault e))}
      (shift (if display-name display-name val) shift?)])))

(defn done-button [osk-atm]
  [osk-button osk-atm nil ["Done" "Done"]
   (fn [osk-atm e input-atm el-atm cursor-pos]
     (.blur @el-atm)
     (swap! osk-atm (fn [osk-map]
                      (-> osk-map
                          (assoc :input-atm nil)
                          (assoc :el-atm nil)
                          (assoc :open? false)))))])

(defn backspace-button [osk-atm]
  [osk-button osk-atm nil ["Backspace" "Backspace"]
   (fn [osk-atm e input-atm el-atm cursor-pos]
     (swap! input-atm (fn [v]
                        (str (subs v 0 (dec cursor-pos))
                             (subs v cursor-pos (count v))))))])

(defn shift-button [osk-atm]
  [osk-button osk-atm nil ["Shift" "Shift"]
   (fn [osk-atm e input-atm el-atm cursor-pos]
     (swap! osk-atm (fn [osk-map] (assoc osk-map :shift? (not (:shift? osk-map))))))])

(defn onscreen-keyboard [osk-atm]
  (let [button-fn (partial osk-button osk-atm)]
    (fn [osk-atm]
      [:div
       [:div {:class (str "onscreen-keyboard-placeholder" (when (not (:open? @osk-atm)) " onscreen-keyboard-placedholder-hidden"))}]
       [:div {:class (str "onscreen-keyboard" " " (if (:open? @osk-atm) "onscreen-keyboard-open" "onscreen-keyboard-closed"))}
        [:div
         (button-fn ["1" "!"])
         (button-fn ["2" "@"])
         (button-fn ["3" "#"])
         (button-fn ["4" "$"])
         (button-fn ["5" "%"])
         (button-fn ["6" "^"])
         (button-fn ["7" "&"])
         (button-fn ["8" "*"])
         (button-fn ["9" "("])
         (button-fn ["0" ")"])
         [backspace-button osk-atm]]
        [:div
         (doall (map button-fn "qwertyuiop"))]
        [:div
         (doall (map button-fn "asdfghjkl"))
         (button-fn [";" ":"])
         (button-fn ["'" "\""])]
        [:div
         (doall (map button-fn "zxcvbnm"))
         (button-fn ["," "<"])
         (button-fn ["." ">"])]
        [:div
         [shift-button osk-atm]
         (button-fn " " ["Space" "Space"])

         [shift-button osk-atm]
         [done-button osk-atm]
         ]]])))

(defcard-rg onscreen-keyboard-card
  [onscreen-keyboard osk-atm])

(defcard-rg osk-atm-card
  (fn []
    [:div 
     (str @osk-atm)]))


