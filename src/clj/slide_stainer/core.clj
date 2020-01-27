(ns slide-stainer.core
  (:require [dvlopt.linux.gpio :as gpio]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response :refer [file-response content-type]]
            [ring.util.request :as request]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [go go-loop <! timeout thread chan mult tap put!]]
            [incanter.core :refer [pow]]
            [clojure.walk :as walk]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all]
            [slide-stainer.graphql :refer :all])
  (:use clojure.test)
  (:import (clojure.lang IPersistentMap))
  (:gen-class))

(defonce pulse-lock
  (atom false))

(defn clean-up-pins-graphql-handler [context args value]
  (clean-up-pins)
  (resolve-state context args value))

(defonce buid-pin-defs-placeholder
  (do (swap! state-atom assoc :setup pin-defs)
      (swap! state-atom assoc :setup-index (index-pin-defs pin-defs))))

(defn inches-to-pulses [id inches]
  (println "foo " @state-atom)
  (let [axis-config (get-in @state-atom [:setup id])
        pulses-per-revolution (:pulses_per_revolution axis-config)
        travel-distance-per-turn (:travel_distance_per_turn axis-config)
        ]
    (int (/ (* inches pulses-per-revolution)
            travel-distance-per-turn))))

(defn pulses-to-inches [id pulses]
  (let [axis-config (get-in @state-atom [:setup id])
        pulses-per-revolution (:pulses_per_revolution axis-config)
        travel-distance-per-turn (:travel_distance_per_turn axis-config)
        ]
    (/ (* pulses travel-distance-per-turn)
       pulses-per-revolution)))

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

