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
            [incanter.core :refer :all]
            [incanter.io :refer :all]
            [incanter.stats :refer :all]
            [clojure.core.async :as async :refer [go go-loop <! timeout]]
            [clojure.walk :as walk])
  (:use clojure.test)
  (:import (clojure.lang IPersistentMap))
  (:gen-class))

(defn swap-in! [atom ks v]
  (swap-vals! atom #(assoc-in % ks v)))

(defonce state-atom (atom {}))
(defonce pulse-lock
  (atom false))

; z dir - true = DOWN

(def pin-defs
  {:stepperZ {:pins
              {17 {::gpio/tag :stepperZ-ena
                   :inverted? false}
               18 {::gpio/tag :stepperZ-dir
                   :inverted? false}
               19 {::gpio/tag :stepperZ-pul
                   :inverted? false}}
              :travel_distance_per_turn 0.063
              :position nil
              :position_limit 9
              :pulses_per_revolution 800}
   :stepperX {:pins
              {26 {::gpio/tag :stepperX-ena
                   :inverted? false}
               27  {::gpio/tag :stepperX-dir
                   :inverted? false}
               22  {::gpio/tag :stepperX-pul
                   :inverted? false}}
              :travel_distance_per_turn 0.063
              :position nil
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

(with-test
  (defn index-pin-defs [pin-defs]
    (apply merge
           (map (fn [[device {pins :pins}]]
                  (as-> pins $
                    (map (fn [[pin_number {tag :dvlopt.linux.gpio/tag inverted? :inverted?}]]
                           {tag {:inverted? inverted? :pin_number pin_number :device device}}) $)
                    (apply merge $))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 4}}
        sample-index {:stepperX-ena {:pin_number 17 :inverted? true :device :stepperX}
                      :stepperX-dir {:pin_number 18 :inverted? true :device :stepperX}
                      :stepperX-pul {:pin_number 19 :inverted? true :device :stepperX}
                      :stepperZ-ena {:pin_number 20 :inverted? true :device :stepperZ}
                      :stepperZ-dir {:pin_number 21 :inverted? true :device :stepperZ}
                      :stepperZ-pul {:pin_number 22 :inverted? true :device :stepperZ}}]
    (is (= sample-index (index-pin-defs sample-pin-defs)))))

(with-test
  (defn get-pin-defs-for-gpio-lib [pin-defs]
    (apply merge (map (fn [[device {pins :pins}]]
                        (apply merge (map (fn [[pin_num {tag  ::gpio/tag
                                                         dir  ::gpio/direction
                                                         edge ::gpio/edge-detection}]]
                                            (let [pin-map (as-> {} $
                                                            (if tag (assoc $ ::gpio/tag tag)  $)
                                                            (if dir (assoc $ ::gpio/direction dir) $)
                                                            (if edge (assoc $ ::gpio/edge-detection edge) $))]
                                              {pin_num pin-map}))
                                          pins))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 4}
                         :led12 {:pins
                                 {12 {::gpio/tag :led12-led
                                      :inverted? true}}}
                         :switch {:pins
                                  {4 {::gpio/tag :switch
                                      ::gpio/direction :input
                                      ::gpio/edge-detection :rising}}}}
        pin-defs-for-lib { 4 {::gpio/tag :switch ::gpio/direction :input ::gpio/edge-detection :rising}
                          12 {::gpio/tag :led12-led}
                          17 {::gpio/tag :stepperX-ena}
                          18 {::gpio/tag :stepperX-dir}
                          19 {::gpio/tag :stepperX-pul}
                          20 {::gpio/tag :stepperZ-ena}
                          21 {::gpio/tag :stepperZ-dir}
                          22 {::gpio/tag :stepperZ-pul}}]
    (is (= pin-defs-for-lib (get-pin-defs-for-gpio-lib sample-pin-defs)))))

;; (with-test
;;   (defn append-to-tag ))

(defn init-watcher []
  ;; (go-loop [seconds 1]
  ;;    (<! (timeout 1000))
  ;;    (println "waited" seconds "seconds")
  ;;    (recur (inc seconds)))
  ;; (go (while true
  ;;       (if-some [evt (gpio/event (:watcher @state-atom) -1)]
  ;;         (println "button triggerd!"))))
  (go (while true
        (if-some [evt (gpio/event (:watcher @state-atom) 1000)]
          (println "Triggered")
          (println "Reading line: " (gpio/poll (:watcher @state-atom) (:buffer @state-atom) :switch)))))
  )

(defn resolve-state [context args value]
  {:contents (str @state-atom)})

(defn init-pins
  ([] (init-pins state-atom))
  ([state-atom]
   (swap! state-atom assoc :device (gpio/device 0))
   (swap! state-atom assoc :handle (gpio/handle (:device @state-atom)
                                                (get-pin-defs-for-gpio-lib (:setup @state-atom))
                                                {::gpio/direction :output}))
   ;; (swap! state-atom assoc :watcher (gpio/watcher (:device @state-atom)
   ;;                                                {4 {::gpio/tag :switch
   ;;                                                    ::gpio/direction :input
   ;;                                                    ::gpio/edge-detection :rising}}))
;   (swap! state-atom assoc :watcher-buffer)
   (swap! state-atom assoc :buffer (gpio/buffer (:handle @state-atom)))
;   (init-watcher)
   ))

(defn clean-up-pins
  ([] (clean-up-pins state-atom))
  ([state-atom]
   (gpio/close (:handle @state-atom))
   (gpio/close (:device @state-atom))
   (swap! state-atom dissoc :handle)
   (swap! state-atom dissoc :device)
   (swap! state-atom dissoc :buffer))
  ([context args value]
   (clean-up-pins)
   (resolve-state context args value)))

(defonce buid-pin-defs-placeholder
  (do (swap! state-atom assoc :setup pin-defs)
      (swap! state-atom assoc :setup-index (index-pin-defs pin-defs))))

(defn inches-to-pulses [id inches]
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

(with-test
  (defn normalize-pin-tag [tag]
    "Take tag names and convert them to a keyword consistently."
    (keyword (clojure.string/replace tag #"^:" "")))
  (is (= (normalize-pin-tag ":foo") :foo))
  (is (= (normalize-pin-tag "foo")  :foo)))

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
  (let [id (normalize-pin-tag (:id args))]
    {:id (str id)
     :position        (get-in @state-atom [:setup id :position])
     :position_inches (get-in @state-atom [:setup id :position_inches])}))

(defn set-axis [context args value]
  (let [id (normalize-pin-tag (:id args))
        position (:position args)
        position_inches (:position_inches args)
        travel-distance-per-turn (:travel_distance_per_turn args)
        position-limit (:position_limit args)
        pulses-per-revolution (:pulses_per_revolution args)
        ]

    (when travel-distance-per-turn (swap-in! state-atom [:setup id :position] travel-distance-per-turn))
    (when position-limit (swap-in! state-atom [:setup id :position] position-limit))
    (when pulses-per-revolution (swap-in! state-atom [:setup id :position] pulses-per-revolution))
    (when position
      (do (swap-in! state-atom [:setup id :position] position)
          (swap-in! state-atom [:setup id :position_inches] (pulses-to-inches id position))))
    (when (and position_inches (not position))
      (do (swap-in! state-atom [:setup id :position_inches] position_inches)
          (swap-in! state-atom [:setup id :position] (inches-to-pulses id position_inches))))
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
  (let [slope 100
        initial_offset 80]
    (min
     (+ (* step slope) initial_offset) ; y = mx+b
     (* 18 1000)                          
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
        dir-val (pos? pulses)
        num-pulses (if dir-val
                     pulses
                     (* -1 pulses))
        nanosecond-wait 1000000 ;(max 1000000 7500)
        precomputed-pulses (precompute-pulse pulse-linear-fn num-pulses)
        ] ; friendly reminder not to take it lower than 7.5us
    (println "move-by-pulses" ena)
    (println (take 10 precomputed-pulses))
    (println (apply max precomputed-pulses))
    (if (compare-and-set! pulse-lock false true)
      (do
        (println "Got the lock")
        (set-pin ena true)
        (java.util.concurrent.locks.LockSupport/parkNanos 5000) ; 5us wait required by driver
        (set-pin dir dir-val)
        (java.util.concurrent.locks.LockSupport/parkNanos (max 300000000 5000)) ; 5us wait required by driver
        (doseq [pulse-val precomputed-pulses]
          (do
            (set-pin pul true)
            (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))
            (set-pin pul false)
            (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))))
        (java.util.concurrent.locks.LockSupport/parkNanos nanosecond-wait)
        (set-pin ena false)
        (when (not (compare-and-set! pulse-lock true false)) (println "Someone messed with the lock"))
        (println "Dropped the lock"))
      (println "Couldn't get the lock on pulse"))
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

(defn move-to-position [id position]
  (let [axis-config (get-in @state-atom [:setup id])
        desired-pos-in-steps (inches-to-pulses id position)
        max-steps-in-bounds (inches-to-pulses id (:position_limit axis-config))
        bounds-checked-desired-pos-in-steps (cond (< desired-pos-in-steps 0) 0
                                                  (> desired-pos-in-steps max-steps-in-bounds) max-steps-in-bounds
                                                  :else desired-pos-in-steps)
        current-pos-in-steps (:position axis-config)
        steps-to-move (- desired-pos-in-steps current-pos-in-steps)
        ]
    (move-by-pulses id steps-to-move)
    ;; (println axis-config)
    ;; (println (:position axis-config))
    ;; (println desired-pos-in-steps current-pos-in-steps)
;    (println "steps-to-move: " steps-to-move)
    (swap-in! state-atom [:setup id :position] bounds-checked-desired-pos-in-steps)
    ))

(defn move-to-position-graphql-handler [context args value]
  (move-to-position
   (normalize-pin-tag (:id args))
   (:position args))
  (resolve-axis context args value))

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
   :mutation/clean_up_pins clean-up-pins
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
  [query-string]
  (-> (lacinia/execute schema query-string nil nil)
      (simplify)))

(defn get-ip-address-handler [req]
  (content-type (response/response (get-ip-address)) "text/html"))

(defn graphql-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println "graphql query: " body)
    (content-type (response/response (str (q (:query body)))) "text/html")))

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
