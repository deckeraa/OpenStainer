(ns slide-stainer.motion
  (:require [dvlopt.linux.gpio :as gpio]
            [incanter.core :refer [pow]]
            [clojure.math.numeric-tower :refer [abs]]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all])
  (:use clojure.test))

(defonce pulse-lock
  (atom false))

(defn set-pin [pin-tag state]
;  (when (not (:device @state-atom)) (init-pins))
  (gpio/write (:handle @state-atom) (-> (:buffer @state-atom) (gpio/set-line pin-tag state))))

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
        initial_offset 180]
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

(with-test
  (defn limit-switch-hit-unexpected?
    ([current-position-in-pulses direction-increasing? number-of-pulses-moved upper-limit-in-pulses]
     (println "limit-switch-hit-unexpected? " current-position-in-pulses number-of-pulses-moved)
     (let [pulse-tolerance 1000]
       (if (nil? current-position-in-pulses)
         false
         (if direction-increasing?
           (> (abs (- upper-limit-in-pulses (+ current-position-in-pulses number-of-pulses-moved)))
              pulse-tolerance)
           (> (abs (- current-position-in-pulses number-of-pulses-moved))
              pulse-tolerance))))))
  (is (= (limit-switch-hit-unexpected? 4000 false 10 nil) true))
  (is (= (limit-switch-hit-unexpected? 1000 false 100 nil) false))
  (is (= (limit-switch-hit-unexpected? 100 false 300 nil) false))
  (is (= (limit-switch-hit-unexpected? 0 true 1000 1200) false))
  (is (= (limit-switch-hit-unexpected? 1000 true 2000 1100) true))
  (is (= (limit-switch-hit-unexpected? 1000 true 1 3000) true))
  (is (= (limit-switch-hit-unexpected? nil  true 1 3000) false)))