(defn get-ip-address []
  (as-> (sh "ifconfig" "wlan0") $ ; why not use hostname -I instead? Less parsing would be needed.
      (:out $)
      (clojure.string/split-lines $) ; split up the various lines of ifconfig output
      (map #(re-find #"inet\s+\d+\.\d+\.\d+\.\d+" %) $) ; find things matching the inet ip
      (filter (complement nil?) $) ; filter out the non-matching lines
      (first $) ; grab the first one (there should only be one
      (clojure.string/replace $ #"inet\s+" "") ; take out the inet portion
      ))

(defn set-pin [pin-tag state]
;  (when (not (:device @state-atom)) (init-pins))
  (gpio/write (:handle @state-atom) (-> (:buffer @state-atom) (gpio/set-line pin-tag state))))

(defn pin-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (set-pin (keyword (:pin-tag body)) (:state body))
    (content-type (response/response "<h1>Success.</h1>") "text/html"))
  )

;; (defn pulse [pin-tag wait-ms num-pulses]
;;   (println "Starting pulse" wait-ms num-pulses)
;;   (if (compare-and-set! pulse-lock false true)
;;     (do
;;       (println "Grabbed the lock" wait-ms num-pulses)
;;       (loop [i num-pulses]
;;         (when (> i 0)
;;           (set-pin pin-tag true)
;;           (java.util.concurrent.locks.LockSupport/parkNanos wait-ms)
;;           (set-pin pin-tag false)
;;           (java.util.concurrent.locks.LockSupport/parkNanos wait-ms)
;;           (recur (- i 1))))
;;       (when (not (compare-and-set! pulse-lock true false)) (println "Someone mess with the lock"))
;;       (println "Finishing pulsing" wait-ms num-pulses))
;;     (println "Couldn't get the lock on pulse")))

(defn resolve-ip [context args value]
;  (println (sh "ifconfig" "wlan0"))
                                        ;  (println (str "resolve-ip: " (get-ip-address)))
  (println "get-ip-address" args value)
  {:inet4 (get-ip-address)
   })

(defn resolve-pins [context args value]
  (println "resolve-pins" args value)
  (map #(resolve-pin-by-id context {:id %} value)
       (vec (keys (:setup-index @state-atom)))))

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

(defn resolve-axis [context args value]
  (let [id (normalize-pin-tag (:id args))
        pos (get-in @state-atom [:setup id :position-in-pulses])]
    {:id (str id)
     :position        pos
     :position_inches (pulses-to-inches id pos) ;(get-in @state-atom [:setup id :position_inches])
     }))

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

(defn pulse-step-fn
  "Stepper pulse generation function that always outputs a 40kHz signal"
  [step]
  400
;  40000
  )

(defn pulse-linear-fn
  "Stepper pulse generation function that uses a linear ramp-up"
  [step]
  (let [slope 15
        initial_offset 80]
    (min
     (+ (* step slope) initial_offset) ; y = mx+b
                                        ;     (* 18 1000)
     (* 15 1000)
     )))

(defn pulse-logistic-fn
  "Stepper pulse generaiton function that uses a logistic-function shape for ramp-up"
  [step]
  (let [e 2.71828
        L 120000 ; maximum value for the curve
        x_0 (/ 800 2)                  ; x-value of the sigmoid's midpoint
        k 0.01 ; logistic growth rate / steepness of curve
        ]
    ;; f(x) = L / (1 + e^(-k(x-x_0
    ;; https://en.wikipedia.org/wiki/Logistic_function
    (- (/ L
          (+ 1 (pow e (* (- k) (- step x_0)))))
       80)))

(with-test
  (defn hz-to-ns
    "Converts from hertz to nanoseconds. For usage with stepper signal generation."
    [hertz]
    (* 1000000000 (/ 1 hertz)))
  (is (= (double (hz-to-ns 1)) (pow 10 9)))
  (is (< (- (hz-to-ns 60000) 16667) ; technically 50000/3, so check that it's close enough to 16667 ns
         1)))

(with-test
  (defn precompute-pulse
    "Takes a pulse-generation function and turns it into a vector with the given number of steps"
    [pulse-fn steps]
    (let [halfway (vec (map pulse-fn (range 1 (inc (/ steps 2))))) ; split up the steps in half so that we can have acceleration and then deceleration. The purpose of this is to provide smooth motion and avoid missed steps.
          second-half (-> (if (odd? steps)
                            (pop halfway) ; if it's an odd number drop off the first step (the one that will end up in the middle) before reversing. This is so that we end up with the correct number of steps.
                            halfway)
                          (reverse))
          ]
      (vec (concat halfway second-half))
      ))
  (let [testing-fn (fn [step] (- step)) ; simple testing function the negative is so that we can easily observer that the testing-fn actually got called.
        ]
    (is (= (precompute-pulse testing-fn 4) [-1 -2 -2 -1]))
    (is (= (precompute-pulse testing-fn 5) [-1 -2 -3 -2 -1]))))

(defn move-by-pulses [id pulses]
  (when (not (:device @state-atom)) (init-pins))
  (let [ena (normalize-pin-tag (str id "-ena"))
        pul (normalize-pin-tag (str id "-pul"))
        dir (normalize-pin-tag (str id "-dir"))
        limit-switch-low (normalize-pin-tag (str id "-limit-switch-low"))
        axis-config (get-in @state-atom [:setup id])
        dir-val (pos? pulses)
        abs-pulses (if dir-val
                     pulses
                     (* -1 pulses))
        nanosecond-wait 1000000 ;(max 1000000 7500)
        precomputed-pulses (precompute-pulse pulse-linear-fn abs-pulses)
        hit-limit-switch? (atom false)
        ] ; friendly reminder not to take it lower than 7.5us
    (println "move-by-pulses max calculated frequency (Hz): " (when (not (empty? precomputed-pulses)) (apply max precomputed-pulses)))
    (if (compare-and-set! pulse-lock false true)
      (do
        (println "Got the lock")
        (println "Current limit switches: " limit-switch-low)
        (println "Status atom" (get-in @state-atom [:status-atm]))
        (set-pin ena true)
        (java.util.concurrent.locks.LockSupport/parkNanos 5000) ; 5us wait required by driver
        (set-pin dir dir-val)
        (java.util.concurrent.locks.LockSupport/parkNanos (max 300000000 5000)) ; 5us wait required by driver
        (try
          (doseq [pulse-val precomputed-pulses]
            (do
              (let [start-time (java.lang.System/nanoTime)
                    wait-time (hz-to-ns pulse-val)
                    tgt-one (+ start-time wait-time)
                    tgt-two (+ tgt-one wait-time)]
                (when (limit-switch-low (deref (:status-atm @state-atom)))
                  (throw (Exception. "Limit switch hit")))
                (set-pin pul true)
                (while (< (java.lang.System/nanoTime) tgt-one) nil) ; busy-wait
                                        ;              (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))
                (set-pin pul false)
                (while (< (java.lang.System/nanoTime) tgt-two) nil)
                )
;              (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))
              ))
          (catch Exception e (do
                               (println (.getMessage e))
                               (reset! hit-limit-switch? true)
                               )))
        (java.util.concurrent.locks.LockSupport/parkNanos nanosecond-wait)
        (set-pin ena false)
        (swap-in! state-atom [:setup id :position-in-pulses]
                  (if @hit-limit-switch?
                    (if dir-val
                      (inches-to-pulses id (:position_limit axis-config))
                      0)
                    (+ (get-in @state-atom [:setup id :position-in-pulses])
                                                        pulses)))
        (when (not (compare-and-set! pulse-lock true false)) (println "Someone messed with the lock"))
        (println "Dropped the lock"))
      (println "Couldn't get the lock on pulse"))
    (not @hit-limit-switch?) ; returns true if all steps were taken, false if the move was interrupted by hitting a limit switch
    ))

(defn move-by-pulses-graphql-handler
  "Example query: mutation {move_by_pulses(id:\":stepperZ\",pulses:-3200){id}}"
  [context args value]
  (move-by-pulses
   (normalize-pin-tag (:id args))
   (:pulses args))
  (resolve-axis context args value))

(defn move-relative [id increment]
  (move-by-pulses id (inches-to-pulses id increment)))

(defn move-relative-graphql-handler
  "Example query: mutation {move_relative(id:\":stepperZ\",increment:-1){id}}"
  [context args value]
  (move-relative
   (normalize-pin-tag (:id args))
   (:increment args))
  (resolve-axis context args value))

(defn move-to-position [id position-in-inches]
  (let [axis-config (get-in @state-atom [:setup id])
        desired-pos-in-steps (inches-to-pulses id position-in-inches)
        max-steps-in-bounds (inches-to-pulses id (:position_limit axis-config))
        bounds-checked-desired-pos-in-steps (cond (< desired-pos-in-steps 0) 0
                                                  (> desired-pos-in-steps max-steps-in-bounds) max-steps-in-bounds
                                                  :else desired-pos-in-steps)
        current-pos-in-steps (:position-in-pulses axis-config)
        steps-to-move (- desired-pos-in-steps current-pos-in-steps)
        ]
    (println "move-to-position: " id steps-to-move)
    (move-by-pulses id steps-to-move)
    ))

(defn move-to-position-graphql-handler [context args value]
  (println "move-to-position-graphql-handler")
  (move-to-position
   (normalize-pin-tag (:id args))
   (:position args))
  (resolve-axis context args value))

(defn move-to-up-position []
  (move-to-position :stepperZ up-pos))

(defn move-to-down-position []
  (move-to-position :stepperZ down-pos))

(defn move-to-jar [jar]
  (move-to-up-position)
  (move-to-position :stepperX (get jar-positions jar))
  (move-to-down-position)
  )

(defn move-to-jar-graphql-handler [context args value]
  (println "move-to-jar-graphql-handler")
  (move-to-jar (normalize-pin-tag (:jar args)))
  (resolve-axis context (assoc args :id :stepperX) value))

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems."
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


(defn resolver-map []
  {:query/pin_by_id resolve-pin-by-id
   :query/ip resolve-ip
   :query/pins resolve-pins
   :mutation/set_pin set_pin
   :query/state resolve-state
   :query/axis resolve-axis
   :mutation/set_axis set-axis
   :mutation/move_by_pulses move-by-pulses-graphql-handler
   :mutation/move_relative move-relative-graphql-handler
   :mutation/move_to_position move-to-position-graphql-handler
   :mutation/move_to_jar move-to-jar-graphql-handler
   :mutation/clean_up_pins clean-up-pins-graphql-handler
})

(defn load-schema
  []
  (-> "schema.edn"
      slurp
      edn/read-string
      (util/attach-resolvers (resolver-map))
      schema/compile))

(def schema (load-schema))

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

(defn q
  [query-string variables]
  (-> (lacinia/execute schema query-string variables nil)
      (simplify)))

(defn get-ip-address-handler [req]
  (content-type (response/response (get-ip-address)) "text/html"))

(defn graphql-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println "graphql query: " body)
    (content-type (response/response (str (q (:query body) (:variables body)))) "text/html")))

(defn led-handler [req]
  (println req))

(defn pulse-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    ;; (pulse (keyword (:pin-tag body))
    ;;        (Integer/parseInt (:wait-ms body))
    ;;        (Integer/parseInt (:num-pulses body)))
    (content-type (response/response "<h1>Success.</h1>") "text/html")))

