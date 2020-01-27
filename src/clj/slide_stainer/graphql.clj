(ns slide-stainer.graphql
  (:require [dvlopt.linux.gpio :as gpio]
            [clojure.java.shell :refer [sh]]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all]))

(defn resolve-state [context args value]
  {:contents (str @state-atom)})

(defn resolve-pin-by-id [context args value]
  (println "resolve-pin-by-id" args value)
  (when (not (:device @state-atom)) (init-pins))
  (let [id (normalize-pin-tag (:id args))
        pin-info (id (:setup-index @state-atom))
        board_value (gpio/get-line (:buffer @state-atom) id)]
    {:id (str id)
     :board_value board_value
     :logical_value (if (:inverted? pin-info) (not board_value) board_value)
     :pin_number (:pin_number pin-info)
     }))

(defn resolve-pins [context args value]
  (println "resolve-pins" args value)
  (map #(resolve-pin-by-id context {:id %} value)
       (vec (keys (:setup-index @state-atom)))))

(defn get-ip-address []
  (as-> (sh "ifconfig" "wlan0") $ ; why not use hostname -I instead? Less parsing would be needed.
      (:out $)
      (clojure.string/split-lines $) ; split up the various lines of ifconfig output
      (map #(re-find #"inet\s+\d+\.\d+\.\d+\.\d+" %) $) ; find things matching the inet ip
      (filter (complement nil?) $) ; filter out the non-matching lines
      (first $) ; grab the first one (there should only be one
      (clojure.string/replace $ #"inet\s+" "") ; take out the inet portion
      ))

(defn resolve-ip [context args value]
  {:inet4 (get-ip-address)})

(defn resolve-axis [context args value]
  (let [id (normalize-pin-tag (:id args))
        pos (get-in @state-atom [:setup id :position-in-pulses])]
    {:id (str id)
     :position        pos
     :position_inches (pulses-to-inches id pos) ;(get-in @state-atom [:setup id :position_inches])
     }))
