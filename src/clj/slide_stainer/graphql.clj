(ns slide-stainer.graphql
  (:require [dvlopt.linux.gpio :as gpio]
            [clojure.java.shell :refer [sh]]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all]
            [slide-stainer.motion :refer :all])
  (:import (clojure.lang IPersistentMap)))

(defn resolve-alarms [context args value]
  (:alarms @state-atom))

(defn resolve-state [context args value]
  {:contents (str @state-atom)
   :alarms (resolve-alarms context args value)})

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
     :alarms (resolve-alarms context args value)
     }))

(defn move-by-pulses-graphql-handler
  "Example query: mutation {move_by_pulses(id:\":stepperZ\",pulses:-3200){id}}"
  [context args value]
  (move-by-pulses
   (normalize-pin-tag (:id args))
   (:pulses args))
  (resolve-axis context args value))

(defn move-relative-graphql-handler
  "Example query: mutation {move_relative(id:\":stepperZ\",increment:-1){id}}"
  [context args value]
  (move-relative
   (normalize-pin-tag (:id args))
   (:increment args))
  (resolve-axis context args value))

(defn move-to-position-graphql-handler [context args value]
  (println "move-to-position-graphql-handler")
  (move-to-position
   (normalize-pin-tag (:id args))
   (:position args))
  (resolve-axis context args value))



(defn move-to-jar-graphql-handler [context args value]
  (println "move-to-jar-graphql-handler")
  (move-to-jar (:jar args))
  (resolve-axis context (assoc args :id :stepperX) value))

(defn clean-up-pins-graphql-handler [context args value]
  (clean-up-pins)
  (resolve-state context args value))

(defn set_pin [context args value]
  (println "setting " (:id args) "to " (:logical_value args))
  (when (not (:device @state-atom)) (init-pins))
  (let [id (normalize-pin-tag (:id args))
        pin-info (id (:setup-index @state-atom))
        requested-val (if (:inverted? pin-info)
                        (not (:logical_value args))
                        (:logical_value args))]
    (gpio/write (:handle @state-atom) (-> (:buffer @state-atom) (gpio/set-line id requested-val))))
  (resolve-pin-by-id context args value))

(defn set-axis [context args value]
  (let [id (normalize-pin-tag (:id args))
        position-in-pulses (:position args)
        position_inches (:position_inches args)
        travel-distance-per-turn (:travel_distance_per_turn args)
        position-limit (:position_limit args)
        pulses-per-revolution (:pulses_per_revolution args)
        ]

    (when travel-distance-per-turn (swap-in! state-atom [:setup id :travel_distance_per_turn] travel-distance-per-turn))
    (when position-limit (swap-in! state-atom [:setup id :position_limit] position-limit))
    (when pulses-per-revolution (swap-in! state-atom [:setup id :pulses_per_revolution] pulses-per-revolution))
    (when position-in-pulses (swap-in! state-atom [:setup id :position-in-pulses] position-in-pulses))
    (when (and position_inches (not position-in-pulses))
      (swap-in! state-atom [:setup id :position-in-pulses] (inches-to-pulses id position_inches)))
    (resolve-axis context args value)))

(defn home-graphql-handler [context args value]
  (println "Homing")
  (home)
  (resolve-state context args value))

(defn clear-alarms-graphql-handler [context args value]
  (println "clear-alarms-graphql-handler")
  (set-limit-switch-hit-unexpectedly-alarm false)
  (resolve-state context args value))

(defn run-procedure-graphql-handler [context args value]
  (let [name (:name args)]
    (println "run-procedure-graphql-handler: " name)
    (run-program-by-name name)
    (resolve-state context args value)))

(defn resolver-map []
  {:query/pin_by_id resolve-pin-by-id
   :query/ip resolve-ip
   :query/pins resolve-pins
   :mutation/set_pin set_pin
   :query/alarms resolve-alarms
   :query/state resolve-state
   :query/axis resolve-axis
   :mutation/set_axis set-axis
   :mutation/move_by_pulses move-by-pulses-graphql-handler
   :mutation/move_relative move-relative-graphql-handler
   :mutation/move_to_position move-to-position-graphql-handler
   :mutation/move_to_jar move-to-jar-graphql-handler
   :mutation/clean_up_pins clean-up-pins-graphql-handler
   :mutation/home home-graphql-handler
   :mutation/clear_alarms clear-alarms-graphql-handler
   :mutation/run_procedure run-procedure-graphql-handler
})

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems. https://lacinia.readthedocs.io/en/latest/tutorial/game-data.html"
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

(defn load-schema
  []
  (-> "schema.edn"
      slurp
      edn/read-string
      (util/attach-resolvers (resolver-map))
      schema/compile))

(def schema (load-schema))

(defn q
  [query-string variables]
  (-> (lacinia/execute schema query-string variables nil)
      (simplify)))