(defn blink [pin_num]
  (try
    (let [device (gpio/device 0)
          handle (gpio/handle device
                              {pin_num {::gpio/tag :red}}
                              {::gpio/direction :output})
          buff (gpio/buffer handle)]
      (println "Setting " pin_num " high")
      (gpio/write handle (-> buff (gpio/set-line :red true)))
                                        ;   (println (gpio/describe-line device 17))
      (Thread/sleep 2000)
      (println "Setting " pin_num " low")
      (gpio/write handle (-> buff (gpio/set-line :red false)))
                                        ;    (println (gpio/describe-line device 17))
      (gpio/close handle)
      (gpio/close device))
    (catch Exception e
      (println e))))

(defn blink-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (blink (:port body))
    (content-type (response/response "<h1>Success.</h1>") "text/html"))
  )

(defn alternating-leds
  "Alternates leds.
   In the REPL, press CTRL-C in order to stop.
   Ex. (alternate {:device       0
                   :interval-ms  250
                   :line-numbers [17 27 22]})"
  ([line-numbers]
   (alternating-leds line-numbers
                     nil))
  ([line-numbers {:as   options
                  :keys [device
                         interval-ms]
                  :or   {device      0
                         interval-ms 500}}]
   (with-open [device' (gpio/device device)
               handle  (gpio/handle device'
                                    (reduce (fn add-led [line-number->line-options line-number]
                                              (assoc line-number->line-options
                                                     line-number
                                                     {::gpio/state false}))
                                            {}
                                            line-numbers)
                                    {::gpio/direction :output})]
     (let [buffer (gpio/buffer handle)]
       (loop [line-numbers' (cycle line-numbers)]
         (gpio/write handle
                     (-> buffer
                         gpio/clear-lines
                         (gpio/set-line (first line-numbers')
                                        true)))
         (Thread/sleep interval-ms)
         (recur (rest line-numbers')))))))

(def api-routes
  ["/" [["led" led-handler]
        ["blink" blink-handler]
        ["pin" pin-handler]
        ["pulse" pulse-handler]
        ["ip" get-ip-address-handler]
        ["graphql" graphql-handler]
        ["cleanup" (fn [req]
                     (clean-up-pins)
                     (content-type (response/response "Pins cleaned up") "text/html"))]
        [true (fn [req] (content-type (response/response "<h1>Hi from Pi.</h1>") "text/html"))]]])

(def app
  (-> (make-handler api-routes)
      (wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete]
       :access-control-allow-credentials ["true"]
       :access-control-allow-headers ["X-Requested-With","Content-Type","Cache-Control"])))
