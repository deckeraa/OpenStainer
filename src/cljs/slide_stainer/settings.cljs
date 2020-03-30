(ns slide-stainer.settings
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.atoms :as atoms]
   [slide-stainer.svg :as svg]
   [slide-stainer.graphql :as graphql])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

;; (defn refresh-fn []
;;   (graphql/graphql-fn {:query (str "{stepperX: axis(id:\"stepperX\"){position_inches}, stepperZ: axis(id:\"stepperZ\"){position_inches}}")
;;                        :handler-fn (fn [resp]
;;                                      (swap! atoms/stepperX-cursor (fn [v] (merge v (:stepperX resp))))
;;                                      (swap! atoms/stepperZ-cursor (fn [v] (merge v (:stepperZ resp)))))}))

(defn refresh-query-fn []
  (str "{stepperX: axis(id:X){positionInches}, stepperZ: axis(id:Z){positionInches}}")
  )

(defn refresh-handler-fn [resp]
  (swap! atoms/stepperX-cursor (fn [v] (merge v (:stepperX resp))))
  (swap! atoms/stepperZ-cursor (fn [v] (merge v (:stepperZ resp))))
  )

(defn positions-and-home [stepperX-cursor stepperZ-cursor]
  (fn []
    (let [position-x (:positionInches @stepperX-cursor)
          position-z (:positionInches @stepperZ-cursor)]
      [:h2 "Current Position"]
      [:div {:class "positions-and-home" :style {:display :flex :align-items :center}}
       [:div {:style {:font-size "24px" :margin "16px"}}
        [:div {} (if (and position-x (not (= "Not homed" position-x))) 
                   (str "X: " (goog.string/format "%.3f" position-x))
                   "Not homed")]
        [:div {} (if (and position-z (not (= "Not homed" position-z))) 
                   (str "Z: " (goog.string/format "%.3f" position-z))
                   "Not homed")]]
       [:button {:on-click #(http/post "http://localhost:8000/home")}
        [svg/home {} "white" 32]
        "Home"]
       ])))

(defn jar-jog-control []
  (fn []
    [:div
     [:h2 "Move to jar"]
     (map (fn [jar]
            ^{:key jar} [:button {:on-click #(http/post (str "http://localhost:8000/move_to_jar/" jar))}
                         (str "Jar #" jar)])
          (range 1 7))
     ]))

(defn up-down-control []
  (fn []
    [:div
     [:h2 "Move slide holder"]
     [:button {:on-click #(http/post "http://localhost:8000/move_to_up_position")} "Up"]
     [:button {:on-click #(http/post "http://localhost:8000/move_to_down_position")} "Down"]]))

(defn kiosk-control []
  (fn []
    [:div
     [:h2 "Kiosk mode"]
     [:button {:on-click #(http/post "http://localhost:8000/exit_kiosk_mode")} "Restart in non-kiosk mode"]]))

(defn developer-mode-control []
  (fn []
    [:div
     [:h2 "Developer mode"]
     [:input {:type "checkbox"
              :on-click (fn [e]
                          (swap! atoms/settings-cursor (fn [atm] (assoc atm :developer (not (:developer atm))))))
              :checked (get-in @atoms/settings-cursor [:developer])}]
     [:label "Display program state information"]]))

(defn notes-control []
  (fn []
    [:div
     (let [scale
           [
            ["C0"	16.35]
            ["D0"	18.35]
            ["E0"	20.60]
            ["F0"	21.83]
            ["G0"	24.50]
            ["A0"	27.50]
            ["B0"	30.87]
            ["C1"	32.70]
            ["D1"	36.71]
            ["E1"	41.20]
            ["F1"	43.65]
            ["G1"	49.00]
            ["A1"	55.00]
            ["B1"	61.74]
            ["C2"	65.41]
            ["D2"	73.42]
            ["E2"	82.41]
            ["F2"	87.31]
            ["G2"	98.00]
            ["A2"	110.00]
            ["B2"	123.47]
            ["C3"	130.81]
            ["D3"	146.83]
            ["E3"	164.81]
            ["F3"	174.61]
            ["G3"	196.00]
            ["A3"	220.00]
            ["B3"	246.94]
            ["C4"	261.63]
            ["D4"	293.66]
            ["E4"	329.63]
            ["F4"	349.23]
            ["G4"	392.00]
            ["A4"	440.00]
            ["B4"	493.88]
            ["C5"	523.25]
            ["D5"	587.33]
            ["E5"	659.25]
            ["F5"	698.46]
            ["G5"	783.99]
            ["A5"	880.00]
            ["B5"	987.77]
            ["C6"	1046.50]
            ["D6"	1174.66]
            ["E6"	1318.51]
            ["F6"	1396.91]
            ["G6"	1567.98]
            ["A6"	1760.00]
            ["B6"	1975.53]
            ["C7"	2093.00]
            ["D7"	2349.32]
            ["E7"	2637.02]
            ["F7"	2793.83]
            ["G7"	3135.96]
            ["A7"	3520.00]
            ["B7"	3951.07]
            ["C8"	4186.01]
            ["D8"	4698.63]
            ["E8"	5274.04]
            ["F8"	5587.65]
            ["G8"	6271.93]
            ["A8"	7040.00]
            ["B8"	7902.13]
            ["C9"       8372.02]
            ]]
       (map (fn [[note hz]]
              [:button {:on-click (fn [e]
                                    (http/get
                                        ;(str "http://localhost:8000/play_note?note_hz=" hz "&duration_ms=" 1000 "&forward=true")
                                     (str "http://localhost:8000/move_one_turn_at_freq?hz=" hz "&forward=true&turns=4")
                                     ))}
               (str note)])
            scale))]))

(defn motor-test-control []
  (let [accel_in_hz_per_sec_atm (reagent/atom "20000")
        max_hz_atm   (reagent/atom "17000")
        number_of_turns_atm (reagent/atom "4")]
    (fn []
      [:div
       [:div
        [:label "max_hz_atm"]
        [:input {:value @max_hz_atm :on-change (fn [e] (reset! max_hz_atm (-> e .-target .-value)))}]]
       [:div
        [:label "accel_in_hz_per_sec"]
        [:input {:value @accel_in_hz_per_sec_atm :on-change (fn [e] (reset! accel_in_hz_per_sec_atm (-> e .-target .-value)))}]]
       [:div
        [:label "number_of_turns"]
        [:input {:value @number_of_turns_atm :on-change (fn [e] (reset! number_of_turns_atm (-> e .-target .-value)))}]]
       [:button {:on-click (fn [e]
                             (http/get (str "http://localhost:8000/run_motor_test?forward=true&accel_in_hz_per_sec=" @accel_in_hz_per_sec_atm
                                            "&max_hz=" @max_hz_atm
                                            "&number_of_turns=" @number_of_turns_atm)))}
        "Run motor test"]])))

(defn settings-control [ratom back-fn]
  (fn []
    [:div
     [:div {:class "nav-header"}
      [:button {:class "round-button" :on-click back-fn}
        [svg/chevron-left {:class "chevron-left" } "white" 36]]
      [:h1 "Settings"]]
     [motor-test-control]
     [notes-control]
     [positions-and-home atoms/stepperX-cursor atoms/stepperZ-cursor]
     [jar-jog-control]
     [up-down-control]
     [kiosk-control]
     [developer-mode-control]
     ]))
