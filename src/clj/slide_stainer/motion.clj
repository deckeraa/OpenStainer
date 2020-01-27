(ns slide-stainer.motion
  (:require [dvlopt.linux.gpio :as gpio]
            [incanter.core :refer [pow]]
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
  (move-to-up-position)
  (move-to-position :stepperX (get jar-positions jar))
  (move-to-down-position))
