(ns slide-stainer.defs
  (:require [dvlopt.linux.gpio :as gpio])
  (:use clojure.test))

(defn swap-in! [atom ks v]
  (swap-vals! atom #(assoc-in % ks v)))

(defonce state-atom (atom {}))
(defonce pulse-lock
  (atom false))

(def up-pos 4)
(def down-pos 0)
(def jar-positions
  {:jar-one 0
   :jar-two 2
   :jar-three 4})

(def pin-defs
  {:stepperZ {:output-pins
              {17 {::gpio/tag :stepperZ-ena
                   :inverted? false}
               18 {::gpio/tag :stepperZ-dir
                   :inverted? false}
               19 {::gpio/tag :stepperZ-pul
                   :inverted? false}}
              :limit-switch-low  {:pin 4 :invert? false}
              :travel_distance_per_turn 0.063
              :position-in-pulses 0
              :position_limit 9
              :pulses_per_revolution 800}
   :stepperX {:output-pins
              {26 {::gpio/tag :stepperX-ena
                   :inverted? false}
               27  {::gpio/tag :stepperX-dir
                   :inverted? false}
               21  {::gpio/tag :stepperX-pul
                   :inverted? false}}
              :travel_distance_per_turn 0.063
              :position-in-pulses 0
              :position_limit 12
              :pulses_per_revolution 800
              }
   :led13 {:pins
           {13 {::gpio/tag :led13-led}}}
   ;; :switch {:pins
   ;;          {4 {::gpio/tag :switch
   ;;              ::gpio/direction :input
   ;;              ::gpio/edge-detection :rising}}}
   })