(defn move-by-pulses [id pulses]
  (when (not (:device @state-atom)) (init-pins))
  (let [ena (normalize-pin-tag (str id "-ena"))
        pul (normalize-pin-tag (str id "-pul"))
        dir (normalize-pin-tag (str id "-dir"))
        dir-val (pos? pulses)
        limit-switch (normalize-pin-tag (str id (if dir-val "-limit-switch-high" "-limit-switch-low")))
        axis-config (get-in @state-atom [:setup id])
        abs-pulses (if dir-val
                     pulses
                     (* -1 pulses))
        nanosecond-wait (max 10000 7500)
        precomputed-pulses (precompute-pulse pulse-linear-fn abs-pulses)
        hit-limit-switch? (atom false)
        current-position (get-in @state-atom [:setup id :position-in-pulses])
        axis-upper-limit-in-pulses (inches-to-pulses id (:position_limit axis-config))
        can-run  (not (limit-switch-hit-unexpectedly-alarm?))
        ] ; friendly reminder not to take it lower than 7.5us
    (println "dir-val" dir-val)
    (println "move-by-pulses max calculated frequency (Hz): " (when (not (empty? precomputed-pulses)) (apply max precomputed-pulses)))
    (if (and can-run (compare-and-set! pulse-lock false true)) ; uses AND short-circuiting. Otherwise causes a lock leak.
      (do
        (println "Got the lock")
        (println "Current limit switch: " limit-switch)
        (println "Status atom" (get-in @state-atom [:status-atm]))
        (set-pin ena true)
        (java.util.concurrent.locks.LockSupport/parkNanos 5000) ; 5us wait required by driver
        (set-pin dir dir-val)
        (java.util.concurrent.locks.LockSupport/parkNanos 5000) ; 5us wait required by driver
        (try
          (doseq [[pulse-num pulse-val] (map-indexed (fn [idx itm] [idx itm]) precomputed-pulses)]
            (do
              (let [start-time (java.lang.System/nanoTime)
                    wait-time (hz-to-ns pulse-val)
                    tgt-one (+ start-time wait-time)]
                (when (:estop (deref (:status-atm @state-atom)))
                  (throw (ex-info "E-stop hit" {:cause :estop})))
                (when (limit-switch (deref (:status-atm @state-atom)))
                  (throw (ex-info "Limit switch hit" {:cause :limit-switch
                                                      :pulse-num pulse-num})))
                (set-pin pul true)
                (while (< (java.lang.System/nanoTime) tgt-one) nil) ; busy-wait
                (let [latency (- (java.lang.System/nanoTime) tgt-one)]
                      (when (> latency (* 1000 1000))
                        (println "latency high: " latency)))
                (let [now-two (java.lang.System/nanoTime)
                      tgt-two (+ now-two wait-time)]
                                        ;              (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))
                  (set-pin pul false)
                  (while (< (java.lang.System/nanoTime) tgt-two) nil))
                )
;              (java.util.concurrent.locks.LockSupport/parkNanos (hz-to-ns pulse-val))
              ))
          (catch Exception e (do
                               (when (limit-switch-hit-unexpected? current-position dir-val
                                                                   (:pulse-num (ex-data e))
                                                                   axis-upper-limit-in-pulses)
                                 (println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                                 (println "!!!!!!!!Limit switch hit unexpected!!!!!"
                                          current-position dir-val
                                          (:pulse-num (ex-data e)) axis-upper-limit-in-pulses)
                                 (println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                                 (slide-stainer.defs/set-limit-switch-hit-unexpectedly-alarm)
                                 )
                               (let [calculated-position (:pulse-num (ex-data e))] ; TODO fix calc
                                 (println (.getMessage e))
                                 (when (= :limit-switch
                                          (:cause (ex-data e)))
                                   (when (and dir-val (+ )))
                                   (reset! hit-limit-switch? e)))
                               )))
        (java.util.concurrent.locks.LockSupport/parkNanos nanosecond-wait)
        (set-pin ena false)
        (let [new-position (if @hit-limit-switch?
                             (if dir-val
                               axis-upper-limit-in-pulses
                               0)
                             (+ current-position
                                pulses))]
          (println "New position: " new-position (nil? @hit-limit-switch?) dir-val)
          (swap-in! state-atom [:setup id :position-in-pulses] new-position))
        (println "Attempting to release the lock: " pulse-lock)
        (when (not (compare-and-set! pulse-lock true false))
          (println "Someone messed with the lock")))
      (println "Couldn't get the lock on pulse, or wasn't able to run. can-run: " can-run " pulse-lock" pulse-lock))
    (not @hit-limit-switch?) ; returns true if all steps were taken, false if the move was interrupted by hitting a limit switch
    ))

(defn move-relative [id increment]
  (move-by-pulses id (inches-to-pulses id increment)))

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

(defn move-to-up-position []
  (move-to-position :stepperZ up-pos))

(defn move-to-down-position []
  (move-to-position :stepperZ down-pos))

(defn move-to-jar [jar]
  (println "move-to-jar: " jar (type jar) jar-positions (get jar-positions jar))
  (move-to-up-position)
  (move-to-position :stepperX (get jar-positions jar))
  (move-to-down-position))

(defn home []
  (slide-stainer.defs/clear-positioning)
  (move-relative :stepperZ up-pos)
  (move-relative :stepperX left-homing-pos))

(def sample-program
  {:name "H&E with Harris' Hematoxylin"
                                        ;   :jar-contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure-steps
   [{:substance "A" :time-in-seconds 10 :jar-number 1}
    {:substance "B" :time-in-seconds 5  :jar-number 2}
    {:substance "C" :time-in-seconds 15 :jar-number 3}]})

(def motion-test-program
  {:name "Motion Test Program"
   :repeat 5
   :procedure-steps
   [{:substance "B" :time-in-seconds 1 :jar-number 1}
    {:substance "C" :time-in-seconds 1 :jar-number 2}
    {:substance "D" :time-in-seconds 1 :jar-number 3}
    {:substance "E" :time-in-seconds 1 :jar-number 4}
    {:substance "F" :time-in-seconds 1 :jar-number 5}]})

(defn run-program [program]
  (doseq [repeat-time (range (or (:repeat program) 1))]
    (doseq [step (:procedure-steps program)]
      (move-to-jar (:jar-number step))
      (Thread/sleep (* 1000 (:time-in-seconds step)))))
  ; return to the up position so that the last step doesn't get excessive staining time
  (move-to-up-position))

(defn run-program-by-id [id]
  ;; TODO do an actual lookup here
  (run-program sample-program))

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

