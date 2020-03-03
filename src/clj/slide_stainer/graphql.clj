(ns slide-stainer.graphql
  (:require [dvlopt.linux.gpio :as gpio]
            [clojure.java.shell :refer [sh]]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.executor :as executor]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all]
            [slide-stainer.motion :refer :all]
            [slide-stainer.db :as db]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [java-time]
            [clj-http.client :as client])
  (:import (clojure.lang IPersistentMap)))

(defn resolve-alarms [context args value]
  (:alarms @state-atom))

(defn resolve-procedure-run-status [context args value]
  (let [proc_run_status (:procedure_run_status @state-atom)]
    {:current_procedure_id (:current_procedure_id proc_run_status)
     :current_procedure_name (:current_procedure_name proc_run_status)
     :current_procedure_step_number (:current_procedure_step_number proc_run_status)
     :current_procedure_step_start_time (format/unparse (format/formatters :date-hour-minute-second-ms)
                                                        (:current_procedure_step_start_time proc_run_status))
                                        ;(java-time/format (:current_procedure_step_start_time proc_run_status))
     :current_cycle_number (:current_cycle_number proc_run_status)})
  )

(defn resolve-state [context args value]
  {:contents (str @state-atom)
   :alarms (resolve-alarms context args value)
   :procedure_run_status (resolve-procedure-run-status context args value)
   :stopped (is-stopped?)
   :motor_lock @slide-stainer.defs/motor-lock})

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

(defn resolve-procedure-by-id [context args value]
  (db/get-doc (:_id args)))

(defn resolve-procedures [context args value]
  (let [indexed-fields #{:procedure/name :procedure/_id}
        queried-fields (set (executor/selections-seq context))
        has-non-indexed-field? (not (empty? (clojure.set/difference queried-fields indexed-fields)))]
    (if has-non-indexed-field?
      (mapv :doc (db/get-procedures true))
      (mapv #(clojure.set/rename-keys % {:key :_id :value :name}) (db/get-procedures false)))))

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
     :position_inches (if (= id :stepper-x)
                        (:body (client/get "http://localhost:8000/pos/x"))
                        (:body (client/get "http://localhost:8000/pos/z")))
                                        ;(pulses-to-inches id pos) ;(get-in @state-atom [:setup id :position_inches])
     :alarms (resolve-alarms context args value)
     }))

(defn resolve-settings [context args value]
  (let [settings-doc (db/get-doc "settings")]
    settings-doc))

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
  (set-stopped! false)
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
  ;; Re-homing clears estop status
  (set-stopped! false)
  ;; these alarms are resolved by re-homing, so have them clear the alarm status
  (set-limit-switch-hit-unexpectedly-alarm false)
  (set-homing-failed-alarm false)
  ;; actually do the re-homing
  (home)
  (resolve-state context args value))

(defn clear-alarms-graphql-handler [context args value]
  (println "clear-alarms-graphql-handler")
  (set-limit-switch-hit-unexpectedly-alarm false)
  (set-homing-failed-alarm false)
  (resolve-state context args value))

(defn run-procedure-graphql-handler [context args value]
  (let [id (:_id args)]
    (println "run-procedure-graphql-handler: " id)
    (let [thr (Thread. (fn [] (run-program-by-id id)))
          max-pri (.getMaxPriority (.getThreadGroup thr))]
      (.setPriority thr max-pri)
      (.start thr))
    (resolve-state context args value)))

(defn save-procedure-graphql-handler [context args value]
  (let [procedure (:procedure args)]
    (println "save-procedure-graphql-handler " procedure (type procedure))
    (if (= (:type procedure) "procedure")
      (db/put-doc procedure)
      (do
        (let [error-msg "procedure passed into save-procedure-graphql-handler is not of the proper type."]
          (println error-msg)
          {:error error-msg})))))

(defn configure-database-graphql-handler [context args value]
  (db/install-views! @db/db)
  (resolve-state context args value))

(defn drop-motor-lock-graphql-handler [context args value]
  (reset! motor-lock false)
  (resolve-state context args value))

(defn move-to-up-position-graphql-handler [context args value]
  (move-to-up-position)
  (resolve-axis context (assoc args :id :stepperZ) value))

(defn move-to-down-position-graphql-handler [context args value]
  (move-to-down-position)
  (resolve-axis context (assoc args :id :stepperZ) value))

(defn resolver-map []
  {:query/pin_by_id resolve-pin-by-id
   :query/ip resolve-ip
   :query/pins resolve-pins
   :query/procedure_by_id resolve-procedure-by-id
   :query/procedures resolve-procedures
   :mutation/set_pin set_pin
   :query/alarms resolve-alarms
   :query/state resolve-state
   :query/axis resolve-axis
   :query/settings resolve-settings
   :mutation/set_axis set-axis
   :mutation/move_by_pulses move-by-pulses-graphql-handler
   :mutation/move_relative move-relative-graphql-handler
   :mutation/move_to_position move-to-position-graphql-handler
   :mutation/move_to_jar move-to-jar-graphql-handler
   :mutation/clean_up_pins clean-up-pins-graphql-handler
   :mutation/home home-graphql-handler
   :mutation/clear_alarms clear-alarms-graphql-handler
   :mutation/run_procedure run-procedure-graphql-handler
   :mutation/save_procedure save-procedure-graphql-handler
   :mutation/drop_motor_lock drop-motor-lock-graphql-handler
   :mutation/configure_database configure-database-graphql-handler
   :mutation/move_to_up_position move-to-up-position-graphql-handler
   :mutation/move_to_down_position move-to-down-position-graphql-handler
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
